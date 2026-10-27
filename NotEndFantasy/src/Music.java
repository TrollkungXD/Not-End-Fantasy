import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.prefs.Preferences;

/**
 * Background music (OST). Plays one MP3 from the Ost/ folder on its own Thread and loops it.
 * MP3 decoding uses the small JLayer library in lib/ (Java Sound itself cannot read MP3).
 * Volume (0-100) and mute are remembered between launches.
 */
public final class Music {
    // file names inside Ost/
    public static final String TITLE = "ThemeLogin.mp3";             // main menu before pressing Start
    public static final String MAP = "MainGameTheme.mp3";            // stage select / node map
    public static final String BATTLE = "NormalFightEverynode.mp3";  // every normal / elite fight
    public static final String BOSS = "Boss1-4Fight.mp3";            // bosses of Stage 1-2 (others have their own track below)
    public static final String REGALIA = "RegaliaFight.mp3";         // final boss
    public static final String YATO = "YatoFight.mp3";               // Yato's duel
    public static final String GLOOM = "GloomKnightFight.mp3";        // Stage 3 boss - Gloom Under Night
    public static final String ABYSS = "AbyssalKnightOst.mp3";        // Stage 4 boss - Knight of Abyss
    public static final String PRIMAL_BOSS = "BossPrimalForest.mp3";  // secret stage boss (Primal Forest)
    public static final String KIRIN = "KirinFight.mp3";             // Divine Kirin (Yato's quest)
    public static final String PRIMAL_MAP = "PrimalWoodOst.mp3";      // node map theme of the secret stage
    public static final String CREDITS = "EndCredit.mp3";            // ending + credits

    public static final String FOLDER = "Ost/";
    private static final Preferences PREFS = prefs();
    private static volatile int volume = PREFS == null ? 70 : PREFS.getInt("volume", 70);
    private static volatile boolean muted = PREFS != null && PREFS.getBoolean("muted", false);
    private static String current;
    private static Player player;
    private static final java.util.List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private Music() {}

    /** Starts a looping track. Does nothing if that track is already playing. */
    public static synchronized void play(String track) {
        if (track.equals(current) && player != null && player.isAlive()) return;
        stop();
        File f = AppFiles.file(FOLDER + track);
        if (!f.exists()) return;                     // missing file = silence, never a crash
        current = track;
        player = new Player(f);
        player.start();
    }
    public static synchronized void stop() {
        if (player != null) player.halt();
        player = null; current = null;
    }

    // volume
    public static int getVolume() { return volume; }
    public static boolean isMuted() { return muted; }
    public static void setVolume(int v) { volume = Math.max(0, Math.min(100, v)); if (volume > 0) muted = false; save(); }
    public static void louder() { setVolume(volume + 10); }
    public static void softer() { setVolume(volume - 10); }
    public static void toggleMute() { muted = !muted; save(); }
    /** UI that shows the volume registers here to repaint when it changes. */
    public static void onChange(Runnable r) { LISTENERS.add(r); }

    private static float gain() {
        if (muted) return 0f;
        float v = volume / 100f;
        return v * v;                                
    }
    private static void save() {
        if (PREFS != null) { try { PREFS.putInt("volume", volume); PREFS.putBoolean("muted", muted); } catch (Exception ignored) { } }
        for (Runnable r : LISTENERS) r.run();
    }
    private static Preferences prefs() {
        try { return Preferences.userRoot().node("NotEndFantasy"); } catch (Exception e) { return null; }
    }

    /** Decodes the MP3 frame by frame, applies the volume and sends it to the speakers. */
    private static final class Player extends Thread {
        private final File file;
        private volatile boolean running = true;
        Player(File file) { super("Music-" + file.getName()); this.file = file; setDaemon(true); }
        void halt() { running = false; }

        @Override public void run() {
            SourceDataLine line = null;
            try {
                while (running) {                                   // loop the track forever
                    try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                        Bitstream bs = new Bitstream(in);
                        Decoder dec = new Decoder();
                        Header h;
                        byte[] out = new byte[0];
                        int frames = 0;
                        while (running && (h = bs.readFrame()) != null) {
                            SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                            if (line == null) {
                                AudioFormat fmt = new AudioFormat(sb.getSampleFrequency(), 16, sb.getChannelCount(), true, false);
                                line = AudioSystem.getSourceDataLine(fmt);
                                line.open(fmt, (int) (fmt.getFrameRate() * fmt.getFrameSize() / 5));   // ~0.2 s buffer = quick volume response
                                line.start();
                            }
                            short[] s = sb.getBuffer();
                            int n = sb.getBufferLength();
                            if (out.length < n * 2) out = new byte[n * 2];
                            float g = gain();
                            for (int i = 0; i < n; i++) {
                                int v = Math.round(s[i] * g);
                                out[2 * i] = (byte) v; out[2 * i + 1] = (byte) (v >> 8);
                            }
                            line.write(out, 0, n * 2);
                            bs.closeFrame();
                            frames++;
                        }
                        bs.close();
                        if (frames == 0) return;                    // not a playable MP3
                    }
                }
            } catch (Exception | LinkageError e) {
                // no sound device or library missing: the game simply runs without music
            } finally {
                if (line != null) { line.stop(); line.flush(); line.close(); }
            }
        }
    }
}
