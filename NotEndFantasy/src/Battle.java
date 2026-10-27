import java.util.*;

/**
 * Battle rules without any drawing (BattlePanel shows it, Battle decides what happens).
 * Round = every living hero acts once, then every monster acts, then effects tick.
 */
public class Battle {
    private final List<Hero> party;
    private final List<Enemy> enemies;
    private final Random rng = new Random();
    private int round = 1;
    private int fp = GameState.MAX_FP;     // Fantasy Points: shared by the party, every battle starts full

    public Battle(List<Hero> party, List<Enemy> enemies) {
        this.party = party; this.enemies = enemies;
        for (Hero h : party) h.startBattle();
        planEnemies();
    }
    public List<Hero> getParty() { return party; }
    public List<Enemy> getEnemies() { return enemies; }
    public int getRound() { return round; }
    public int getFp() { return fp; }
    public boolean canUse(Hero h, Skill s) { return !h.isSilenced() && s.isReady() && fp >= s.getFpCost() && s.canUse(h, party); }

    public List<Hero> livingHeroes() { List<Hero> l = new ArrayList<>(); for (Hero h : party) if (h.isAlive()) l.add(h); return l; }
    public List<Enemy> livingEnemies() { List<Enemy> l = new ArrayList<>(); for (Enemy e : enemies) if (e.isAlive()) l.add(e); return l; }
    public boolean won() { return livingEnemies().isEmpty(); }
    public boolean lost() { return livingHeroes().isEmpty(); }

    private void planEnemies() { for (Enemy e : livingEnemies()) e.plan(party, enemies, rng); }

    //hero actions
    public static final int MAX_ENEMIES = 5;

    public String attack(Hero h, Enemy t) {
        if (h.rollBlindMiss(rng)) return h.getName() + " is BLIND and misses!";
        // Bloody Opus: the attack also hits random other enemies
        List<Enemy> targets = new ArrayList<>(); targets.add(t);
        List<Enemy> others = livingEnemies(); others.remove(t); Collections.shuffle(others, rng);
        for (int i = 0; i < h.basicTargets() - 1 && i < others.size(); i++) targets.add(others.get(i));
        StringBuilder sb = new StringBuilder(h.getName() + " uses " + h.basicName() + ":");
        for (Enemy e : targets) {
            int d = e.takeDamage(h.basicPower(), h.basicIsMagic());
            sb.append(" ").append(e.getName()).append(" -").append(d).append(e.isAlive() ? "" : " (defeated)").append(".");
        }
        String extra = h.afterAttack(targets);
        return extra.isEmpty() ? sb.toString() : sb + " " + extra;
    }
    public static final int GUARD_FP = 1;      // Defend also restores FP

    public String defend(Hero h) {
        h.giveBuff(4, 1);
        int before = fp;
        fp = Math.min(GameState.MAX_FP, fp + GUARD_FP);
        return h.getName() + " defends! DEF/M.DEF +4 until the next round" + (fp > before ? " and +" + (fp - before) + " FP." : ".");
    }
    public String skill(Hero h, Skill s, Enemy t, Hero ally) { return skill(h, s, t, null, ally); }
    /** t2 = second chosen enemy for two-target skills. */
    public String skill(Hero h, Skill s, Enemy t, Enemy t2, Hero ally) {
        if (fp < s.getFpCost()) return "Not enough FP.";
        if (h.isSilenced()) return h.getName() + " is SILENCED and can't use skills.";
        fp -= s.getFpCost();
        return s.use(h, t, t2, ally, enemies, party) + "  (-" + s.getFpCost() + " FP)";
    }
    /** Heroes that can be chosen for an ally-target skill. */
    public List<Hero> allyChoices(Hero user, Skill s) {
        List<Hero> l = new ArrayList<>();
        for (Hero h : livingHeroes()) if (!(s.allyExcludesSelf() && h == user)) l.add(h);
        return l;
    }
    public String item(Hero h, Item it, Enemy t, Inventory inv) {
        inv.remove(it);
        return h.getName() + " uses " + it.getName() + ". " + it.use(h, t, party, enemies);
    }

    //  enemy phase
    /** One monster's turn. Enraged bosses act twice; FP drain is applied here. */
    public String enemyAct(Enemy e) {
        if (!e.isAlive()) return "";
        boolean stunned = e.isStunned();
        StringBuilder sb = new StringBuilder(e.act(party, enemies, rng));
        for (int i = 1; !stunned && i < e.actionsPerRound() && e.isAlive() && !lost(); i++) {
            e.prepareSecond(party, enemies, rng);
            sb.append('\n').append(e.act(party, enemies, rng));
        }
        int drain = e.takeFpDrain();
        if (drain > 0) fp = Math.max(0, fp - drain);
        return sb.toString();
    }

    /** Ticks poison/buffs/cooldowns, then monsters choose their next move. */
    public String endRound() {
        StringBuilder sb = new StringBuilder();
        for (Hero h : party) {
            String p = h.passiveRoundEnd(); if (!p.isEmpty()) sb.append(p).append('\n');
            String s = h.tickStatus(); if (!s.isEmpty()) sb.append(s).append('\n'); h.tickSkills();
        }
        for (Enemy e : enemies) { String s = e.tickStatus(); if (!s.isEmpty()) sb.append(s).append('\n'); }
        round++;
        fp = Math.min(GameState.MAX_FP, fp + GameState.FP_PER_ROUND);
        planEnemies();
        return sb.toString().trim();
    }

    public int reward() { int m = 0; for (Enemy e : enemies) m += e.getReward(); return m; }

    /** After a win, fallen heroes get back up with 1 HP and effects are cleared. */
    public void finish() { for (Hero h : party) { h.revive(1); h.clearStatus(); } }
}
