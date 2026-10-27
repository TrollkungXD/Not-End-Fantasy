import java.awt.Color;
import java.util.*;

/** Base class of everything that fights. All stats and status effects are private (Encapsulation). */
public abstract class Entity {
    private final String name;
    private int hp, maxHp, def, mdef, atk, matk;

    // ---- timed effects (value + turns left). They tick down at the end of every round.
    // good
    private int defUp, defUpT, mdefUp, mdefUpT;     // +DEF / +M.DEF
    private int boost, boostT;                      // +ATK/M.ATK
    private int aggro, aggroT;                      // AGGRESSIVE: +ATK but -DEF
    private int regen, regenT;                      // gain HP each round
    private int reduce, reduceT;                    // every hit taken is reduced by this much
    private int shield, shieldT;                    // barrier that absorbs damage
    private int thorns, thornsT;                    // attackers take this much damage
    private int concealT;                           // enemies can't pick this target
    private int evadePct, evadeT;                   // chance to dodge + counter
    private int dodgeT;                             // dodge every attack while active
    private int spellWard;                          // Hymn: null the next spell and reflect this much magic damage
    // bad
    private int weak, weakT;                        // -ATK/M.ATK
    private int brk, brkT;                          // Armor Break: -DEF/M.DEF
    private int poison, poisonT, burn, burnT;       // lose HP each round
    private int bleed, bleedT;                      // BLEED: lose HP each round AND ATK/M.ATK -bleed
    private int airT;                               // airborne: magic damage taken x1.2
    private int mdefBreakT, matkZeroT;              // M.DEF is 0  /  M.ATK is 0
    private int silenceT, blindT, curseT;           // no skills / 50% miss / half healing
    private int stun, freezeT, steadyT;             // skip turns / frozen look / stun immunity after a stun

    protected Entity(String name, int maxHp, int def, int mdef, int atk, int matk) {
        this.name = name;
        this.hp = this.maxHp = maxHp;
        this.def = def; this.mdef = mdef; this.atk = atk; this.matk = matk;
    }

    public abstract Color getColor();     // each subclass decides its own look

    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getDef() { return Math.max(0, def + defUp - brk - aggro - (freezeT > 0 ? 3 : 0)); }
    public int getMdef() { return mdefBreakT > 0 ? 0 : Math.max(0, mdef + mdefUp - brk); }
    public int getAtk() { return Math.max(0, atk + boost + aggro - weak - (bleedT > 0 ? bleed : 0)); }
    public int getMatk() { return matkZeroT > 0 ? 0 : Math.max(0, matk + boost - weak - (bleedT > 0 ? bleed : 0)); }
    public int getBaseDef() { return def; }
    public int getBaseMdef() { return mdef; }
    public int getBaseAtk() { return atk; }
    public int getBaseMatk() { return matk; }
    public int getGuard() { return Math.max(defUp, mdefUp); }
    public int getBuff() { return getGuard(); }
    public int getBoost() { return boost; }
    public int getWeak() { return weak; }
    public int getPoison() { return poisonT > 0 ? poison : 0; }
    public int getThorns() { return thornsT > 0 ? thorns : 0; }
    public boolean isAirborne() { return airT > 0; }
    public boolean isConcealed() { return concealT > 0; }
    public boolean isStunned() { return stun > 0; }
    public boolean isSilenced() { return silenceT > 0; }
    public boolean isAlive() { return hp > 0; }
    public double hpRatio() { return maxHp == 0 ? 0 : hp / (double) maxHp; }
    /** Any bad status (used by Synecdoche). */
    public boolean hasAilment() {
        return poisonT > 0 || burnT > 0 || bleedT > 0 || weakT > 0 || brkT > 0 || stun > 0 || airT > 0 || mdefBreakT > 0
                || silenceT > 0 || blindT > 0 || curseT > 0;
    }

    /** True when a cleanse would remove something (bad timed effects only). */
    public final boolean hasBadStatus() {
        return poisonT > 0 || burnT > 0 || bleedT > 0 || weakT > 0 || brkT > 0 || stun > 0 || airT > 0 || mdefBreakT > 0
                || matkZeroT > 0 || silenceT > 0 || blindT > 0 || curseT > 0;
    }

    // ---------------------------------------------------------------- damage
    /** Physical: power - DEF.  Magical: power - M.DEF (x1.2 when airborne).  Always at least 1 before reductions. */
    public int takeDamage(int power, boolean magical) {
        int d = Math.max(1, power - (magical ? getMdef() : getDef()));
        if (magical && airT > 0) d = (int) Math.ceil(d * 1.5);
        if (reduceT > 0) d = Math.max(0, d - reduce);
        if (shieldT > 0 && shield > 0) { int s = Math.min(shield, d); shield -= s; d -= s; }
        return takeTrueDamage(d);
    }
    public int takeTrueDamage(int dmg) {
        dmg = Math.min(hp, Math.max(0, dmg));
        hp -= dmg;
        return dmg;
    }
    /** Healing. CURSE halves it. */
    public int heal(int amount) {
        if (!isAlive()) return 0;
        if (curseT > 0) amount = amount / 2;
        int before = hp;
        hp = Math.min(maxHp, hp + Math.max(0, amount));
        return hp - before;
    }
    public int healPct(double pct) { return heal((int) Math.round(maxHp * pct)); }
    public void revive(int amount) { if (!isAlive()) hp = Math.max(1, Math.min(maxHp, amount)); }
    public void loseHpKeep1(int amount) { if (isAlive()) hp = Math.max(1, hp - amount); }   // traps never kill
    /** BLIND: 50% chance that an attack misses. */
    public boolean rollBlindMiss(Random r) { return blindT > 0 && r.nextBoolean(); }

    // giving effects
    public void giveBuff(int a, int t) { giveDefUp(a, t); giveMdefUp(a, t); }
    public void giveDefUp(int a, int t) { defUp = Math.max(defUp, a); defUpT = Math.max(defUpT, t); }
    public void giveMdefUp(int a, int t) { mdefUp = Math.max(mdefUp, a); mdefUpT = Math.max(mdefUpT, t); }
    public void giveBoost(int a, int t) { boost = Math.max(boost, a); boostT = Math.max(boostT, t); }   // never stacks
    public void giveAggressive(int a, int t) { aggro = Math.max(aggro, a); aggroT = Math.max(aggroT, t); }
    public void giveWeak(int a, int t) { weak = Math.max(weak, a); weakT = Math.max(weakT, t); }
    public void giveBreak(int a, int t) { brk = Math.max(brk, a); brkT = Math.max(brkT, t); }
    public void givePoison(int a, int t) { poison = Math.max(poison, a); poisonT = Math.max(poisonT, t); }
    public void giveBurn(int a, int t) { burn = Math.max(burn, a); burnT = Math.max(burnT, t); }
    public void giveBleed(int a, int t) { bleed = Math.max(bleed, a); bleedT = Math.max(bleedT, t); }
    public void giveRegen(int a, int t) { regen = Math.max(regen, a); regenT = Math.max(regenT, t); }
    public void giveReduce(int a, int t) { reduce = Math.max(reduce, a); reduceT = Math.max(reduceT, t); }
    public void giveShield(int a, int t) { shield = Math.max(shield, a); shieldT = Math.max(shieldT, t); }
    public void giveThorns(int a, int t) { thorns = Math.max(thorns, a); thornsT = Math.max(thornsT, t); }
    public void giveAirborne(int t) { airT = Math.max(airT, t); }
    public void breakMdef(int t) { mdefBreakT = Math.max(mdefBreakT, t); }
    public void zeroMatk(int t) { matkZeroT = Math.max(matkZeroT, t); }
    public void conceal(int t) { concealT = Math.max(concealT, t); }
    public void giveEvade(int pct, int t) { evadePct = Math.max(evadePct, pct); evadeT = Math.max(evadeT, t); }
    public void giveSilence(int t) { silenceT = Math.max(silenceT, t); }
    public void giveBlind(int t) { blindT = Math.max(blindT, t); }
    public void giveCurse(int t) { curseT = Math.max(curseT, t); }
    /** Returns false when the target is STEADY (just recovered from a stun). */
    public boolean giveStun(int turns) { if (steadyT > 0) return false; stun = Math.max(stun, turns); return true; }
    /** FREEZE = stun + DEF -3 while frozen. */
    public boolean giveFreeze(int turns) { if (!giveStun(turns)) return false; freezeT = Math.max(freezeT, turns + 1); return true; }
    public void giveDodgeNext() { giveDodge(1); }
    public void giveDodge(int turns) { dodgeT = Math.max(dodgeT, turns); }
    public void giveSpellWard() { giveSpellWard(20); }
    public void giveSpellWard(int reflect) { spellWard = Math.max(spellWard, Math.max(1, reflect)); }

    // consuming effects
    /** Uses up one stunned turn. Returns true when this turn must be skipped. */
    public boolean consumeStun() {
        if (stun <= 0) return false;
        if (--stun == 0) { steadyT = 2; freezeT = 0; }     // can't be stunned again right away
        return true;
    }
    /** True while DODGE is active (every attack misses). */
    public boolean consumeDodge() { return dodgeT > 0; }
    /** Returns the reflect damage (0 = no Hymn). The Hymn is used up. */
    public int consumeSpellWard() { int r = spellWard; spellWard = 0; return r; }
    public boolean rollEvade(Random r) { return evadeT > 0 && r.nextInt(100) < evadePct; }

    public void cleanse() {
        weak = weakT = brk = brkT = poison = poisonT = burn = burnT = bleed = bleedT = airT = mdefBreakT = 0;
        silenceT = blindT = curseT = stun = freezeT = matkZeroT = 0;
    }
    public void clearStatus() {
        aggro = aggroT = 0;
        defUp = defUpT = mdefUp = mdefUpT = boost = boostT = regen = regenT = reduce = reduceT = 0;
        shield = shieldT = thorns = thornsT = matkZeroT = concealT = evadePct = evadeT = steadyT = 0;
        dodgeT = spellWard = 0;
        cleanse();
    }

    /** Called at the end of every round. Returns a log line (or ""). */
    public String tickStatus() {
        StringBuilder log = new StringBuilder();
        if (regenT > 0 && isAlive()) {
            int h = heal(regen);
            if (h > 0) log.append(name).append(" recovers ").append(h).append(" HP (regen). ");
            if (--regenT == 0) regen = 0;
        }
        if (poisonT > 0 && isAlive()) {
            int d = takeTrueDamage(poison);
            log.append(name).append(" takes ").append(d).append(" poison dmg").append(isAlive() ? ". " : " and falls! ");
            if (--poisonT == 0) poison = 0;
        }
        if (bleedT > 0 && isAlive()) {
            int d = takeTrueDamage(bleed);
            log.append(name).append(" bleeds for ").append(d).append(isAlive() ? ". " : " and falls! ");
            if (--bleedT == 0) bleed = 0;
        }
        if (burnT > 0 && isAlive()) {
            int d = takeTrueDamage(burn);
            log.append(name).append(" burns for ").append(d).append(isAlive() ? ". " : " and falls! ");
            if (--burnT == 0) burn = 0;
        }
        if (defUpT > 0 && --defUpT == 0) defUp = 0;
        if (mdefUpT > 0 && --mdefUpT == 0) mdefUp = 0;
        if (boostT > 0 && --boostT == 0) boost = 0;
        if (aggroT > 0 && --aggroT == 0) aggro = 0;
        if (weakT > 0 && --weakT == 0) weak = 0;
        if (brkT > 0 && --brkT == 0) brk = 0;
        if (reduceT > 0 && --reduceT == 0) reduce = 0;
        if (shieldT > 0 && --shieldT == 0) shield = 0;
        if (thornsT > 0 && --thornsT == 0) thorns = 0;
        if (evadeT > 0 && --evadeT == 0) evadePct = 0;
        if (airT > 0) airT--;
        if (mdefBreakT > 0) mdefBreakT--;
        if (matkZeroT > 0) matkZeroT--;
        if (concealT > 0) concealT--;
        if (dodgeT > 0) dodgeT--;
        if (silenceT > 0) silenceT--;
        if (blindT > 0) blindT--;
        if (curseT > 0) curseT--;
        if (steadyT > 0) steadyT--;
        return log.toString().trim();
    }

    //  UI info
    private static String t(int turns) { return turns >= 50 ? "" : " (" + turns + (turns == 1 ? " turn)" : " turns)"); }

    /** Every active effect as {short label, explanation}. Subclasses add their own. */
    public List<String[]> statusInfo() {
        List<String[]> l = new ArrayList<>();
        if (stun > 0) l.add(new String[]{freezeT > 0 ? "FROZEN" : "STUN", freezeT > 0 ? "Frozen solid: skips the next turn and DEF -3." : "Stunned: skips the next turn."});
        if (defUp > 0 && defUp == mdefUp && defUpT == mdefUpT) l.add(new String[]{"GUARD +" + defUp, "DEF and M.DEF +" + defUp + t(defUpT)});
        else {
            if (defUp > 0) l.add(new String[]{"DEF +" + defUp, "DEF +" + defUp + t(defUpT)});
            if (mdefUp > 0) l.add(new String[]{"M.DEF +" + mdefUp, "M.DEF +" + mdefUp + t(mdefUpT)});
        }
        if (boost > 0) l.add(new String[]{"POWER +" + boost, "ATK and M.ATK +" + boost + t(boostT)});
        if (aggro > 0) l.add(new String[]{"AGGRESSIVE +" + aggro, "Aggressive: ATK +" + aggro + " but DEF -" + aggro + t(aggroT)});
        if (reduceT > 0) l.add(new String[]{"DMG -" + reduce, "Every hit taken is reduced by " + reduce + t(reduceT)});
        if (shieldT > 0 && shield > 0) l.add(new String[]{"SHIELD " + shield, "Barrier absorbs the next " + shield + " damage" + t(shieldT)});
        if (thornsT > 0) l.add(new String[]{"THORNS " + thorns, "Attackers take " + thorns + " damage" + t(thornsT)});
        if (regenT > 0) l.add(new String[]{"REGEN " + regen, "Recovers " + regen + " HP at the end of each round" + t(regenT)});
        if (dodgeT > 0) l.add(new String[]{"DODGE", "Dodges every attack" + t(dodgeT)});
        if (evadeT > 0) l.add(new String[]{"EVADE " + evadePct + "%", evadePct + "% chance to dodge and counterattack" + t(evadeT)});
        if (spellWard > 0) l.add(new String[]{"HYMN", "The next spell is nullified and the caster takes " + spellWard + " magic damage."});
        if (concealT > 0) l.add(new String[]{"HIDDEN", "Enemies can't pick this target for single attacks" + t(concealT)});
        if (steadyT > 0) l.add(new String[]{"STEADY", "Just recovered from a stun: can't be stunned or frozen" + t(steadyT)});
        if (weak > 0) l.add(new String[]{"WEAK -" + weak, "ATK and M.ATK -" + weak + t(weakT)});
        if (brk > 0) l.add(new String[]{"BREAK -" + brk, "Armor Break: DEF and M.DEF -" + brk + t(brkT)});
        if (poisonT > 0) l.add(new String[]{"POISON " + poison, "Loses " + poison + " HP at the end of each round" + t(poisonT)});
        if (bleedT > 0) l.add(new String[]{"BLEED " + bleed, "Loses " + bleed + " HP at the end of each round and ATK/M.ATK -" + bleed + t(bleedT)});
        if (burnT > 0) l.add(new String[]{"BURN " + burn, "Loses " + burn + " HP at the end of each round" + t(burnT)});
        if (silenceT > 0) l.add(new String[]{"SILENCE", "Can't use skills (Attack, Defend and Items only)" + t(silenceT)});
        if (blindT > 0) l.add(new String[]{"BLIND", "50% of attacks miss" + t(blindT)});
        if (curseT > 0) l.add(new String[]{"CURSE", "Healing received is halved" + t(curseT)});
        if (airT > 0) l.add(new String[]{"AIRBORNE", "Takes 50% more magic damage" + t(airT)});
        if (mdefBreakT > 0) l.add(new String[]{"M.DEF 0", "M.DEF is reduced to 0" + t(mdefBreakT)});
        if (matkZeroT > 0) l.add(new String[]{"M.ATK 0", "M.ATK is 0" + t(matkZeroT)});
        return l;
    }
    /** Just the short labels. */
    public final List<String> statusLabels() {
        List<String> l = new ArrayList<>();
        for (String[] s : statusInfo()) l.add(s[0]);
        return l;
    }

    protected void gain(int dHp, int dDef, int dMdef, int dAtk, int dMatk) {
        maxHp += dHp; hp += dHp; def += dDef; mdef += dMdef; atk += dAtk; matk += dMatk;
    }
}
