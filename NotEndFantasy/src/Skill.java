import java.util.*;

/** Abstract skill. Each subclass implements apply() its own way (Polymorphism). */
public abstract class Skill {
    /** ENEMY = pick one, ALL_ENEMIES, SELF, ALLY = most wounded ally (auto),
     *  PARTY = every living ally, PICK_ALLY = the player chooses an ally. */
    public enum Target { ENEMY, ALL_ENEMIES, SELF, ALLY, PARTY, PICK_ALLY }
    public enum Guard { BOTH, DEF, MDEF }
    protected static final Random RNG = new Random();

    private final String name, desc;
    private final int cooldown;
    private final Target target;
    private int cdLeft;
    private int fpCost = 1;          // Fantasy Point cost (shared by the party, minimum 1)
    private String fx;               // effect picture shown when used (see Assets.fx)

    protected Skill(String name, String desc, int cooldown, Target target) {
        this.name = name; this.desc = desc; this.cooldown = cooldown; this.target = target;
    }
    /** Fluent setters used when building the roster: new Skill.X(...).fp(3).fx("CROSS_GOLD") */
    public Skill fp(int cost) { fpCost = Math.max(1, cost); return this; }
    public Skill fx(String effect) { fx = effect; return this; }
    public int getFpCost() { return fpCost; }
    public String getFx() { return fx; }
    public String getName() { return name; }
    public String getDesc() { return desc; }
    public int getCooldown() { return cooldown; }
    public int getCooldownLeft() { return cdLeft; }
    public Target getTarget() { return target; }
    public boolean needsTarget() { return target == Target.ENEMY; }
    public boolean needsAlly() { return target == Target.PICK_ALLY; }
    /** Skill that asks for TWO enemies (Irene - Sword and Handcannon). */
    public boolean needsTwoTargets() { return false; }
    /** Skill that asks for an enemy AND an ally (Shu - Bountiful Harvest). */
    public boolean needsAllyToo() { return false; }
    /** The second chosen enemy (only for needsTwoTargets skills). */
    protected Enemy second;
    /** True when the user can't pick themselves (Last Line of Defense). */
    public boolean allyExcludesSelf() { return false; }
    /** Extra condition, e.g. needs a second hero. */
    public boolean canUse(Hero user, List<Hero> party) {
        if (!allyExcludesSelf()) return true;
        for (Hero h : party) if (h != user && h.isAlive()) return true;
        return false;
    }
    public boolean isReady() { return cdLeft == 0; }
    public void tick() { if (cdLeft > 0) cdLeft--; }
    public void resetCooldown() { cdLeft = 0; }
    public String targetLabel() {
        switch (target) {
            case ENEMY: return "pick enemy";
            case ALL_ENEMIES: return "all enemies";
            case ALLY: return "ally";
            case PICK_ALLY: return "pick ally";
            case PARTY: return "party";
            default: return "self";
        }
    }

    public final String use(Hero user, Enemy chosen, Enemy second, Hero ally, List<Enemy> enemies, List<Hero> party) {
        this.second = second;
        return use(user, chosen, ally, enemies, party);
    }
    public final String use(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
        cdLeft = cooldown;
        return apply(user, chosen, ally, enemies, party).replaceAll(" {2,}", " ").trim();
    }
    protected abstract String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party);

    // helpers
    protected List<Hero> affected(Hero user, Hero ally, List<Hero> party) {
        List<Hero> out = new ArrayList<>();
        switch (target) {
            case PARTY: for (Hero h : party) if (h.isAlive()) out.add(h); break;
            case ALLY: out.add(Item.mostWounded(party, user)); break;
            case PICK_ALLY: out.add(ally != null ? ally : user); break;
            default: out.add(user);
        }
        return out;
    }
    protected static String names(List<Hero> hs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hs.size(); i++) sb.append(i > 0 ? ", " : "").append(hs.get(i).getName());
        return sb.toString();
    }
    protected static List<Enemy> living(List<Enemy> es) { List<Enemy> l = new ArrayList<>(); for (Enemy e : es) if (e.isAlive()) l.add(e); return l; }
    protected String head(Hero user) { return user.getName() + " uses " + name + ": "; }
    /** Stun roll. Bosses resist: their chance is halved. */
    protected static boolean stun(Enemy e, int chance) {
        if (!e.isAlive()) return false;
        int c = e.isBoss() ? chance / 2 : chance;
        if (RNG.nextInt(100) < c) return e.giveStun(1);
        return false;
    }

    // damage skills
    public static class Damage extends Skill {
        private final double mult;
        private final boolean magical;
        private final int hits;

        public Damage(String name, String desc, int cd, Target t, double mult, boolean magical, int hits) {
            super(name, desc, cd, t);
            this.mult = mult; this.magical = magical; this.hits = hits;
        }
        protected double multFor(Hero user, Enemy e) { return mult; }
        /** Extra power added on top of stat x mult (e.g. + M.ATK). 0 by default. */
        protected int bonusPower(Hero user, Enemy e) { return 0; }
        protected int strike(Hero user, Enemy e) {
            if (user.rollBlindMiss(RNG)) return 0;           // BLIND: this hit misses
            int power = (int) Math.round((magical ? user.getMatk() : user.getAtk()) * multFor(user, e)) + bonusPower(user, e);
            return e.takeDamage(power, magical);
        }
        /** Extra effect on each target that survived. */
        protected String after(Hero user, Enemy e) { return ""; }
        protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            if (getTarget() == Target.ALL_ENEMIES) return living(enemies);
            List<Enemy> l = new ArrayList<>(); l.add(chosen); return l;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            List<Enemy> ts = targets(user, chosen, enemies);
            StringBuilder sb = new StringBuilder(head(user));
            for (Enemy e : ts) {
                int total = 0;
                for (int i = 0; i < hits && e.isAlive(); i++) total += strike(user, e);
                sb.append(e.getName()).append(" -").append(total).append(e.isAlive() ? "" : " (defeated)");
                String extra = e.isAlive() ? after(user, e) : "";
                sb.append(extra.isEmpty() ? "" : " " + extra).append(". ");
            }
            return sb + user.afterAttack(ts);
        }
    }

    /** Hits the chosen enemy plus random other enemies. */
    public static class MultiHit extends Damage {
        private final int count;
        public MultiHit(String name, String desc, int cd, int count, double mult, boolean magical) {
            super(name, desc, cd, Target.ENEMY, mult, magical, 1); this.count = count;
        }
        @Override protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            List<Enemy> others = living(enemies); others.remove(chosen);
            Collections.shuffle(others, RNG);
            List<Enemy> l = new ArrayList<>(); l.add(chosen);
            for (int i = 0; i < count - 1 && i < others.size(); i++) l.add(others.get(i));
            return l;
        }
    }

    /** Damage with a chance to stun. */
    public static class StunStrike extends Damage {
        private final int chance;
        public StunStrike(String name, String desc, int cd, double mult, boolean magical, int chance) {
            super(name, desc, cd, Target.ENEMY, mult, magical, 1); this.chance = chance;
        }
        @Override protected String after(Hero user, Enemy e) { return stun(e, chance) ? "- STUNNED" : "(no stun)"; }
    }

    /** Damage + poison. */
    public static class Venom extends Damage {
        private final int dot, turns;
        public Venom(String name, String desc, int cd, double mult, int dot, int turns) {
            super(name, desc, cd, Target.ENEMY, mult, false, 1); this.dot = dot; this.turns = turns;
        }
        @Override protected String after(Hero user, Enemy e) { e.givePoison(dot, turns); return "- POISONED"; }
    }

    /** Damage + heal for part of it. */
    public static class Drain extends Damage {
        private final double ratio;
        public Drain(String name, String desc, int cd, double mult, double ratio) {
            super(name, desc, cd, Target.ENEMY, mult, false, 1); this.ratio = ratio;
        }
        @Override protected int strike(Hero user, Enemy e) { int d = super.strike(user, e); user.heal((int) Math.round(d * ratio)); return d; }
    }

    /** Irene - Gust: hit and make the enemy AIRBORNE. */
    public static class Gust extends Damage {
        private final int turns;
        public Gust(String name, String desc, int cd, double mult, boolean magical, int turns) { super(name, desc, cd, Target.ENEMY, mult, magical, 1); this.turns = turns; }
        private boolean selfHeal;
        /** The user also heals (ATK + M.ATK) / 2. */
        public Gust healSelf() { selfHeal = true; return this; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            int h = selfHeal ? user.heal((user.getAtk() + user.getMatk()) / 2) : 0;
            return h > 0 ? s + " " + user.getName() + " recovers " + h + " HP." : s;
        }
        @Override protected String after(Hero user, Enemy e) { e.giveAirborne(turns); return "- AIRBORNE"; }
    }

    /** Irene - Shattertide: hit and gain DEF. */
    public static class GuardStrike extends Damage {
        private final int def, turns;
        public GuardStrike(String name, String desc, int cd, double mult, boolean magical, int def, int turns) {
            super(name, desc, cd, Target.ENEMY, mult, magical, 1); this.def = def; this.turns = turns;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            user.giveBuff(def, turns);
            return super.apply(user, chosen, ally, enemies, party) + " " + user.getName() + " DEF/M.DEF +" + def + ".";
        }
    }

    /** Irene - Judgment: huge hit on everyone, then she is stunned. */
    public static class Judgment extends Damage {
        private final double airMult;
        public Judgment(String name, String desc, int cd, double mult) { this(name, desc, cd, mult, mult); }
        /** airMult = multiplier against AIRBORNE enemies. */
        public Judgment(String name, String desc, int cd, double mult, double airMult) { super(name, desc, cd, Target.ALL_ENEMIES, mult, false, 1); this.airMult = airMult; }
        @Override protected double multFor(Hero user, Enemy e) { return e.isAirborne() ? airMult : super.multFor(user, e); }
        private int bleedChance, bleed, bleedTurns, shield, shieldTurns;
        /** Each enemy hit has chance% to BLEED (amount HP/turn and ATK/M.ATK -amount). */
        public Judgment bleed(int chance, int amount, int turns) { bleedChance = chance; bleed = amount; bleedTurns = turns; return this; }
        /** The user gets a shield after the attack. */
        public Judgment shield(int amount, int turns) { shield = amount; shieldTurns = turns; return this; }
        @Override protected String after(Hero user, Enemy e) {
            if (bleedChance <= 0) return "";
            if (RNG.nextInt(100) >= bleedChance) return "(no bleed)";
            e.giveBleed(bleed, bleedTurns);
            return "- BLEED";
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            user.giveStun(1);
            if (shield > 0) { user.giveShield(shield, shieldTurns); s += " " + user.getName() + " gains a " + shield + " damage shield."; }
            return s + " " + user.getName() + " is exhausted (STUNNED next turn).";
        }
    }

    /** Logos - Synecdoche: stronger against enemies with a bad status. */
    public static class Synecdoche extends Damage {
        private final double low;
        public Synecdoche(String name, String desc, int cd, double high, double low) { super(name, desc, cd, Target.ALL_ENEMIES, high, true, 1); this.low = low; }
        @Override protected double multFor(Hero user, Enemy e) { return e.hasAilment() ? super.multFor(user, e) : low; }
    }

    /** Logos - Extended Acuity: big hit, costs half his HP (never kills him). */
    public static class Sacrifice extends Damage {
        public Sacrifice(String name, String desc, int cd, double mult) { super(name, desc, cd, Target.ENEMY, mult, true, 1); }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            int cost = user.getHp() / 2;
            user.loseHpKeep1(cost);
            return s + " " + user.getName() + " pays " + cost + " HP.";
        }
    }

    /** Shu - Bountiful Harvest: hit and heal herself. */
    public static class Harvest extends Damage {
        private final int heal;
        public Harvest(String name, String desc, int cd, double mult, int heal) { super(name, desc, cd, Target.ENEMY, mult, true, 1); this.heal = heal; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            return s + " " + user.getName() + " +" + user.heal(heal) + " HP.";
        }
    }

    /** Ingrid - Damoclean Interrogation: up to N random enemies + DEF for a chosen ally. */
    public static class Interrogation extends Damage {
        private final int count, def, turns;
        public Interrogation(String name, String desc, int cd, double mult, int count, int def, int turns) {
            super(name, desc, cd, Target.PICK_ALLY, mult, false, 1); this.count = count; this.def = def; this.turns = turns;
        }
        @Override protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            List<Enemy> l = living(enemies); Collections.shuffle(l, RNG);
            return new ArrayList<>(l.subList(0, Math.min(count, l.size())));
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            Hero a = ally != null ? ally : user;
            a.giveDefUp(def, turns);
            return super.apply(user, chosen, ally, enemies, party) + " " + a.getName() + " DEF +" + def + ".";
        }
    }

    /** Fixed damage that ignores DEF: main to the target (or everyone), splash to the rest (Vina). */
    public static class FlatBlast extends Skill {
        private final int main, splash;
        public FlatBlast(String name, String desc, int cd, Target t, int main, int splash) { super(name, desc, cd, t); this.main = main; this.splash = splash; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            List<Enemy> ts = living(enemies);
            StringBuilder sb = new StringBuilder(head(user));
            for (Enemy e : ts) {
                int d = e.takeTrueDamage(getTarget() == Target.ALL_ENEMIES || e == chosen ? main : splash);
                sb.append(e.getName()).append(" -").append(d).append(e.isAlive() ? "" : " (defeated)").append(". ");
            }
            return sb + user.afterAttack(ts);
        }
    }

    // ================================================================ support skills
    public static class Heal extends Skill {
        private final int amount;
        public Heal(String name, String desc, int cd, Target t, int amount) { super(name, desc, cd, t); this.amount = amount; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            StringBuilder sb = new StringBuilder(head(user));
            for (Hero h : affected(user, ally, party)) sb.append(h.getName()).append(" +").append(h.heal(amount)).append(" HP. ");
            return sb.toString();
        }
    }

    /** DEF and/or M.DEF up. */
    public static class Buff extends Skill {
        private final Guard kind; private final int amount, turns;
        public Buff(String name, String desc, int cd, Target t, Guard kind, int amount, int turns) {
            super(name, desc, cd, t); this.kind = kind; this.amount = amount; this.turns = turns;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            List<Hero> hs = affected(user, ally, party);
            for (Hero h : hs) {
                if (kind != Guard.MDEF) h.giveDefUp(amount, turns);
                if (kind != Guard.DEF) h.giveMdefUp(amount, turns);
            }
            String what = kind == Guard.BOTH ? "DEF/M.DEF" : kind == Guard.DEF ? "DEF" : "M.DEF";
            return head(user) + names(hs) + " " + what + " +" + amount + " for " + turns + " turns.";
        }
    }

    /** ATK/M.ATK up (does not stack with itself). */
    public static class Inspire extends Skill {
        private final int amount, turns;
        public Inspire(String name, String desc, int cd, Target t, int amount, int turns) { super(name, desc, cd, t); this.amount = amount; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            List<Hero> hs = affected(user, ally, party);
            for (Hero h : hs) h.giveBoost(amount, turns);
            return head(user) + names(hs) + " ATK/M.ATK +" + amount + " for " + turns + " turns.";
        }
    }

    /** Exusiai - Angel's Blessings: ally boost + heal, and boost herself. */
    public static class Blessing extends Skill {
        private final int boost, heal, turns;
        public Blessing(String name, String desc, int cd, int boost, int heal, int turns) { super(name, desc, cd, Target.PICK_ALLY); this.boost = boost; this.heal = heal; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            Hero a = ally != null ? ally : user;
            a.giveBoost(boost, turns); int h = a.heal(heal);
            user.giveBoost(boost, turns);
            return head(user) + a.getName() + " +" + h + " HP and ATK +" + boost + "; " + user.getName() + " ATK +" + boost + ".";
        }
    }

    /** Vina - The Kings' Sighs: party takes less damage. */
    public static class Ward extends Skill {
        private final int amount, turns;
        public Ward(String name, String desc, int cd, int amount, int turns) { super(name, desc, cd, Target.PARTY); this.amount = amount; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int a = amount > 0 ? amount : Math.max(1, user.getDef() / 2);      // 0 = user DEF / 2
            for (Hero h : affected(user, ally, party)) h.giveReduce(a, turns);
            return head(user) + "the party takes " + a + " less damage from every hit for " + turns + " turns.";
        }
    }

    /** Exusiai - Hymn of Respite. */
    public static class SpellWard extends Skill {
        private final double mult;
        /** reflect damage = mult x M.ATK at the moment the skill is used */
        public SpellWard(String name, String desc, int cd, double mult) { super(name, desc, cd, Target.SELF); this.mult = mult; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int r = (int) Math.round(user.getMatk() * mult);
            user.giveSpellWard(r);
            return head(user) + "the next spell that hits " + user.getName() + " is nullified and reflected for " + r + " magic damage.";
        }
    }

    /** Hivia - Emergency Camouflage. */
    public static class Camouflage extends Skill {
        private final int heal, turns;
        public Camouflage(String name, String desc, int cd, int heal, int turns) { super(name, desc, cd, Target.PICK_ALLY); this.heal = heal; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            Hero a = ally != null ? ally : user;
            int h = a.heal(heal); a.conceal(turns);
            return head(user) + a.getName() + " +" + h + " HP and is HIDDEN from enemies.";
        }
    }

    /** Hivia - Strike Defensive. */
    public static class Thorns extends Skill {
        private final int dmg, turns, bonus, selfHeal;
        private boolean allStats;
        /** Formula becomes (DEF + M.DEF + ATK + M.ATK) / 2 + bonus. */
        public Thorns allStats() { allStats = true; return this; }
        public Thorns(String name, String desc, int cd, int dmg, int turns) { this(name, desc, cd, dmg, turns, 0, 0); }
        /** bonus = added to the (DEF + M.DEF) / 2 formula, selfHeal = HP the user recovers. */
        public Thorns(String name, String desc, int cd, int dmg, int turns, int bonus, int selfHeal) {
            super(name, desc, cd, Target.SELF); this.dmg = dmg; this.turns = turns; this.bonus = bonus; this.selfHeal = selfHeal;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int d = dmg > 0 ? dmg : (allStats ? (user.getDef() + user.getMdef() + user.getAtk() + user.getMatk()) / 2
                                               : (user.getDef() + user.getMdef()) / 2) + bonus;      // 0 = formula + bonus
            user.giveThorns(d, turns);
            int h = selfHeal > 0 ? user.heal(selfHeal) : 0;
            return head(user) + user.getName() + " braces. Attackers will take " + d + " damage" + (h > 0 ? ", and " + user.getName() + " recovers " + h + " HP." : ".");
        }
    }

    /** Hivia - Last Line of Defense. */
    public static class Protect extends Skill {
        private final int turns, selfHeal;
        public Protect(String name, String desc, int cd, int turns, int selfHeal) { super(name, desc, cd, Target.PICK_ALLY); this.turns = turns; this.selfHeal = selfHeal; }
        @Override public boolean allyExcludesSelf() { return true; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            if (ally == null || ally == user) return head(user) + "no ally to protect.";
            ally.protectedBy(user, turns);
            int h = user.heal(selfHeal);
            return head(user) + user.getName() + " will take every single-target attack aimed at " + ally.getName() + (h > 0 ? " and recovers " + h + " HP." : ".");
        }
    }

    /** Phantom - Phantom of the Night. */
    public static class Barrier extends Skill {
        private final int amount, turns, heal;
        public Barrier(String name, String desc, int cd, int amount, int turns, int heal) { super(name, desc, cd, Target.SELF); this.amount = amount; this.turns = turns; this.heal = heal; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int a = amount > 0 ? amount : Math.max(1, user.getAtk());          // 0 = user ATK
            user.giveShield(a, turns);
            int h = user.heal(heal);
            return head(user) + "a barrier absorbs the next " + a + " damage" + (h > 0 ? " and " + user.getName() + " recovers " + h + " HP." : ".");
        }
    }

    /** Phantom - Bloody Opus. */
    public static class Empower extends Skill {
        private final int amount, hits;
        public Empower(String name, String desc, int cd, int amount, int hits) { super(name, desc, cd, Target.SELF); this.amount = amount; this.hits = hits; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            user.giveEmpower(amount, hits);
            return head(user) + "ATK +" + amount + " for the next " + hits + " attacks.";
        }
    }

    /** Phantom - Phantom in the Mirror. */
    public static class Clone extends Skill {
        private final int dmg, turns;
        public Clone(String name, String desc, int cd, int dmg, int turns) { super(name, desc, cd, Target.SELF); this.dmg = dmg; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int d = dmg > 0 ? dmg : user.getAtk() / 3;          // 0 = current ATK / 3 (rounded down)
            user.giveClone(d, turns);
            return head(user) + "a shadow clone appears for " + turns + " turns (+" + d + " damage per attack).";
        }
    }

    /** Phantom - Phantom in the Mirror: cleanse + ATK up for the party, chance to BLIND every enemy. */
    public static class MirrorNight extends Skill {
        private final int atkUp, atkTurns, blindChance, blindTurns;
        public MirrorNight(String name, String desc, int cd, int atkUp, int atkTurns, int blindChance, int blindTurns) {
            super(name, desc, cd, Target.PARTY);
            this.atkUp = atkUp; this.atkTurns = atkTurns; this.blindChance = blindChance; this.blindTurns = blindTurns;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            List<Hero> hs = affected(user, ally, party);
            for (Hero h : hs) { h.cleanse(); h.giveAtkUp(atkUp, atkTurns); }
            List<String> blinded = new ArrayList<>();
            for (Enemy e : living(enemies))
                if (RNG.nextInt(100) < blindChance) { e.giveBlind(blindTurns); blinded.add(e.getName()); }
            return head(user) + "Party cleansed, ATK +" + atkUp + " for " + atkTurns + " turns. "
                    + (blinded.isEmpty() ? "No enemy was blinded." : "BLIND: " + String.join(", ", blinded) + ".");
        }
    }

    /** Ingrid - Accelerando. */
    public static class Dodge extends Skill {
        public Dodge(String name, String desc, int cd) { super(name, desc, cd, Target.SELF); }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            user.giveDodgeNext();
            return head(user) + user.getName() + " will dodge the next attack.";
        }
    }

    /** Ingrid - La Volpe Nascosta. */
    public static class Evasion extends Skill {
        private final int pct, turns, heal;
        public Evasion(String name, String desc, int cd, int pct, int turns, int heal) { super(name, desc, cd, Target.SELF); this.pct = pct; this.turns = turns; this.heal = heal; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            user.giveEvade(pct, turns);
            int h = user.heal(heal);
            return head(user) + pct + "% evade for " + turns + " turns, countering every dodge" + (h > 0 ? ", +" + h + " HP." : ".");
        }
    }

    /** Logos - Perish. */
    public static class Perish extends Skill {
        private final int turns, weak, weakTurns, heal;
        public Perish(String name, String desc, int cd, int turns, int weak, int weakTurns, int heal) {
            super(name, desc, cd, Target.ENEMY); this.turns = turns; this.weak = weak; this.weakTurns = weakTurns; this.heal = heal;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            chosen.breakMdef(turns);
            if (weak > 0) chosen.giveWeak(weak, weakTurns);
            user.zeroMatk(2);
            int h = user.heal(heal);
            return head(user) + chosen.getName() + "'s M.DEF drops to 0" + (weak > 0 ? " and it is WEAK -" + weak : "") + "! "
                    + user.getName() + (h > 0 ? " recovers " + h + " HP, but " : "'s ") + "M.ATK fades for a turn.";
        }
    }

    /** Shu - From Seasons Fourfold. */
    public static class Regen extends Skill {
        private final int amount, turns;
        public Regen(String name, String desc, int cd, int amount, int turns) { super(name, desc, cd, Target.PARTY); this.amount = amount; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            for (Hero h : affected(user, ally, party)) h.giveRegen(amount, turns);
            return head(user) + "Seeds of Healing bloom: party +" + amount + " HP each turn for " + turns + " turns.";
        }
    }

    /** Shu - Samsara. */
    /** Hivia: an ally gets DEF/M.DEF up and SAFEHAVEN (heals every time an enemy hits them). */
    public static class Haven extends Skill {
        private final int guard, heal, turns;
        public Haven(String name, String desc, int cd, int guard, int heal, int turns) { super(name, desc, cd, Target.PICK_ALLY); this.guard = guard; this.heal = heal; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            Hero a = ally != null && ally.isAlive() ? ally : user;
            a.giveBuff(guard, turns);
            a.giveSafehaven(heal, turns);
            return head(user) + a.getName() + " gets DEF/M.DEF +" + guard + " and SAFEHAVEN (+" + heal + " HP when hit) for " + turns + " turns.";
        }
    }
    /** Exusiai: no damage, every enemy may BURN for (user ATK) per turn. */
    public static class Ignite extends Skill {
        private final int chance, turns;
        public Ignite(String name, String desc, int cd, int chance, int turns) { super(name, desc, cd, Target.ALL_ENEMIES); this.chance = chance; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int amount = Math.max(1, user.getAtk());
            StringBuilder sb = new StringBuilder(head(user));
            for (Enemy e : living(enemies)) {
                boolean ok = RNG.nextInt(100) < chance;
                if (ok) e.giveBurn(amount, turns);
                sb.append(e.getName()).append(ok ? " - BURN " + amount : " (resisted)").append(". ");
            }
            return sb.toString().trim();
        }
    }
    /** Logos: no damage, WEAK -(M.ATK / 2) for (ATK) turns. */
    public static class Enfeeble extends Skill {
        private final int count;
        public Enfeeble(String name, String desc, int cd) { this(name, desc, cd, 1); }
        /** count = chosen enemy + random others. */
        public Enfeeble(String name, String desc, int cd, int count) { super(name, desc, cd, Target.ENEMY); this.count = count; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            if (user.rollBlindMiss(RNG)) return head(user) + "BLIND - it misses!";
            int amount = Math.max(1, user.getMatk() / 2), turns = Math.max(1, user.getAtk());
            List<Enemy> others = living(enemies); others.remove(chosen); Collections.shuffle(others, RNG);
            List<Enemy> ts = new ArrayList<>(); ts.add(chosen);
            for (int i = 0; i < count - 1 && i < others.size(); i++) ts.add(others.get(i));
            StringBuilder names = new StringBuilder();
            for (Enemy e : ts) { e.giveWeak(amount, turns); names.append(names.length() > 0 ? ", " : "").append(e.getName()); }
            return head(user) + names + (ts.size() > 1 ? " are" : " is") + " WEAK -" + amount + " for " + turns + " turns.";
        }
    }
    /** Logos: one magic hit, stronger against a wounded enemy. */
    public static class Execute extends Damage {
        private final double high, below;
        public Execute(String name, String desc, int cd, double mult, double high, double below) { super(name, desc, cd, Target.ENEMY, mult, true, 1); this.high = high; this.below = below; }
        @Override protected double multFor(Hero user, Enemy e) { return e.hpRatio() < below ? high : super.multFor(user, e); }
    }
    /** Logos: magic hit on ALL, stronger vs enemies that already had a bad status, then a chance to FREEZE. */
    public static class Glaciate extends Damage {
        private final double high; private final int chance;
        public Glaciate(String name, String desc, int cd, double mult, double high, int chance) { super(name, desc, cd, Target.ALL_ENEMIES, mult, true, 1); this.high = high; this.chance = chance; }
        @Override protected double multFor(Hero user, Enemy e) { return e.hasAilment() ? high : super.multFor(user, e); }   // checked before this hit
        @Override protected String after(Hero user, Enemy e) {
            if (RNG.nextInt(100) >= chance) return "(no freeze)";
            return e.giveFreeze(1) ? "- FROZEN" : "(resisted)";
        }
    }
    /** Shu: taunt one enemy and link SAMSARA to the whole party (Shu heals whenever an enemy hits anyone). */
    public static class Samsara extends Skill {
        private final int turns;
        public Samsara(String name, String desc, int cd, int turns) { super(name, desc, cd, Target.ENEMY); this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            chosen.tauntBy(user, turns);
            for (Hero h : party) if (h.isAlive()) h.linkSamsara(user, turns);
            return head(user) + chosen.getName() + " can only target " + user.getName() + " for " + turns + " turns. SAMSARA: every enemy hit on the party heals "
                    + user.getName() + " " + Hero.SAMSARA_HEAL + " HP.";
        }
    }

    public static class Taunt extends Skill {
        private final int turns;
        public Taunt(String name, String desc, int cd, int turns) { super(name, desc, cd, Target.ENEMY); this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            chosen.tauntBy(user, turns);
            return head(user) + chosen.getName() + " can only target " + user.getName() + " for " + turns + " turns.";
        }
    }

    /** Cleanse the party (kept for items/future heroes). */
    public static class Purify extends Skill {
        public Purify(String name, String desc, int cd) { super(name, desc, cd, Target.PARTY); }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            for (Hero h : affected(user, ally, party)) { h.cleanse(); h.heal(4); }
            return head(user) + "the party is cleansed and recovers 4 HP.";
        }
    }

    //  Orchid (secret hero)
    /** Arrow Rain: hits N random enemies and makes them WEAK. */
    public static class Volley extends Damage {
        private final int count, weak, turns;
        public Volley(String name, String desc, int cd, int count, double mult, int weak, int turns) {
            super(name, desc, cd, Target.ALL_ENEMIES, mult, false, 1); this.count = count; this.weak = weak; this.turns = turns;
        }
        @Override protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            List<Enemy> l = living(enemies); Collections.shuffle(l, RNG);
            return new ArrayList<>(l.subList(0, Math.min(count, l.size())));
        }
        @Override protected String after(Hero user, Enemy e) { e.giveWeak(weak, turns); return "- WEAK -" + weak; }
    }

    /** Hits with ATK and M.ATK together (both reduced by the matching defence). */
    public static class Hybrid extends Damage {
        private final double atkMult, matkMult;
        public Hybrid(String name, String desc, int cd, Target t, double atkMult, double matkMult) {
            super(name, desc, cd, t, 1, false, 1); this.atkMult = atkMult; this.matkMult = matkMult;
        }
        @Override protected int strike(Hero user, Enemy e) {
            if (user.rollBlindMiss(RNG)) return 0;
            int d = e.takeDamage((int) Math.round(user.getAtk() * atkMult), false);
            if (e.isAlive()) d += e.takeDamage((int) Math.round(user.getMatk() * matkMult), true);
            return d;
        }
    }

    /** A flying in the moon: big hybrid hit, then Orchid is WEAK next turn. */
    public static class Moonfall extends Hybrid {
        private final int weak;
        public Moonfall(String name, String desc, int cd, double atkMult, double matkMult, int weak) { super(name, desc, cd, Target.ENEMY, atkMult, matkMult); this.weak = weak; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            user.giveWeak(weak, 2);
            return s + " " + user.getName() + " is exhausted: WEAK -" + weak + " next turn.";
        }
    }

    /** Dragon Piercer: hybrid hit on everyone, then DEF up for a turn. */
    public static class Piercer extends Hybrid {
        private final int def;
        public Piercer(String name, String desc, int cd, double atkMult, double matkMult, int def) { super(name, desc, cd, Target.ALL_ENEMIES, atkMult, matkMult); this.def = def; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            user.giveDefUp(def, 1);
            return s + " " + user.getName() + " DEF +" + def + ".";
        }
    }

    /** Tale of Lonely Mystery: the party turns AGGRESSIVE (+ATK, -DEF). */
    public static class Aggressive extends Skill {
        private final int amount, turns;
        public Aggressive(String name, String desc, int cd, int amount, int turns) { super(name, desc, cd, Target.PARTY); this.amount = amount; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            for (Hero h : affected(user, ally, party)) h.giveAggressive(amount, turns);
            return head(user) + "the party becomes AGGRESSIVE: ATK +" + amount + ", DEF -" + amount + " for " + turns + " turns.";
        }
    }

    // ================================================================ patch: new hero skill types
    /** Damage that can inflict a status. count > 1 with an ENEMY target = chosen enemy + random others. */
    public static class Hex extends Damage {
        private final int count, chance, amount, turns;
        private final EnemyMove.Ailment ailment;
        public Hex(String name, String desc, int cd, Target t, double mult, boolean magical, int count,
                   EnemyMove.Ailment ailment, int chance, int amount, int turns) {
            super(name, desc, cd, t, mult, magical, 1);
            this.count = count; this.ailment = ailment; this.chance = chance; this.amount = amount; this.turns = turns;
        }
        @Override protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            if (getTarget() != Target.ENEMY || count <= 1) return super.targets(user, chosen, enemies);
            List<Enemy> others = living(enemies); others.remove(chosen); Collections.shuffle(others, RNG);
            List<Enemy> l = new ArrayList<>(); l.add(chosen);
            for (int i = 0; i < count - 1 && i < others.size(); i++) l.add(others.get(i));
            return l;
        }
        @Override protected String after(Hero user, Enemy e) {
            if (ailment == EnemyMove.Ailment.STUN) return stun(e, chance) ? "- STUNNED" : "(no stun)";
            if (RNG.nextInt(100) >= chance) return "(resisted)";
            switch (ailment) {
                case BLIND: e.giveBlind(turns); break;
                case WEAK: e.giveWeak(amount, turns); break;
                case BREAK: e.giveBreak(amount, turns); break;
                case BURN: e.giveBurn(amount, turns); break;
                case POISON: e.givePoison(amount, turns); break;
                case CURSE: e.giveCurse(turns); break;
                case SILENCE: e.giveSilence(turns); break;
                case FREEZE: if (!e.giveFreeze(1)) return "(resisted)"; break;
                default: break;
            }
            return "- " + ailment;
        }
    }

    /** Big hit on the target, smaller hit on every other enemy (Vina - And All Be In My Name). */
    public static class Splash extends Damage {
        private final double main, splash;
        private final int selfHeal;
        private Enemy focus;
        public Splash(String name, String desc, int cd, double main, double splash) { this(name, desc, cd, main, splash, 0); }
        public Splash(String name, String desc, int cd, double main, double splash, int selfHeal) {
            super(name, desc, cd, Target.ENEMY, main, false, 1); this.main = main; this.splash = splash; this.selfHeal = selfHeal;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            int h = selfHeal > 0 ? user.heal(selfHeal) : 0;
            return h > 0 ? s + " " + user.getName() + " recovers " + h + " HP." : s;
        }
        @Override protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            focus = chosen;
            List<Enemy> l = living(enemies); l.remove(chosen); l.add(0, chosen);
            return l;
        }
        @Override protected double multFor(Hero user, Enemy e) { return e == focus ? main : splash; }
        /** The main target also takes + M.ATK. */
        @Override protected int bonusPower(Hero user, Enemy e) { return e == focus && addMatk ? user.getMatk() : 0; }
        private boolean addMatk;
        public Splash withMatk() { addMatk = true; return this; }
    }

    /** Irene - Sword and Handcannon: pick two enemies; the first takes ATK damage, the second M.ATK damage. */
    public static class DualShot extends Skill {
        private final double atkMult, matkMult;
        public DualShot(String name, String desc, int cd, double atkMult, double matkMult) { super(name, desc, cd, Target.ENEMY); this.atkMult = atkMult; this.matkMult = matkMult; }
        @Override public boolean needsTwoTargets() { return true; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            Enemy b = second != null && second.isAlive() ? second : chosen;
            StringBuilder sb = new StringBuilder(head(user));
            int d1 = user.rollBlindMiss(RNG) ? 0 : chosen.takeDamage((int) Math.round(user.getAtk() * atkMult), false);
            sb.append("Sword: ").append(chosen.getName()).append(" -").append(d1).append(chosen.isAlive() ? "" : " (defeated)").append(". ");
            if (!b.isAlive()) b = chosen;
            if (b.isAlive()) {
                int d2 = user.rollBlindMiss(RNG) ? 0 : b.takeDamage((int) Math.round(user.getMatk() * matkMult), true);
                sb.append("Handcannon: ").append(b.getName()).append(" -").append(d2).append(b.isAlive() ? "" : " (defeated)").append(". ");
            }
            List<Enemy> hit = new ArrayList<>(); hit.add(chosen); if (b != chosen) hit.add(b);
            return sb + user.afterAttack(hit);
        }
    }

    /** Exusiai - Angel's Blessings: the whole party heals and gets ATK/M.ATK up. */
    public static class Anthem extends Skill {
        private final int heal, boost, turns;
        public Anthem(String name, String desc, int cd, int heal, int boost, int turns) { super(name, desc, cd, Target.PARTY); this.heal = heal; this.boost = boost; this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            StringBuilder sb = new StringBuilder(head(user));
            for (Hero h : affected(user, ally, party)) { sb.append(h.getName()).append(" +").append(h.heal(heal)).append(" HP. "); h.giveBoost(boost, turns); }
            return sb + "Party ATK/M.ATK +" + boost + " for " + turns + " turns.";
        }
    }

    /** Phantom - Bloody Opus: for the rest of the battle his Attack hits several random enemies with more ATK. */
    public static class Opus extends Skill {
        private final int targets, atk, turns;
        public Opus(String name, String desc, int cd, int targets, int atk, int turns) { super(name, desc, cd, Target.SELF); this.targets = targets; this.atk = atk; this.turns = turns; }
        @Override public boolean canUse(Hero user, List<Hero> party) { return !user.hasOpus(); }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int a = atk > 0 ? atk : user.getMatk();                 // 0 = ATK + user's M.ATK
            user.giveOpus(targets, a, turns);
            return head(user) + "for " + turns + " turns " + user.getName() + "'s Attack hits " + targets + " enemies with ATK +" + a + ".";
        }
    }

    /** Ingrid - Accelerando: dodge everything this turn, heal and cleanse herself. */
    public static class Sidestep extends Skill {
        private final int turns, heal;
        public Sidestep(String name, String desc, int cd, int turns, int heal) { super(name, desc, cd, Target.SELF); this.turns = turns; this.heal = heal; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            user.giveDodge(turns); user.cleanse();
            int h = user.heal(heal);
            return head(user) + user.getName() + " dodges every attack this turn, recovers " + h + " HP and shakes off all bad effects.";
        }
    }

    /** Ingrid - Damoclean Interrogation: hits N random enemies and the whole party gets DEF up. */
    public static class Onslaught extends Damage {
        private final int count, def, turns;
        private double matkMult;
        public Onslaught(String name, String desc, int cd, double mult, int count, int def, int turns) {
            super(name, desc, cd, Target.PARTY, mult, false, 1); this.count = count; this.def = def; this.turns = turns;
        }
        /** Also adds x M.ATK to every hit. */
        public Onslaught plusMatk(double m) { matkMult = m; return this; }
        @Override protected int bonusPower(Hero user, Enemy e) { return (int) Math.round(user.getMatk() * matkMult); }
        @Override protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            List<Enemy> l = living(enemies); Collections.shuffle(l, RNG);
            return new ArrayList<>(l.subList(0, Math.min(count, l.size())));
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            for (Hero h : party) if (h.isAlive()) h.giveDefUp(def, turns);
            return s + " The party DEF +" + def + " for " + turns + " turns.";
        }
    }

    /** Shu - Bountiful Harvest: hit an enemy, then heal a chosen ally by Shu's DEF + M.DEF. */
    public static class Harvest2 extends Damage {
        public Harvest2(String name, String desc, int cd, double mult) { super(name, desc, cd, Target.ENEMY, mult, true, 1); }
        @Override protected int bonusPower(Hero user, Enemy e) { return user.getAtk() / 2; }       // extra: + ATK / 2
        @Override public boolean needsAllyToo() { return true; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            String s = super.apply(user, chosen, ally, enemies, party);
            Hero a = ally != null && ally.isAlive() ? ally : user;
            int h = a.heal(user.getMdef() + user.getMatk());          // heal = Shu M.DEF + M.ATK
            return s + " " + a.getName() + " recovers " + h + " HP.";
        }
    }

    //  Yato (secret hero, Kirin Hunter)
    /** Demon Mode: Yato's ATK rises by her own M.ATK for some turns. */
    public static class DemonMode extends Skill {
        private final int turns;
        public DemonMode(String name, String desc, int cd, int turns) { super(name, desc, cd, Target.SELF); this.turns = turns; }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            int amount = Math.max(1, user.getMatk());
            user.giveDemon(amount, turns);
            return head(user) + user.getName() + " enters DEMON MODE: ATK +" + amount + " for " + turns + " turns.";
        }
    }

    /** Blade Dance: the whole party gets RAGE (ATK up), but the dancer is SILENCED afterwards. */
    public static class BladeDance extends Skill {
        private final int atk, turns, silence;
        public BladeDance(String name, String desc, int cd, int atk, int turns, int silence) {
            super(name, desc, cd, Target.PARTY); this.atk = atk; this.turns = turns; this.silence = silence;
        }
        @Override protected String apply(Hero user, Enemy chosen, Hero ally, List<Enemy> enemies, List<Hero> party) {
            List<Hero> hs = affected(user, ally, party);
            for (Hero h : hs) h.giveRage(atk, turns);
            user.giveSilence(silence + 1);        // +1: this round's tick happens right after, so she misses her next `silence` turns
            return head(user) + names(hs) + " gain RAGE: ATK +" + atk + " for " + turns + " turns. " + user.getName() + " is SILENCED for " + silence + " turns.";
        }
    }

    /** Midair Spinning Blade Dance: chosen enemy + random others, each hit makes them BLEED. */
    public static class SpinningBlade extends Damage {
        private final int count, bleed, turns;
        public SpinningBlade(String name, String desc, int cd, int count, double mult, int bleed, int turns) {
            super(name, desc, cd, Target.ENEMY, mult, false, 1); this.count = count; this.bleed = bleed; this.turns = turns;
        }
        @Override protected List<Enemy> targets(Hero user, Enemy chosen, List<Enemy> enemies) {
            List<Enemy> others = living(enemies); others.remove(chosen); Collections.shuffle(others, RNG);
            List<Enemy> l = new ArrayList<>(); l.add(chosen);
            for (int i = 0; i < count - 1 && i < others.size(); i++) l.add(others.get(i));
            return l;
        }
        @Override protected String after(Hero user, Enemy e) { e.giveBleed(bleed, turns); return "- BLEED " + bleed; }
    }
}
