import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Window size button (top bar). Makes the whole game bigger: 100% -> 125% -> 150% -> 100%.
 * Java Swing can only change its UI scale before the window opens, so the choice is saved
 * and the game restarts itself to apply it (sun.java2d.uiScale). Sizes that don't fit the screen are skipped.
 */
public final class WindowSize {
    private static final int[] LEVELS = { 100, 125, 150 };      // percent of the normal size
    private static final Preferences PREFS = prefs();
    private WindowSize() {}

    /** Call first thing in main(), before any window exists. */
    public static void applyBeforeStart() {
        if (PREFS == null || System.getProperty("sun.java2d.uiScale") != null) return;
        int level = PREFS.getInt("sizeLevel", 100);
        double base = PREFS.getDouble("baseScale", 0);
        if (level != 100 && base > 0) System.setProperty("sun.java2d.uiScale", String.valueOf(base * level / 100.0));
    }

    public static int current() { return PREFS == null ? 100 : PREFS.getInt("sizeLevel", 100); }

    /** Button action: pick the next size that fits, then restart (or apply on next launch). */
    public static void next(Component parent) {
        if (PREFS == null) return;
        int cur = current();
        GraphicsConfiguration gc = parent.getGraphicsConfiguration();
        double nowScale = gc == null ? 1.0 : gc.getDefaultTransform().getScaleX();
        double base = nowScale * 100.0 / cur;                         // the screen's own scale (Windows display setting)
        Window w = SwingUtilities.getWindowAncestor(parent);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); // in current UI units
        double usableW = usable.width * nowScale, usableH = usable.height * nowScale;                   // real pixels
        double winW = (w == null ? 1016 : w.getWidth()), winH = (w == null ? 785 : w.getHeight());   // current UI units

        List<Integer> fits = new ArrayList<>();
        for (int lv : LEVELS) {
            double f = (double) lv / cur;                             // how much bigger than now
            if (lv == 100 || (winW * nowScale * f <= usableW && winH * nowScale * f <= usableH)) fits.add(lv);
        }
        int idx = fits.indexOf(cur);
        int target = fits.get((idx + 1) % fits.size());
        if (target == cur) {
            JOptionPane.showMessageDialog(parent, "Your screen is too small for a bigger window.", "Window Size", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object[] opts = { "Restart now", "Next launch", "Cancel" };
        int ans = JOptionPane.showOptionDialog(parent,
                "Change the window size to " + target + "%?\n\nThe game has to restart to apply it.\nRestarting now ends the current run.",
                "Window Size", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        if (ans != 0 && ans != 1) return;
        PREFS.putInt("sizeLevel", target);
        PREFS.putDouble("baseScale", base);
        try { PREFS.flush(); } catch (Exception ignored) { }
        if (ans == 0 && !restart()) {
            JOptionPane.showMessageDialog(parent, "Could not restart automatically.\nClose and open the game again to use " + target + "%.", "Window Size", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Starts a new copy of the game with the same Java and classpath, then closes this one. */
    private static boolean restart() {
        try {
            List<String> cmd = new ArrayList<>();
            String app = System.getProperty("jpackage.app-path");
            if (app != null && !app.isEmpty()) cmd.add(app);
            else {
                File bin = new File(System.getProperty("java.home"), "bin");
                File javaw = new File(bin, "javaw.exe");
                cmd.add(javaw.exists() ? javaw.getPath() : new File(bin, "java").getPath());
                cmd.add("-cp"); cmd.add(System.getProperty("java.class.path"));
                cmd.add("Main");
            }
            new ProcessBuilder(cmd).directory(new File(System.getProperty("user.dir"))).start();
            System.exit(0);
            return true;
        } catch (Exception e) { return false; }
    }

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("NotEndFantasy"); } catch (Exception e) { return null; }
    }
}
