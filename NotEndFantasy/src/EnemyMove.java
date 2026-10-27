import java.util.*;
import java.util.function.Supplier;

/**
 * A move a monster can use. Enemies pick their next move at the start of each round,
 * and the battle screen shows it above their head (an "intent") so the player can react.
 * Every subclass performs differently (Polymorphism).
 */
public abstract class EnemyMove {
    public enum Kind { ATTACK, MAGIC, AOE, DEBUFF, BUFF, HEAL, CHARGE, SUMMON }

    private final String name;
    private final int weight;
    private String fx;                       // effect picture (null = chosen from the kind, see getFx)
    /** Optional: pick the effect picture yourself, e.g. new EnemyMove.Strike(...).fx("CROSS_RED") */
    public EnemyMove fx(String effect) { fx = effect; return this; }
    /** Effect shown on the heroes this move hits (names are in Assets.fx). */
    public String getFx() { return fx != null ? fx : defaultFx(); }
    /** Default picture for this kind of move; subclasses can choose a better one. */
    protected String defaultFx() {
        switch (getKind()) {
            case ATTACK: return "CROSS_RED";
            case MAGIC: return "ORB_RED";
            case AOE: return "RAYS_ORANGE";
            case DEBUFF: return "DARK_RING";
            case BUFF: return "RING_GOLD";
            case HEAL: return "AURA_GOLD";
            case CHARGE: return "STARBURST_GOLD";
            case SUMMON: return "WIND_SWIRL";
            default: return null;
        }
    }

    protected EnemyMove(String name, int weight) { this.name = name; this.weight = weight; }
    public String getName() { return name; }
    public int getWeight() { return weight; }
    public abstract Kind getKind();
    /** Text shown in the intent bubble. */
    public abstract String describe(Enemy self);
    public boolean usable(Enemy self, List<Hero> party, List<Enemy> allies) { return true; }
    /** Longer explanation for the tooltip on the intent bubble. */
    public String explain(Enemy self) {
        switch (getKind()) {
            case ATTACK: return describe(self) + " - physical attack on one hero (reduced by DEF).";
            case MAGIC: return describe(self) + " - magic attack on one hero (reduced by M.DEF).";
            case AOE: return describe(self) + " - hits the whole party.";
            case BUFF: return describe(self) + " - makes the monsters stronger.";
            case HEAL: return describe(self) + " - heals a wounded monster.";
            case CHARGE: return describe(self) + " - next round it unleashes a huge hit. Defend or shield up!";
            case SUMMON: return describe(self) + " - calls a minion into battle.";
            default: return describe(self) + " - weakens a hero.";
        }
    }
    public abstract String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng);

    // helpers
    protected static List<Hero> living(List<Hero> party) {
        List<Hero> l = new ArrayList<>();
        for (Hero h : party) if (h.isAlive()) l.add(h);
        return l;
    }
    /** Picks a single target: taunt first, hidden heroes are skipped, guardians take the hit. */
    protected static Hero pick(Enemy self, List<Hero> party, Random rng) {
        List<Hero> l = living(party);
        if (l.isEmpty()) return null;
        Hero t = self.getTaunter();
        if (t == null) {
            List<Hero> seen = new ArrayList<>();
            for (Hero h : l) if (!h.isConcealed()) seen.add(h);
            if (seen.isEmpty()) seen = l;
            t = seen.get(rng.nextInt(seen.size()));
        }
        return t.redirectTarget();
    }
    /**
     * One hit on a hero, with all the defensive tricks: dodge, evade + counter,
     * Hymn of Respite (nulls and reflects a spell) and thorns. Notes are added to the log.
     */
    protected static int hit(Enemy self, Hero t, int power, boolean magical, Random rng, StringBuilder note) {
        if (self.rollBlindMiss(rng)) { note.append(" ").append(self.getName()).append(" is BLIND and misses!"); return 0; }
        if (t.consumeDodge()) { note.append(" ").append(t.getName()).append(" DODGES!"); return 0; }
        if (t.rollEvade(rng)) {
            int c = self.takeDamage(t.basicPower(), t.basicIsMagic());
            note.append(" ").append(t.getName()).append(" evades and counters for ").append(c).append("!");
            return 0;
        }
        int reflect = magical ? t.consumeSpellWard() : 0;
        if (reflect > 0) {
            int r = self.takeDamage(reflect, true);
            note.append(" Hymn of Respite nullifies the spell! ").append(self.getName()).append(" takes ").append(r).append(".");
            return 0;
        }
        int d = t.takeDamage(power, magical);
        if (t.getThorns() > 0 && self.isAlive()) note.append(" Thorns: ").append(self.getName()).append(" takes ").append(self.takeTrueDamage(t.getThorns())).append(".");
        note.append(t.onEnemyHit());                                   // Safehaven / Samsara heals
        return d;
    }
    protected static int power(Enemy self, double mult, boolean magical) {
        return (int) Math.round((magical ? self.getMatk() : self.getAtk()) * mult);
    }
    protected static String fell(Hero h) { return h.isAlive() ? "." : " - " + h.getName() + " falls!"; }

    //  moves
    /** Single target hit, possibly several times. */
    public static class Strike extends EnemyMove {
        private final double mult; private final boolean magical; private final int hits;
        public Strike(String name, double mult, boolean magical, int hits, int weight) {
            super(name, weight); this.mult = mult; this.magical = magical; this.hits = hits;
        }
        @Override public Kind getKind() { return magical ? Kind.MAGIC : Kind.ATTACK; }
        @Override public String describe(Enemy self) {
            return getName() + " " + power(self, mult, magical) + (hits > 1 ? " x" + hits : "");
        }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            Hero t = pick(self, party, rng);
            if (t == null) return "";
            int total = 0; StringBuilder note = new StringBuilder();
            for (int i = 0; i < hits && t.isAlive() && self.isAlive(); i++) total += hit(self, t, power(self, mult, magical), magical, rng, note);
            return self.getName() + " uses " + getName() + " on " + t.getName() + ": " + total + (magical ? " magic" : "") + " dmg" + fell(t) + note;
        }
    }

    /** Hits every hero. */
    public static class Sweep extends EnemyMove {
        private final double mult; private final boolean magical;
        public Sweep(String name, double mult, boolean magical, int weight) { super(name, weight); this.mult = mult; this.magical = magical; }
        @Override public Kind getKind() { return Kind.AOE; }
        @Override public String describe(Enemy self) { return getName() + " " + power(self, mult, magical) + " ALL"; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            StringBuilder sb = new StringBuilder(self.getName() + " uses " + getName() + "!"), note = new StringBuilder();
            for (Hero h : living(party)) {
                if (!self.isAlive()) break;
                sb.append(" ").append(h.getName()).append(" -").append(hit(self, h, power(self, mult, magical), magical, rng, note)).append(h.isAlive() ? "" : " (falls)");
            }
            return sb.toString() + note;
        }
    }

    /** Hit and poison. */
    public static class Venom extends EnemyMove {
        @Override protected String defaultFx() { return "SHARDS_PINK"; }
        private final double mult; private final boolean magical; private final int dot, turns;
        public Venom(String name, double mult, boolean magical, int dot, int turns, int weight) {
            super(name, weight); this.mult = mult; this.magical = magical; this.dot = dot; this.turns = turns;
        }
        @Override public Kind getKind() { return Kind.DEBUFF; }
        @Override public String describe(Enemy self) { return getName() + " " + power(self, mult, magical) + " +Poison"; }
        @Override public String explain(Enemy self) { return describe(self) + " - hits one hero and POISONS: -" + dot + " HP each round for " + turns + " turns."; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            Hero t = pick(self, party, rng);
            if (t == null) return "";
            StringBuilder note = new StringBuilder();
            int d = hit(self, t, power(self, mult, magical), magical, rng, note);
            boolean landed = note.indexOf("DODGES") < 0 && note.indexOf("evades") < 0 && note.indexOf("nullifies") < 0;
            if (t.isAlive() && landed) t.givePoison(dot, turns);
            return self.getName() + " uses " + getName() + " on " + t.getName() + ": " + d + " dmg" + (t.isAlive() ? (landed ? " and POISON (" + dot + "/turn)." : ".") : fell(t)) + note;
        }
    }

    /** Lowers a hero's ATK and M.ATK. */
    public static class Weaken extends EnemyMove {
        @Override protected String defaultFx() { return "DARK_ORB"; }
        private final int amount, turns;
        public Weaken(String name, int amount, int turns, int weight) { super(name, weight); this.amount = amount; this.turns = turns; }
        @Override public Kind getKind() { return Kind.DEBUFF; }
        @Override public String describe(Enemy self) { return getName() + " (Weak -" + amount + ")"; }
        @Override public String explain(Enemy self) { return describe(self) + " - WEAK: one hero's ATK/M.ATK -" + amount + " for " + turns + " turns."; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            Hero t = pick(self, party, rng);
            if (t == null) return "";
            t.giveWeak(amount, turns);
            return self.getName() + " uses " + getName() + ": " + t.getName() + " is WEAKENED (ATK/M.ATK -" + amount + ").";
        }
    }

    /** Raises its own (or every ally's) DEF. */
    public static class Guard extends EnemyMove {
        private final int amount, turns; private final boolean team;
        public Guard(String name, int amount, int turns, boolean team, int weight) {
            super(name, weight); this.amount = amount; this.turns = turns; this.team = team;
        }
        @Override public Kind getKind() { return Kind.BUFF; }
        @Override public String describe(Enemy self) { return getName() + " (DEF +" + amount + (team ? " all)" : ")"); }
        @Override public boolean usable(Enemy self, List<Hero> party, List<Enemy> allies) { return self.getGuard() == 0; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            if (team) { for (Enemy e : allies) if (e.isAlive()) e.giveBuff(amount, turns); }
            else self.giveBuff(amount, turns);
            return self.getName() + " uses " + getName() + ": DEF/M.DEF +" + amount + (team ? " for all monsters." : ".");
        }
    }

    /** Raises every monster's attack. */
    public static class Rally extends EnemyMove {
        private final int amount, turns;
        public Rally(String name, int amount, int turns, int weight) { super(name, weight); this.amount = amount; this.turns = turns; }
        @Override public Kind getKind() { return Kind.BUFF; }
        @Override public String describe(Enemy self) { return getName() + " (ATK +" + amount + " all)"; }
        @Override public boolean usable(Enemy self, List<Hero> party, List<Enemy> allies) { return self.getBoost() == 0; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            for (Enemy e : allies) if (e.isAlive()) e.giveBoost(amount, turns);
            return self.getName() + " uses " + getName() + "! All monsters ATK/M.ATK +" + amount + ".";
        }
    }

    /** Heals the most wounded monster. Only chosen when someone is hurt. */
    public static class Mend extends EnemyMove {
        private final double pct;
        public Mend(String name, double pct, int weight) { super(name, weight); this.pct = pct; }
        @Override public Kind getKind() { return Kind.HEAL; }
        @Override public String describe(Enemy self) { return getName() + " (Heal)"; }
        private Enemy hurt(List<Enemy> allies) {
            Enemy best = null;
            for (Enemy e : allies) if (e.isAlive() && e.hpRatio() < 0.7 && (best == null || e.hpRatio() < best.hpRatio())) best = e;
            return best;
        }
        @Override public boolean usable(Enemy self, List<Hero> party, List<Enemy> allies) { return hurt(allies) != null; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            Enemy t = hurt(allies);
            if (t == null) t = self;
            return self.getName() + " uses " + getName() + ": " + t.getName() + " recovers " + t.healPct(pct) + " HP.";
        }
    }

    /**
     * Group heal + cleanse: every living monster loses ALL bad effects (poison, burn, bleed, stun, silence, ...)
     * and heals a % of its Max HP. The user heals selfPct instead (bosses have huge HP, so they use less).
     * Only chosen when someone is hurt or has a bad status, then waits `cooldown` of the user's turns.
     */
    public static class Restore extends EnemyMove {
        private final double pct, selfPct;
        private final int cooldown;
        private int readyAt;
        public Restore(String name, double pct, double selfPct, int cooldown, int weight) {
            super(name, weight); this.pct = pct; this.selfPct = selfPct; this.cooldown = cooldown;
        }
        public Restore(String name, double pct, int cooldown, int weight) { this(name, pct, pct, cooldown, weight); }
        @Override public Kind getKind() { return Kind.HEAL; }
        @Override public String describe(Enemy self) { return getName() + " (Heal all + Cleanse)"; }
        @Override public String explain(Enemy self) {
            return getName() + " - every monster is cured of ALL bad effects and heals " + Math.round(pct * 100) + "% Max HP"
                    + (selfPct != pct ? " (" + self.getName() + ": " + Math.round(selfPct * 100) + "%)" : "") + ".";
        }
        @Override protected String defaultFx() { return "HEAL_GREEN"; }
        @Override public boolean usable(Enemy self, List<Hero> party, List<Enemy> allies) {
            if (self.getActions() < readyAt) return false;
            for (Enemy e : allies) if (e.isAlive() && (e.hpRatio() < 0.85 || e.hasBadStatus())) return true;
            return false;
        }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            readyAt = self.getActions() + cooldown;
            StringBuilder sb = new StringBuilder(self.getName() + " uses " + getName() + "! All monsters are cleansed.");
            for (Enemy e : allies) {
                if (!e.isAlive()) continue;
                e.cleanse();
                int h = e.healPct(e == self ? selfPct : pct);
                if (h > 0) sb.append(" ").append(e.getName()).append(" +").append(h).append(" HP.");
            }
            return sb.toString();
        }
    }

    /** Hit and heal for half the damage. */
    public static class Drain extends EnemyMove {
        @Override protected String defaultFx() { return "RING_BLOOD"; }
        private final double mult; private final boolean magical;
        public Drain(String name, double mult, boolean magical, int weight) { super(name, weight); this.mult = mult; this.magical = magical; }
        @Override public Kind getKind() { return magical ? Kind.MAGIC : Kind.ATTACK; }
        @Override public String describe(Enemy self) { return getName() + " " + power(self, mult, magical) + " +Drain"; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            Hero t = pick(self, party, rng);
            if (t == null) return "";
            StringBuilder note = new StringBuilder();
            int d = hit(self, t, power(self, mult, magical), magical, rng, note);
            int healed = self.heal((d + 1) / 2);
            return self.getName() + " uses " + getName() + " on " + t.getName() + ": " + d + " dmg, heals " + healed + fell(t) + note;
        }
    }

    /** Spends a turn charging, then is forced to unleash a huge hit next round. */
    public static class Charge extends EnemyMove {
        private final String release; private final double mult; private final boolean magical;
        public Charge(String release, double mult, boolean magical, int weight) {
            super("Charging", weight); this.release = release; this.mult = mult; this.magical = magical;
        }
        @Override public Kind getKind() { return Kind.CHARGE; }
        @Override public String describe(Enemy self) { return "Charging " + release + "!"; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            self.setForced(new Strike("!! " + release, mult, magical, 1, 0));
            return self.getName() + " is gathering power for " + release + "... Defend or guard up!";
        }
    }

    /** Calls a minion while there is room. */
    public static class Summon extends EnemyMove {
        private final Supplier<Enemy> minion; private final int max;
        public Summon(String name, Supplier<Enemy> minion, int max, int weight) { super(name, weight); this.minion = minion; this.max = max; }
        @Override public Kind getKind() { return Kind.SUMMON; }
        @Override public String describe(Enemy self) { return getName() + " (Summon)"; }
        @Override public boolean usable(Enemy self, List<Hero> party, List<Enemy> allies) {
            int alive = 0;
            for (Enemy e : allies) if (e.isAlive()) alive++;
            return alive < max;
        }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            Enemy m = minion.get();
            allies.add(m);
            return self.getName() + " uses " + getName() + ": a " + m.getName() + " appears!";
        }
    }

    /** Kamikaze: hits everyone hard, then the monster is destroyed. */
    public static class Explode extends EnemyMove {
        private final double mult;
        public Explode(String name, double mult, int weight) { super(name, weight); this.mult = mult; }
        @Override public Kind getKind() { return Kind.AOE; }
        @Override public String describe(Enemy self) { return getName() + " " + power(self, mult, false) + " ALL (self-destruct)"; }
        @Override public boolean usable(Enemy self, List<Hero> party, List<Enemy> allies) { return self.hpRatio() < 0.75; }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            StringBuilder sb = new StringBuilder(self.getName() + " uses " + getName() + " and explodes!"), note = new StringBuilder();
            int p = power(self, mult, false);
            for (Hero h : living(party)) sb.append(" ").append(h.getName()).append(" -").append(hit(self, h, p, false, rng, note));
            self.takeTrueDamage(self.getHp());
            return sb.toString() + note;
        }
    }

    // ================================================================ status effects
    public enum Ailment { STUN, FREEZE, SILENCE, BLIND, BREAK, BURN, POISON, CURSE, WEAK }

    /** Puts a status effect on a hero. Returns true if it landed. */
    protected static boolean inflict(Hero h, Ailment a, int amount, int turns) {
        switch (a) {
            case STUN: return h.giveStun(1);
            case FREEZE: return h.giveFreeze(1);
            case SILENCE: h.giveSilence(turns); return true;
            case BLIND: h.giveBlind(turns); return true;
            case BREAK: h.giveBreak(amount, turns); return true;
            case BURN: h.giveBurn(amount, turns); return true;
            case POISON: h.givePoison(amount, turns); return true;
            case CURSE: h.giveCurse(turns); return true;
            default: h.giveWeak(amount, turns); return true;
        }
    }

    /**
     * Hit (optional, mult 0 = no damage) + a chance to inflict a status, on one hero or the whole party.
     * Examples: Frozen Gale (FREEZE), Eclipse (SILENCE all), Armor Rend (BREAK).
     */
    public static class Afflict extends EnemyMove {
        private final double mult; private final boolean magical, aoe;
        private final Ailment ailment; private final int amount, turns, chance;
        public Afflict(String name, double mult, boolean magical, Ailment ailment, int amount, int turns, int chance, boolean aoe, int weight) {
            super(name, weight);
            this.mult = mult; this.magical = magical; this.ailment = ailment; this.amount = amount;
            this.turns = turns; this.chance = chance; this.aoe = aoe;
        }
        @Override public Kind getKind() { return aoe && mult > 0 ? Kind.AOE : mult > 0 ? (magical ? Kind.MAGIC : Kind.ATTACK) : Kind.DEBUFF; }
        @Override protected String defaultFx() {
            switch (ailment) {
                case STUN: return "BOLT_FORK";
                case FREEZE: return "STARDUST_CYAN";
                case SILENCE: return "DARK_RING";
                case BLIND: return "SMOKE_GRAY";
                case BREAK: return "SHARDS_DARK";
                case BURN: return "FIRE_PILLAR";
                case POISON: return "SHARDS_PINK";
                case CURSE: return "SMOKE_RED";
                case WEAK: return "DARK_ORB";
                default: return super.defaultFx();
            }
        }
        @Override public String explain(Enemy self) {
            String what;
            switch (ailment) {
                case STUN: what = "STUN: skips the next turn"; break;
                case FREEZE: what = "FREEZE: skips the next turn and DEF -3"; break;
                case SILENCE: what = "SILENCE: can't use skills for " + turns + " turns"; break;
                case BLIND: what = "BLIND: 50% of attacks miss for " + turns + " turns"; break;
                case BREAK: what = "ARMOR BREAK: DEF and M.DEF -" + amount + " for " + turns + " turns"; break;
                case BURN: what = "BURN: loses " + amount + " HP each round for " + turns + " turns"; break;
                case POISON: what = "POISON: loses " + amount + " HP each round for " + turns + " turns"; break;
                case CURSE: what = "CURSE: healing received is halved for " + turns + " turns"; break;
                default: what = "WEAK: ATK/M.ATK -" + amount + " for " + turns + " turns"; break;
            }
            return describe(self) + " - " + (mult > 0 ? "hits and " : "") + (aoe ? "targets the WHOLE party" : "targets one hero")
                    + ", " + chance + "% chance of " + what + ".";
        }
        @Override public String describe(Enemy self) {
            String amt = (ailment == Ailment.BREAK || ailment == Ailment.WEAK || ailment == Ailment.BURN || ailment == Ailment.POISON) ? " " + amount : "";
            return getName() + (mult > 0 ? " " + power(self, mult, magical) : "") + (aoe ? " ALL" : "") + " +" + ailment + amt + (chance < 100 ? " " + chance + "%" : "");
        }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            List<Hero> targets = new ArrayList<>();
            if (aoe) targets.addAll(living(party)); else { Hero t = pick(self, party, rng); if (t != null) targets.add(t); }
            StringBuilder sb = new StringBuilder(self.getName() + " uses " + getName() + "!"), note = new StringBuilder();
            for (Hero h : targets) {
                if (!self.isAlive()) break;
                sb.append(" ").append(h.getName());
                if (mult > 0) {
                    StringBuilder n = new StringBuilder();
                    int d = hit(self, h, power(self, mult, magical), magical, rng, n);
                    note.append(n);
                    sb.append(" -").append(d);
                    if (n.length() > 0) continue;                      // dodged / nullified: no status either
                }
                if (h.isAlive() && rng.nextInt(100) < chance && inflict(h, ailment, amount, turns)) sb.append(" [").append(ailment).append("]");
                else if (h.isAlive()) sb.append(" (resisted)");
            }
            return sb.toString() + note;
        }
    }

    /** Hits and steals Fantasy Points from the party. */
    public static class FpDrain extends EnemyMove {
        @Override protected String defaultFx() { return "BURST_PRISM"; }
        private final double amount; private final double mult; private final boolean magical;
        /** amount may have a fraction: 1.5 = drains 1 FP, plus a 50% chance of 1 more. */
        public FpDrain(String name, double amount, double mult, boolean magical, int weight) { super(name, weight); this.amount = amount; this.mult = mult; this.magical = magical; }
        private String amountText() { return amount == Math.floor(amount) ? String.valueOf((int) amount) : String.valueOf(amount); }
        @Override public Kind getKind() { return Kind.DEBUFF; }
        @Override public String describe(Enemy self) { return getName() + " " + power(self, mult, magical) + " -" + amountText() + " FP"; }
        @Override public String explain(Enemy self) { return describe(self) + " - hits one hero and steals " + amountText() + " Fantasy Points from the party"
                + (amount == Math.floor(amount) ? "." : " (the fraction is the chance of 1 extra)."); }
        @Override public String perform(Enemy self, List<Hero> party, List<Enemy> allies, Random rng) {
            Hero t = pick(self, party, rng);
            if (t == null) return "";
            StringBuilder note = new StringBuilder();
            int d = hit(self, t, power(self, mult, magical), magical, rng, note);
            int n = (int) Math.floor(amount) + (rng.nextDouble() < amount - Math.floor(amount) ? 1 : 0);
            self.drainFp(n);
            return self.getName() + " uses " + getName() + " on " + t.getName() + ": " + d + " dmg" + fell(t) + " and drains " + n + " FP!" + note;
        }
    }
}
