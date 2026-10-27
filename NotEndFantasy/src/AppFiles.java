import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the game's resource folder (the one holding Monster/, bg/, Ost/, ...).
 * Works when run from the IDE, from NotEndFantasy.jar (double-click or java -jar) and from a jpackage app.
 * Checked in order, for each start folder (jpackage app, the jar's folder, the working directory, and their parents):
 *   <dir>/usage,  <dir>/NotEndFantasy/usage,  <dir>
 * The first one that contains a Monster folder is used.
 */
public final class AppFiles {
    private AppFiles() {}

    private static File root;

    public static synchronized File root() {
        if (root == null) root = locate();
        return root;
    }

    private static File locate() {
        List<File> starts = new ArrayList<>();
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher != null && !launcher.isEmpty()) addWithParents(starts, new File(launcher).getAbsoluteFile().getParentFile());
        try {
            File code = new File(AppFiles.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            addWithParents(starts, code.isFile() ? code.getParentFile() : code);   // jar -> its folder, classes dir -> itself
        } catch (Exception ignored) { }
        addWithParents(starts, new File(System.getProperty("user.dir")).getAbsoluteFile());

        String[] subs = { "usage", "NotEndFantasy" + File.separator + "usage", "" };
        for (File s : starts)
            for (String sub : subs) {
                File c = sub.isEmpty() ? s : new File(s, sub);
                if (new File(c, "Monster").isDirectory()) return c;
            }
        return new File(System.getProperty("user.dir"));   // nothing found: old behaviour
    }

    private static void addWithParents(List<File> list, File dir) {
        for (int i = 0; dir != null && i < 3; i++, dir = dir.getParentFile())
            if (!list.contains(dir)) list.add(dir);
    }

    public static File file(String relativePath) {
        File f = new File(relativePath);
        return f.isAbsolute() ? f : new File(root(), relativePath);
    }
}
