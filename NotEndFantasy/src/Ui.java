import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/** Shared look and small drawing helpers. */
public final class Ui {
    private Ui() {}
    public static final Font TITLE = new Font("SansSerif", Font.BOLD, 52);
    public static final Font H1 = new Font("SansSerif", Font.BOLD, 22);
    public static final Font H2 = new Font("SansSerif", Font.BOLD, 17);
    public static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font BOLD = new Font("SansSerif", Font.BOLD, 14);
    public static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);
    public static final Font SMALL_B = new Font("SansSerif", Font.BOLD, 11);
    public static final Color BG = new Color(20, 22, 34), PANEL = new Color(34, 37, 56), BTN = new Color(52, 56, 88),
            BTN_HOVER = new Color(72, 78, 122), LINE = new Color(95, 100, 140),
            TEXT = new Color(236, 236, 246), DIM = new Color(150, 154, 180), GOLD = new Color(244, 196, 64),
            RED = new Color(220, 80, 80), GREEN = new Color(90, 200, 120), BLUE = new Color(90, 150, 230),
            PURPLE = new Color(170, 120, 230), ORANGE = new Color(240, 150, 60), TEAL = new Color(80, 200, 190);

    public static JButton button(String text, ActionListener a) {
        JButton b = new JButton(text);
        b.setFont(BODY); b.setForeground(TEXT); b.setBackground(BTN);
        b.setFocusPainted(false); b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(BTN_HOVER); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(BTN); }
        });
        b.addActionListener(a);
        return b;
    }
    public static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    public static void center(Graphics2D g, String s, int cx, int y, Font f, Color c) {
        g.setFont(f); g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }
    /** Shortens text with "..." so it fits in w pixels. */
    public static String fit(Graphics2D g, String s, Font f, int w) {
        FontMetrics fm = g.getFontMetrics(f);
        if (fm.stringWidth(s) <= w) return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > w) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
    /** Draws word-wrapped text and returns the next free y. */
    public static int wrap(Graphics2D g, String s, int x, int y, int w, Font f, Color c) {
        g.setFont(f); g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        String line = "";
        for (String word : s.split(" ")) {
            String t = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(t) > w && !line.isEmpty()) { g.drawString(line, x, y); y += fm.getHeight(); line = word; }
            else line = t;
        }
        if (!line.isEmpty()) { g.drawString(line, x, y); y += fm.getHeight(); }
        return y;
    }
    public static void bar(Graphics2D g, int x, int y, int w, int h, int v, int max, Color c, boolean label) {
        g.setColor(new Color(0, 0, 0, 150)); g.fillRoundRect(x, y, w, h, h, h);
        int fw = max <= 0 ? 0 : (int) Math.round(w * Math.max(0, Math.min(v, max)) / (double) max);
        g.setColor(c); if (fw > 0) g.fillRoundRect(x, y, fw, h, h, h);
        g.setColor(new Color(255, 255, 255, 90)); g.drawRoundRect(x, y, w, h, h, h);
        if (label) center(g, v + "/" + max, x + w / 2, y + h - 3, SMALL, Color.WHITE);
    }
    /** HP bar whose color goes green -> yellow -> red. */
    public static void hpBar(Graphics2D g, int x, int y, int w, int h, Entity e) {
        double r = e.hpRatio();
        Color c = r > 0.5 ? GREEN : r > 0.25 ? GOLD : RED;
        bar(g, x, y, w, h, e.getHp(), e.getMaxHp(), c, true);
    }

    public static Color statusColor(String label) {
        if (label.startsWith("P:")) return new Color(170, 120, 230);          // permanent passive (Yato's reward)
        if (label.startsWith("GUARD") || label.startsWith("DEF") || label.startsWith("M.DEF +") || label.startsWith("DMG") || label.startsWith("SHIELD")) return BLUE;
        if (label.startsWith("AGGRESSIVE") || label.startsWith("DEMON") || label.startsWith("RAGE")) return new Color(220, 70, 110);
        if (label.startsWith("POWER") || label.startsWith("OPUS") || label.startsWith("CLONE")) return ORANGE;
        if (label.startsWith("REGEN") || label.startsWith("SAFEHAVEN") || label.startsWith("SAMSARA")) return GREEN.darker();
        if (label.startsWith("DODGE") || label.startsWith("EVADE") || label.startsWith("HIDDEN")) return TEAL.darker();
        if (label.startsWith("HYMN") || label.startsWith("THORNS") || label.startsWith("GUARDED") || label.startsWith("TAUNT")) return GOLD.darker();
        if (label.startsWith("WEAK") || label.startsWith("M.DEF 0") || label.startsWith("M.ATK 0") || label.startsWith("AIRBORNE")) return PURPLE;
        if (label.startsWith("POISON")) return new Color(110, 190, 70);
        if (label.startsWith("STUN") || label.startsWith("ENRAGED")) return RED;
        if (label.startsWith("FROZEN")) return new Color(110, 180, 240);
        if (label.startsWith("BURN")) return new Color(235, 110, 40);
        if (label.startsWith("BLEED")) return new Color(190, 30, 50);
        if (label.startsWith("BREAK") || label.startsWith("CURSE")) return new Color(150, 60, 160);
        if (label.startsWith("SILENCE") || label.startsWith("BLIND")) return new Color(90, 90, 110);
        if (label.startsWith("STEADY")) return TEAL.darker();
        if (label.startsWith("BOSS") || label.startsWith("ELITE")) return GOLD.darker().darker();
        return DIM;
    }
    /** Draws small colored status tags centered on cx. */
    public static void statusChips(Graphics2D g, Entity e, int cx, int y) {
        List<String> ls = e.statusLabels();
        if (ls.isEmpty()) return;
        g.setFont(SMALL_B);
        FontMetrics fm = g.getFontMetrics();
        int total = 0;
        for (String s : ls) total += fm.stringWidth(s) + 12 + 4;
        int x = cx - total / 2;
        for (String s : ls) {
            int w = fm.stringWidth(s) + 12;
            Color c = statusColor(s);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200)); g.fillRoundRect(x, y, w, 15, 8, 8);
            g.setColor(Color.WHITE); g.drawString(s, x + 6, y + 12);
            x += w + 4;
        }
    }

    /** Compact hero card: portrait, name, HP bar. Used on map, stage select and battle. */
    public static void heroChip(Graphics2D g, Hero h, int x, int y, int w, int hh) {
        g.setColor(new Color(10, 12, 24, 215)); g.fillRoundRect(x, y, w, hh, 12, 12);
        g.setColor(h.isAlive() ? h.getColor() : RED.darker()); g.drawRoundRect(x, y, w, hh, 12, 12);
        Image p = Assets.hero(h.getName());
        int ps = hh - 8;
        if (p != null) Assets.contain(g, p, x + 4, y + 4, ps, ps);
        else { g.setColor(h.getColor()); g.fillOval(x + 6, y + 6, ps - 4, ps - 4); }
        int tx = x + ps + 10, tw = w - ps - 18;
        g.setFont(BOLD); g.setColor(h.isAlive() ? TEXT : RED); g.drawString(h.getName() + (h.isAlive() ? "" : "  KO"), tx, y + 17);
        hpBar(g, tx, y + hh - 22, tw, 14, h);
    }

    /** Scales an image into a box without stretching it. */
    public static Icon icon(Image img, int maxW, int maxH) {
        int iw = Math.max(1, img.getWidth(null)), ih = Math.max(1, img.getHeight(null));
        double sc = Math.min(maxW / (double) iw, maxH / (double) ih);
        return new ImageIcon(img.getScaledInstance(Math.max(1, (int) (iw * sc)), Math.max(1, (int) (ih * sc)), Image.SCALE_SMOOTH));
    }

    /** Lets the player pick which hero gets an effect. Returns null if cancelled. */
    public static Hero chooseHero(Component parent, List<Hero> party, String title) {
        if (party.size() == 1) return party.get(0);
        String[] names = new String[party.size()];
        for (int i = 0; i < names.length; i++) {
            Hero h = party.get(i);
            names[i] = h.getName() + " (" + h.getHp() + "/" + h.getMaxHp() + ")";
        }
        int pick = JOptionPane.showOptionDialog(parent, "Choose a hero:", title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
        return pick < 0 ? null : party.get(pick);
    }
}
