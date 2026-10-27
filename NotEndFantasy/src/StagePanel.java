import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class StagePanel extends JPanel {
    private final GameFrame frame;
    private final GameState gs;
    private final Stage[] previews;

    public StagePanel(GameFrame frame, GameState gs) {
        this.frame = frame; this.gs = gs;
        setLayout(new BorderLayout());
        setBackground(Ui.BG);

        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(12, 18, 8, 18));
        JLabel title = new JLabel("Select Stage", SwingConstants.LEFT);
        title.setFont(Ui.TITLE.deriveFont(32f)); title.setForeground(Ui.GOLD);
        head.add(title, BorderLayout.WEST);
        head.add(new PartyStrip(), BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        // the secret stage card appears once Yato got the map (or after it was cleared)
        int cards = gs.isSecretFound() || gs.isCleared(GameState.SECRET_STAGE) ? GameState.STAGES + 1 : GameState.STAGES;
        previews = new Stage[cards];
        JPanel grid = new JPanel(new GridLayout(1, cards, 10, 0));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        for (int i = 0; i < cards; i++) { previews[i] = Stage.create(i + 1); grid.add(new Card(i)); }
        add(grid, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 14));
        south.setOpaque(false);
        south.add(Ui.button("Abandon Run", e -> {
            if (JOptionPane.showConfirmDialog(this, "Abandon this run? Everything will be lost.", "Abandon", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                gs.wipe(); frame.showMenu();
            }
        }));
        south.add(Ui.button("Inventory", e -> { new InventoryDialog(frame, gs).setVisible(true); repaint(); }));
        add(south, BorderLayout.SOUTH);
    }

    /** Small party summary in the header. */
    private class PartyStrip extends JComponent {
        PartyStrip() { setPreferredSize(new Dimension(198 * Math.max(2, gs.getParty().size()) + 80, 54)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.antialias(g);
            int x = 0;
            for (Hero h : gs.getParty()) { Ui.heroChip(g, h, x, 2, 190, 48); x += 198; }
            if (gs.getParty().size() == 1) {
                g.setColor(new Color(255, 255, 255, 30)); g.fillRoundRect(x, 2, 190, 48, 12, 12);
                Ui.center(g, "Companion joins", x + 95, 22, Ui.SMALL, Ui.DIM);
                Ui.center(g, "after Stage " + GameState.RECRUIT_AFTER, x + 95, 38, Ui.SMALL, Ui.DIM);
            }
            g.setFont(Ui.BOLD); g.setColor(Ui.GOLD);
            g.drawString(gs.getMora() + " Mora", getWidth() - 74, 32);
            g.dispose();
        }
    }

    private class Card extends JPanel {
        private final int idx;
        Card(int idx) {
            this.idx = idx;
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (gs.isUnlocked(idx + 1)) frame.enterStage(idx + 1);
                }
            });
            if (gs.isUnlocked(idx + 1)) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.antialias(g);
            Stage s = previews[idx];
            boolean open = gs.isUnlocked(idx + 1), done = gs.isCleared(idx + 1);
            int w = getWidth(), h = getHeight();
            Image bg = Assets.background(s.getNumber());
            g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 18, 18));
            if (bg != null) Assets.cover(g, bg, 0, 0, w, h);
            g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 24, open ? 165 : 210), 0, h, new Color(10, 12, 24, 235)));
            g.fillRect(0, 0, w, h);
            g.setClip(null);
            g.setColor(open ? s.getAccent() : done ? Ui.GREEN.darker() : new Color(70, 70, 85));
            g.setStroke(new BasicStroke(open ? 3f : 2f)); g.drawRoundRect(1, 1, w - 3, h - 3, 18, 18);
            Ui.center(g, s.isSecret() ? "SECRET STAGE" : "STAGE " + s.getNumber(), w / 2, 40, Ui.BOLD, open ? s.getAccent() : Ui.DIM);
            int y = Ui.wrap(g, s.getName(), 14, 78, w - 28, Ui.H1, open || done ? Ui.TEXT : Ui.DIM) + 6;
            y = Ui.wrap(g, s.getSubtitle(), 14, y, w - 28, Ui.BODY, Ui.DIM) + 14;
            g.setFont(Ui.BODY); g.setColor(Ui.TEXT);
            g.drawString("Path: " + s.getLength() + " steps", 14, y); y += 20;
            g.drawString("Nodes: " + s.getNodes().size(), 14, y); y += 20;
            y = Ui.wrap(g, "Boss: " + s.getBossName(), 14, y, w - 28, Ui.BODY, Ui.TEXT) + 6;
            String party = s.isSecret() ? (gs.isOrchidLocked() ? "Yato walks with you. (Orchid locked this run)" : "Reward: secret hero Orchid joins!") : s.getNumber() <= GameState.RECRUIT_AFTER ? "Solo" : "Party of 2+";
            Ui.wrap(g, party, 14, y, w - 28, Ui.SMALL, s.isSecret() ? Ui.GOLD : Ui.DIM);
            String status = done ? "CLEARED" : open ? "ENTER" : s.isSecret() ? "AFTER STAGE 4" : "LOCKED";
            Ui.center(g, status, w / 2, h - 30, Ui.H1, done ? Ui.GREEN : open ? Ui.GOLD : Ui.RED);
            g.dispose();
        }
    }
}
