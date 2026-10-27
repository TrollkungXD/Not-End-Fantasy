import java.awt.Color;
import java.util.*;

/**
 * Abstract enemy. Each monster owns a list of EnemyMoves (Composition) and picks one
 * every round. Subclasses decide the basic attack and special behaviour (Inheritance + Polymorphism).
 */
public abstract class Enemy extends Entity {
    private int reward;
    private final Color color;
    private final boolean boss;
    private boolean elite;
    private final List<EnemyMove> moves = new ArrayList<>();
    private EnemyMove intent, intent2, forced;
    private Hero taunter; private int tauntT;      // Samsara: must attack this hero
    private int fpDrain;                            // FP to steal from the party (Battle collects it)

    protected Enemy(String name, int hp, int def, int mdef, int atk, int matk, int reward, Color color, boolean boss) {
        super(name, hp, def, mdef, atk, matk);
        this.reward = reward; this.color = color; this.boss = boss;
    }
    public int getReward() { return reward; }
    public boolean isBoss() { return boss; }
    public boolean isElite() { return elite; }
    @Override public Color getColor() { return color; }
    @Override public String getName() { return elite ? "Elite " + super.getName() : super.getName(); }
    /** Name without prefixes, used to find the sprite. */
    public String getBaseName() { return super.getName(); }

    public Enemy with(EnemyMove... ms) { moves.addAll(Arrays.asList(ms)); return this; }
    public List<EnemyMove> getMoves() { return Collections.unmodifiableList(moves); }

    /** The plain attack every monster can always fall back on. */
    protected abstract EnemyMove basic();

    /** Stronger version found through events. */
    public Enemy promote() {
        elite = true;
        boolean magic = getMatk() > getAtk();
        gain(getMaxHp(), 2, 2, 4, 4);                  // double HP, +2 DEF/M.DEF, +4 ATK/M.ATK
        reward = reward * 3;
        with(new EnemyMove.Charge("Elite Overdrive", 2.2, magic, 2),
             new EnemyMove.Afflict("Crushing Presence", 0.7, magic, EnemyMove.Ailment.BREAK, 3, 2, 70, true, 2),
             new EnemyMove.Afflict("Paralyzing Glare", 0, false, EnemyMove.Ailment.STUN, 0, 1, 40, false, 1));
        return this;
    }

    // ---------------------------------------------------------------- FP drain
    void drainFp(int n) { fpDrain += n; }
    /** Battle calls this after the monster acts. */
    public int takeFpDrain() { int n = fpDrain; fpDrain = 0; return n; }
    /** How many moves this monster makes per round (bosses: 2 when enraged). */
    public int actionsPerRound() { return 1; }
    private int actions;                            // how many turns this monster has taken (move cooldowns use it)
    public int getActions() { return actions; }

    // ---------------------------------------------------------------- taunt
    public void tauntBy(Hero h, int turns) { taunter = h; tauntT = turns; }
    /** The hero this monster is forced to attack, or null. */
    public Hero getTaunter() { return tauntT > 0 && taunter != null && taunter.isAlive() ? taunter : null; }
    @Override public String tickStatus() { if (tauntT > 0 && --tauntT == 0) taunter = null; return super.tickStatus(); }
    @Override public List<String[]> statusInfo() {
        List<String[]> l = super.statusInfo();
        if (getTaunter() != null) l.add(new String[]{"TAUNT " + taunter.getName(), "Can only attack " + taunter.getName() + " (" + tauntT + " turns)."});
        if (elite) l.add(0, new String[]{"ELITE", "Elite monster: much stronger, extra moves, triple reward."});
        return l;
    }
    @Override public boolean hasAilment() { return super.hasAilment() || getTaunter() != null; }

    // ---------------------------------------------------------------- AI
    public EnemyMove getIntent() { return intent; }
    /** Second move of an enraged boss (shown in advance too), or null. */
    public EnemyMove getIntent2() { return intent2; }
    void setForced(EnemyMove m) { forced = m; }

    /** Chooses the move for this round (weighted random among usable moves). */
    public void plan(List<Hero> party, List<Enemy> allies, Random rng) {
        beforePlan();
        if (forced != null) { intent = forced; forced = null; }
        else intent = choose(party, allies, rng);
        intent2 = actionsPerRound() > 1 ? choose(party, allies, rng) : null;   // enraged boss: both moves are known in advance
    }
    /** Hook for subclasses (Boss checks for ENRAGE here so both moves can be shown). */
    protected void beforePlan() { }
    private EnemyMove choose(List<Hero> party, List<Enemy> allies, Random rng) {
        List<EnemyMove> pool = new ArrayList<>();
        pool.add(basic());
        for (EnemyMove m : moves) if (m.usable(this, party, allies)) pool.add(m);
        int total = 0;
        for (EnemyMove m : pool) total += m.getWeight();
        int roll = rng.nextInt(Math.max(1, total));
        for (EnemyMove m : pool) { roll -= m.getWeight(); if (roll < 0) return m; }
        return pool.get(0);
    }
    /** Before the 2nd action: use the move that was announced (or pick one if there wasn't any). */
    public void prepareSecond(List<Hero> party, List<Enemy> allies, Random rng) {
        if (intent2 != null) { intent = intent2; intent2 = null; }
        else intent = choose(party, allies, rng);
    }

    /** The enemy takes its turn and returns a log line. */
    public String act(List<Hero> party, List<Enemy> allies, Random rng) {
        actions++;
        if (consumeStun()) { intent = null; intent2 = null; return getName() + " is STUNNED and can't move!"; }
        String pre = beforeAct();
        if (intent == null || (!intent.usable(this, party, allies) && intent.getWeight() > 0)) plan(party, allies, rng);
        EnemyMove m = intent;
        intent = null;
        return pre + m.perform(this, party, allies, rng);
    }
    protected String beforeAct() { return ""; }

    // kinds
    public static class Physical extends Enemy {
        public Physical(String n, int hp, int def, int mdef, int atk, int reward, Color c) { super(n, hp, def, mdef, atk, 0, reward, c, false); }
        @Override protected EnemyMove basic() { return new EnemyMove.Strike("Attack", 1.0, false, 1, 4); }
    }

    public static class Magic extends Enemy {
        public Magic(String n, int hp, int def, int mdef, int matk, int reward, Color c) { super(n, hp, def, mdef, 0, matk, reward, c, false); }
        @Override protected EnemyMove basic() { return new EnemyMove.Strike("Spell", 1.0, true, 1, 4); }
    }

    /** Boss: mixes physical and magic, and becomes ENRAGED below half HP. */
    public static class Boss extends Enemy {
        private boolean enraged;
        public Boss(String n, int hp, int def, int mdef, int atk, int matk, int reward, Color c) { super(n, hp, def, mdef, atk, matk, reward, c, true); }
        @Override protected EnemyMove basic() {
            return getAtk() >= getMatk() ? new EnemyMove.Strike("Slash", 1.0, false, 1, 3) : new EnemyMove.Strike("Dark Bolt", 1.0, true, 1, 3);
        }
        public boolean isEnraged() { return enraged; }
        /** Phase 2: below half HP the boss acts twice per round. */
        @Override public int actionsPerRound() { return enraged ? 2 : 1; }
        private String enrageMsg = "";
        @Override protected void beforePlan() {
            if (!enraged && hpRatio() <= 0.5) enrageMsg = beforeActInner();
        }
        @Override protected String beforeAct() {
            String m = enrageMsg; enrageMsg = "";
            return m.isEmpty() ? beforeActInner() : m;
        }
        private String beforeActInner() {
            if (enraged || hpRatio() > 0.5) return "";
            enraged = true;

            cleanse();
            return getName() + " becomes ENRAGED! Bad effects removed, and it now acts TWICE per round!\n";
        }
        @Override public List<String[]> statusInfo() {
            List<String[]> l = super.statusInfo();
            if (enraged) l.add(0, new String[]{"ENRAGED", "Phase 2: acts twice per round (both moves are shown above it)."});
            else l.add(0, new String[]{"BOSS", "Below 50% HP it becomes ENRAGED and acts twice per round. Stuns only land half as often."});
            return l;
        }
    }
}
