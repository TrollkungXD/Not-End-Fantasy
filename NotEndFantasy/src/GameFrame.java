import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

/** Main window. Swaps the content panel for each screen. */
public class GameFrame extends JFrame {
    private final GameState gs = new GameState();
    private MapPanel mapPanel;

    public GameFrame() {
        super("Not End Fantasy");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 720);
        setResizable(false);
        setLocationRelativeTo(null);
        // music volume bar on top; the window grows by its height so every screen keeps its full size
        setJMenuBar(new SoundBar());
        setSize(1000, 720 + SoundBar.HEIGHT);
        setLocationRelativeTo(null);
        showMenu();
    }
    private void swap(JPanel p) { setContentPane(p); revalidate(); repaint(); }

    public void showMenu() { Music.play(Music.TITLE); swap(new MenuPanel(this)); }
    public void showCharSelect() { Music.play(Music.MAP); swap(new CharSelectPanel(this, gs, false)); }
    public void showRecruit() { Music.play(Music.MAP); swap(new CharSelectPanel(this, gs, true)); }
    public void showStages() { Music.play(Music.MAP); swap(new StagePanel(this, gs)); }
    public void showMap() { Music.play(mapStage != null && mapStage.isSecret() ? Music.PRIMAL_MAP : Music.MAP); swap(mapPanel); }
    private Stage mapStage;                          // stage on the current map (picks the map theme)

    /** Starts a brand new run with one hero. */
    public void startGame(Hero leader) { gs.newGame(leader); showStages(); }

    public void recruit(Hero h) {
        gs.recruit(h);
        JOptionPane.showMessageDialog(this, h.getName() + " joins your party!\nFrom now on both heroes fight together.", "New Companion", JOptionPane.INFORMATION_MESSAGE);
        showStages();
    }

    public void enterStage(int number) {
        mapStage = Stage.create(number);
        mapPanel = new MapPanel(this, gs, mapStage);
        showMap();
    }
    public void startBattle(Stage st, List<Enemy> enemies, boolean boss, Consumer<Boolean> done) {
        startBattle(st, enemies, boss, battleMusic(st, enemies, boss), done);
    }
    /** track = file in Ost/ (see Music). */
    public void startBattle(Stage st, List<Enemy> enemies, boolean boss, String track, Consumer<Boolean> done) {
        Music.play(track);
        swap(new BattlePanel(gs, st, enemies, boss, done));
    }
    private static String battleMusic(Stage st, List<Enemy> enemies, boolean boss) {
        for (Enemy e : enemies) if (e.getBaseName().equals("Arch Regalia")) return Music.REGALIA;
        if (!boss) return Music.BATTLE;
        switch (st.getNumber()) {                    // each boss has its own track (falls back to Boss1-4Fight)
            case 3: return Music.GLOOM;
            case 4: return Music.ABYSS;
            case GameState.SECRET_STAGE: return Music.PRIMAL_BOSS;
            default: return Music.BOSS;
        }
    }

    /** Permadeath: the whole run is gone, back to the very beginning. */
    public void gameOver() {
        JOptionPane.showMessageDialog(this, "Your whole party has fallen...\n\nEverything is lost: heroes, items, Mora and stages.\nThe journey starts again from the very beginning.",
                "Game Over", JOptionPane.WARNING_MESSAGE);
        gs.wipe();
        showMenu();
    }

    /** Ending: credits roll, then the thank-you scene. The run is wiped when the player leaves it. */
    public void showCredits() {
        Music.play(Music.CREDITS);
        swap(new CreditsPanel(this, gs.getParty(), () -> { gs.wipe(); showMenu(); }));
    }

    public void stageCleared(Stage st) {
        int bonus = 40 + 20 * st.getNumber();
        gs.addMora(bonus);
        for (Hero hero : gs.getParty()) hero.stageBonus();
        gs.healAll(GameState.CLEAR_HEAL);
        gs.markCleared(st.getNumber());
        if (st.isSecret()) {
            String msg = "You defeated " + st.getBossName() + " and walked out of the Primal Forest!\n+" + bonus + " Mora\nParty grows: Max HP +5, ATK +1, M.ATK +1. Party recovers 50% HP.";
            boolean joins = !gs.isOrchidLocked() && !gs.partyFull();
            if (joins) gs.recruit(Hero.orchid());
            String extra = joins ? "\n\nA girl with a mysterious aura steps out of the trees...\nSECRET HERO UNLOCKED: Orchid the Ranger joins your party as the 3rd member!"
                    : gs.isOrchidLocked() ? "\n\nA girl with a bow watches from the canopy, glances at Yato, and disappears into the leaves...\n(Orchid can't join this run - Yato already took the 3rd place.)" : "";
            JOptionPane.showMessageDialog(this, msg + extra,
                    "Secret Stage Clear", JOptionPane.INFORMATION_MESSAGE);
            showStages();
            return;
        }
        if (st.getNumber() == GameState.STAGES) {
            JOptionPane.showMessageDialog(this, "You defeated " + st.getBossName() + "!\nThe throne is empty... but the fantasy is not over.",
                    "Game Clear", JOptionPane.INFORMATION_MESSAGE);
            showCredits();
            return;
        }
        JOptionPane.showMessageDialog(this, "Stage cleared! Next stage unlocked.\n+" + bonus + " Mora\nParty grows: Max HP +5, ATK +1, M.ATK +1\nParty recovers 50% HP.",
                "Stage Clear", JOptionPane.INFORMATION_MESSAGE);
        if (gs.needsRecruit()) showRecruit(); else showStages();
    }
}
