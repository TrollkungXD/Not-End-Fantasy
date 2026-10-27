import java.util.*;

/** Abstract item. Each subclass has its own use() (Polymorphism). */
public abstract class Item {
    private final String name, desc;
    private final int price;

    protected Item(String name, String desc, int price) { this.name = name; this.desc = desc; this.price = price; }
    public String getName() { return name; }
    public String getDesc() { return desc; }
    public int getPrice() { return price; }
    public boolean needsTarget() { return false; }     // true -> pick an enemy
    public boolean usableOnMap() { return false; }
    public boolean instantOnBuy() { return false; }    // true -> applied right away in shop
    /** Can the item do something right now? */
    public boolean canUse(List<Hero> party) { return true; }
    public String tag() { return usableOnMap() ? "Map + Battle" : instantOnBuy() ? "Permanent" : "Battle only"; }
    /** Picture file in Item/ (null = default art). */
    public String getIcon() { return null; }

    /** user = hero using it, target = chosen enemy, party/enemies = the battle sides (enemies is empty on the map). */
    public abstract String use(Hero user, Enemy target, List<Hero> party, List<Enemy> enemies);

    public static Hero mostWounded(List<Hero> party, Hero fallback) {
        Hero best = null;
        for (Hero h : party) if (h.isAlive() && (best == null || h.hpRatio() < best.hpRatio())) best = h;
        return best == null ? fallback : best;
    }

    // ---------------------------------------------------------------- kinds
    /** Heal the most wounded ally (on the map: the hero you choose). */
    public static class Heal extends Item {
        private final int amount;
        public Heal(String n, String d, int price, int amount) { super(n, d, price); this.amount = amount; }
        @Override public boolean usableOnMap() { return true; }
        @Override public boolean canUse(List<Hero> party) { for (Hero h : party) if (h.isAlive() && h.getHp() < h.getMaxHp()) return true; return false; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) {
            Hero h = mostWounded(party, u);
            return h.getName() + " recovers " + h.heal(amount) + " HP.";
        }
    }

    /** Heal every living ally. */
    public static class PartyHeal extends Item {
        private final int amount;
        public PartyHeal(String n, String d, int price, int amount) { super(n, d, price); this.amount = amount; }
        @Override public boolean usableOnMap() { return true; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) {
            StringBuilder sb = new StringBuilder();
            for (Hero h : party) if (h.isAlive()) sb.append(h.getName()).append(" +").append(h.heal(amount)).append(" HP. ");
            return sb.toString().trim();
        }
    }

    /** Bring a fallen ally back in battle. */
    public static class Revive extends Item {
        private final double pct;
        public Revive(String n, String d, int price, double pct) { super(n, d, price); this.pct = pct; }
        @Override public boolean canUse(List<Hero> party) { for (Hero h : party) if (!h.isAlive()) return true; return false; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) {
            for (Hero h : party) if (!h.isAlive()) { h.revive((int) Math.round(h.getMaxHp() * pct)); return h.getName() + " is revived with " + h.getHp() + " HP!"; }
            return "Nobody needs it.";
        }
    }

    public static class Bomb extends Item {
        private final int dmg;
        public Bomb(String n, String d, int price, int dmg) { super(n, d, price); this.dmg = dmg; }
        @Override public boolean needsTarget() { return true; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) { return "The bomb hits " + t.getName() + " for " + t.takeTrueDamage(dmg) + " dmg!"; }
    }

    public static class MegaBomb extends Item {
        private final int dmg;
        public MegaBomb(String n, String d, int price, int dmg) { super(n, d, price); this.dmg = dmg; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) {
            StringBuilder sb = new StringBuilder("BOOM!");
            for (Enemy e : enemies) if (e.isAlive()) sb.append(" ").append(e.getName()).append(" -").append(e.takeTrueDamage(dmg));
            return sb.toString();
        }
    }

    /** Remove poison/weakness from the party and heal a little. */
    public static class Cleanse extends Item {
        private final int amount;
        public Cleanse(String n, String d, int price, int amount) { super(n, d, price); this.amount = amount; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) {
            for (Hero h : party) if (h.isAlive()) { h.cleanse(); h.heal(amount); }
            return "The party is cured and recovers " + amount + " HP.";
        }
    }

    /** Reset the user's cooldowns. */
    public static class Ether extends Item {
        public Ether(String n, String d, int price) { super(n, d, price); }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) { u.resetCooldowns(); return u.getName() + "'s skills are ready again!"; }
    }

    /** Temporary ATK/M.ATK boost for the user. */
    public static class Draught extends Item {
        private final int amount, turns;
        public Draught(String n, String d, int price, int amount, int turns) { super(n, d, price); this.amount = amount; this.turns = turns; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) { u.giveBoost(amount, turns); return u.getName() + " feels powerful! ATK/M.ATK +" + amount + "."; }
    }

    /** Temporary DEF/M.DEF boost for the whole party. */
    public static class Ward extends Item {
        private final int amount, turns;
        public Ward(String n, String d, int price, int amount, int turns) { super(n, d, price); this.amount = amount; this.turns = turns; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) {
            for (Hero h : party) if (h.isAlive()) h.giveBuff(amount, turns);
            return "A shining ward covers the party: DEF/M.DEF +" + amount + ".";
        }
    }

    /** Permanent stat upgrade, applied right away when bought. */
    public static class Tonic extends Item {
        private final Hero.Stat stat;
        public Tonic(String n, String d, int price, Hero.Stat stat) { super(n, d, price); this.stat = stat; }
        @Override public boolean instantOnBuy() { return true; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) {
            return u.upgrade(stat) ? u.getName() + "'s " + stat + " went up!" : "No effect - already at the limit.";
        }
    }

    /** Story item: can't be used, only handed to an NPC in an event. */
    public static class KeyItem extends Item {
        private final String icon;
        public KeyItem(String n, String d, int price, String icon) { super(n, d, price); this.icon = icon; }
        @Override public String getIcon() { return icon; }
        @Override public boolean canUse(List<Hero> party) { return false; }
        @Override public String tag() { return "Key item"; }
        @Override public String use(Hero u, Enemy t, List<Hero> party, List<Enemy> enemies) { return "Nothing happens. Maybe someone is looking for this..."; }
    }
}
