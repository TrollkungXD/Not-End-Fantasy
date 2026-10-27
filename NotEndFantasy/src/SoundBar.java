import javax.swing.*;
import java.awt.*;

/** Slim bar at the very top of the window: window size button, music volume [-] [+] and a mute button. Never covers the game screens. */
public class SoundBar extends JMenuBar {
    public static final int HEIGHT = 26;
    private final JLabel level = new JLabel();
    private final JButton mute;

    public SoundBar() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        setBackground(new Color(12, 13, 22));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.LINE));
        setPreferredSize(new Dimension(1000, HEIGHT));
        JLabel title = new JLabel("Music");
        title.setFont(Ui.SMALL_B); title.setForeground(Ui.DIM);
        level.setFont(Ui.SMALL_B);
        level.setHorizontalAlignment(SwingConstants.CENTER);
        level.setPreferredSize(new Dimension(42, 20));
        mute = small("Mute", "Turn the music off / on", e -> Music.toggleMute());
        mute.setPreferredSize(new Dimension(58, 20));
        JButton size = small("Size " + WindowSize.current() + "%", "Make the game window bigger / smaller (100% / 125% / 150%)", null);
        size.addActionListener(e -> WindowSize.next(size));
        size.setPreferredSize(new Dimension(84, 20));
        add(size);
        JLabel gap = new JLabel(); gap.setPreferredSize(new Dimension(10, 20));
        add(gap);
        add(title);
        add(small("-", "Volume down", e -> Music.softer()));
        add(level);
        add(small("+", "Volume up", e -> Music.louder()));
        add(mute);
        refresh();
        Music.onChange(() -> SwingUtilities.invokeLater(this::refresh));
    }

    private void refresh() {
        boolean off = Music.isMuted() || Music.getVolume() == 0;
        level.setText(off ? "OFF" : Music.getVolume() + "%");
        level.setForeground(off ? Ui.RED : Ui.GOLD);
        mute.setText(Music.isMuted() ? "Unmute" : "Mute");
    }

    private static JButton small(String text, String tip, java.awt.event.ActionListener a) {
        JButton b = Ui.button(text, a);
        b.setFont(Ui.SMALL_B);
        b.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        b.setPreferredSize(new Dimension(24, 20));
        b.setToolTipText(tip);
        b.setFocusable(false);
        return b;
    }
}
