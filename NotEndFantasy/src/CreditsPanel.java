import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Ending screen.
 */
public class CreditsPanel extends JPanel {
    public static final String CREATOR = "VT";
    public static final String PLAYER = "Player";

    private static final String[] LINES = {
        "@TITLE",
        "",
        "# Your Party",
        "@PARTY",
        "",
        "# Created by",
        CREATOR,
        "",
        "# Game Design",
        CREATOR,
        "# Programming",
        CREATOR,
        "# Story, Events & Balance",
        CREATOR,
        "",
        "# Made for",
        "Object-Oriented Programming (040613204)",
        "",
        "# Heroes",
        "Vina  -  Exusiai  -  Hivia  -  Phantom",
        "Irene  -  Ingrid  -  Logos  -  Shu",
        "Orchid, the hidden Ranger  -  Yato, the Kirin Hunter",
        "",
        "# Realms",
        "Curse Wood",
        "Ice Castle",
        "Burning Prophet Ruin",
        "Night Realm",
        "Primal Forest",
        "Memories Of Warfield",
        "",
        "# Bosses",
        "Faceworm Queen  -  Knight of Windstorm",
        "Gloom Under Night  -  Knight of Abyss",
        "Ancient Tree  -  Yato, the Wandering Blade",
        "Arch Regalia  -  Divine Kirin",
        "",
        "# Character & NPC Art",
        "Arknights (Hypergryph)",
        "# Monster Sprites",
        "Ragnarok Online (Gravity)",
        "# Music",
        "Not End Fantasy soundtrack selection",
        "",
        "All borrowed art and music belong to their original creators.",
        "Made as a non-commercial school project.",
        "",
        "# Special Thanks",
        "You, for walking every node until the very end.",
    };

    private static final double SPEED = 42;              // pixels per second
    private static final int LINE_H = 30, HEAD_H = 34, PARTY_H = 190, TITLE_H = 110;

    private final List<Hero> party;
    private final Runnable onExit;
    private final Timer timer;
    private final long start = System.currentTimeMillis();
    private long thanksAt = -1;                            
    private final JButton skip, back;
    private final int totalHeight;

    public CreditsPanel(GameFrame frame, List<Hero> party, Runnable onExit) {
        this.party = new ArrayList<>(party);               
        this.onExit = onExit;
        setLayout(null);
        setBackground(Color.BLACK);
        int h = 0;
        for (String l : LINES) h += heightOf(l);
        totalHeight = h;

        skip = Ui.button("Skip", e -> showThanks());
        back = Ui.button("Return to Title", e -> leave());
        back.setFont(Ui.H2);
        back.setVisible(false);
        add(skip); add(back);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (thanksAt < 0) showThanks(); }
        });
        timer = new Timer(16, e -> {
            if (thanksAt < 0 && scroll() > totalHeight + getHeight()) showThanks();
            repaint();
        });
        timer.start();
    }

    @Override public void doLayout() {
        skip.setBounds(getWidth() - 100, getHeight() - 46, 88, 32);
        back.setBounds(getWidth() / 2 - 110, getHeight() - 90, 220, 44);
    }

    private double scroll() { return (System.currentTimeMillis() - start) / 1000.0 * SPEED; }

    private void showThanks() {
        if (thanksAt >= 0) return;
        thanksAt = System.currentTimeMillis();
        skip.setVisible(false);
        Timer t = new Timer(2500, e -> back.setVisible(true));   
        t.setRepeats(false); t.start();
    }
    private void leave() { timer.stop(); onExit.run(); }

    private static int heightOf(String l) {
        if (l.equals("@TITLE")) return TITLE_H;
        if (l.equals("@PARTY")) return PARTY_H;
        if (l.startsWith("# ")) return HEAD_H;
        return LINE_H;
    }

    // dr
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        Ui.antialias(g);
        int w = getWidth(), h = getHeight();
        if (thanksAt < 0) drawCredits(g, w, h); else drawThanks(g, w, h);
        g.dispose();
    }

    private void backdrop(Graphics2D g, int w, int h, int stage, int dark) {
        Image bg = Assets.background(stage);
        if (bg != null) Assets.cover(g, bg, 0, 0, w, h);
        else { g.setPaint(new GradientPaint(0, 0, new Color(44, 30, 82), 0, h, Ui.BG)); g.fillRect(0, 0, w, h); }
        g.setColor(new Color(0, 0, 0, dark)); g.fillRect(0, 0, w, h);
    }

    private void drawCredits(Graphics2D g, int w, int h) {
        backdrop(g, w, h, GameState.STAGES, 200);
        int y = (int) (h - scroll());
        int cx = w / 2;
        for (String l : LINES) {
            int lh = heightOf(l);
            if (y + lh > -40 && y < h + 40) {
                if (l.equals("@TITLE")) {
                    Ui.center(g, "NOT END FANTASY", cx, y + 60, Ui.TITLE, Ui.GOLD);
                    Ui.center(g, "Thank you for playing", cx, y + 96, Ui.H1, Ui.TEXT);
                } else if (l.equals("@PARTY")) {
                    drawParty(g, cx, y + 6, 150);
                } else if (l.startsWith("# ")) {
                    Ui.center(g, l.substring(2).toUpperCase(), cx, y + 26, Ui.H2, Ui.GOLD);
                } else if (!l.isEmpty()) {
                    Ui.center(g, l, cx, y + 22, l.equals(CREATOR) ? Ui.H1 : Ui.BODY.deriveFont(16f), Ui.TEXT);
                }
            }
            y += lh;
        }
        g.setPaint(new GradientPaint(0, 0, Color.BLACK, 0, 70, new Color(0, 0, 0, 0))); g.fillRect(0, 0, w, 70);
        g.setPaint(new GradientPaint(0, h - 70, new Color(0, 0, 0, 0), 0, h, Color.BLACK)); g.fillRect(0, h - 70, w, 70);
    }

    /** Portraits of the heroes who survived the run, side by side. */
    private void drawParty(Graphics2D g, int cx, int top, int ph) {
        int n = Math.max(1, party.size()), cw = 150, x = cx - n * cw / 2;
        for (Hero hero : party) {
            Image p = Assets.hero(hero.getName());
            if (p != null) Assets.contain(g, p, x + 10, top, cw - 20, ph - 20);
            else { g.setColor(hero.getColor()); g.fillOval(x + cw / 2 - 30, top + 30, 60, 60); }
            Ui.center(g, hero.getName(), x + cw / 2, top + ph, Ui.BOLD, hero.getColor());
            x += cw;
        }
    }

    private void drawThanks(Graphics2D g, int w, int h) {
        long t = System.currentTimeMillis() - thanksAt;
        float fade = (float) Math.min(1.0, t / 2000.0);
        backdrop(g, w, h, GameState.SECRET_STAGE, 170);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));
        for (int i = 0; i < 36; i++) {
            double k = (t / 1000.0 * (8 + i % 7) + i * 53) % (h + 40);
            int x = (int) ((i * 131 + Math.sin(t / 900.0 + i) * 18) % w);
            g.setColor(new Color(255, 230, 150, 60 + (i * 29) % 120));
            g.fillOval(x, (int) (h - k), 3 + i % 3, 3 + i % 3);
        }
        int cx = w / 2;
        Ui.center(g, "To " + PLAYER , cx, 120, Ui.H1.deriveFont(30f), Ui.GOLD);
        Ui.center(g, "thank you for playing this game.", cx, 162, Ui.H1, Ui.TEXT);
        Font body = Ui.BODY.deriveFont(16f);
        Ui.center(g, "Every hero, every monster and every node on the map", cx, 214, body, Ui.DIM);
        Ui.center(g, "exists because you adventure through them.", cx, 238, body, Ui.DIM);
        Ui.center(g, "The fantasy does not end here.", cx, 280, body.deriveFont(Font.ITALIC), Ui.TEXT);
        drawParty(g, cx, 318, 170);
        Ui.center(g, "-  THANK YOU FOR PLAYING  -", cx, 548, Ui.H2, Ui.GOLD);
        g.setComposite(old);
    }
}
