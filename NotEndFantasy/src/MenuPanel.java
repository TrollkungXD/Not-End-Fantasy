import javax.swing.*;
import java.awt.*;

public class MenuPanel extends JPanel {
    public MenuPanel(GameFrame frame) {
        setLayout(new GridBagLayout());
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(Box.createVerticalStrut(260));
        JButton start = Ui.button("Start Game", e -> frame.showCharSelect());
        JButton quit = Ui.button("Quit", e -> System.exit(0));
        for (JButton b : new JButton[]{start, quit}) {
            b.setFont(Ui.H1); b.setAlignmentX(CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(260, 52)); b.setPreferredSize(new Dimension(260, 52));
        }
        box.add(start); box.add(Box.createVerticalStrut(14)); box.add(quit);
        add(box);
    }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Ui.antialias(g);
        g.setPaint(new GradientPaint(0, 0, new Color(44, 30, 82), 0, getHeight(), Ui.BG));
        g.fillRect(0, 0, getWidth(), getHeight());
        for (int i = 0; i < 40; i++) {                      // simple starfield
            g.setColor(new Color(255, 255, 255, 40 + (i * 37) % 90));
            g.fillOval((i * 173) % getWidth(), (i * 97) % (getHeight() / 2), 3, 3);
        }
        Ui.center(g, "NOT END FANTASY", getWidth() / 2, 170, Ui.TITLE, Ui.GOLD);
        Ui.center(g, "A turn-based dungeon adventure", getWidth() / 2, 210, Ui.H1, Ui.TEXT);
        g.dispose();
    }
}
