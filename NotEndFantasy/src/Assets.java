import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


public final class Assets {
    private static final Map<String, Image> CACHE = new HashMap<>();
    private Assets() {}

    public static Image image(String path) {
        if (CACHE.containsKey(path)) return CACHE.get(path);
        Image img = null;
        try {
            File f = AppFiles.file(path);
            if (f.exists()) img = path.toLowerCase().endsWith(".gif") ? Toolkit.getDefaultToolkit().createImage(f.getPath()) : ImageIO.read(f);
            if (img != null && path.toLowerCase().endsWith(".gif")) {          
                MediaTracker mt = new MediaTracker(new Canvas());
                mt.addImage(img, 0); mt.waitForAll(2000);
            }
        } catch (Exception ignored) { img = null; }
        CACHE.put(path, img);
        return img;
    }

    public static Image hero(String name) {
        switch (name) {
            case "Vina": return image("PlayerCharaceter/Vina_Gladiator.png");
            case "Exusiai": return image("PlayerCharaceter/Exusial_Bard.png");
            case "Hivia": return image("PlayerCharaceter/Hivia_Defender.png");
            case "Phantom": return image("PlayerCharaceter/Phantom_Assassin.png");
            // companions (join after Stage 2)
            case "Irene": return image("PlayerCharaceter/ExtraChar/Irene_Inquisiter.png");
            case "Ingrid": return image("PlayerCharaceter/ExtraChar/Ingrid_Agent.png");
            case "Logos": return image("PlayerCharaceter/ExtraChar/Logos_Caster.png");
            case "Shu": return image("PlayerCharaceter/ExtraChar/Shu_Meditator.png");
            // secret hero
            case "Orchid": return image("PlayerCharaceter/Secret Character/Orchid_Ranger.png");
            case "Yato": return image("PlayerCharaceter/Secret Character/Yato_Kirin Hunter.png");
            default: return null;
        }
    }

    /** Monster sprites */
    private static Map<String, String> monsterIndex;
    private static String findMonster(String file) {
        if (monsterIndex == null) {
            monsterIndex = new HashMap<>();
            index(AppFiles.file("Monster"));
        }
        return monsterIndex.get(file.toLowerCase());
    }
    private static void index(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) index(f);
            else monsterIndex.putIfAbsent(f.getName().toLowerCase(), f.getPath());
        }
    }

    /** Sprite file for each monster. */
    private static final Map<String, String> SPRITES = new HashMap<>();
    static {
        SPRITES.put("Skeleton Soldier", "SKEL_SOLDIER.gif");
        SPRITES.put("Raydric Archer", "C_RAYDRIC_ARCHER.gif");      
        SPRITES.put("Venom Chimera", "EP16_2_VENOM_KIMERA.gif");
        SPRITES.put("Miguel", "MD_MIGUEL.gif");
        SPRITES.put("Red Pitaya", "PITAYA_R.gif");
        SPRITES.put("Ancient Tree", "Boss_ANCIENT_TREE.gif");
        SPRITES.put("Evil Huntress", "4_F_HUNTER_EVIL.gif");
        SPRITES.put("Drake", "4_M_DRAKE.gif");
        SPRITES.put("Minorous", "E_MINOROUS.gif");
        SPRITES.put("Valkyrie", "Vakyrie.gif");
        SPRITES.put("Aqua Angelic", "Aqua_Angelic.gif");
        SPRITES.put("Gloom Under Night", "Boss_GLOOMUNDERNIGHT.gif");
        SPRITES.put("Faceworm Queen", "Boss_FACEWORM_QUEEN.gif");
        SPRITES.put("Knight of Windstorm", "Boss_KNIGHT_OF_WINDSTORM.gif");
        SPRITES.put("Knight of Abyss", "Boss_KnightOfAbyss.gif");
        SPRITES.put("Arch Regalia", "Boss_ArchRegalia.gif");
        SPRITES.put("Yato", "Yato_Fight.png");
        SPRITES.put("Divine Kirin", "Divine_Kirin.png");      // converted from Dovine_Kirin.png (that file is really WebP, Java can't read it)                      
    }

    public static Image enemy(String name) {
        String file = SPRITES.getOrDefault(name, name.toUpperCase().replace(' ', '_') + ".gif");
        String path = findMonster(file);
        return path == null ? null : image(path);
    }

    public static Image background(int stageNumber) {
        String[][] files = {
            {"Cursed_wood1.png"}, {"Ice_Castle2.png"}, {"Burning Prophet Ruin.png", "FireProphet3.png"},
            {"Night_Realm4.png", "UnendingWarfield4.png"}, {"Memories_Of_Warfield5.png", "UnendingWarfield4.png"},
            {"Primal Forest.png"}};                                   // 6 = secret stage
        if (stageNumber < 1 || stageNumber > files.length) return null;
        for (String f : files[stageNumber - 1]) { Image img = image("bg/" + f); if (img != null) return img; }
        return null;
    }
    // skill effects (SFX sheets)
    private static final Map<String, Object[]> FX = new HashMap<>();
    private static void fx(String name, String sheet, int x, int y, int w, int h) { FX.put(name, new Object[]{sheet, new Rectangle(x, y, w, h)}); }
    static {
        String a = "phit_1040518500_0.png", b = "phit_1040916100_0.png", c = "phit_3890030000.png", d = "sp_1040216600_0_s2_a.png";
        fx("SLASH_PURPLE", a, 15, 330, 280, 270);  fx("VORTEX", a, 325, 10, 290, 290);      fx("ORB_PURPLE", a, 640, 10, 290, 300);
        fx("SHARDS_PINK", a, 320, 325, 290, 240);  fx("LIGHTNING", a, 625, 320, 260, 260);  fx("BURST_PINK", a, 575, 590, 200, 200);
        fx("SPARKS_ORANGE", a, 770, 585, 160, 150); fx("SLASH_RED", a, 150, 630, 155, 70);
        fx("EMBERS", b, 300, 10, 300, 230);        fx("SPIKES_DARK", b, 840, 0, 180, 240);  fx("SPIKES_DARK2", b, 770, 240, 170, 250);
        fx("CLAW_FIRE", b, 300, 250, 100, 170);
        fx("FLARE_GOLD", c, 615, 10, 300, 180);    fx("STAR_PURPLE", c, 515, 220, 230, 230); fx("BURST_VIOLET", c, 755, 195, 230, 215);
        fx("CROSS_GOLD", c, 10, 625, 265, 265);    fx("DARK_ORB", c, 285, 625, 260, 260);   fx("GLOW_VIOLET", c, 555, 680, 120, 110);
        fx("BLOOD_BURST", c, 740, 660, 165, 160);  fx("SPARKLES", c, 300, 15, 300, 180);
        fx("HEAL_GREEN", d, 10, 418, 398, 357);    fx("RING_GREEN", d, 811, 540, 184, 174); fx("BURST_CYAN", d, 20, 811, 286, 276);
        fx("SHIELD_BLUE", d, 20, 20, 383, 383);    fx("STAR_WHITE", d, 632, 1112, 214, 204); fx("BEAM_CYAN", d, 326, 1117, 296, 219);
        fx("RAYS_GREEN", d, 714, 1341, 194, 184);  fx("RAYS_YELLOW", d, 321, 1352, 240, 194); fx("ORBS_ORANGE", d, 10, 1423, 301, 306);
        fx("RING_CYAN", d, 418, 291, 219, 250);    fx("RING_ORANGE", d, 663, 296, 240, 219); fx("SPROUT_GREEN", d, 714, 1545, 168, 163);
        // added sheets: e = fire/gold hits, f = light & stars, g = wind/lightning/rock
        String e = "phit_3040161000_03.png", f = "phit_3040397000.png", g = "summon_2040406000_01_damage.png";
        fx("SMOKE_RED", e, 15, 15, 290, 300);  fx("SHARDS_DARK", e, 320, 30, 300, 175);  fx("RING_GOLD", e, 630, 20, 260, 240);
        fx("BURST_GOLD", e, 325, 250, 250, 240);  fx("RAYS_ORANGE", e, 590, 290, 245, 225);  fx("CROSS_RED", e, 835, 280, 125, 300);
        fx("FLAMES", e, 10, 325, 300, 210);  fx("RING_BLOOD", e, 320, 500, 250, 245);  fx("SPARKS_FIRE", e, 790, 580, 170, 160);
        fx("FIRE_PILLAR", e, 10, 580, 175, 290);  fx("FIRE_BALL", e, 195, 560, 110, 140);  fx("ORB_FIRE", e, 200, 750, 220, 220);
        fx("ORB_RED", e, 730, 770, 220, 220);
        fx("STARBURST_GOLD", f, 15, 15, 385, 375);  fx("BURST_PRISM", f, 410, 0, 270, 270);  fx("MIST_BLUE", f, 690, 20, 240, 230);
        fx("RING_YELLOW", f, 405, 275, 245, 245);  fx("AURA_GOLD", f, 695, 290, 240, 210);  fx("TWINKLE_GOLD", f, 35, 400, 235, 245);
        fx("STARDUST_CYAN", f, 310, 525, 225, 230);  fx("FLARE_WHITE", f, 550, 550, 200, 200);  fx("GLITTER", f, 745, 530, 175, 170);
        fx("WIND_GRAY", g, 0, 310, 285, 225);  fx("WIND_SWIRL", g, 310, 340, 195, 240);  fx("BOLT_GREEN", g, 500, 330, 200, 180);
        fx("BOLT_GREEN2", g, 895, 365, 95, 210);  fx("BLAST_FIRE", g, 710, 355, 165, 115);  fx("FIRE_STREAKS", g, 710, 220, 225, 120);
        fx("BOLT_WIDE", g, 705, 620, 235, 75);  fx("BOLT_FORK", g, 720, 835, 125, 115);  fx("BOLT_CHAIN", g, 200, 815, 160, 150);
        fx("SMOKE_GRAY", g, 15, 540, 180, 190);  fx("DARK_RING", g, 0, 755, 205, 200);  fx("ROCK_SLAM", g, 755, 0, 195, 155);
    }
    private static final Map<String, Image> FX_CACHE = new HashMap<>();
    private static final Map<String, java.awt.image.BufferedImage> SHEETS = new HashMap<>();
    /** The effect picture with this name, or null. */
    public static Image fx(String name) {
        if (name == null || !FX.containsKey(name)) return null;
        if (FX_CACHE.containsKey(name)) return FX_CACHE.get(name);
        Image out = null;
        try {
            Object[] e = FX.get(name);
            java.awt.image.BufferedImage sheet = SHEETS.get(e[0]);
            if (sheet == null) { sheet = ImageIO.read(AppFiles.file("SFX/" + e[0])); SHEETS.put((String) e[0], sheet); }
            Rectangle r = (Rectangle) e[1];
            if (sheet != null) out = sheet.getSubimage(r.x, r.y, Math.min(r.width, sheet.getWidth() - r.x), Math.min(r.height, sheet.getHeight() - r.y));
        } catch (Exception ignored) { out = null; }
        FX_CACHE.put(name, out);
        return out;
    }

    /** A specific NPC picture from npc*/
    public static Image npc(String file) { return file == null ? null : image("npc/" + file); }
    /** A picture from Item*/
    public static Image item(String file) { return file == null ? null : image("Item/" + file); }

    public static Image merchant() { return image("npc/avg_shop_bryota_1#17$1.png"); }
    /** A resident appears alongside non-combat map encounters. */
    public static Image npcFor(String encounter) {
        String[] files = {
            "avg_206_gnosis_1#6$1.png", "avg_362_saga_1#5$1.png", "avg_4046_ebnhlz_1#8$2.png",
            "avg_4064_mlynar_1#4$2.png", "avg_4065_judge_1#3$2.png"
        };
        int index = Math.floorMod(encounter.hashCode(), files.length);
        return image("npc/" + files[index]);
    }
    /** Background for a special event: a picture in bg/Event whose name matches the event title
     *  (case, spaces, _ and - ignored, e.g. "Auspicious Sign.png" or "auspicious_sign.jpg"), otherwise the fallback (e.g. "bg/Cursed_wood1.png"). */
    public static Image eventBg(String title, String fallback) {
        String key = title.toLowerCase().replaceAll("[^a-z0-9]", "");
        File[] fs = AppFiles.file("bg/Event").listFiles();
        if (fs != null) for (File f : fs) {
            String n = f.getName().toLowerCase();
            int dot = n.lastIndexOf('.');
            if (dot > 0 && n.substring(0, dot).replaceAll("[^a-z0-9]", "").equals(key)) {
                Image img = image(f.getPath());
                if (img != null) return img;
            }
        }
        return fallback == null ? null : image(fallback);
    }
    public static Image itemArt() { return image("UI_Aesthetic/relic_31.png"); }

    public static void cover(Graphics2D g, Image img, int x, int y, int w, int h) {
        if (img == null || w <= 0 || h <= 0) return;
        int iw = img.getWidth(null), ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return;
        double scale = Math.max(w / (double) iw, h / (double) ih);
        int dw = (int) Math.ceil(iw * scale), dh = (int) Math.ceil(ih * scale);
        Shape old = g.getClip();
        g.clipRect(x, y, w, h);
        g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
        g.setClip(old);
    }

    public static void contain(Graphics2D g, Image img, int x, int y, int w, int h) {
        if (img == null || w <= 0 || h <= 0) return;
        int iw = img.getWidth(null), ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return;
        double scale = Math.min(w / (double) iw, h / (double) ih);
        int dw = (int) Math.round(iw * scale), dh = (int) Math.round(ih * scale);
        g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
    }
}
