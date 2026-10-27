import java.util.*;

/**
 * Abstract map event. Every event offers options; each subclass decides its own
 * options and what happens (Polymorphism). Simple events reuse the same classes
 * with different numbers, story events use Choice with small lambdas.
 */
public abstract class Event {
    protected static final Random RNG = new Random();

    /** What happened after a choice. fight = start a battle, elite = the battle is against an elite. */
    public static final class Result {
        public final String text; public final boolean fight, elite;
        /** true = just talking: the dialog goes back to the choices afterwards (the node is NOT finished). */
        public boolean again;
        /** Special battle (e.g. Yato): its own enemies, music and a reward that runs after a win. null = normal. */
        public java.util.function.Supplier<List<Enemy>> foes;
        public String music;
        public java.util.function.BiConsumer<java.awt.Component, GameState> onWin;
        private Result(String text, boolean fight, boolean elite) { this.text = text; this.fight = fight; this.elite = elite; }
        public static Result text(String t) { return new Result(t, false, false); }
        /** A question/answer: show the text, then return to the same choices. */
        public static Result talk(String t) { Result r = new Result(t, false, false); r.again = true; return r; }
        public static Result fight(String t) { return new Result(t, true, false); }
        public static Result elite(String t) { return new Result(t, true, true); }
        /** A boss-style battle against custom enemies, with its own music and reward. */
        public static Result duel(String t, java.util.function.Supplier<List<Enemy>> foes, String music,
                                  java.util.function.BiConsumer<java.awt.Component, GameState> onWin) {
            Result r = new Result(t, true, false); r.foes = foes; r.music = music; r.onWin = onWin; return r;
        }
    }
    public interface Effect { Result apply(GameState s, Random r); }

    /** One button in an event. cost = Mora needed; needs = item needed. */
    public static final class Option {
        public final String label, hint; public final int cost; public final Item needs; public final Effect effect;
        private java.util.function.Predicate<GameState> condition = s -> true;
        public Option(String label, String hint, Effect effect) { this(label, hint, 0, null, effect); }
        public Option(String label, String hint, int cost, Item needs, Effect effect) {
            this.label = label; this.hint = hint; this.cost = cost; this.needs = needs; this.effect = effect;
        }
        /** Extra requirement, e.g. "doesn't own the map yet". */
        public Option onlyIf(java.util.function.Predicate<GameState> c) { condition = c; return this; }
        public boolean available(GameState s) { return s.getMora() >= cost && (needs == null || s.getInventory().count(needs) > 0) && condition.test(s); }
    }

    private final String title, intro;
    private String npc;                       // picture in npc/ (null = a random resident)
    protected Event(String title, String intro) { this.title = title; this.intro = intro; }
    private String bg;                        // fallback background (path); bg/Event/<title>.png wins if it exists
    private boolean special;                  // special node: uses a background picture
    public Event withNpc(String file) { npc = file; return this; }
    public String getNpc() { return npc; }
    /** Special node with a background picture: bg/Event/<title>.png, or this fallback file. */
    public Event withBg(String fallback) { bg = fallback; special = true; return this; }
    public boolean hasBg() { return special; }
    public String getBg() { return bg; }
    public String getTitle() { return title; }
    public String getIntro() { return intro; }
    public abstract List<Option> getOptions();

    public Result choose(int index, GameState s) {
        Option o = getOptions().get(index);
        if (!o.available(s)) return Result.talk("You can't do that right now.");
        s.loseMora(o.cost);
        if (o.needs != null) s.getInventory().remove(o.needs);
        return o.effect.apply(s, RNG);
    }

    protected static Option leave(String label) { return new Option(label, "You keep walking.", (s, r) -> Result.text("You move on.")); }
    protected static String tonicFor(GameState s, Random r) {
        Item t = Items.randomTonic(r);
        Hero h = s.randomHero(r);
        return t.getName() + "! " + t.use(h, null, s.getParty(), Collections.emptyList());
    }

    //  story events
    public static class Choice extends Event {
        private final List<Option> options;
        public Choice(String title, String intro, Option... options) {
            super(title, intro);
            if (options.length == 0) throw new IllegalArgumentException("An event needs at least one option.");
            this.options = Collections.unmodifiableList(Arrays.asList(options));
        }
        @Override public List<Option> getOptions() { return options; }
    }

    //  reusable obstacles / rewards
    /** Obstacle: walk through and get hurt, or try to disarm it. */
    public static class Trap extends Event {
        private final int dmg;
        public Trap(String t, String i, int dmg) { super(t, i); this.dmg = dmg; }
        @Override public List<Option> getOptions() {
            return Arrays.asList(
                new Option("Push through", "Grit your teeth and go.", (s, r) -> {
                    s.getHero().loseHpKeep1(5 + dmg);
                    return Result.text(s.getHero().getName() + " loses " + (5 + dmg) + " HP.");
                }),
                new Option("Disarm it carefully", "Steady hands... hopefully.", (s, r) -> {
                    if (r.nextBoolean()) { s.addMora(15 + dmg); return Result.text("Click. Disarmed! You salvage " + (15 + dmg) + " Mora of parts."); }
                    return Result.text("It goes off in your hands! " + s.hurtAll(5 + dmg));
                }));
        }
    }

    /** Reward: maybe a trapped chest. */
    public static class Chest extends Event {
        private final int mora;
        public Chest(String t, String i, int mora) { super(t, i); this.mora = mora; }
        @Override public List<Option> getOptions() {
            return Arrays.asList(
                new Option("Open it", "It's just a chest. Right?", (s, r) -> {
                    int roll = r.nextInt(10);
                    if (roll < 6) { s.addMora(mora); s.getInventory().add(Items.POTION, 1); return Result.text("You get " + mora + " Mora and a Potion!"); }
                    if (roll < 8) { s.addMora(mora / 2); s.getInventory().add(Items.randomSpecial(r), 1); return Result.text("You get " + mora / 2 + " Mora and a rare item!"); }
                    return Result.fight("The chest grows teeth! It was a trap - monsters attack!");
                }),
                leave("Leave it alone"));
        }
    }

    /** Heal: free small heal or pay for more. */
    public static class Shrine extends Event {
        private final double pct;
        public Shrine(String t, String i, double pct) { super(t, i); this.pct = pct; }
        @Override public List<Option> getOptions() {
            return Arrays.asList(
                new Option("Rest a moment", "Catch your breath.", (s, r) -> Result.text(s.healAll(pct))),
                new Option("Offer 40 Mora", "Gods like gold.", 40, null, (s, r) -> Result.text("The water glows brighter. " + s.healAll(pct * 2))),
                new Option("Bottle the water", "For the road.", (s, r) -> { s.getInventory().add(Items.POTION, 1); return Result.text("You fill a bottle. +1 Potion."); }));
        }
    }

    /** Obstacle: lose Mora unless you catch the thief. */
    public static class Thief extends Event {
        private final int mora;
        public Thief(String t, String i, int mora) { super(t, i); this.mora = mora; }
        @Override public List<Option> getOptions() {
            return Arrays.asList(
                new Option("Chase it!", "It's fast. Are you faster?", (s, r) -> {
                    if (r.nextInt(10) < 6) { s.addMora(20); return Result.text("You catch the thief and take back your coins, plus 20 Mora for your trouble!"); }
                    int m = Math.min(mora, s.getMora()); s.loseMora(m);
                    return Result.text("It escapes and you trip. Lose " + m + " Mora. " + s.hurtAll(3));
                }),
                new Option("Let it go", "Some coins aren't worth the run.", (s, r) -> {
                    int m = Math.min(mora, s.getMora()); s.loseMora(m); return Result.text("You lose " + m + " Mora.");
                }));
        }
    }

    /** Obstacle: fight or try to run. */
    public static class Ambush extends Event {
        public Ambush(String t, String i) { super(t, i); }
        @Override public List<Option> getOptions() {
            return Arrays.asList(
                new Option("Fight!", "Weapons out.", (s, r) -> Result.fight("Weapons out!")),
                new Option("Run away", "Legs, don't fail me now.", (s, r) -> {
                    if (r.nextBoolean()) { s.loseMora(10); return Result.text("You escape, dropping some coins on the way."); }
                    return Result.fight("Too slow - they catch you!");
                }));
        }
    }

    // ================================================================ events used in every stage
    public static List<Event> common(int n) {
        List<Event> l = new ArrayList<>();
        l.add(new Choice("Wandering Merchant",
            "A hooded merchant opens a battered box. \"Mystery goods! Could be treasure, could be junk. Feeling lucky?\"",
            new Option("Buy a mystery box", "Could be anything. Anything.", 20 + 5 * n, null, (s, r) -> {
                int roll = r.nextInt(100);
                if (roll < 15) return Result.text("You open it... rocks. The merchant is already gone.");
                if (roll < 45) { s.getInventory().add(Items.HI_POTION, 1); return Result.text("A Hi-Potion!"); }
                if (roll < 75) { Item it = Items.randomSpecial(r); s.getInventory().add(it, 1); return Result.text("A rare " + it.getName() + "!"); }
                return Result.text(tonicFor(s, r));
            }),
            new Option("Haggle", "Everything has a price. Even free.", (s, r) -> {
                if (r.nextBoolean()) { s.getInventory().add(Items.POTION, 1); return Result.text("\"Fine, fine, take this and go.\" +1 Potion."); }
                return Result.text("\"Bah! No deals for cheapskates.\" The merchant walks away.");
            }),
            leave("Walk away")));
        l.add(new Choice("Goblin Gambler",
            "A goblin flips a gold coin on a barrel. \"Heads you win double, tails I keep it! Fair game, yes?\"",
            new Option("Bet 20 Mora", "Heads or tails?", 20, null, (s, r) -> {
                if (r.nextBoolean()) { s.addMora(40); return Result.text("Heads! You win 40 Mora."); }
                return Result.text("Tails. The goblin cackles.");
            }),
            new Option("Bet 50 Mora", "Go big. The coin looks... odd.", 50, null, (s, r) -> {
                if (r.nextInt(100) < 45) { s.addMora(110); return Result.text("HEADS! The goblin faints. +110 Mora."); }
                return Result.text("Tails... was that coin double-sided?");
            }),
            new Option("Accuse him of cheating", "Goblins don't like being called liars.", (s, r) -> {
                if (r.nextBoolean()) { s.addMora(25); return Result.text("He panics and throws 25 Mora at you to keep quiet."); }
                return Result.fight("\"You calling ME a cheat?!\" His friends jump out!");
            })));
        l.add(new Choice("Blood Altar",
            "A black stone altar hums with power. Carved words read: \"Strength for blood. Life for gold.\"",
            new Option("Offer blood", "Strength for blood.", (s, r) -> {
                Hero lead = s.getHero(); lead.loseHpKeep1((int) Math.round(lead.getMaxHp() * 0.35));
                Hero h = s.randomHero(r);
                Hero.Stat st = r.nextBoolean() ? Hero.Stat.ATK : Hero.Stat.MATK;
                h.upgrade(st); h.upgrade(st);
                return Result.text(lead.getName() + " bleeds on the altar. " + h.getName() + "'s " + st + " +2!");
            }),
            new Option("Offer 40 Mora", "Life for gold.", 40, null, (s, r) -> {
                for (Hero h : s.getParty()) h.growMaxHp(4);
                return Result.text("The gold melts into light. Everyone's Max HP +4.");
            }),
            leave("Back away slowly")));
        l.add(new Choice("Wounded Traveler",
            "A traveler leans against a tree, clutching a bleeding arm. \"Please... do you have any medicine?\"",
            new Option("Give a Potion", "He really needs it.", 0, Items.POTION, (s, r) -> {
                s.addMora(30 + 5 * n);
                if (r.nextBoolean()) { s.getInventory().add(Items.ANTIDOTE, 1); return Result.text("\"Thank you!\" He gives you " + (30 + 5 * n) + " Mora and an Antidote."); }
                return Result.text("\"Thank you!\" He gives you " + (30 + 5 * n) + " Mora.");
            }),
            new Option("Rob him", "He looks rich. And alone... probably.", (s, r) -> {
                s.addMora(25);
                if (r.nextBoolean()) return Result.fight("You grab 25 Mora... and his bodyguards appear!");
                return Result.text("You take 25 Mora and leave. Not very heroic.");
            }),
            leave("Ignore him")));
        l.add(new Choice("Strange Fountain",
            "Water of shifting colors bubbles out of the ground. It smells sweet... maybe too sweet.",
            new Option("Drink", "Sweet. Maybe too sweet.", (s, r) -> {
                int roll = r.nextInt(10);
                if (roll < 4) return Result.text("Refreshing! " + s.healAll(0.4));
                if (roll < 7) return Result.text("Ugh, it's toxic! " + s.hurtAll(4 + n));
                for (Hero h : s.getParty()) h.growMaxHp(3);
                return Result.text("You feel tougher. Everyone's Max HP +3!");
            }),
            new Option("Fill a bottle", "Save it for later.", (s, r) -> { s.getInventory().add(Items.POTION, 1); return Result.text("+1 Potion."); }),
            leave("Don't touch it")));
        l.add(new Choice("Champion's Challenge",
            "A scarred warrior blocks the path. \"Prove your worth! Beat my strongest beast and I'll pay you well.\"",
            new Option("Accept the duel", "His strongest beast. He pays well.", (s, r) -> Result.elite("\"Ha! Release the beast!\"")),
            leave("Decline politely")));
        l.add(new Choice("Old Swordmaster",
            "An old swordmaster sits on a rock. \"I can teach you something... for a price. Or we could spar.\"",
            new Option("Train (" + (30 + 5 * n) + " Mora)", "Old masters know old tricks.", 30 + 5 * n, null, (s, r) -> {
                Hero h = s.getHero(); h.upgrade(Hero.Stat.ATK); h.upgrade(Hero.Stat.MATK);
                return Result.text(h.getName() + " learns a new technique! ATK +1, M.ATK +1.");
            }),
            new Option("Spar for free", "It will hurt. It might teach.", (s, r) -> {
                Hero h = s.getHero(); h.loseHpKeep1(10);
                if (r.nextBoolean()) { h.upgrade(Hero.Stat.DEF); return Result.text("Bruised but wiser. " + h.getName() + " DEF +1."); }
                return Result.text("He beats you without even standing up. -10 HP.");
            }),
            leave("Leave")));
        return l;
    }
}
