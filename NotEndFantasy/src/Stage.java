import java.awt.Color;
import java.util.*;
import java.util.function.Supplier;

/** One big stage: a node graph with its own layout, monsters, events and boss. */
public class Stage {
    public static final int MAP_H = 420;

    private final int number;
    private int length;                              // number of columns; set from the layout automatically
    private final String name, subtitle;
    private final Color bg, accent;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Supplier<Enemy>> monsters = new ArrayList<>();
    private Supplier<Enemy> boss;
    private String bossName = "?";
    private Node start;
    private final Random rng = new Random();

    private Stage(int number, int length, String name, String subtitle, Color bg, Color accent) {
        this.number = number; this.length = length; this.name = name; this.subtitle = subtitle;
        this.bg = bg; this.accent = accent;
    }
    public int getNumber() { return number; }
    public int getLength() { return length; }
    public String getName() { return name; }
    public String getSubtitle() { return subtitle; }
    public Color getBg() { return bg; }
    public Color getAccent() { return accent; }
    public List<Node> getNodes() { return nodes; }
    public Node getStart() { return start; }
    public String getBossName() { return bossName; }

    //  spawning
    /** How many monsters a normal fight has. Stages 1-2 are balanced for a solo hero. */
    private int groupSize() {
        int roll = rng.nextInt(100);
        switch (number) {
            case 1: return roll < 70 ? 1 : 2;
            case 2: return roll < 45 ? 1 : roll < 90 ? 2 : 3;
            case 3: return roll < 40 ? 2 : roll < 85 ? 3 : 4;
            case 4: case 6: return roll < 30 ? 2 : roll < 75 ? 3 : 4;
            default: return roll < 50 ? 3 : roll < 90 ? 4 : 5;
        }
    }
    public List<Enemy> spawn(Node n) {
        List<Enemy> out = new ArrayList<>();
        if (n.getType() == Node.Type.BOSS) { out.add(boss.get()); return out; }
        int count = groupSize();
        for (int i = 0; i < count; i++) out.add(randomMonster());
        return out;
    }
    /** Elite fight: one promoted monster (plus an escort from stage 3). */
    public List<Enemy> spawnElite() {
        List<Enemy> out = new ArrayList<>();
        out.add(randomMonster().promote());
        for (int i = 2; i < number; i++) out.add(randomMonster());   // escorts grow with the stage
        return out;
    }
    private Enemy randomMonster() { return monsters.get(rng.nextInt(monsters.size())).get(); }

    //  layout
    private static Node.Type typeOf(String c) {
        switch (c) {
            case "X": return Node.Type.START;
            case "M": return Node.Type.MONSTER;
            case "E": return Node.Type.EVENT;
            case "S": return Node.Type.SHOP;
            case "R": return Node.Type.REST;
            case "B": return Node.Type.BOSS;
            default:  throw new IllegalArgumentException("Unknown node code \"" + c + "\" in a stage layout (use X M E S R B).");
        }
    }
    private static double frac(int i, int n) { return n == 1 ? 0.5 : i / (n - 1.0); }
    private static int nearest(double f, int n) { return (int) Math.round(f * (n - 1)); }

    private void layout(String[] layers, long seed, int jitter, List<Event> pool) {
        length = layers.length;                       // the map/camera/step counter always follow the real layout
        Random r = new Random(seed);
        Deque<Event> deck = new ArrayDeque<>();      // events are dealt like cards: no repeats until the deck runs out
        List<List<Node>> cols = new ArrayList<>();
        for (int c = 0; c < layers.length; c++) {
            String[] codes = layers[c].trim().split("\\s+");     // extra spaces are ignored
            List<Node> col = new ArrayList<>();
            for (int i = 0; i < codes.length; i++) {
                Node n = new Node(typeOf(codes[i]), c);
                boolean edge = c == 0 || c == layers.length - 1;
                double jx = edge ? 0 : r.nextInt(2 * jitter + 1) - jitter;
                double jy = codes.length == 1 ? 0 : r.nextInt(2 * jitter + 1) - jitter;
                n.setPos(110 + c * 150 + jx, 90 + MAP_H * (i + 1.0) / (codes.length + 1) + jy);
                if (n.getType() == Node.Type.EVENT) {
                    if (deck.isEmpty()) { List<Event> copy = new ArrayList<>(pool); Collections.shuffle(copy, rng); deck.addAll(copy); }
                    n.setEvent(deck.poll());
                }
                if (n.getType() == Node.Type.SHOP) n.setStock(makeStock(rng));
                col.add(n); nodes.add(n);
            }
            cols.add(col);
        }
        start = cols.get(0).get(0);
        start.setCleared(true);
        for (int c = 0; c + 1 < cols.size(); c++) link(cols.get(c), cols.get(c + 1));
    }

    private void link(List<Node> a, List<Node> b) {
        if (a.size() == 1 && b.size() <= 3) {
            for (Node next : b) a.get(0).addNext(next);
            return;
        }
        for (int i = 0; i < a.size(); i++)
            for (int j = 0; j < b.size(); j++)
                if (Math.abs(frac(i, a.size()) - frac(j, b.size())) <= 0.5001) a.get(i).addNext(b.get(j));
        for (int i = 0; i < a.size(); i++)                                   // no dead ends
            if (a.get(i).getNext().isEmpty()) a.get(i).addNext(b.get(nearest(frac(i, a.size()), b.size())));
        for (int j = 0; j < b.size(); j++) {                                 // no orphans
            boolean hasParent = false;
            for (Node p : a) if (p.getNext().contains(b.get(j))) hasParent = true;
            if (!hasParent) a.get(nearest(frac(j, b.size()), a.size())).addNext(b.get(j));
        }
    }

    private static Map<Item, Integer> makeStock(Random r) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(Items.POTION, 4); m.put(Items.HI_POTION, 2); m.put(Items.ANTIDOTE, 2); m.put(Items.BOMB, 3);
        List<Item> sp = new ArrayList<>(Items.specials());
        Collections.shuffle(sp, r);
        for (int i = 0; i < 3; i++) m.put(sp.get(i), 1 + r.nextInt(2));
        List<Item> t = new ArrayList<>(Items.tonics());
        Collections.shuffle(t, r);
        m.put(t.get(0), 1); m.put(t.get(1), 1);
        return m;
    }

    private void setBoss(String name, Supplier<Enemy> b) { bossName = name; boss = b; }
    public boolean isSecret() { return number == GameState.SECRET_STAGE; }

    /** Puts a story event on one random "?" node in the middle of the map (the player may or may not find it). */
    private void placeSpecial(Event e) {
        List<Node> candidates = new ArrayList<>();
        for (Node n : nodes) if (n.getType() == Node.Type.EVENT && n.getLayer() >= 3 && n.getLayer() <= length - 3) candidates.add(n);
        if (candidates.isEmpty()) for (Node n : nodes) if (n.getType() == Node.Type.EVENT) candidates.add(n);
        if (!candidates.isEmpty()) candidates.get(rng.nextInt(candidates.size())).setEvent(e);
    }
    private static List<Event> pool(int n, Event... special) {
        List<Event> l = new ArrayList<>(Arrays.asList(special));
        l.addAll(Event.common(n));
        return l;
    }


    // monster helpers
    private static Enemy phys(String n, int hp, int def, int mdef, int atk, int reward, Color c) { return new Enemy.Physical(n, hp, def, mdef, atk, reward, c); }
    private static Enemy mage(String n, int hp, int def, int mdef, int matk, int reward, Color c) { return new Enemy.Magic(n, hp, def, mdef, matk, reward, c); }

    // the 5 stages (one per Monster/ folder)
    public static Stage create(int number) {
        switch (number) {
            case 1: return cursedWood();
            case 2: return iceCastle();
            case 3: return burningRuin();
            case 4: return nightRealm();
            case GameState.SECRET_STAGE: return primalForest();
            default: return warfield();
        }
    }

    /** Stage 1 - Monster/Curse Wood. Solo. Each monster has one simple trick. */
    private static Stage cursedWood() {
        Stage s = new Stage(1, 12, "Curse Wood", "A twisted forest. The trees whisper your name.",
                new Color(28, 60, 44), new Color(110, 200, 130));
        s.monsters.add(() -> phys("Poring", 15, 2, 2, 10, 15, new Color(240, 140, 170))
                .with(new EnemyMove.Mend("Jelly Regen", 0.8, 4)));
        s.monsters.add(() -> mage("Spore", 16, 0, 2, 7, 15, new Color(170, 110, 200))
                .with(new EnemyMove.Venom("Toxic Spores", 0.9, true, 3, 3, 3)));
        s.monsters.add(() -> phys("Orc Archer", 16, 0, 0, 7, 20, new Color(150, 150, 160))
                .with(new EnemyMove.Afflict("Blinding Shot", 0.9, false, EnemyMove.Ailment.BLIND, 0, 2, 70, false, 1), new EnemyMove.Strike("Double Shot", 0.6, false, 2, 2), new EnemyMove.Charge("Piercing Arrow", 1.8, false, 1)));
        s.monsters.add(() -> mage("Live Peach Tree", 20, 2, 1, 6, 25, new Color(230, 150, 160))
                .with(new EnemyMove.Restore("Blossom Shower", 0.15, 3, 1),
                      new EnemyMove.Mend("Sweet Nectar", 0.6, 2), new EnemyMove.Weaken("Tangling Roots", 2, 2, 3)));
        s.monsters.add(() -> mage("Mandragora", 15, 2, 4, 7, 25, new Color(120, 170, 80))
                .with(new EnemyMove.Afflict("Deafening Shriek", 0, true, EnemyMove.Ailment.SILENCE, 0, 1, 40, false, 1), new EnemyMove.Sweep("Piercing Scream", 0.8, true, 2), new EnemyMove.Weaken("Root Bind", 2, 2, 1)));
        s.monsters.add(() -> mage("Mavka", 16, 0, 4, 8, 20, new Color(140, 200, 170))
                .with(new EnemyMove.Afflict("Witch's Hex", 0, true, EnemyMove.Ailment.CURSE, 0, 3, 90, false, 1), new EnemyMove.Drain("Life Siphon", 1.0, true, 2), new EnemyMove.Mend("Forest Song", 0.3, 1)));
        s.monsters.add(() -> phys("Parasite", 15, 2, 2, 7, 20, new Color(110, 150, 70))
                .with(new EnemyMove.Strike("Vine Whip", 1, false, 2, 2), new EnemyMove.Venom("Parasitic Seed", 0.8, false, 2, 3, 2)));
        s.monsters.add(() -> phys("Orc Lord", 20, 3, 3, 8, 20, new Color(110, 100, 130))
                .with(new EnemyMove.Afflict("Skull Bash", 1.0, false, EnemyMove.Ailment.STUN, 0, 1, 30, false, 1), new EnemyMove.Rally("War Shout", 2, 2, 1), new EnemyMove.Charge("Crushing Axe", 1.8, false, 1)));
        s.monsters.add(() -> phys("Leib Olmai", 16, 2, 2, 10, 20, new Color(90, 110, 160))
                .with(new EnemyMove.Afflict("Rending Claws", 0.8, false, EnemyMove.Ailment.BREAK, 2, 2, 70, false, 1), new EnemyMove.Strike("Maul", 0.6, false, 2, 2), new EnemyMove.Mend("Hibernate", 0.25, 1)));
        s.setBoss("Faceworm Queen", () -> new Enemy.Boss("Faceworm Queen", 67, 2, 2, 6, 8, 95, new Color(110, 40, 60))
                .with(new EnemyMove.Restore("Royal Jelly", 0.15, 0.10, 4, 1),
                      new EnemyMove.Summon("Spawn Argiope", () -> phys("Argiope", 16, 2, 0, 4, 20, new Color(90, 70, 50))
                          .with(new EnemyMove.Venom("Poison Sting", 1, false, 1, 1, 1)), 2, 1),
                      new EnemyMove.Venom("Acid Spit", 0.5, true, 1, 3, 2),new EnemyMove.Afflict("Extraordinary Poison Acid", 0, false, EnemyMove.Ailment.BREAK, 5, 2, 95, false, 1),
                      new EnemyMove.Afflict("Sticky Web", 0, false, EnemyMove.Ailment.BLIND, 0, 3, 85, false, 1),
                      new EnemyMove.Charge("Devour", 1.4, false, 1), new EnemyMove.Sweep("Tail Sweep", 0.7, false, 1)));
        s.layout(new String[]{"X", "M", "M E M", "E E M", "E M", "S R", "M", "M E", "R E E", "M E", "E S", "E M E", "R E M", "B"}, 11, 12, pool(1,
            new Event.Trap("Thorn Thicket", "Black thorns block the path ahead.", 5),
            new Event.Trap("Bear Trap", "You spot a rusty bear trap half buried in the leaves.", 10),
            new Event.Chest("Hollow Log", "Something glints inside a hollow log.", 20),
            new Event.Shrine("Mossy Spring", "Cool, clear water bubbles out from the moss.", 0.25),
            new Event.Thief("Goblin Pickpocket", "A goblin snatches your coin purse and runs!", 18),
            new Event.Ambush("Wolf Ambush", "Yellow eyes glow in the bushes. A wolf pack surrounds you!"),
            new Event.Choice("Echoes Beyond the Door",
                "A heated conversation can be heard from inside the room. You listen carefully and make out bits and pieces through a thick Leithanian accent. \n\"He killed everyone at the party? Just for that play of his?\" \n\"We should never have let Ahrendts know about our plans. He's been out of his mind ever since he ran into that Crimson Troupe!\" \n\"Don't say that, Andreas! At least he's actually done some research on Arts.\"",
                new Event.Option("Swing the doors and walk in", "Who is inside?", (st, r) -> {
                    if (r.nextBoolean()) {
                        Hero h = st.randomHero(r); h.upgrade(Hero.Stat.MATK); h.upgrade(Hero.Stat.MATK);
                        return Event.Result.text("There is no one inside the room, only a broken staff lying on the ground. The argument was merely your imagination.\n\n" + h.getName() + " takes the Broken Staff: M.ATK +2!");
                    }
                    return Event.Result.fight("The talking stops. Several figures turn toward you with weapons drawn!");
                }),
                new Event.Option("Keep listening", "Ears to the door.", (st, r) -> {
                    st.addMora(30);
                    return Event.Result.text("They mention a hidden stash under the floorboard outside. You quietly take it: +30 Mora.");
                }),
                new Event.Option("Sounds dangerous, better leave", "You keep walking.", (st, r) ->
                    Event.Result.text("There seem to be quite a few people inside. It would be best to return after you have made the necessary preparations."))),
            new Event.Choice("Auspicious Sign",
                "You discover a colony of musbeasts in a clearing. The little white creatures are bunched up together like a woolen quilt. Your presence has not scared them away, and on the contrary, you have a vague sense that they're actually inviting you.",
                new Event.Option("Play with the musbeasts", "Tiny paws everywhere.", (st, r) -> {
                    st.addMora(25);
                    return Event.Result.text("You have a great time holding many tiny paws with fur stuck down your neck. After a while, the musbeasts disperse, leaving behind something shiny where the \"woolen quilt\" used to be.\n\n+25 Mora.");
                }),
                new Event.Option("Dive into the fluff!", "It looks SO soft.", (st, r) -> {
                    Hero h = st.randomHero(r); h.growMaxHp(5);
                    return Event.Result.text("Even through your coldproof gear, you can fully feel the softness and warmth. You spend the afternoon lying down on the ground, thinking about the sky, the forest, the snowy mountains, the land, and do nothing else.\n\n" + h.getName() + " feels very happy and healthy: Max HP +5!");
                })).withBg("bg/Cursed_wood1.png")));
        s.placeSpecial(new Event.Choice("Noir Corne",
            "A tired man in a dark coat sits against a dead tree, turning a worn notebook over in his hands.\n\"There's something I meant to give to someone, at the right time... but I don't think I'll be lucky enough to see that day. Would you take it instead? It isn't free, I'm afraid - things like this always ask for a piece of you.\"",
            new Event.Option("Accept the notebook", "\"It isn't free... it asks for a piece of you.\"", (st, r) -> {
                Hero h = st.getHero(); h.loseMaxHp(5);
                st.getInventory().add(Items.NOIR_NOTES, 1);
                return Event.Result.text("The moment your fingers touch the cover, a chill runs through your chest and something leaves you for good.\nNoir smiles, relieved. \"Thank you. If you ever meet a swordswoman who hunts the lightning beast... she'll know what to do with it.\"\n\n" + h.getName() + " Max HP -5 (permanent).\nGot: Noir Corne's Personal Notes (Key item)");
            }).onlyIf(st -> st.getInventory().count(Items.NOIR_NOTES) == 0),
            new Event.Option("Ask what it is", "Just talk.", (st, r) -> Event.Result.talk(
                "\"Notes. Tracks, weather, the sound thunder makes before it walks. Years of chasing something divine.\" He coughs. \"It's no use to me anymore. But to the right hunter, it's everything.\"")),
            Event.leave("Decline politely")).withNpc("Noir.png").withBg("bg/Cursed_wood1.png"));
        return s;
    }

    /** Stage 2 - Monster/Ice Castle. Still solo. Guards, gift-box tricks and big charged hits. */
    private static Stage iceCastle() {
        Stage s = new Stage(2, 14, "Ice Castle", "A frozen palace where nothing stays warm for long.",
                new Color(30, 40, 78), new Color(110, 170, 240));
        s.monsters.add(() -> mage("Snowier", 25, 1, 2, 8, 23, new Color(200, 220, 250))
                .with(new EnemyMove.Afflict("Frost Breath", 1, true, EnemyMove.Ailment.FREEZE, 0, 1, 25, false, 1), new EnemyMove.Sweep("Blizzard", 0.9, true, 2), new EnemyMove.Guard("Frost Shell", 3, 2, true, 1)));
        s.monsters.add(() -> phys("Garm Baby", 16, 3, 1, 12, 26, new Color(170, 200, 230))
                .with(new EnemyMove.Afflict("Icy Howl", 0, false, EnemyMove.Ailment.WEAK, 2, 2, 70, true, 1), new EnemyMove.Strike("Frost Bite", 0.6, false, 2, 2), new EnemyMove.Rally("Pup Howl", 2, 2, 1)));
        s.monsters.add(() -> mage("Gazeti", 14, 0, 8, 9, 24, new Color(140, 200, 240))
                .with(new EnemyMove.Afflict("Ice Prison", 0.8, true, EnemyMove.Ailment.FREEZE, 0, 1, 35, false, 1), new EnemyMove.Strike("Ice Spear", 1.3, true, 1, 2), new EnemyMove.Weaken("Frostbite", 2, 2, 1)));
        s.monsters.add(() -> phys("Antonio", 16, 2, 0, 8, 28, new Color(200, 80, 80))
                .with(new EnemyMove.Strike("Candy Cane Swing", 1.2, false, 1, 2), new EnemyMove.Explode("Gift Bomb", 1.7, 6)));
        s.monsters.add(() -> phys("Mystcase", 15, 2, 1, 8, 25, new Color(230, 190, 90))
                .with(new EnemyMove.Afflict("Confetti Blast", 0.5, false, EnemyMove.Ailment.BLIND, 0, 2, 70, true, 1), new EnemyMove.Guard("Shut the Lid", 3, 2, false, 1), new EnemyMove.Strike("Surprise!", 1.4, false, 1, 2)));
        s.monsters.add(() -> phys("Sasquatch", 30, 2, 0, 9, 27, new Color(220, 220, 230))
                .with(new EnemyMove.Afflict("Ground Pound", 0.8, false, EnemyMove.Ailment.STUN, 0, 1, 35, true, 1), new EnemyMove.Strike("Maul", 0.6, false, 2, 2), new EnemyMove.Charge("Avalanche Slam", 1.8, false, 1)));
        s.monsters.add(() -> phys("Ice Titan", 24, 6, 2, 7, 30, new Color(150, 190, 230))
                .with(new EnemyMove.Afflict("Glacier Crush", 1.1, false, EnemyMove.Ailment.BREAK, 3, 2, 80, false, 1), new EnemyMove.Guard("Glacial Armor", 2, 3, false, 1), new EnemyMove.Charge("Frost Slam", 1.8, false, 1)));
        s.monsters.add(() -> mage("Aqua Angelic", 21, 0, 6, 11, 37, new Color(120, 200, 255))
                .with(new EnemyMove.Restore("Seraph's Hymn", 0.15, 3, 1),
                      new EnemyMove.Afflict("Silent Snow", 0, true, EnemyMove.Ailment.SILENCE, 0, 3, 75, false, 1), new EnemyMove.Sweep("Holy Frost", 0.8, true, 2), new EnemyMove.Mend("Seraph's Grace", 0.2, 1)));
        s.setBoss("Knight of Windstorm", () -> new Enemy.Boss("Knight of Windstorm", 107, 2, 4, 8, 8, 130, new Color(130, 170, 200))
                .with(new EnemyMove.Restore("Eye of the Storm", 0.15, 0.10, 4, 1),
                      new EnemyMove.Summon("Call Freezer", () -> mage("Freezer", 10, 2, 2, 8, 25, new Color(150, 210, 240))
                          .with(new EnemyMove.Afflict("Frost Touch", 0.6, true, EnemyMove.Ailment.FREEZE, 0, 1, 10, false, 2)), 2, 2),
                      new EnemyMove.Sweep("Windstorm", 1, true, 2), new EnemyMove.Charge("Gale Lance", 1.5, false, 1),
                      new EnemyMove.Afflict("Frozen Gale", 0.8, true, EnemyMove.Ailment.FREEZE, 0, 1, 30, true, 1),
                      new EnemyMove.Afflict("Shatter Armor", 1.1, false, EnemyMove.Ailment.BREAK, 3, 3, 40, false, 1),
                      new EnemyMove.Guard("Storm Shield", 2, 4, false, 2)));
        s.layout(new String[]{"X", "M", "M E", "E M", "M", "R E", "M E", "S", "E M M", "E M", "R E M", "E E", "M S M", "B"}, 22, 18, pool(2,
            new Event.Trap("Falling Icicles", "Huge icicles hang from the ceiling above the hall.", 10),
            new Event.Trap("Frozen Floor", "The floor ahead is slick black ice.", 15),
            new Event.Chest("Frozen Treasure", "A chest is sealed inside a block of ice.", 35),
            new Event.Shrine("Warm Hearth", "A fireplace still burns in an empty room.", 0.25),
            new Event.Thief("Ice Imp", "A tiny imp snatches your purse and slides away!", 20),
            new Event.Ambush("Castle Guard", "Frozen suits of armor creak to life!"),
            new Event.Choice("Singing Crystal", "A huge ice crystal hums a beautiful, haunting melody. It looks very valuable.",
                new Event.Option("Strike it", "It looks valuable. And fragile.", (st, r) -> {
                    if (r.nextBoolean()) { st.addMora(45); return Event.Result.text("It cracks cleanly. You pocket 45 Mora of crystal."); }
                    return Event.Result.text("It shatters violently! " + st.hurtAll(6));
                }),
                new Event.Option("Listen to the song", "Such a gentle melody.", (st, r) -> Event.Result.text("The melody eases your pain. " + st.healAll(0.2))),
                Event.leave("Move along")),
            new Event.Choice("Heart of Ice",
                "You discover a cavern buried beneath a frozen wasteland. Countless crystalline spires pierce the ground, their jagged edges glimmering with an eerie turquoise light. A narrow path winds through the frozen depths, leading toward a staircase half-swallowed by ice. The air is deathly cold, and the faint sound of cracking ice echoes through the chamber. Yet, deep within the cavern, something seems to be glowing with a warmth that should not exist in a place such as this.",
                new Event.Option("Approach the glowing crystals", "Something glitters in the ice.", (st, r) -> {
                    st.addMora(67);
                    return Event.Result.text("You carefully approach the crystals, discovering veins of precious minerals embedded within the ice. With a little effort, you manage to extract some of the more accessible fragments before the cold drives you away.\n\n+67 Mora.");
                }),
                new Event.Option("Follow the frozen staircase", "Slippery steps, leading up.", (st, r) -> Event.Result.text(
                    "You climb the slippery steps, venturing deeper into the cavern. At the top, you find a breathtaking view of the frozen depths, and for a brief moment, the beauty of this forgotten place makes the harsh journey worthwhile.\n\n" + st.healAll(0.5))),
                new Event.Option("Reach into the unnatural glow", "Warmth where there should be none.", (st, r) -> {
                    Hero h = st.randomHero(r);
                    String lost = h.lower(Hero.Stat.MATK) ? h.getName() + " M.ATK -1." : h.getName() + " has no M.ATK left to lose.";
                    return Event.Result.text("You reach toward the mysterious light. A sudden surge of freezing energy courses through your body, and the cavern erupts in a dazzling flash. You stumble backward, your limbs numb from the cold, but the glow soon fades, leaving only silence behind.\n\n" + lost);
                })).withBg("bg/Ice_Castle2.png")));
        return s;
    }

    /** Stage 3 - Monster/Burning Prophet Ruin. The companion joins. Burns, curses, explosions. */
    private static Stage burningRuin() {
        Stage s = new Stage(3, 16, "Burning Prophet Ruin", "Ashes of a temple that foresaw its own fire.",
                new Color(66, 24, 30), new Color(240, 120, 80));
        s.monsters.add(() -> phys("Explosion Bat", 30, 3, 3, 14, 26, new Color(200, 80, 60))
                .with(new EnemyMove.Strike("Bite", 0.9, false, 2, 2), new EnemyMove.Explode("Self-Destruct", 2, 8)));
        s.monsters.add(() -> mage("Heater", 29, 4, 3, 11, 25, new Color(250, 130, 50))
                .with(new EnemyMove.Afflict("Heat Wave", 0.8, true, EnemyMove.Ailment.BURN, 3, 3, 70, true, 2), new EnemyMove.Sweep("Flame Burst", 0.7, true, 2), new EnemyMove.Venom("Scorch", 0.7, true, 3, 2, 1)));
        s.monsters.add(() -> mage("Kasa", 26, 4, 8, 14, 28, new Color(240, 90, 60))
                .with(new EnemyMove.Afflict("Inferno", 1.2, true, EnemyMove.Ailment.BURN, 4, 3, 80, false, 1), new EnemyMove.Strike("Flame Bolt", 1.3, true, 1, 2), new EnemyMove.Guard("Fire Wall", 2, 2, true, 1)));
        s.monsters.add(() -> phys("Salamander", 24, 8, 2, 11, 35, new Color(230, 110, 40))
                .with(new EnemyMove.Afflict("Searing Bite", 0.9, false, EnemyMove.Ailment.BURN, 5, 5, 75, false, 1), new EnemyMove.Strike("Tail Lash", 0.5, false, 3, 2), new EnemyMove.Sweep("Fire Breath", 0.7, false, 1)));
        s.monsters.add(() -> phys("Lava Golem", 30, 10, 0, 12, 43, new Color(160, 70, 40))
                .with(new EnemyMove.Afflict("Magma Crush", 1.8, false, EnemyMove.Ailment.BREAK, 3, 4, 80, false, 1), new EnemyMove.Guard("Magma Shell", 4, 2, false, 1), new EnemyMove.Charge("Molten Fist", 1.9, false, 3)));
        s.monsters.add(() -> mage("Banshee Master", 34, 1, 9, 16, 42, new Color(180, 180, 220))
                .with(new EnemyMove.Afflict("Banshee Scream", 1, true, EnemyMove.Ailment.SILENCE, 0, 3, 85, true, 1), new EnemyMove.Drain("Soul Wail", 1.6, true, 2), new EnemyMove.Weaken("Dread Shriek", 3, 4, 1)));
        s.monsters.add(() -> mage("Nightmare Terror", 30, 2, 8, 9, 37, new Color(90, 40, 60))
                .with(new EnemyMove.Afflict("Bad Dream", 0, true, EnemyMove.Ailment.STUN, 0, 1, 70, false, 1), new EnemyMove.Sweep("Nightmare", 0.9, true, 2), new EnemyMove.Weaken("Terror", 3, 2, 1)));
        s.monsters.add(() -> phys("Removal", 28, 3, 6, 10, 35, new Color(120, 120, 140))
                .with(new EnemyMove.Afflict("Grave Curse", 0, true, EnemyMove.Ailment.CURSE, 0, 5, 90, false, 1), new EnemyMove.Venom("Rotten Bite", 0.8, false, 3, 3, 2), new EnemyMove.Mend("Undying", 0.25, 1)));
        s.monsters.add(() -> mage("Tha Despero", 47, 4, 4, 16, 43, new Color(200, 160, 90))
                .with(new EnemyMove.Restore("Purifying Light", 0.15, 3, 1),
                      new EnemyMove.Afflict("Blinding Light", 0.9, true, EnemyMove.Ailment.BLIND, 0, 2, 90, true, 1), new EnemyMove.Charge("Prophecy of Ruin", 1.8, true, 1), new EnemyMove.Drain("Despair", 1.0, true, 1)));
        s.setBoss("Gloom Under Night", () -> new Enemy.Boss("Gloom Under Night", 137, 2, 7, 12, 12, 180, new Color(110, 30, 50))
                .with(new EnemyMove.Restore("Ashen Requiem", 0.15, 0.10, 4, 1),
                      new EnemyMove.Summon("Raise Molten Golem", () -> phys("Molten Golem", 20, 3, 3, 10, 20, new Color(200, 90, 40))
                          .with(new EnemyMove.Afflict("Molten Fist", 0.9, false, EnemyMove.Ailment.BURN, 3, 3, 70, false, 2)), 3, 1),
                      new EnemyMove.Afflict("Gloom Wave", 1.3, true, EnemyMove.Ailment.BURN, 5, 2, 70, true, 2),
                      new EnemyMove.Afflict("Eclipse", 0.5, true, EnemyMove.Ailment.SILENCE, 0, 3, 95, true, 1),
                      new EnemyMove.FpDrain("Devour Dreams", 2, 0.8, true, 1), new EnemyMove.Charge("Endless Night", 2, false, 1),
                      new EnemyMove.Weaken("Despair", 3, 2, 3)));
        s.layout(new String[]{"X", "M", "M E", "M M", "E", "S M", "M E M", "R E", "E M", "E M", "M R M", "S M", "E E", "M E E", "R M", "E M S", "B"}, 33, 24, pool(3,
            new Event.Trap("Lava Burst", "The ground ahead glows and bubbles.", 15),
            new Event.Trap("Collapsing Pillar", "A burnt pillar leans over the path, ready to fall.", 20),
            new Event.Chest("Prophet's Coffer", "A blackened coffer survived the fire.", 40),
            new Event.Shrine("Ember Altar", "Warm embers flicker on an ancient altar.", 0.25),
            new Event.Thief("Ash Imp", "A sooty imp pulls coins from your bag!", 30),
            new Event.Ambush("Burning Hall", "The flames take shape and attack!"),
            new Event.Choice("The Last Prophecy", "A charred stone tablet still glows. Half of a prophecy is readable: \"...the one who reads this will...\"",
                new Event.Option("Read the rest aloud", "\"...the one who reads this will...\"", (st, r) -> {
                    if (r.nextBoolean()) { for (Hero h : st.getParty()) h.upgrade(Hero.Stat.ATK); return Event.Result.text("\"...become stronger.\" Everyone ATK +1!"); }
                    return Event.Result.text("\"...burn.\" Flames burst from the tablet! " + st.hurtAll(7));
                }),
                new Event.Option("Smash the tablet", "Prophecies are meant to be broken.", (st, r) -> Event.Result.fight("The fire spirits guarding the tablet attack!")),
                Event.leave("Leave it alone")),
            new Event.Choice("Epochal Revision",
                "\"Look, this is one of the many tomes that the Sarkaz has written to hold their prophecies. Let's see, destroyed homeland, cruel enemies, the usual stuff... Wait, these are unlucky incidents... that all happened to me recently?! Could this book be foretelling my fate? I wonder if I can change my luck if I did some revisions...\"",
                new Event.Option("\"If Originium was edible...\"", "Rewrite your luck... for a price.", 50, null, (st, r) -> {
                    Item it = Items.randomSpecial(r); st.getInventory().add(it, 1);
                    return Event.Result.text("\"...And we all lived great lives. The end!\"\n\n-50 Mora. Got: " + it.getName() + "!");
                }),
                new Event.Option("\"If the land united into one...\"", "Unity has its costs.", (st, r) -> {
                    Hero h = st.randomHero(r);
                    boolean lost = h.lower(Hero.Stat.ATK); h.growMaxHp(5);
                    return Event.Result.text("\"...And we all lived great lives. The end!\"\n\n" + h.getName() + (lost ? " ATK -1, " : " ") + "Max HP +5.");
                }),
                new Event.Option("\"If, tomorrow, I run into...\"", "\"But am I willing to be at the mercy of prophecy?\"", (st, r) -> Event.Result.text(
                    "\"Best not add anything! What's so bad about a little misfortune?\""))).withBg("bg/Burning Prophet Ruin.png")));
        s.placeSpecial(new Event.Choice("Myrtle's Curio Stall",
            "A small girl sits on a crate surrounded by odd trinkets. \"Hey, hey! You look like adventurers. I've got something really special... a map of a forest that doesn't exist on any other map. Only 200 Mora!\"",
            new Event.Option("Buy the Topographical Map", "Someone in another land may be looking for it.", 200, null, (st, r) -> {
                st.getInventory().add(Items.TOPO_MAP, 1);
                return Event.Result.text("Myrtle grins and hands you a faded map covered in strange trails.\n\"Show it to someone who knows the old roads. I heard a swordswoman in the Night Realm is looking for it!\"\n\nGot: Topographical Map (Key item)");
            }).onlyIf(st -> st.getInventory().count(Items.TOPO_MAP) == 0),
            new Event.Option("Ask about the map", "Just talk.", (st, r) -> Event.Result.talk(
                "\"It shows the Primal Forest! Grown-ups say it's just a story. But Yato - she's a wandering swordswoman in the Night Realm - she believes it's real.\"")),
            Event.leave("Maybe later")).withNpc("Myrtle.png"));
        return s;
    }

    /** Stage 4 - Monster/Nigh Realm. The undead rally, drain and summon. */
    private static Stage nightRealm() {
        Stage s = new Stage(4, 18, "Night Realm", "Where the dead walk and the moon never sets.",
                new Color(40, 30, 70), new Color(180, 130, 240));
        s.monsters.add(() -> phys("Skeleton Soldier", 27, 5, 5, 11, 31, new Color(200, 200, 190))
                .with(new EnemyMove.Afflict("Shield Slam", 1.2, false, EnemyMove.Ailment.STUN, 0, 1, 44, false, 1), new EnemyMove.Guard("Shield Up", 5, 2, false, 1), new EnemyMove.Strike("Bone Cleave", 1.4, false, 1, 2)));
        s.monsters.add(() -> phys("Jakk", 30, 5, 5, 16, 29, new Color(230, 140, 40))
                .with(new EnemyMove.Afflict("Flaming Pumpkin", 0.8, false, EnemyMove.Ailment.BURN, 3, 3, 70, false, 1), new EnemyMove.Strike("Pumpkin Flurry", 0.9, false, 3, 2), new EnemyMove.Charge("Jack-o'-Blast", 1.8, false, 1)));
        s.monsters.add(() -> mage("Wraith", 26, 0, 10, 11, 29, new Color(90, 150, 170))
                .with(new EnemyMove.Afflict("Soul Lock", 0, true, EnemyMove.Ailment.SILENCE, 0, 2, 77, false, 1), new EnemyMove.Drain("Soul Siphon", 1.5, true, 2), new EnemyMove.Weaken("Curse", 3, 2, 1)));
        s.monsters.add(() -> mage("Loli Ruri", 30, 2, 9, 10, 41, new Color(150, 150, 200))
                .with(new EnemyMove.Afflict("Blood Curse", 1.5, true, EnemyMove.Ailment.CURSE, 0, 3, 80, false, 1), new EnemyMove.Drain("Vampire Kiss", 1.0, true, 2), new EnemyMove.Weaken("Charm", 3, 2, 1)));
        s.monsters.add(() -> phys("Zombie Prisoner", 50, 0, 0, 14, 25, new Color(120, 140, 120))
                .with(new EnemyMove.Venom("Rotten Bite", 1.2, false, 3, 3, 2), new EnemyMove.Mend("Undying", 1.5, 1)));
        s.monsters.add(() -> mage("Sohee", 29, 2, 4, 12, 24, new Color(220, 220, 240))
                .with(new EnemyMove.Afflict("Mournful Wail", 1.2, true, EnemyMove.Ailment.WEAK, 2, 6, 90, true, 1), new EnemyMove.Sweep("Wailing", 1.2, true, 2), new EnemyMove.Weaken("Hex", 2, 4, 1)));
        s.monsters.add(() -> mage("Dark Priest", 30, 0, 8, 12, 39, new Color(110, 60, 120))
                .with(new EnemyMove.Restore("Unholy Rite", 0.15, 3, 1),
                      new EnemyMove.Afflict("Darkness", 0.5, true, EnemyMove.Ailment.BLIND, 0, 2, 80, true, 1), new EnemyMove.Mend("Dark Heal", 1.2, 2), new EnemyMove.Rally("Unholy Blessing", 2, 3, 1)));
        s.monsters.add(() -> phys("Evil Huntress", 29, 3, 2, 10, 30, new Color(160, 60, 90))
                .with(new EnemyMove.Afflict("Pinning Arrow", 0.9, false, EnemyMove.Ailment.STUN, 0, 1, 40, false, 1), new EnemyMove.Strike("Volley", 0.5, false, 3, 2), new EnemyMove.Charge("Heartseeker", 1.8, false, 1),
                      new EnemyMove.Weaken("Crippling Shot", 3, 2, 1)));
        s.monsters.add(() -> phys("Drake", 40, 6, 6, 12, 30, new Color(80, 110, 150))
                .with(new EnemyMove.Afflict("Plundering Slash", 1.0, false, EnemyMove.Ailment.BREAK, 3, 2, 70, false, 1), new EnemyMove.Rally("Pirate's Call", 2, 2, 1), new EnemyMove.Strike("Cutlass", 1.4, false, 1, 2), new EnemyMove.Drain("Soul Steal", 1.5, false, 3)));
        s.monsters.add(() -> phys("Skeleton General", 26, 8, 8, 10, 32, new Color(200, 190, 170))
                .with(new EnemyMove.Afflict("Battle Cry", 0, false, EnemyMove.Ailment.WEAK, 3, 4, 70, true, 1), new EnemyMove.Rally("Command", 3, 3, 1), new EnemyMove.Strike("General's Edge", 1.4, false, 1, 2),
                      new EnemyMove.Guard("Phalanx", 3, 2, true, 1)));
        s.setBoss("Knight of Abyss", () -> new Enemy.Boss("Knight of Abyss", 230, 4, 8, 16, 14, 220, new Color(130, 120, 110))
                .with(new EnemyMove.Restore("Abyssal Oath", 0.15, 0.10, 4, 1),
                      new EnemyMove.Summon("Call Raydric Archer", () -> phys("Raydric Archer", 25, 4, 0, 7, 30, new Color(120, 110, 130))
                          .with(new EnemyMove.Strike("Arrow Volley", 0.9, false, 3, 4), new EnemyMove.Afflict("Pinning Shot", 0.8, false, EnemyMove.Ailment.STUN, 0, 1, 30, false, 1)), 3, 1),
                      new EnemyMove.Charge("Abyssal Cleave", 1.8, false, 2),
                      new EnemyMove.Afflict("Soul Chain", 1.5, false, EnemyMove.Ailment.STUN, 0, 1, 45, false, 1),
                      new EnemyMove.Afflict("Armor Rend", 0.8, false, EnemyMove.Ailment.BREAK, 4, 4, 70, true, 1),
                      new EnemyMove.Afflict("Dread Aura", 1.2, true, EnemyMove.Ailment.WEAK, 3, 3, 50, true, 1),
                      new EnemyMove.Drain("Soul Rend", 1.2, false, 1), new EnemyMove.FpDrain("Abyssal Hunger", 2, 0.8, false, 1)));
        s.layout(new String[]{"X", "M", "M E", "M M E", "E M", "S R", "M M", "M E M", "R", "M M", "E M E", "M E", "S M", "M E", "R", "M M", "E E", "E E","B"}, 44, 28, pool(4,
            new Event.Trap("Cursed Spikes", "The floor tiles ahead are stained red. Something is hidden here.", 25),
            new Event.Trap("Haunting Chill", "A ghostly cold mist fills the road.", 35),
            new Event.Chest("Grave Goods", "A noble's coffin, still sealed.", 55),
            new Event.Shrine("Moonlit Chapel", "A faint prayer echoes in an empty chapel.", 0.25),
            new Event.Thief("Poltergeist", "Unseen hands pull coins from your bag!", 30),
            new Event.Ambush("Rising Dead", "Hands burst out of the ground!"),
            new Event.Choice("Lost Spirit", "A small ghost girl floats by a grave, crying. \"I lost my locket... I can't rest without it.\"",
                new Event.Option("Help her search", "Cold dirt and colder hands.", (st, r) -> {
                    st.getHero().loseHpKeep1(6);
                    for (Hero h : st.getParty()) h.upgrade(Hero.Stat.MDEF);
                    return Event.Result.text("You dig through cold dirt and find it. She smiles and fades away. A blessing settles on you: everyone M.DEF +1.");
                }),
                new Event.Option("Banish the spirit", "The dead don't like being sent away.", (st, r) -> Event.Result.fight("Her crying turns into a scream. The dead rise to protect her!")),
                Event.leave("Pretend you didn't see her")),
            new Event.Choice("Fallen Adventurer", "A knight's body lies against a tombstone, still holding a full backpack.",
                new Event.Option("Loot the body", "He won't need it anymore... right?", (st, r) -> {
                    st.getInventory().add(Items.HI_POTION, 1); st.addMora(30);
                    if (r.nextInt(10) < 4) return Event.Result.text("+1 Hi-Potion, +30 Mora... then a dark curse crawls up your arms. " + st.hurtAll(7));
                    return Event.Result.text("+1 Hi-Potion and +30 Mora.");
                }),
                new Event.Option("Give a proper burial", "Let him rest.", (st, r) -> Event.Result.text("You feel at peace. " + st.healAll(0.2))),
                Event.leave("Leave it"))));
        s.placeSpecial(new Event.Choice("Yato, the Wandering Blade",
            "A swordswoman sits on a broken gravestone, studying the moon. \"I've searched this realm for a forest that was erased from every map... You wouldn't happen to have a map of it, would you?\"",
            new Event.Option("Give the Topographical Map", "This is what she's been searching for.", 0, Items.TOPO_MAP, (st, r) -> {
                st.findSecret();
                return Event.Result.text("Her eyes widen. \"The Primal Forest... it really exists.\"\nShe rolls up the map. \"Clear this realm first. Then I'll take you there.\"\n\nSECRET STAGE UNLOCKED: Primal Forest (after clearing Night Realm).");
            }),
            new Event.Option("Challenge her to a duel", "Her blade has never lost. Losing here means the end.", (st, r) -> Event.Result.duel(
                "She stands up slowly and draws her blade. Moonlight runs along the edge.\n\"Then don't hold back. I won't.\"",
                () -> new ArrayList<>(Collections.singletonList(yato())), Music.YATO, Stage::yatoReward)).onlyIf(st -> !st.isYatoDefeated()),
            Event.leave("Wish her luck")).withNpc("Yato.png"));
        return s;
    }

    /** Yato, the Wandering Blade - optional super boss in the Night Realm.  */
    private static Enemy yato() {
        return new Enemy.Boss("Yato", 270, 6, 6, 16, 10, 250, new Color(150, 120, 220))
            .with(new EnemyMove.Restore("Moonlit Meditation", 0.15, 0.10, 4, 1),
                      new EnemyMove.Strike("Twin Moon Slash", 0.8, false, 2, 3),
                  new EnemyMove.Strike("Iai: Crescent", 1.6, false, 1, 2),
                  new EnemyMove.Sweep("Thousand Petal Cuts", 0.8, false, 2),
                  new EnemyMove.Charge("Moonlight Severance", 3.0, false, 1),
                  new EnemyMove.Afflict("Shadow Step", 1.2, false, EnemyMove.Ailment.BREAK, 3, 2, 85, false, 1),
                  new EnemyMove.Afflict("Pressure Point", 1.0, false, EnemyMove.Ailment.STUN, 0, 1, 45, false, 1),
                  new EnemyMove.Afflict("Bleeding Lotus", 0.8, false, EnemyMove.Ailment.POISON, 3, 3, 70, true, 1),
                  new EnemyMove.FpDrain("Feint", 2, 0.8, false, 1),
                  new EnemyMove.Guard("Parry Stance", 4, 2, false, 1),
                  new EnemyMove.Rally("Wanderer's Focus", 3, 2, 1));
    }
    /** Divine Kirin - secret boss in the Primal Forest (Yato's quest). Lightning magic, stuns and silences. */
    private static Enemy divineKirin() {
        return new Enemy.Boss("Divine Kirin", 240, 6, 5, 14, 16, 300, new Color(110, 220, 210))
            .with(new EnemyMove.Restore("Auspicious Rain", 0.15, 0.10, 4, 1),
                      new EnemyMove.Strike("Thunder Hoof", 1.4, false, 1, 2),
                  new EnemyMove.Sweep("Heavenly Thunder", 1.0, true, 2),
                  new EnemyMove.Afflict("Judgement Bolt", 1.2, true, EnemyMove.Ailment.STUN, 0, 1, 45, false, 1),
                  new EnemyMove.Afflict("Static Field", 0.6, true, EnemyMove.Ailment.SILENCE, 0, 4, 50, true, 1),
                  new EnemyMove.Afflict("Celestial Gallop", 0.9, false, EnemyMove.Ailment.BREAK, 3, 2, 70, true, 1),
                  new EnemyMove.Charge("Divine Horn", 2.6, true, 1),
                  new EnemyMove.Guard("Auspicious Cloud", 5, 3, false, 1),
                  new EnemyMove.FpDrain("Storm Gathers", 1, 0.8, true, 1));
    }
    /** After beating the Divine Kirin: Yato joins as the 3rd hero and Orchid is locked for this run. */
    private static void kirinReward(java.awt.Component parent, GameState gs) {
        gs.getInventory().remove(Items.NOIR_NOTES);
        boolean joined = gs.recruitYato();
        String msg = joined
            ? "The Kirin's last thunderclap fades into a gentle rain.\nYato closes Noir's notebook and tucks it inside her coat. \"He said it would reach the right person at the right time. Guess that's you.\"\n\"I'm coming with you. Try to keep up.\"\n\nSECRET HERO UNLOCKED: Yato the Kirin Hunter joins your party as the 3rd member!\n(Orchid can't join this run.)"
            : "The Kirin falls. Yato bows deeply. \"Thank you. Noir can rest now.\"";
        javax.swing.JOptionPane.showMessageDialog(parent, msg, "Yato", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    /** After beating Yato: one Passive of your choice, and she still takes the map if you have it. */
    private static void yatoReward(java.awt.Component parent, GameState gs) {
        gs.defeatYato();                              // the 250 Mora comes from the battle reward
        String learned = PassiveDialog.choose(parent, gs, Passive.rewards(), "Yato's Reward");
        javax.swing.JOptionPane.showMessageDialog(parent, "Yato sheathes her sword and laughs.\n\"You're the real thing. Take this - it's how I survived out here.\"\n\n" + learned,
                "Yato's Reward", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        if (!gs.isSecretFound() && gs.getInventory().count(Items.TOPO_MAP) > 0
                && javax.swing.JOptionPane.showConfirmDialog(parent, "\"By the way... is that a map of the Primal Forest in your bag?\"\n\nGive her the Topographical Map?",
                        "Yato", javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION) {
            gs.getInventory().remove(Items.TOPO_MAP);
            gs.findSecret();
            javax.swing.JOptionPane.showMessageDialog(parent, "\"The Primal Forest... it really exists.\"\n\nSECRET STAGE UNLOCKED: Primal Forest (after clearing Night Realm).", "Yato", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Stage 5 - Monster/Memories Of Warfield. The final stage. */
    private static Stage warfield() {
        Stage s = new Stage(5, 20, "Memories Of Warfield", "Echoes of a war that never ended. The last king waits.",
                new Color(60, 40, 30), new Color(255, 200, 90));
        s.monsters.add(() -> phys("Goldporing", 70, 0, 10, 16, 60, new Color(250, 210, 60))
                .with(new EnemyMove.Mend("Shiny Bounce", 2, 2)));
        s.monsters.add(() -> phys("Alarm", 60, 8, 8, 14, 32, new Color(200, 180, 120))
                .with(new EnemyMove.Afflict("Time Stop", 0, false, EnemyMove.Ailment.STUN, 0, 1, 80, false, 1), new EnemyMove.Strike("Clock Hands", 1, false, 3, 2), new EnemyMove.Rally("Time's Up", 2, 2, 1)));
        s.monsters.add(() -> phys("Harpy", 46, 4, 3, 12, 35, new Color(180, 140, 110))
                .with(new EnemyMove.Afflict("Gale Feathers", 1.2, false, EnemyMove.Ailment.BLIND, 0, 2, 70, true, 1), new EnemyMove.Strike("Talon Dive", 0.6, false, 2, 2), new EnemyMove.Sweep("Feather Storm", 0.6, false, 1)));
        s.monsters.add(() -> phys("Raydric", 35, 6, 3, 10, 22, new Color(90, 90, 110))
                .with(new EnemyMove.Afflict("Armor Splitter", 1.5, false, EnemyMove.Ailment.BREAK, 3, 2, 80, false, 1), new EnemyMove.Guard("Iron Wall", 4, 2, true, 1), new EnemyMove.Strike("Heavy Slash", 1.4, false, 1, 2)));
        s.monsters.add(() -> mage("Retribution", 67, 1, 12, 14, 56, new Color(250, 200, 120))
                .with(new EnemyMove.Afflict("Holy Fire", 1.5, true, EnemyMove.Ailment.BURN, 5, 3, 80, true, 1), new EnemyMove.Sweep("Judgement Fire", 0.9, true, 2), new EnemyMove.Strike("Holy Strike", 1.3, true, 1, 1)));
        s.monsters.add(() -> mage("Shelter", 57, 3, 5, 12, 25, new Color(150, 200, 230))
                .with(new EnemyMove.Restore("Holy Sanctuary", 0.15, 3, 1),
                      new EnemyMove.Afflict("Seal", 0, true, EnemyMove.Ailment.SILENCE, 0, 2, 77, false, 1), new EnemyMove.Mend("Sanctuary", 2, 2), new EnemyMove.Guard("Barrier", 3, 2, true, 1)));
        s.monsters.add(() -> mage("Piamette", 48, 3, 5, 14, 22, new Color(230, 150, 200))
                .with(new EnemyMove.Afflict("Doll's Curse", 0.8, true, EnemyMove.Ailment.CURSE, 0, 5, 80, true, 1), new EnemyMove.Weaken("Puppet Strings", 3, 2, 1), new EnemyMove.Drain("Doll's Embrace", 1.0, true, 2)));
        s.monsters.add(() -> mage("Incubus", 55, 3, 5, 10, 22, new Color(150, 60, 120))
                .with(new EnemyMove.Afflict("Nightmare Kiss", 0.9, true, EnemyMove.Ailment.STUN, 0, 1, 35, false, 1), new EnemyMove.Drain("Kiss of Ruin", 1.0, true, 2), new EnemyMove.Weaken("Charm", 3, 2, 1)));
        s.monsters.add(() -> mage("Wicked Nymph", 34, 2, 8, 15, 42, new Color(120, 200, 160))
                .with(new EnemyMove.Afflict("Lullaby", 0.5, true, EnemyMove.Ailment.STUN, 0, 1, 60, true, 1), new EnemyMove.Sweep("Wicked Song", 0.7, true, 2), new EnemyMove.Venom("Toxic Petal", 0.7, true, 3, 3, 1)));
        s.monsters.add(() -> mage("Evil Cloud Hermit", 49, 10, 5, 12, 34, new Color(140, 140, 170))
                .with(new EnemyMove.Afflict("Thunderstorm", 1.5, true, EnemyMove.Ailment.BLIND, 0, 2, 80, true, 1), new EnemyMove.Charge("Thunder Cloud", 1.8, true, 1), new EnemyMove.Sweep("Acid Rain", 0.8, true, 2)));
        s.monsters.add(() -> phys("Minorous", 80, 12, 0, 18, 38, new Color(170, 90, 60))
                .with(new EnemyMove.Afflict("Earthquake", 1, false, EnemyMove.Ailment.STUN, 0, 1, 30, true, 1), new EnemyMove.Charge("Hammer Slam", 2.5, false, 1), new EnemyMove.Rally("Bull Roar", 3, 3, 1)));
        s.monsters.add(() -> phys("Valkyrie", 61, 7, 7, 14, 44, new Color(230, 230, 250))
                .with(new EnemyMove.Afflict("Spear of Odin", 1.6, false, EnemyMove.Ailment.BREAK, 3, 4, 80, false, 1), new EnemyMove.Strike("Spear Thrust", 1.4, false, 1, 2), new EnemyMove.Guard("Valhalla", 5, 5, true, 1),
                      new EnemyMove.Mend("Einherjar", 0.5, 1)));
        s.setBoss("Arch Regalia", () -> new Enemy.Boss("Arch Regalia", 360, 10, 10, 10, 20, 350, new Color(240, 200, 80))
                .with(new EnemyMove.Restore("Royal Grace", 0.15, 0.10, 4, 1),
                      new EnemyMove.Summon("Summon Reginleif", () -> phys("Reginleif", 35, 4, 3, 16, 10, new Color(230, 230, 250))
                          .with(new EnemyMove.Strike("Valkyrie Lance", 1.2, false, 1, 2), new EnemyMove.Guard("Shield Maiden", 4, 3, true, 1)), 3, 1),
                      new EnemyMove.Afflict("Regal Decree", 1.2, true, EnemyMove.Ailment.CURSE, 0, 3, 80, true, 2),
                      new EnemyMove.Charge("Crown Breaker", 2.4, false, 1),
                      new EnemyMove.Afflict("Royal Silence", 1.5, true, EnemyMove.Ailment.SILENCE, 0, 2, 60, true, 1),
                      new EnemyMove.FpDrain("Royal Tax", 2, 1.5, true, 1),new EnemyMove.Mend("World Blessing", 0.2, 1),
                      new EnemyMove.Afflict("Kneel!", 1.2, false, EnemyMove.Ailment.STUN, 0, 1, 45, false, 1),
                      new EnemyMove.Afflict("Oppress", 1.2, true, EnemyMove.Ailment.WEAK, 3, 4, 80, true, 1),
                      new EnemyMove.Drain("Tribute", 1.5, false, 1)));
        s.layout(new String[]{"X", "M", "M E", "E M M", "M", "S E", "M M", "E M E", "R", "M", "M E M", "S", "M M", "E M", "M E M", "R", "M M", "S E", "R", "B"}, 55, 28, pool(5,
            new Event.Trap("Collapsing Bridge", "The stone bridge across the battlefield is cracked.", 10),
            new Event.Trap("Old Minefield", "Rusty mines poke out of the mud.", 9),
            new Event.Chest("Royal Treasury", "Gold spills out of a broken war chest.", 70),
            new Event.Shrine("Fallen Queen's Garden", "Blue roses still bloom between the trenches.", 0.25),
            new Event.Ambush("Royal Guard", "\"Intruders on the king's field!\""),
            new Event.Choice("The Regal Envoy", "A masked envoy bows. \"His Majesty offers gold if you turn back. Or... you could pay tribute and pass in peace.\"",
                new Event.Option("Take the bribe", "Royal gold. Warm to the touch.", (st, r) -> {
                    st.addMora(80); return Event.Result.text("+80 Mora. The coins burn your hands with royal magic. " + st.hurtAll(6));
                }),
                new Event.Option("Pay tribute (40 Mora)", "Bow to the king.", 40, null, (st, r) -> Event.Result.text("\"Wise.\" Royal healers tend to you. " + st.healAll(0.35))),
                new Event.Option("Draw your weapon", "The envoy's guards look strong.", (st, r) -> Event.Result.elite("\"Then die like the others.\"")))));
        return s;
    }

    /** SECRET stage - Monster/Primal Forest. Reached by giving Yato the Topographical Map. Clearing it unlocks Orchid. */
    private static Stage primalForest() {
        Stage s = new Stage(GameState.SECRET_STAGE, 16, "Primal Forest", "A forest older than any map. Something watches from the canopy.",
                new Color(24, 56, 30), new Color(150, 230, 120));
        s.monsters.add(() -> phys("Spring Rabbit", 30, 2, 2, 20, 50, new Color(240, 220, 200))
                .with(new EnemyMove.Strike("Hop Kick", 0.6, false, 2, 2), new EnemyMove.Afflict("Dizzy Hop", 0.6, false, EnemyMove.Ailment.STUN, 0, 1, 25, false, 1)));
        s.monsters.add(() -> mage("Red Pitaya", 35, 2, 4, 18, 50, new Color(230, 70, 110))
                .with(new EnemyMove.Afflict("Thorn Spray", 0.6, true, EnemyMove.Ailment.POISON, 3, 3, 70, true, 2), new EnemyMove.Mend("Juicy Regrowth", 0.25, 1)));
        s.monsters.add(() -> phys("Permeter", 40, 6, 2, 12, 25, new Color(150, 140, 100))
                .with(new EnemyMove.Guard("Shell Up", 3, 2, true, 1), new EnemyMove.Strike("Rolling Charge", 1.3, false, 1, 2)));
        s.monsters.add(() -> phys("Solider", 40, 5, 2, 12, 27, new Color(110, 150, 90))
                .with(new EnemyMove.Strike("Spear Thrust", 1.4, false, 1, 2), new EnemyMove.Afflict("Shell Bash", 0.8, false, EnemyMove.Ailment.STUN, 0, 1, 30, false, 1)));
        s.monsters.add(() -> mage("Owl Duke", 26, 2, 5, 13, 24, new Color(160, 130, 90))
                .with(new EnemyMove.Restore("Moonlit Chorus", 0.15, 3, 1),
                      new EnemyMove.Afflict("Hypnotic Gaze", 0, true, EnemyMove.Ailment.SILENCE, 0, 2, 50, false, 1), new EnemyMove.Sweep("Thunder Feathers", 0.7, true, 2)));
        s.monsters.add(() -> phys("Gryphon", 37, 3, 3, 13, 26, new Color(200, 170, 110))
                .with(new EnemyMove.Strike("Dive Talon", 1, false, 2, 2), new EnemyMove.Afflict("Gale Wings", 0.6, false, EnemyMove.Ailment.BLIND, 0, 2, 60, true, 1),
                      new EnemyMove.Charge("Sky Dive", 2, false, 6)));
        s.monsters.add(() -> phys("Venom Chimera", 50, 4, 3, 13, 36, new Color(120, 160, 80))
                .with(new EnemyMove.Venom("Venom Fang", 1.2, false, 4, 4, 2), new EnemyMove.Afflict("Toxic Breath", 0.6, true, EnemyMove.Ailment.POISON, 3, 6, 100, true, 1)));
        s.monsters.add(() -> mage("Miguel", 30, 2, 6, 17, 32, new Color(120, 120, 200))
                .with(new EnemyMove.Afflict("Mystic Seal", 1, true, EnemyMove.Ailment.CURSE, 0, 3, 89, false, 1), new EnemyMove.Drain("Soul Siphon", 1.5, true, 2)));
        s.monsters.add(() -> phys("Turtle General", 40, 5, 5, 19, 70, new Color(90, 130, 90))
                .with(new EnemyMove.Rally("War Drums", 5, 2, 2), new EnemyMove.Afflict("Shell Quake", 0.9, false, EnemyMove.Ailment.BREAK, 3, 2, 70, true, 1),
                      new EnemyMove.Guard("Iron Shell", 5, 2, true, 1)));
        s.setBoss("Lord Of Mysterious", () -> new Enemy.Boss("Lord Of Mysterious", 250, 5, 4, 16, 14, 270, new Color(90, 120, 60))
                .with(new EnemyMove.Restore("Mysterious Renewal", 0.15, 0.10, 4, 1),
                      new EnemyMove.Summon("Bear Fruit", () -> mage("Red Pitaya", 30, 3, 3, 14, 6, new Color(230, 70, 110))
                          .with(new EnemyMove.Afflict("Thorn Spray", 0.9, true, EnemyMove.Ailment.POISON, 3, 3, 70, true, 2)), 3, 1),
                      new EnemyMove.Afflict("Venom Prison", 1, false, EnemyMove.Ailment.STUN, 0, 1, 60, false, 1),
                      new EnemyMove.Afflict("Viper Storm", 1, true, EnemyMove.Ailment.POISON, 8, 3, 100, true, 2),
                      new EnemyMove.Sweep("Split Barrage", 1, false, 1), new EnemyMove.Mend("Healing Acidic", 0.3, 1),
                      new EnemyMove.Charge("Insane Drive", 2, false, 1), new EnemyMove.FpDrain("No more soul", 1, 1, true, 1)));
        s.layout(new String[]{"X", "M", "M E", "M M", "E M", "S M", "M E M", "R", "M M", "E M", "M M", "S", "M E", "R M", "E", "B"}, 66, 24, pool(4,
            new Event.Trap("Strangling Vines", "Thick vines twist across the path, moving on their own.", 9),
            new Event.Chest("Druid's Cache", "A mossy chest is hidden in the roots of a giant tree.", 60),
            new Event.Shrine("Primal Spring", "Water older than the world wells up between the stones.", 0.25),
            new Event.Ambush("Beast Stampede", "The ground shakes - a herd of beasts charges straight at you!")));
        s.placeSpecial(new Event.Choice("Yato, the Kirin Hunter",
            "Yato crouches by a scorched ring of grass, tracing claw marks burned into the earth. Thunder rolls somewhere above the canopy, though the sky is clear.\n\"It's here. The Divine Kirin. I've hunted it across half the world... but it always sees me coming. I need to know how it moves.\"",
            new Event.Option("Give Noir Corne's Personal Notes", "The thunder answers. Losing here means the end - and the forest's other secret may stay hidden.", 0, Items.NOIR_NOTES, (st, r) -> Event.Result.duel(
                "Yato opens the notebook and goes very still. \"Noir... you old fool. You actually found its trail.\"\nShe reads fast, then looks up as the air begins to crackle. \"It's coming - right now. Fight with me!\"\n\nLightning splits the clearing. The Divine Kirin descends.",
                () -> new ArrayList<>(Collections.singletonList(divineKirin())), Music.KIRIN, Stage::kirinReward)).onlyIf(st -> !st.partyFull() && !st.hasHero("Yato")),
            new Event.Option("Ask about the Kirin", "Just talk.", (st, r) -> Event.Result.talk(
                "\"A divine beast made of storm. Nobody who chased it ever wrote down how it fights - except maybe one man, a hunter named Noir. He vanished in a cursed wood years ago.\"")),
            Event.leave("Wish her luck")).withNpc("Yato.png").withBg("bg/Primal Forest.png"));
        return s;
    }
}
