import java.util.*;

/** Everything that persists between screens during one run. */
public class GameState {
    public static final boolean UNLOCK_ALL = false;   // set true to test any stage
    public static final int STAGES = 5;
    public static final int RECRUIT_AFTER = 2;        // a companion joins after this stage
    public static final double REST_HEAL = 0.5;       // Save Spot heals half of Max HP
    public static final double CLEAR_HEAL = 0.5;      // clearing a stage heals half of Max HP
    public static final int SECRET_STAGE = 6;         // Primal Forest (reached through Yato in Night Realm)
    public static final int MAX_FP = 5;               // Fantasy Points shared by the whole party (full again after every node)
    public static final int FP_PER_ROUND = 1;
    public static final int MAX_PARTY = 3;            // leader + companion + ONE secret hero (Orchid, Yato, ...)         // FP regained at the end of every round

    private final List<Hero> party = new ArrayList<>();
    private int mora;
    private final Inventory inventory = new Inventory();
    private final Set<Integer> cleared = new HashSet<>();
    private boolean secretFound;                      // Yato received the Topographical Map
    private boolean yatoDefeated;                     // Yato's duel can only be won once per run
    private boolean orchidLocked;                     // a different secret hero (Yato) took the 3rd slot this run

    /** Brand new run with one hero. Nothing from an old run is kept. */
    public void newGame(Hero leader) {
        party.clear(); party.add(leader);
        mora = 40; cleared.clear(); secretFound = false; yatoDefeated = false; orchidLocked = false;
        inventory.clear(); inventory.add(Items.POTION, 2);
    }
    /** Wipes everything (used on Game Over). */
    public void wipe() { party.clear(); cleared.clear(); inventory.clear(); mora = 0; secretFound = false; yatoDefeated = false; orchidLocked = false; }

    public void findSecret() { secretFound = true; }
    public boolean isSecretFound() { return secretFound; }
    public void defeatYato() { yatoDefeated = true; }
    public boolean isYatoDefeated() { return yatoDefeated; }
    public boolean isOrchidLocked() { return orchidLocked; }
    public boolean partyFull() { return party.size() >= MAX_PARTY; }
    public boolean hasHero(String name) { for (Hero h : party) if (h.getName().equals(name)) return true; return false; }
    /** Yato takes the secret slot: she joins now and Orchid can't join in this run. */
    public boolean recruitYato() {
        if (partyFull() || hasHero("Yato")) return false;
        recruit(Hero.yato());
        orchidLocked = true;
        return true;
    }

    /** A companion joins and catches up with the stage bonuses the leader already earned. */
    public void recruit(Hero h) {
        for (int i = 0; i < cleared.size(); i++) h.stageBonus();
        party.add(h);
    }
    public boolean needsRecruit() { return party.size() == 1 && cleared.contains(RECRUIT_AFTER); }

    /** The first living hero leads on the map. */
    public Hero getHero() { for (Hero h : party) if (h.isAlive()) return h; return party.get(0); }
    public List<Hero> getParty() { return Collections.unmodifiableList(party); }
    public List<Hero> living() { List<Hero> l = new ArrayList<>(); for (Hero h : party) if (h.isAlive()) l.add(h); return l; }
    public Hero randomHero(Random r) { List<Hero> l = living(); return l.get(r.nextInt(l.size())); }

    public String healAll(double pct) {
        StringBuilder sb = new StringBuilder();
        for (Hero h : party) { h.revive(1); sb.append(h.getName()).append(" +").append(h.healPct(pct)).append(" HP  "); }
        return sb.toString().trim();
    }
    public String hurtAll(int dmg) {
        for (Hero h : party) h.loseHpKeep1(dmg);
        return party.size() == 1 ? "You lose " + dmg + " HP." : "Everyone loses " + dmg + " HP.";
    }

    public Inventory getInventory() { return inventory; }
    public int getMora() { return mora; }
    public void addMora(int n) { mora += n; }
    public boolean spend(int n) { if (mora < n) return false; mora -= n; return true; }
    public void loseMora(int n) { mora = Math.max(0, mora - n); }

    /** Stages are played in order. A cleared stage cannot be replayed (no farming in a roguelike run). */
    public boolean isUnlocked(int stage) {
        if (UNLOCK_ALL) return true;
        if (stage == SECRET_STAGE) return secretFound && cleared.contains(4) && !cleared.contains(SECRET_STAGE) && !cleared.contains(STAGES);
        return !cleared.contains(stage) && (stage == 1 || cleared.contains(stage - 1));
    }
    public boolean isCleared(int stage) { return cleared.contains(stage); }
    public void markCleared(int stage) { cleared.add(stage); }
    public int clearedCount() { return cleared.size(); }
}
