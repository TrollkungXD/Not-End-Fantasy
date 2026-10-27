import java.util.*;

/**
 * A permanent trait a hero keeps for the rest of the run (reward from beating Yato).
 * Each subclass decides what happens at the start of a battle and/or at the end of a round (Polymorphism).
 * Numbers are easy to change in rewards() at the bottom.
 */
public abstract class Passive {
    private final String name, tag, desc;
    protected Passive(String name, String tag, String desc) { this.name = name; this.tag = tag; this.desc = desc; }
    public String getName() { return name; }
    /** Short label for the status chip in battle. */
    public String getTag() { return tag; }
    public String getDesc() { return desc; }

    /** Called when a battle begins (after the hero's old effects were cleared). */
    public void onBattleStart(Hero h) {}
    /** Called at the end of every round while the hero is alive. Returns a log line or "". */
    public String onRoundEnd(Hero h) { return ""; }

    // ================================================================ the passives
    /** ATK and M.ATK up for the whole battle. */
    public static class Sharpness extends Passive {
        private final int amount;
        public Sharpness(String name, int amount) { super(name, "EDGE", "Every battle starts with ATK/M.ATK +" + amount + " for the whole battle."); this.amount = amount; }
        @Override public void onBattleStart(Hero h) { h.giveBoost(amount, 99); }
    }
    /** Starts every battle hard to hit. */
    public static class Footwork extends Passive {
        private final int pct, turns;
        public Footwork(String name, int pct, int turns) { super(name, "IAI", "Every battle starts with Dodge (the first hit on you misses) and " + pct + "% Evade for " + turns + " turns."); this.pct = pct; this.turns = turns; }
        @Override public void onBattleStart(Hero h) { h.giveDodge(1); h.giveEvade(pct, turns); }
    }
    /** Heals a little every round. */
    public static class Vigor extends Passive {
        private final int amount;
        public Vigor(String name, int amount) { super(name, "VIGOR", "Recover " + amount + " HP at the end of every round."); this.amount = amount; }
        @Override public String onRoundEnd(Hero h) {
            int got = h.heal(amount);
            return got > 0 ? h.getName() + " recovers " + got + " HP (" + getName() + ")." : "";
        }
    }
    /** Starts every battle with a shield and DEF/M.DEF up. */
    public static class Resolve extends Passive {
        private final int shield, guard, turns;
        public Resolve(String name, int shield, int guard, int turns) {
            super(name, "RESOLVE", "Every battle starts with a " + shield + " damage Shield and DEF/M.DEF +" + guard + " for " + turns + " turns.");
            this.shield = shield; this.guard = guard; this.turns = turns;
        }
        @Override public void onBattleStart(Hero h) { h.giveShield(shield, turns); h.giveBuff(guard, turns); }
    }

    /** The rewards Yato offers after you defeat her. Change the numbers here. */
    public static List<Passive> rewards() {
        return Arrays.asList(
            new Sharpness("Moonlit Edge", 3),
            new Footwork("Iaido Stance", 30, 3),
            new Vigor("Wanderer's Vigor", 3),
            new Resolve("Unbroken Resolve", 10, 2, 3));
    }
}
