import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/** Pick the starting hero, or (recruit mode) pick the companion who joins after Stage 2. */
public class CharSelectPanel extends JPanel {
    private final List<Hero> roster = new ArrayList<>();
    private int selected = -1;
    private final JButton confirm;

    public CharSelectPanel(GameFrame frame, GameState gs, boolean recruit) {
        // starters at the beginning, the 4 companions after Stage 2
        for (Hero h : recruit ? Hero.companions() : Hero.starters()) {
            boolean inParty = false;
            for (Hero p : gs.getParty()) if (p.getName().equals(h.getName())) inParty = true;
            if (!inParty) roster.add(h);
        }
        setLayout(new BorderLayout());
        setBackground(Ui.BG);
        JPanel head = new JPanel(new GridLayout(0, 1)); head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(14, 0, 8, 0));
        JLabel title = new JLabel(recruit ? "A new companion wants to join you!" : "Choose your hero", SwingConstants.CENTER);
        title.setFont(Ui.TITLE.deriveFont(32f)); title.setForeground(Ui.GOLD);
        JLabel sub = new JLabel(recruit ? "Pick 1 of 4 new allies. They catch up with the stage bonuses you already earned."
                : "You start alone. After clearing Stage 2 a companion will join you.  Death ends the whole run.", SwingConstants.CENTER);
        sub.setFont(Ui.BODY); sub.setForeground(Ui.DIM);
        head.add(title); head.add(sub);
        add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, roster.size(), 14, 0));
        grid.setOpaque(false);
        int side = roster.size() <= 3 ? 110 : 16;
        grid.setBorder(BorderFactory.createEmptyBorder(0, side, 0, side));
        for (int i = 0; i < roster.size(); i++) grid.add(new Card(i));
        add(grid, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 12));
        south.setOpaque(false);
        confirm = Ui.button(recruit ? "Recruit" : "Begin Journey", e -> {
            Hero h = roster.get(selected);
            if (recruit) frame.recruit(h); else frame.startGame(h);
        });
        confirm.setEnabled(false);
        if (!recruit) south.add(Ui.button("Back", e -> frame.showMenu()));
        south.add(confirm);
        add(south, BorderLayout.SOUTH);
    }

    private class Card extends JPanel {
        private final int idx;
        Card(int idx) {
            this.idx = idx;
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    selected = selected == idx ? -1 : idx;
                    confirm.setEnabled(selected >= 0); CharSelectPanel.this.repaint();
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.antialias(g);
            Hero h = roster.get(idx);
            boolean sel = selected == idx;
            int w = getWidth(), cx = w / 2;
            g.setColor(Ui.PANEL); g.fillRoundRect(0, 0, w - 1, getHeight() - 1, 18, 18);
            g.setStroke(new BasicStroke(sel ? 4f : 1.5f));
            g.setColor(sel ? Ui.GOLD : new Color(80, 85, 120));
            g.drawRoundRect(1, 1, w - 3, getHeight() - 3, 18, 18);
            g.setStroke(new BasicStroke(1f));
            Image portrait = Assets.hero(h.getName());
            if (portrait != null) Assets.contain(g, portrait, cx - 55, 10, 110, 125);
            else { g.setColor(h.getColor()); g.fillOval(cx - 30, 18, 60, 60); g.fillRoundRect(cx - 38, 74, 76, 60, 20, 20); }
            Ui.center(g, h.getName(), cx, 156, Ui.H1, Ui.TEXT);
            Ui.center(g, sel ? "SELECTED" : h.getRole(), cx, 174, Ui.BOLD, sel ? Ui.GOLD : h.getColor());
            int y = Ui.wrap(g, h.getBlurb(), 12, 192, w - 24, Ui.SMALL, Ui.DIM) + 2;
            String[] labels = {"HP", "DEF", "M.DEF", "ATK", "M.ATK"};
            int[] vals = {h.getMaxHp(), h.getDef(), h.getMdef(), h.getAtk(), h.getMatk()};
            int[] max = {Math.max(60, h.getMaxHp()), 10, 10, 15, 15};
            Color[] cols = {Ui.GREEN, Ui.BLUE, new Color(150, 110, 220), Ui.RED, new Color(240, 140, 60)};
            for (int i = 0; i < 5; i++) {
                g.setFont(Ui.SMALL); g.setColor(Ui.TEXT); g.drawString(labels[i], 12, y + 11);
                Ui.bar(g, 58, y, w - 70, 13, vals[i], max[i], cols[i], true);
                y += 17;
            }
            y += 10;
            g.setFont(Ui.BOLD); g.setColor(Ui.GOLD); g.drawString("Skills", 12, y);
            g.setFont(Ui.SMALL); g.setColor(Ui.DIM);
            String basic = "Attack = " + h.basicName() + (h.basicName().equals("Attack") ? "" : " (" + h.basicDesc().replace("Hit using ", "uses ").replace("Magic hit with ", "uses ") + ")");
            g.drawString(basic, w - 12 - g.getFontMetrics().stringWidth(basic), y); y += 15;
            for (Skill s : h.getSkills()) {
                g.setFont(Ui.BOLD.deriveFont(12f)); g.setColor(Ui.TEXT); g.drawString(s.getName() + "  (" + s.getFpCost() + " FP, CD " + s.getCooldown() + ")", 12, y); y += 13;
                y = Ui.wrap(g, s.getDesc(), 12, y, w - 24, Ui.SMALL, Ui.DIM) + 3;
            }
            g.dispose();
        }
    }
}
