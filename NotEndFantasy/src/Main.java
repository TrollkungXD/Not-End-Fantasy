import javax.swing.*;
import java.awt.Color;

public class Main {
    public static void main(String[] args) {
        WindowSize.applyBeforeStart();          // saved window size (must run before any window opens)
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
            UIManager.put("Button.disabledText", new Color(115, 118, 140));
            new GameFrame().setVisible(true);
        });
    }
}
