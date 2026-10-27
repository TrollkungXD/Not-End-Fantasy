import java.awt.Color;
import java.util.*;

/** Playable character. Skills are part of the hero (Composition). */
public class Hero extends Entity {
    public enum Stat { HP, DEF, MDEF, ATK, MATK }

    private final String role, blurb;
    private final Color color;
    private final List<Skill> skills = new ArrayList<>();
    private int safeHeal, safeT;              // Safehaven: heal when hit by an enemy
    private Hero samsara; private int samsaraT; // Samsara: when an enemy hits this hero, the linked Shu heals
    private final List<Passive> passives = new ArrayList<>();   // permanent traits (Yato's reward)
    private Stat basicStat = Stat.ATK;        // which stat the plain Attack command uses

    // hero-only effects
    private int empower, empowerHits;         // Bloody Opus: +ATK for the next N attacks
    private int cloneDmg, cloneT;             // Phantom in the Mirror
    private Hero guardian; private int guardT; // Last Line of Defense: attacks go to the guardian
    private int opusTargets = 1, opusAtk;     // Bloody Opus (rest of the battle): basic attack hits N enemies, +ATK
    private int demonAtk, demonT;             // Yato - Demon Mode: ATK + (her M.ATK)
    private int rageAtk, rageT;               // Yato - Blade Dance: RAGE, ATK only (not M.ATK)

    public Hero(String name, String role, String blurb, Color color,
                int hp, int def, int mdef, int atk, int matk) {
        super(name, hp, def, mdef, atk, matk);
        this.role = role; this.blurb = blurb; this.color = color;
    }

    public Hero with(Skill s) { skills.add(s); return this; }
    /** Attack command uses this stat instead of ATK (MATK = magic attack, DEF = shield strike). */
    public Hero basic(Stat s) { basicStat = s; return this; }
    public List<Skill> getSkills() { return Collections.unmodifiableList(skills); }
    public String getRole() { return role; }
    public String getBlurb() { return blurb; }
    @Override public Color getColor() { return color; }

    // basic attack
    public boolean basicIsMagic() { return basicStat == Stat.MATK; }
    public int basicPower() { return basicStat == Stat.MATK ? getMatk() : basicStat == Stat.DEF ? getDef() : getAtk(); }
    public String basicName() { return basicStat == Stat.MATK ? "Magic Bolt" : basicStat == Stat.DEF ? "Shield Strike" : "Attack"; }
    public String basicDesc() { return basicStat == Stat.MATK ? "Magic hit with M.ATK" : basicStat == Stat.DEF ? "Hit using DEF" : "Basic hit with ATK"; }

    //  hero effects
    @Override public int getAtk() { return super.getAtk() + (empowerHits > 0 ? empower : 0) + opusAtk + (demonT > 0 ? demonAtk : 0) + (rageT > 0 ? rageAtk : 0); }
    /** Demon Mode: ATK +amount for some turns (no stacking). */
    public void giveDemon(int amount, int turns) { demonAtk = Math.max(demonAtk, amount); demonT = Math.max(demonT, turns); }
    /** RAGE: ATK +amount for some turns (no stacking). */
    public void giveRage(int amount, int turns) { rageAtk = Math.max(rageAtk, amount); rageT = Math.max(rageT, turns); }
    /** Bloody Opus: for the rest of the battle the basic attack hits `targets` enemies and ATK +atk. */
    public void giveOpus(int targets, int atk) { opusTargets = Math.max(opusTargets, targets); opusAtk = Math.max(opusAtk, atk); }
    public boolean hasOpus() { return opusTargets > 1 || opusAtk > 0; }
    /** How many enemies the plain Attack hits. */
    public int basicTargets() { return opusTargets; }
    public void giveEmpower(int amount, int hits) { empower = amount; empowerHits = hits; }
    public void giveClone(int dmg, int turns) { cloneDmg = dmg; cloneT = turns; }
    public void protectedBy(Hero g, int turns) { guardian = g; guardT = turns; }
    /** Who really takes a single-target hit aimed at this hero. */
    public Hero redirectTarget() { return guardT > 0 && guardian != null && guardian != this && guardian.isAlive() ? guardian : this; }

    /** Called once after every attacking action: uses an Opus charge and lets the clone strike. */
    public String afterAttack(List<Enemy> targets) {
        StringBuilder sb = new StringBuilder();
        if (cloneT > 0)
            for (Enemy e : targets) if (e.isAlive()) sb.append("The shadow clone strikes ").append(e.getName()).append(" for ").append(e.takeTrueDamage(cloneDmg)).append(". ");
        if (empowerHits > 0 && --empowerHits == 0) { empower = 0; sb.append("Bloody Opus fades. "); }
        return sb.toString().trim();
    }

    @Override public String tickStatus() {
        if (safeT > 0 && --safeT == 0) safeHeal = 0;
        if (samsaraT > 0 && --samsaraT == 0) samsara = null;
        if (cloneT > 0) cloneT--;
        if (guardT > 0 && --guardT == 0) guardian = null;
        if (demonT > 0 && --demonT == 0) demonAtk = 0;
        if (rageT > 0 && --rageT == 0) rageAtk = 0;
        return super.tickStatus();
    }
    @Override public void clearStatus() {
        super.clearStatus();
        safeHeal = safeT = samsaraT = 0; samsara = null;
        empower = empowerHits = cloneDmg = cloneT = guardT = opusAtk = 0; guardian = null; opusTargets = 1;
        demonAtk = demonT = rageAtk = rageT = 0;
    }
    @Override public List<String[]> statusInfo() {
        List<String[]> l = super.statusInfo();
        if (empowerHits > 0) l.add(new String[]{"OPUS +" + empower + " x" + empowerHits, "Bloody Opus: ATK +" + empower + " for the next " + empowerHits + " attacks."});
        if (hasOpus()) l.add(new String[]{"OPUS", "Bloody Opus (this battle): Attack hits " + opusTargets + " random enemies, ATK +" + opusAtk + "."});
        if (demonT > 0) l.add(new String[]{"DEMON +" + demonAtk, "Demon Mode: ATK +" + demonAtk + " (" + demonT + (demonT == 1 ? " turn)." : " turns).")});
        if (rageT > 0) l.add(new String[]{"RAGE +" + rageAtk, "Rage: ATK +" + rageAtk + " (" + rageT + (rageT == 1 ? " turn)." : " turns).")});
        if (cloneT > 0) l.add(new String[]{"CLONE", "Shadow clone: every attack deals +" + cloneDmg + " damage (" + cloneT + " turns)."});
        for (Passive p : passives) l.add(0, new String[]{"P:" + p.getTag(), "Passive - " + p.getName() + ": " + p.getDesc()});
        if (safeT > 0) l.add(new String[]{"SAFEHAVEN", "Recovers " + safeHeal + " HP every time an enemy hits " + getName() + " (" + safeT + " turns)."});
        if (samsaraT > 0 && samsara != null) l.add(new String[]{"SAMSARA", "When an enemy hits " + getName() + ", " + samsara.getName() + " recovers " + SAMSARA_HEAL + " HP (" + samsaraT + " turns)."});
        if (guardT > 0 && guardian != null) l.add(new String[]{"GUARDED", guardian.getName() + " takes single-target attacks aimed at " + getName() + " (" + guardT + " turns)."});
        return l;
    }

    public void startBattle() {
        clearStatus(); for (Skill s : skills) s.resetCooldown();
        for (Passive p : passives) p.onBattleStart(this);
    }
    /** End-of-round passive effects (only while alive). */
    public String passiveRoundEnd() {
        StringBuilder sb = new StringBuilder();
        if (!isAlive()) return "";
        for (Passive p : passives) { String s = p.onRoundEnd(this); if (!s.isEmpty()) sb.append(sb.length() > 0 ? "\n" : "").append(s); }
        return sb.toString();
    }
    public void addPassive(Passive p) { passives.add(p); }

    public static final int SAMSARA_HEAL = 10;
    public void giveSafehaven(int heal, int turns) { safeHeal = Math.max(safeHeal, heal); safeT = Math.max(safeT, turns); }
    public void linkSamsara(Hero shu, int turns) { samsara = shu; samsaraT = Math.max(samsaraT, turns); }
    /** Called every time an enemy attack lands on this hero (Safehaven / Samsara). Returns a log note or "". */
    public String onEnemyHit() {
        StringBuilder sb = new StringBuilder();
        if (safeT > 0 && isAlive()) { int h = heal(safeHeal); if (h > 0) sb.append(" Safehaven: ").append(getName()).append(" +").append(h).append(" HP."); }
        if (samsaraT > 0 && samsara != null && samsara.isAlive()) { int h = samsara.heal(SAMSARA_HEAL); if (h > 0) sb.append(" Samsara: ").append(samsara.getName()).append(" +").append(h).append(" HP."); }
        return sb.toString();
    }
    public List<Passive> getPassives() { return Collections.unmodifiableList(passives); }
    public void tickSkills() { for (Skill s : skills) s.tick(); }
    public void resetCooldowns() { for (Skill s : skills) s.resetCooldown(); }
    public void restoreAll() { clearStatus(); revive(1); heal(getMaxHp()); }

    /** Small reward for clearing a stage. Heroes who attack with DEF also gain DEF. */
    public void stageBonus() {
        upgrade(Stat.HP); upgrade(Stat.ATK); upgrade(Stat.MATK);
        if (basicStat == Stat.DEF) upgrade(Stat.DEF);
    }

    /** Permanent upgrade. Returns false when the stat is already at its limit. */
    public boolean upgrade(Stat s) {
        switch (s) {
            case HP:   gain(5, 0, 0, 0, 0); return true;                 // no cap any more
            case DEF:  if (getBaseDef() >= 10) return false;   gain(0, 1, 0, 0, 0); return true;
            case MDEF: if (getBaseMdef() >= 10) return false;  gain(0, 0, 1, 0, 0); return true;
            case ATK:  gain(0, 0, 0, 1, 0); return true;
            default:   gain(0, 0, 0, 0, 1); return true;
        }
    }
    /** Max HP raise that ignores the shop cap (used by rare events). */
    public void growMaxHp(int n) { gain(n, 0, 0, 0, 0); }
    /** Permanent Max HP loss (Noir's bargain). Never below 1 Max HP and never kills. */
    public void loseMaxHp(int n) {
        n = Math.min(n, getMaxHp() - 1);
        if (n <= 0) return;
        gain(-n, 0, 0, 0, 0);
        if (!isAlive()) revive(1);
    }
    /** Permanent stat loss from events (never below 0). */
    public boolean lower(Stat s) {
        switch (s) {
            case ATK:  if (getBaseAtk() <= 0) return false; gain(0, 0, 0, -1, 0); return true;
            case MATK: if (getBaseMatk() <= 0) return false; gain(0, 0, 0, 0, -1); return true;
            case DEF:  if (getBaseDef() <= 0) return false; gain(0, -1, 0, 0, 0); return true;
            case MDEF: if (getBaseMdef() <= 0) return false; gain(0, 0, -1, 0, 0); return true;
            default:   loseMaxHp(5); return true;
        }
    }

    // ================================================================ rosters
    /** The 4 heroes you can start with. A fresh copy is created every call. */
    public static List<Hero> starters() {
        List<Hero> l = new ArrayList<>();
        l.add(new Hero("Vina", "Sword of Victoria", "A lioness who carries her homeland on her shoulders. Protects allies, strikes down threats.",
                new Color(230, 180, 70), 40, 5, 2, 5, 0)
            .with(new Skill.Damage("Reforged Radiance", "Golden burst: ALL enemies take x1.5 ATK.", 3, Skill.Target.ALL_ENEMIES, 1.5, false, 1).fp(2).fx("STARBURST_GOLD"))
            .with(new Skill.Inspire("Homeward Bound", "Vina ATK +5 for 3 turns (no stacking).", 4, Skill.Target.SELF, 5, 3).fp(1).fx("FLARE_GOLD"))
            .with(new Skill.Ward("The Kings' Sighs", "PARTY: all damage taken -(Vina DEF / 2) for 4 turns.", 4, 0, 4).fp(2).fx("RING_YELLOW"))
            .with(new Skill.Splash("And All Be In My Name", "x2 ATK + M.ATK to one enemy, x1 ATK to every other enemy. Vina heals 20 HP.", 5, 2.0, 1.0, 20).withMatk().fp(4).fx("CROSS_GOLD")));
        l.add(new Hero("Exusiai", "Wanderer Bard", "A cheerful angel whose joyful songs help everyone.",
                new Color(220, 90, 80), 35, 4, 4, 0, 5).basic(Stat.MATK)
            .with(new Skill.Ignite("Encore", "No damage: 80% chance each enemy BURNS for (Exusiai ATK) per turn, 2 turns.", 4, 80, 2).fp(1).fx("FLAMES"))
            .with(new Skill.Hex("Star of Valley", "One enemy takes x2 M.ATK, 60% BLIND for 2 turns.", 3, Skill.Target.ENEMY, 2, true, 1,
                    EnemyMove.Ailment.BLIND, 60, 0, 2).fp(1).fx("FLARE_WHITE"))
            .with(new Skill.SpellWard("Hymn of Respite", "Null the next magic hit on you; the caster takes x2.5 M.ATK magic dmg.", 4, 2.5).fp(1).fx("GLOW_VIOLET"))
            .with(new Skill.Anthem("Angel's Blessings", "PARTY: heal 13 HP and ATK/M.ATK +4 for 3 turns.", 5, 13, 4, 3).fp(3).fx("GLITTER")));
        l.add(new Hero("Hivia", "The Guardian", "Quiet and steadfast. Hides her comrades from danger so they can fight another day.",
                new Color(140, 170, 120), 50, 5, 3, 2, 0).basic(Stat.DEF)
            .with(new Skill.Camouflage("Emergency Camouflage", "Ally heals 10 HP and is HIDDEN for 2 turn.", 3, 10, 2).fp(1).fx("HEAL_GREEN"))
            .with(new Skill.Buff("Group Camouflage", "PARTY: DEF +3 for 3 turns.", 4, Skill.Target.PARTY, Skill.Guard.DEF, 3, 3).fp(2).fx("SHIELD_BLUE"))
            .with(new Skill.Thorns("Strike Defensive", "Brace this turn: attackers take (DEF + M.DEF + ATK + M.ATK) / 2 + 2 damage. Hivia heals 5 HP.", 2, 0, 1, 2, 5).allStats().fp(1).fx("SPIKES_DARK2"))
            .with(new Skill.Haven("Last Line of Defense", "Ally: DEF/M.DEF +5 and SAFEHAVEN for 2 turns (heals 5 HP every time an enemy hits them).", 4, 5, 5, 2).fp(3).fx("AURA_GOLD")));
        l.add(new Hero("Phantom", "Assassin", "A performer between reality and illusion. A second shadow follows his every move.",
                new Color(140, 110, 190), 25, 3, 2, 10, 0)
            .with(new Skill.Barrier("Phantom of the Night", "Barrier absorbs damage equal to Phantom's ATK for 3 turns, heal 10 HP.", 3, 0, 3, 10).fp(1).fx("ORB_PURPLE"))
            .with(new Skill.Opus("Bloody Opus", "Rest of battle: Attack hits 3 random enemies, ATK + Phantom's M.ATK.", 4, 3, 0).fp(2).fx("BLOOD_BURST"))
            .with(new Skill.Hex("Night Raid", "ALL enemies take x1.3 ATK, 50% STUN each.", 3, Skill.Target.ALL_ENEMIES, 1.3, false, 1,
                    EnemyMove.Ailment.STUN, 50, 0, 1).fp(2).fx("SLASH_PURPLE"))
            .with(new Skill.MirrorNight("Phantom in the Mirror", "Shadow clone (4 turns): every attack deals +ATK/3 damage. Cleanse the party, party ATK +2 for 4 turns, 50% BLIND on every enemy (2 turns).",
                    5, 4, 2, 4, 50, 2).fp(3).fx("DARK_ORB")));
        return l;
    }

    /** The 4 heroes who can join after Stage 2. */
    public static List<Hero> companions() {
        List<Hero> l = new ArrayList<>();
        l.add(new Hero("Irene", "Inquisitor", "She walks this path to redeem all sinners. Sword and gun, mix and match.",
                new Color(200, 60, 70), 40, 3, 4, 4, 4)
            .with(new Skill.DualShot("Sword and Handcannon", "Pick 2 enemies: 1st takes x1.6 ATK, 2nd takes x1.6 M.ATK.", 1, 1.6, 1.6).fp(1).fx("CROSS_RED"))
            .with(new Skill.Gust("Gust", "x2 ATK, enemy is AIRBORNE: magic dmg taken x1.5 (3 turns). Irene heals (ATK + M.ATK) / 2.", 3, 2, false, 3).healSelf().fp(2).fx("WIND_GRAY"))
            .with(new Skill.GuardStrike("Shattertide", "x2.5 M.ATK to one enemy, Irene DEF/M.DEF +3 (4 turns).", 2, 2.5, true, 3, 4).fp(1).fx("BURST_CYAN"))
            .with(new Skill.Judgment("Judgment", "Massive x2.5 ATK to ALL enemies (x3 vs AIRBORNE), 50% BLEED 5 (3 turns). Irene gets a 15 shield, then is STUNNED 1 turn.", 4, 2.5, 3.0).bleed(50, 5, 3).shield(15, 2).fp(4).fx("BURST_GOLD")));
        l.add(new Hero("Ingrid", "Agent", "For her daughter she can do anything. Finish the foe quickly, clean up safely.",
                new Color(110, 140, 200), 35, 5, 4, 7, 0)
            .with(new Skill.Sidestep("Accelerando", "Dodge EVERY attack this turn, heal 5 HP, remove her bad effects.", 3, 1, 5).fp(1).fx("BEAM_CYAN"))
            .with(new Skill.Hex("Discreet Discipline", "Target + 1 more enemy take x1.5 ATK, 33% STUN.", 2, Skill.Target.ENEMY, 1.5, false, 2,
                    EnemyMove.Ailment.STUN, 33, 0, 1).fp(1).fx("BLAST_FIRE"))
            .with(new Skill.Onslaught("Damoclean Interrogation", "x1.2 ATK + x0.8 M.ATK to up to 3 enemies. PARTY DEF +2 (2 turns).", 3, 1.2, 3, 2, 2).plusMatk(0.8).fp(2).fx("SPIKES_DARK"))
            .with(new Skill.Evasion("La Volpe Nascosta", "50% evade for 3 turns (dodging = counterattack), heal 5 HP.", 5, 50, 3, 5).fp(3).fx("SPARKS_ORANGE")));
        l.add(new Hero("Logos", "Caster", "A lost banshee looking for a way home. Destroys foes with strange magic.",
                new Color(120, 90, 160), 30, 3, 5, 0, 9).basic(Stat.MATK)
            .with(new Skill.Enfeeble("Requiem Aeternam", "No damage: target + 2 random enemies are WEAK -(M.ATK / 2) for (Logos ATK) turns.", 2, 3).fp(1).fx("DARK_RING"))
            .with(new Skill.Execute("Perish", "x2 M.ATK to one enemy. x4 M.ATK if it is below 50% HP.", 3, 2.0, 4.0, 0.5).fp(1).fx("DARK_ORB"))
            .with(new Skill.Hex("Synecdoche", "ALL enemies take x1.3 M.ATK, 70% BREAK -3 for 4 turns.", 2, Skill.Target.ALL_ENEMIES, 1.3, true, 1,
                    EnemyMove.Ailment.BREAK, 70, 3, 4).fp(2).fx("SHARDS_DARK"))
            .with(new Skill.Glaciate("Extended Acuity", "ALL enemies take x1.5 M.ATK (x3 if they already had a bad status), 80% FREEZE.", 3, 1.5, 3.0, 80).fp(3).fx("STARDUST_CYAN")));
        l.add(new Hero("Shu", "Meditator", "A wanderer at peace with samsara. Gives shelter and protects the innocent.",
                new Color(110, 190, 150), 45, 4, 5, 0, 5).basic(Stat.MATK)
            .with(new Skill.Regen("From Seasons Fourfold", "PARTY: Seed of Healing, +5 HP every turn for 3 turns.", 4, 5, 3).fp(1).fx("SPROUT_GREEN"))
            .with(new Skill.Heal("Verdant Wisdom", "Chosen ally heals 30 HP.", 3, Skill.Target.PICK_ALLY, 30).fp(2).fx("HEAL_GREEN"))
            .with(new Skill.Harvest2("Bountiful Harvest", "x1.6 M.ATK + ATK/2 to one enemy, then a chosen ally heals Shu's M.DEF + M.ATK.", 3, 1.6).fp(1).fx("RAYS_GREEN"))
            .with(new Skill.Samsara("Samsara", "One enemy can only target Shu for 3 turns. SAMSARA: whenever an enemy hits Shu or an ally, Shu heals 10 HP.", 6, 3).fp(3).fx("RING_GREEN")));
        return l;
    }

    /** Secret hero: joins as the 3rd party member after clearing the secret stage (Primal Forest). */
    public static Hero orchid() {
        return new Hero("Orchid", "Ranger", "A girl with a mysterious aura. Beautiful, dangerous, and deadly with a bow.",
                new Color(190, 120, 220), 40, 4, 2, 6, 6)
            .with(new Skill.Volley("Arrow Rain", "Hit 2 random enemies (x1.8 ATK) and WEAK -3 for 3 turns.", 2, 2, 1.8, 3, 3).fp(2).fx("FIRE_STREAKS"))
            .with(new Skill.Moonfall("A Flying in the Moon", "One enemy: x2 ATK + x2 M.ATK. Orchid is WEAK -5 next turn.", 3, 2, 2, 5).fp(2).fx("BURST_VIOLET"))
            .with(new Skill.Aggressive("Tale of Lonely Mystery", "PARTY: AGGRESSIVE (ATK +2, DEF -2) for 3 turns.", 4, 2, 3).fp(2).fx("SPARKLES"))
            .with(new Skill.Piercer("Dragon Piercer", "ALL enemies: x1 ATK + x1 M.ATK. Orchid DEF +5 for 1 turn.", 3, 1, 1, 5).fp(3).fx("BOLT_WIDE"));
    }

    /** Secret hero: joins as the 3rd party member after beating the Divine Kirin (Primal Forest, needs Noir's notes).
     *  Taking her locks Orchid for the rest of the run. */
    public static Hero yato() {
        return new Hero("Yato", "Kirin Hunter", "A woman with confident, sharp eyes and a keen wit. Her combat prowess is real - she fights as quick as lightning.",
                new Color(110, 190, 230), 30, 4, 4, 8, 2)
            .with(new Skill.Hex("Kirin Bolts", "One enemy takes x2 ATK, 90% STUN.", 4, Skill.Target.ENEMY, 2.0, false, 1,
                    EnemyMove.Ailment.STUN, 90, 0, 1).fp(1).fx("BOLT_FORK"))
            .with(new Skill.DemonMode("Demon Mode", "Rage mode: Yato ATK + her M.ATK for 4 turns.", 5, 4).fp(2).fx("RING_BLOOD"))
            .with(new Skill.BladeDance("Blade Dance", "PARTY: RAGE, ATK +3 for 3 turns. Yato is SILENCED for 2 turns.", 4, 3, 3, 2).fp(1).fx("WIND_SWIRL"))
            .with(new Skill.SpinningBlade("Midair Spinning Blade Dance", "Target + 2 more enemies take x1.8 ATK and BLEED 5 for 3 turns.", 4, 3, 1.8, 5, 3).fp(3).fx("SLASH_PURPLE"));
    }

    public static List<Hero> roster() { List<Hero> l = starters(); l.addAll(companions()); l.add(orchid()); l.add(yato()); return l; }
}
