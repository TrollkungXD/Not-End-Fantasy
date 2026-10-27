import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/** Node map. The pin moves between nodes on its own Thread and the camera follows it. */
public class MapPanel extends JPanel {
    private final GameFrame frame;
    private final GameState gs;
    private final Stage stage;
    private final MapCanvas canvas = new MapCanvas();
    private final JLabel title = new JLabel(), info = new JLabel();
    private final List<Node> path = new ArrayList<>();
    private Node current, hover;
    private volatile double pinX, pinY, camX;
    private volatile boolean busy;

    public MapPanel(GameFrame frame, GameState gs, Stage stage) {
        this.frame = frame; this.gs = gs; this.stage = stage;
        current = stage.getStart();
        path.add(current);
        pinX = current.getX(); pinY = current.getY();
        setLayout(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setBackground(Ui.PANEL);
        top.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, stage.getAccent()),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        title.setFont(Ui.H1); title.setForeground(stage.getAccent());
        title.setText((stage.isSecret() ? "Secret Stage" : "Stage " + stage.getNumber()) + ": " + stage.getName());
        top.add(title, BorderLayout.WEST);
        info.setFont(Ui.BOLD); info.setForeground(Ui.GOLD);
        top.add(info, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(Ui.button("Inventory", e -> { new InventoryDialog(frame, gs).setVisible(true); refresh(); }));
        btns.add(Ui.button("Leave Stage", e -> {
            if (!busy && JOptionPane.showConfirmDialog(this, "Leave this stage? Your position on the map is lost\n(HP, items and Mora are kept).", "Leave", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                frame.showStages();
        }));
        top.add(btns, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        int step = current.getLayer(), len = stage.getLength() - 1;
        info.setText("Step " + step + "/" + len + "     Mora " + gs.getMora() + "     Potions " + (gs.getInventory().count(Items.POTION) + gs.getInventory().count(Items.HI_POTION)));
        canvas.repaint();
    }

    private double clampCam(double c) {
        double max = 110 + (stage.getLength() - 1) * 150 + 110 - canvas.getWidth();
        return Math.max(0, Math.min(c, max));
    }

    // movement (Thread)
    private void moveTo(Node dest) {
        busy = true;
        final double sx = pinX, sy = pinY, dx = dest.getX(), dy = dest.getY();
        Thread t = new Thread(() -> {
            int steps = 36;
            for (int i = 1; i <= steps; i++) {
                double e = i / (double) steps;
                e = e * e * (3 - 2 * e);
                pinX = sx + (dx - sx) * e;
                pinY = sy + (dy - sy) * e;
                camX = clampCam(pinX - canvas.getWidth() / 2.0);
                canvas.repaint();
                try { Thread.sleep(16); } catch (InterruptedException ex) { return; }
            }
            SwingUtilities.invokeLater(() -> arrive(dest));
        });
        t.setDaemon(true);
        t.start();
    }

    private void arrive(Node n) {
        current = n; busy = false; path.add(n); refresh();
        switch (n.getType()) {
            case MONSTER: case BOSS: if (!n.isCleared()) fight(n, false); break;
            case EVENT: if (!n.isCleared()) runEvent(n); break;
            case SHOP: openShop(n); break;
            case REST: if (!n.isCleared()) rest(n); break;
            default: break;
        }
    }

    private void fight(Node n, boolean elite) {
        List<Enemy> es = elite ? stage.spawnElite() : stage.spawn(n);
        boolean boss = n.getType() == Node.Type.BOSS;
        frame.startBattle(stage, es, boss, win -> {
            if (!win) { frame.gameOver(); return; }
            n.setCleared(true);
            if (boss) frame.stageCleared(stage); else { frame.showMap(); refresh(); }
        });
    }

    /** Special event battle (Yato): custom enemies + music, reward after the win. */
    private void duel(Node n, Event.Result r) {
        frame.startBattle(stage, r.foes.get(), true, r.music, win -> {
            if (!win) { frame.gameOver(); return; }
            n.setCleared(true);
            frame.showMap(); refresh();
            if (r.onWin != null) r.onWin.accept(frame, gs);
            refresh();
        });
    }

    private void runEvent(Node n) {
        Event.Result r = new EventDialog(frame, gs, n.getEvent()).run();
        refresh();
        if (r != null && r.foes != null) duel(n, r);
        else if (r != null && r.fight) fight(n, r.elite);
        else n.setCleared(true);
        refresh();
    }

    private void rest(Node n) {
        String healed = gs.healAll(GameState.REST_HEAL);
        n.setCleared(true);
        refresh();
        JOptionPane.showMessageDialog(this, "You rest at the save spot.\nRecovered half of Max HP.\n\n" + healed, "Save Spot", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openShop(Node n) {
        new ShopDialog(frame, gs, n).setVisible(true);
        refresh();
    }

    // drawing
    private static Color colorOf(Node.Type t) {
        switch (t) {
            case MONSTER: return new Color(205, 70, 70);
            case EVENT:   return new Color(150, 100, 215);
            case SHOP:    return new Color(230, 180, 60);
            case REST:    return new Color(80, 190, 120);
            case BOSS:    return new Color(150, 20, 45);
            default:      return new Color(70, 100, 170);
        }
    }
    private static String symbolOf(Node.Type t) {
        switch (t) {
            case MONSTER: return "M";
            case EVENT:   return "?";
            case SHOP:    return "$";
            case REST:    return "+";
            case BOSS:    return "B";
            default:      return "GO";
        }
    }
    private String tooltipOf(Node n) {
        switch (n.getType()) {
            case MONSTER: return n.isCleared() ? "Battle (cleared)" : "Battle - monsters ahead";
            case EVENT:   return n.isCleared() ? "Event (done)" : "Event - something unknown...";
            case SHOP:    return "Shop - Bryota sells items";
            case REST:    return n.isCleared() ? "Save Spot (used)" : "Save Spot - heal 50% HP";
            case BOSS:    return "BOSS - " + stage.getBossName();
            default:      return "Start";
        }
    }

    private class MapCanvas extends JPanel {
        private final javax.swing.Timer timer = new javax.swing.Timer(50, e -> repaint());
        private boolean centered;

        MapCanvas() {
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { hover = nodeAt(e.getX(), e.getY()); setCursor(Cursor.getPredefinedCursor(hover != null && current.getNext().contains(hover) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (busy) return;
                    Node n = nodeAt(e.getX(), e.getY());
                    if (n == null) return;
                    if (current.getNext().contains(n)) moveTo(n);
                    else if (n == current && n.getType() == Node.Type.SHOP) openShop(n);
                }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }
        @Override public void addNotify() { super.addNotify(); timer.start(); }
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

        private Node nodeAt(int mx, int my) {
            for (Node n : stage.getNodes()) {
                double dx = n.getX() - (mx + camX), dy = n.getY() - my;
                if (dx * dx + dy * dy <= 28 * 28) return n;
            }
            return null;
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Ui.antialias(g);
            int w = getWidth(), h = getHeight();
            if (!centered && w > 0) { camX = clampCam(pinX - w / 2.0); centered = true; }
            Image bg = Assets.background(stage.getNumber());
            if (bg != null) Assets.cover(g, bg, 0, 0, w, h);
            else { g.setPaint(new GradientPaint(0, 0, stage.getBg().brighter(), 0, h, stage.getBg().darker().darker())); g.fillRect(0, 0, w, h); }
            Color tint = stage.getBg();
            g.setColor(new Color(tint.getRed() / 3, tint.getGreen() / 3, tint.getBlue() / 3, 110)); g.fillRect(0, 0, w, h);
            double t = System.currentTimeMillis() / 250.0;

            g.translate(-camX, 0);
            // paths: faint for all, bright for reachable, solid gold for where you've been
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{9f, 9f}, 0f));
            for (Node n : stage.getNodes())
                for (Node m : n.getNext()) {
                    boolean reach = n == current && !busy;
                    g.setColor(reach ? stage.getAccent() : new Color(255, 255, 255, 55));
                    g.drawLine((int) n.getX(), (int) n.getY(), (int) m.getX(), (int) m.getY());
                }
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(244, 196, 64, 200));
            for (int i = 0; i + 1 < path.size(); i++)
                g.drawLine((int) path.get(i).getX(), (int) path.get(i).getY(), (int) path.get(i + 1).getX(), (int) path.get(i + 1).getY());
            g.setStroke(new BasicStroke(1f));
            for (Node n : stage.getNodes()) drawNode(g, n, Math.sin(t) * 3);
            drawPin(g, Math.sin(t * 1.5) * 3);
            if (hover != null) drawTooltip(g, hover);
            g.translate(camX, 0);

            drawHud(g, w, h);
            g.dispose();
        }

        private void drawNode(Graphics2D g, Node n, double pulse) {
            int r = n.getType() == Node.Type.BOSS ? 30 : 22;
            int x = (int) n.getX(), y = (int) n.getY();
            boolean reach = !busy && current.getNext().contains(n);
            boolean past = n.getLayer() <= current.getLayer() && n != current;
            boolean done = n.isCleared() && n.getType() != Node.Type.START && n.getType() != Node.Type.SHOP;
            if (reach) {
                int pr = r + 8 + (int) pulse;
                g.setColor(new Color(255, 255, 255, 70)); g.fillOval(x - pr, y - pr, pr * 2, pr * 2);
            }
            Color c = done || past ? new Color(85, 85, 98) : colorOf(n.getType());
            if (!reach && !done && !past && n != current) c = new Color(c.getRed(), c.getGreen(), c.getBlue(), 200);
            g.setColor(c);
            g.fillOval(x - r, y - r, r * 2, r * 2);
            g.setStroke(new BasicStroke(3f));
            g.setColor(reach && n == hover ? Ui.GOLD : past ? new Color(150, 150, 160) : Color.WHITE);
            g.drawOval(x - r, y - r, r * 2, r * 2);
            g.setStroke(new BasicStroke(1f));
            Ui.center(g, done ? "v" : symbolOf(n.getType()), x, y + 7, Ui.H1, past ? new Color(170, 170, 180) : Color.WHITE);
        }

        private void drawTooltip(Graphics2D g, Node n) {
            String s = tooltipOf(n);
            g.setFont(Ui.BOLD);
            int tw = g.getFontMetrics().stringWidth(s) + 20;
            int x = (int) n.getX() - tw / 2, y = (int) n.getY() + 34;
            g.setColor(new Color(10, 12, 24, 230)); g.fillRoundRect(x, y, tw, 26, 10, 10);
            g.setColor(colorOf(n.getType())); g.drawRoundRect(x, y, tw, 26, 10, 10);
            g.setColor(Ui.TEXT); g.drawString(s, x + 10, y + 18);
        }

        private void drawPin(Graphics2D g, double bob) {
            int x = (int) pinX, y = (int) pinY - 40 + (int) bob;
            g.setColor(new Color(0, 0, 0, 90)); g.fillOval(x - 14, (int) pinY - 6, 28, 10);
            Image portrait = Assets.hero(gs.getHero().getName());
            if (portrait != null) {
                g.setColor(new Color(10, 12, 24, 200)); g.fillOval(x - 26, y - 40, 52, 52);
                g.setColor(Ui.GOLD); g.setStroke(new BasicStroke(2.5f)); g.drawOval(x - 26, y - 40, 52, 52); g.setStroke(new BasicStroke(1f));
                Assets.contain(g, portrait, x - 22, y - 36, 44, 44);
                g.setColor(Ui.GOLD); g.fillPolygon(new int[]{x - 7, x + 7, x}, new int[]{y + 11, y + 11, y + 22}, 3);
            } else { g.setColor(new Color(235, 60, 60)); g.fillPolygon(new int[]{x - 11, x + 11, x}, new int[]{y - 8, y - 8, y + 12}, 3); g.fillOval(x - 13, y - 26, 26, 26); g.setColor(Color.WHITE); g.fillOval(x - 5, y - 18, 10, 10); }
        }

        private void drawHud(Graphics2D g, int w, int h) {
            int x = 14;
            for (Hero hero : gs.getParty()) {
                Ui.heroChip(g, hero, x, h - 86, 250, 72);
                g.setFont(Ui.SMALL); g.setColor(Ui.DIM);
                g.drawString("ATK " + hero.getAtk() + "  M.ATK " + hero.getMatk() + "  DEF " + hero.getDef() + "  M.DEF " + hero.getMdef(), x + 74, h - 46);
                x += 260;
            }
            if (gs.getParty().size() == 1 && stage.getNumber() <= GameState.RECRUIT_AFTER) {
                g.setColor(new Color(10, 12, 24, 170)); g.fillRoundRect(x, h - 86, 170, 72, 12, 12);
                Ui.center(g, "Companion joins", x + 85, h - 54, Ui.SMALL, Ui.DIM);
                Ui.center(g, "after Stage " + GameState.RECRUIT_AFTER, x + 85, h - 38, Ui.SMALL, Ui.DIM);
            }

            String[] names = {"Monster", "Event", "Shop", "Save Spot", "Boss"};
            Node.Type[] types = {Node.Type.MONSTER, Node.Type.EVENT, Node.Type.SHOP, Node.Type.REST, Node.Type.BOSS};
            g.setColor(new Color(10, 12, 24, 215)); g.fillRoundRect(w - 150, h - 130, 136, 116, 14, 14);
            for (int i = 0; i < 5; i++) {
                g.setColor(colorOf(types[i])); g.fillOval(w - 138, h - 120 + i * 21, 14, 14);
                g.setFont(Ui.SMALL); g.setColor(Ui.TEXT); g.drawString(symbolOf(types[i]) + "  " + names[i], w - 116, h - 109 + i * 21);
            }
            String hint = busy ? "Travelling..." : "Click a glowing node to travel.  Hover a node to see what it is.";
            g.setFont(Ui.BODY);
            int tw = g.getFontMetrics().stringWidth(hint) + 24;
            g.setColor(new Color(10, 12, 24, 170)); g.fillRoundRect(w / 2 - tw / 2 + 60, 10, tw, 26, 12, 12);
            Ui.center(g, hint, w / 2 + 60, 28, Ui.BODY, Ui.TEXT);
        }
    }
}
