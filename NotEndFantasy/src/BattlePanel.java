import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Turn-based battle screen. The rules live in Battle; enemy turns run on a separate Thread. */
public class BattlePanel extends JPanel {
    private final GameState gs;
    private final Battle battle;
    private final List<Hero> party;
    private final List<Enemy> enemies;
    private final Stage stage;
    private final boolean boss;
    private final Consumer<Boolean> done;

    private final List<Hero> order = new ArrayList<>();   // heroes living at the start of this round
    private final Set<Hero> acted = Collections.newSetFromMap(new IdentityHashMap<>());   // already acted this round
    private boolean heroPhase;                             // waiting for the player's command
    private Hero hero;

    private final JTextArea log = new JTextArea();
    private final CardLayout menus = new CardLayout();
    private final JPanel menuPanel = new JPanel(menus);
    private final JLabel turnLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel skillList = new JPanel(new GridLayout(0, 1, 4, 4));
    private final JPanel itemList = new JPanel(new GridLayout(0, 1, 4, 4));
    private final JPanel allyPanel = new JPanel(new GridLayout(0, 1, 6, 6));
    private JButton attackBtn;
    private final Arena arena = new Arena();
    private final PartyPanel partyPanel = new PartyPanel();
    private final Map<Entity, Long> shake = new IdentityHashMap<>();
    private final Map<Entity, Rectangle> rects = new IdentityHashMap<>();
    private final List<Floater> floaters = new ArrayList<>();
    private Consumer<Enemy> pending;
    private boolean targeting, over;
    private Enemy hover;

    /** A skill effect picture that grows and fades over a fighter. */
    private static final class Effect {
        final Entity who; final Image img; final long start = System.currentTimeMillis();
        Effect(Entity who, Image img) { this.who = who; this.img = img; }
    }
    private final List<Effect> effects = new ArrayList<>();

    // tooltips: {Rectangle area, String text} collected while painting
    private final List<Object[]> arenaTips = new ArrayList<>(), partyTips = new ArrayList<>();
    private Map<Entity, Set<String>> statusBefore = new IdentityHashMap<>();

    private static Set<String> statusKinds(Entity e) {
        Set<String> s = new LinkedHashSet<>();
        for (String l : e.statusLabels()) s.add(l.split(" ")[0]);
        return s;
    }
    private static String tipHtml(String title, String text) {
        return "<html><b>" + title + "</b><br>" + text.replace("<", "&lt;") + "</html>";
    }
    private static String findTip(List<Object[]> tips, Point p) {
        for (int i = tips.size() - 1; i >= 0; i--) if (((Rectangle) tips.get(i)[0]).contains(p)) return (String) tips.get(i)[1];
        return null;
    }

    private static final class Floater {
        final Entity who; final String text; final Color color; final long start;
        int yOff; boolean small;
        Floater(Entity who, String text, Color color) { this.who = who; this.text = text; this.color = color; this.start = System.currentTimeMillis(); }
        Floater(Entity who, String text, Color color, int yOff) { this(who, text, color); this.yOff = yOff; this.small = true; }
    }

    public BattlePanel(GameState gs, Stage stage, List<Enemy> enemies, boolean boss, Consumer<Boolean> done) {
        this.gs = gs; this.party = gs.getParty(); this.stage = stage;
        this.enemies = enemies; this.boss = boss; this.done = done;
        this.battle = new Battle(party, enemies);
        setLayout(new BorderLayout());
        add(arena, BorderLayout.CENTER);
        add(buildSouth(), BorderLayout.SOUTH);
        StringBuilder sb = new StringBuilder(boss ? "A powerful enemy blocks the way: " : "Enemies appear: ");
        for (int i = 0; i < enemies.size(); i++) sb.append(i > 0 ? ", " : "").append(enemies.get(i).getName());
        append(sb.toString());
        append("Tip: the bubble above each monster shows what it will do this round.");
        startRound();
    }

    //  UI building
    private JPanel buildSouth() {
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBackground(Ui.BG);
        south.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, stage.getAccent()),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        south.setPreferredSize(new Dimension(1000, 214));

        partyPanel.setPreferredSize(new Dimension(250, 196));
        south.add(partyPanel, BorderLayout.WEST);

        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
        log.setBackground(Ui.PANEL); log.setForeground(Ui.TEXT); log.setFont(Ui.BODY.deriveFont(13f));
        log.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane lsp = new JScrollPane(log); lsp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        south.add(lsp, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 6)); right.setOpaque(false);
        right.setPreferredSize(new Dimension(350, 196));
        turnLabel.setFont(Ui.H2); turnLabel.setForeground(Ui.GOLD);
        right.add(turnLabel, BorderLayout.NORTH);

        menuPanel.setOpaque(false);
        JPanel cmd = new JPanel(new GridLayout(2, 2, 6, 6));
        cmd.setOpaque(false);
        attackBtn = bigButton("Attack", "Basic hit with ATK", e -> askTarget(this::attack));
        cmd.add(attackBtn);
        cmd.add(bigButton("Skill", "Use a special move", e -> openSkills()));
        cmd.add(bigButton("Defend", "DEF/M.DEF +4 this round, +1 FP", e -> defend()));
        cmd.add(bigButton("Item", "Use something from the bag", e -> openItems()));
        menuPanel.add(cmd, "cmd");
        menuPanel.add(subMenu(skillList), "skills");
        menuPanel.add(subMenu(itemList), "items");

        JPanel target = new JPanel(new GridLayout(0, 1, 6, 6));
        target.setOpaque(false);
        JLabel tl = new JLabel("Click an enemy to select the target.", SwingConstants.CENTER);
        tl.setFont(Ui.BOLD); tl.setForeground(Ui.GOLD);
        target.add(tl);
        target.add(Ui.button("Cancel", e -> cancelTarget()));
        menuPanel.add(target, "target");

        allyPanel.setOpaque(false);
        menuPanel.add(allyPanel, "ally");

        JLabel wait = new JLabel("Monsters are acting...", SwingConstants.CENTER);
        wait.setFont(Ui.H1); wait.setForeground(Ui.DIM);
        menuPanel.add(wait, "wait");
        right.add(menuPanel, BorderLayout.CENTER);
        south.add(right, BorderLayout.EAST);
        return south;
    }

    private JButton bigButton(String name, String sub, ActionListener a) {
        JButton b = Ui.button("<html><center><b style='font-size:13px'>" + name + "</b><br><span style='font-size:9px;color:#b4b8d4'>" + sub + "</span></center></html>", a);
        return b;
    }

    private JPanel subMenu(JPanel list) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        list.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(14);
        p.add(sp, BorderLayout.CENTER);
        p.add(Ui.button("Back", e -> menus.show(menuPanel, "cmd")), BorderLayout.SOUTH);
        return p;
    }

    private void append(String s) {
        if (s == null || s.trim().isEmpty()) return;
        log.append(s.trim() + "\n"); log.setCaretPosition(log.getDocument().getLength());
    }

    // player commands
    private void attack(Enemy t) { perform(() -> battle.attack(hero, t)); }
    private void defend() { perform(() -> battle.defend(hero)); }

    private void openSkills() {
        skillList.removeAll();
        for (Skill s : hero.getSkills()) {
            boolean ok = battle.canUse(hero, s);
            String state = !s.isReady() ? "ready in " + s.getCooldownLeft() : battle.getFp() < s.getFpCost() ? "need " + s.getFpCost() + " FP" : s.targetLabel();
            JButton b = Ui.button("<html><b>" + s.getName() + "</b> &nbsp;<span style='color:#7fd8ff'>" + s.getFpCost() + " FP</span> &nbsp;<span style='color:" + (ok ? "#f4c440" : "#888") + "'>[" + state + "]</span><br><span style='font-size:9px'>"
                    + s.getDesc() + " (CD " + s.getCooldown() + ")</span></html>", e -> useSkill(s));
            b.setEnabled(ok);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            skillList.add(b);
        }
        skillList.revalidate(); skillList.repaint();
        menus.show(menuPanel, "skills");
    }

    private void useSkill(Skill s) {
        if (s.needsTwoTargets()) askTarget(t1 -> askTarget(t2 -> castSkill(s, t1, t2, null), "Pick the 2nd target (" + s.getName() + ")"), "Pick the 1st target (" + s.getName() + ")");
        else if (s.needsTarget() && s.needsAllyToo()) askTarget(t -> askAlly(battle.allyChoices(hero, s), a -> castSkill(s, t, null, a)));
        else if (s.needsTarget()) askTarget(t -> castSkill(s, t, null));
        else if (s.needsAlly()) askAlly(battle.allyChoices(hero, s), a -> castSkill(s, null, a));
        else castSkill(s, null, null);
    }

    /** Uses the skill and plays its effect picture on everyone it touched. */
    private void castSkill(Skill s, Enemy t, Hero ally) { castSkill(s, t, null, ally); }
    private void castSkill(Skill s, Enemy t, Enemy t2, Hero ally) {
        Hero user = hero;
        Map<Entity, Integer> before = snapshot();
        perform(() -> battle.skill(user, s, t, t2, ally));
        Image img = Assets.fx(s.getFx());
        if (img == null) return;
        Set<Entity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<Entity, Integer> en : before.entrySet()) if (en.getKey().getHp() != en.getValue()) hit.add(en.getKey());
        if (t != null) hit.add(t);
        switch (s.getTarget()) {
            case SELF: hit.add(user); break;
            case PICK_ALLY: hit.add(ally != null ? ally : user); break;
            case PARTY: for (Hero h : party) if (h.isAlive()) hit.add(h); break;
            case ALL_ENEMIES: for (Enemy e : enemies) if (e.isAlive()) hit.add(e); break;
            default: break;
        }
        for (Entity e : hit) effects.add(new Effect(e, img));
    }

    private void openItems() {
        itemList.removeAll();
        if (gs.getInventory().entries().isEmpty()) {
            JLabel l = new JLabel("No items. Visit a shop!", SwingConstants.CENTER);
            l.setForeground(Ui.DIM); l.setFont(Ui.BODY);
            itemList.add(l);
        }
        for (Map.Entry<Item, Integer> en : gs.getInventory().entries().entrySet()) {
            Item it = en.getKey();
            JButton b = Ui.button("<html><b>" + it.getName() + "</b> x" + en.getValue() + "<br><span style='font-size:9px'>" + it.getDesc() + "</span></html>", e -> useItem(it));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setEnabled(it.canUse(party));
            itemList.add(b);
        }
        itemList.revalidate(); itemList.repaint();
        menus.show(menuPanel, "items");
    }

    private void useItem(Item it) {
        if (it.needsTarget()) askTarget(t -> perform(() -> battle.item(hero, it, t, gs.getInventory())));
        else perform(() -> battle.item(hero, it, null, gs.getInventory()));
    }

    //  targeting
    private String targetPrompt = "Select a target: click an enemy";
    private void askTarget(Consumer<Enemy> action) { askTarget(action, "Select a target: click an enemy"); }
    private void askTarget(Consumer<Enemy> action, String prompt) {
        List<Enemy> alive = battle.livingEnemies();
        if (alive.size() == 1) { action.accept(alive.get(0)); return; }   // only one choice
        pending = action; targeting = true; targetPrompt = prompt;
        menus.show(menuPanel, "target");
    }
    /** Choose an ally with buttons (only asked when there is more than one choice). */
    private void askAlly(List<Hero> choices, Consumer<Hero> action) {
        if (choices.isEmpty()) return;
        if (choices.size() == 1) { action.accept(choices.get(0)); return; }
        allyPanel.removeAll();
        JLabel l = new JLabel("Choose an ally:", SwingConstants.CENTER);
        l.setFont(Ui.BOLD); l.setForeground(Ui.GOLD);
        allyPanel.add(l);
        for (Hero h : choices)
            allyPanel.add(Ui.button(h.getName() + "   (" + h.getHp() + "/" + h.getMaxHp() + " HP)", e -> action.accept(h)));
        allyPanel.add(Ui.button("Cancel", e -> menus.show(menuPanel, "cmd")));
        allyPanel.revalidate(); allyPanel.repaint();
        menus.show(menuPanel, "ally");
    }
    private void cancelTarget() { targeting = false; pending = null; menus.show(menuPanel, "cmd"); }

    //  turn flow
    private void startRound() {
        order.clear(); order.addAll(battle.livingHeroes());
        acted.clear();
        nextHero();
    }
    /** Heroes who can still act this round. */
    private List<Hero> waiting() {
        List<Hero> l = new ArrayList<>();
        for (Hero h : order) if (h.isAlive() && !acted.contains(h)) l.add(h);
        return l;
    }
    /** Picks the first hero who hasn't acted yet (stunned heroes lose their turn). */
    private void nextHero() {
        heroPhase = false;
        for (Hero h : waiting()) {
            if (h.consumeStun()) { append(h.getName() + " is stunned and loses this turn."); acted.add(h); continue; }
            select(h);
            return;
        }
        enemyTurn();
    }
    private void select(Hero h) {
        hero = h;
        heroPhase = true;
        targeting = false; pending = null;
        attackBtn.setText("<html><center><b style='font-size:13px'>" + hero.basicName() + "</b><br><span style='font-size:9px;color:#b4b8d4'>" + hero.basicDesc() + "</span></center></html>");
        int left = waiting().size();
        turnLabel.setText(hero.getName() + "'s turn" + (left > 1 ? "  (" + left + " left)" : "") + "   FP " + battle.getFp() + "/" + GameState.MAX_FP);
        turnLabel.setToolTipText(left > 1 ? "Click another hero (card or character) to let them act first." : null);
        menus.show(menuPanel, "cmd");
        partyPanel.repaint();
    }
    /** Player clicked a hero: let that hero act now instead (if they haven't acted this round). */
    private void switchTo(Hero h) {
        if (over || !heroPhase || h == hero || !h.isAlive() || acted.contains(h) || !order.contains(h)) return;
        if (h.isStunned()) {
            h.consumeStun(); acted.add(h);
            append(h.getName() + " is stunned and loses this turn.");
            if (waiting().isEmpty()) { heroPhase = false; enemyTurn(); } else select(hero);
            return;
        }
        select(h);
    }

    private Map<Entity, Integer> snapshot() {
        Map<Entity, Integer> m = new IdentityHashMap<>();
        statusBefore = new IdentityHashMap<>();
        for (Hero member : party) { m.put(member, member.getHp()); statusBefore.put(member, statusKinds(member)); }
        for (Enemy e : enemies) { m.put(e, e.getHp()); statusBefore.put(e, statusKinds(e)); }
        return m;
    }
    /** Shakes whoever lost HP and spawns floating numbers. */
    private void flashChanged(Map<Entity, Integer> before) {
        long until = System.currentTimeMillis() + 300;
        for (Hero h : party) before.putIfAbsent(h, h.getHp());
        for (Enemy e : enemies) before.putIfAbsent(e, e.getMaxHp());   // summoned this turn
        for (Map.Entry<Entity, Integer> en : before.entrySet()) {
            int diff = en.getKey().getHp() - en.getValue();
            if (diff < 0) { shake.put(en.getKey(), until); floaters.add(new Floater(en.getKey(), String.valueOf(diff), new Color(255, 90, 80))); }
            else if (diff > 0) floaters.add(new Floater(en.getKey(), "+" + diff, Ui.GREEN));
        }
        // status indicator: every newly applied buff/debuff pops up over the fighter
        List<Entity> all = new ArrayList<>(party); all.addAll(enemies);
        for (Entity e : all) {
            if (!e.isAlive()) continue;
            Set<String> old = statusBefore.getOrDefault(e, Collections.emptySet());
            int n = 0;
            for (String label : e.statusLabels()) {
                String kind = label.split(" ")[0];
                if (old.contains(kind) || kind.equals("BOSS") || kind.equals("ELITE")) continue;
                floaters.add(new Floater(e, label, Ui.statusColor(label).brighter(), 34 + 18 * n++));
            }
        }
        partyPanel.repaint();
    }

    private void perform(Supplier<String> action) {
        if (over) return;
        heroPhase = false;
        menus.show(menuPanel, "wait");
        Map<Entity, Integer> before = snapshot();
        append(action.get());
        flashChanged(before);
        if (battle.won()) { win(); return; }
        acted.add(hero);
        nextHero();
    }

    /** Effect picture of a monster move: on the heroes it hurt, or on the monster side for buffs/heals/charge. */
    private void enemyFx(Enemy user, EnemyMove m, Map<Entity, Integer> before) {
        if (m == null) return;
        Image img = Assets.fx(m.getFx());
        if (img == null) return;
        boolean support = m.getKind() == EnemyMove.Kind.BUFF || m.getKind() == EnemyMove.Kind.HEAL;
        boolean any = false;
        for (Map.Entry<Entity, Integer> en : before.entrySet()) {
            Entity who = en.getKey();
            boolean hurtHero = who instanceof Hero && who.getHp() < en.getValue();
            boolean healedFoe = support && who instanceof Enemy && who.getHp() > en.getValue();
            if (hurtHero || healedFoe) { effects.add(new Effect(who, img)); any = true; }
        }
        if (!any && (support || m.getKind() == EnemyMove.Kind.CHARGE || m.getKind() == EnemyMove.Kind.SUMMON)) effects.add(new Effect(user, img));
    }

    private void enemyTurn() {
        turnLabel.setText("Enemy turn");
        menus.show(menuPanel, "wait");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
                for (Enemy e : new ArrayList<>(enemies)) {
                    if (!e.isAlive()) continue;
                    Thread.sleep(650);
                    SwingUtilities.invokeAndWait(() -> {
                        Map<Entity, Integer> before = snapshot();
                        EnemyMove m1 = e.isStunned() ? null : e.getIntent(), m2 = e.isStunned() ? null : e.getIntent2();
                        append(battle.enemyAct(e));
                        flashChanged(before);
                        enemyFx(e, m1, before);
                        if (m2 != null && m2 != m1) enemyFx(e, m2, before);
                    });
                    if (battle.lost() || battle.won()) break;
                }
                Thread.sleep(400);
                SwingUtilities.invokeLater(this::endRound);
            } catch (Exception ex) {
                // window closed while waiting
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void endRound() {
        if (battle.lost()) { lose(); return; }
        if (battle.won()) { win(); return; }
        Map<Entity, Integer> before = snapshot();
        append(battle.endRound());
        flashChanged(before);
        if (battle.lost()) { lose(); return; }
        if (battle.won()) { win(); return; }
        append("--- Round " + battle.getRound() + " ---");
        startRound();
    }

    private void win() {
        if (over) return;
        over = true;
        int mora = battle.reward();
        String extra = "";
        if (Math.random() < 0.2) { gs.getInventory().add(Items.POTION, 1); extra = "\nDropped: Potion"; }
        gs.addMora(mora);
        battle.finish();
        append("Victory! +" + mora + " Mora");
        turnLabel.setText("Victory!");
        JOptionPane.showMessageDialog(this, "Victory!\n+" + mora + " Mora" + extra
                + (party.size() > 1 ? "\n(Fallen allies get back up with 1 HP.)" : ""), "Battle Won", JOptionPane.INFORMATION_MESSAGE);
        done.accept(true);
    }

    private void lose() {
        if (over) return;
        over = true;
        append("The party was defeated...");
        done.accept(false);
    }

    // party panel (bottom-left)
    private class PartyPanel extends JComponent {
        PartyPanel() {
            setToolTipText("");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int h = party.size() == 1 ? 120 : party.size() == 2 ? 94 : 64;
                    int i = e.getY() / h;
                    if (i >= 0 && i < party.size()) switchTo(party.get(i));
                }
            });
        }
        @Override public String getToolTipText(MouseEvent e) { return findTip(partyTips, e.getPoint()); }
        @Override protected void paintComponent(Graphics g0) {
            partyTips.clear();
            Graphics2D g = (Graphics2D) g0.create();
            Ui.antialias(g);
            int y = 0, h = party.size() == 1 ? 120 : party.size() == 2 ? 94 : 64;
            boolean compact = party.size() >= 3;          // 3 heroes: stats on one line, chips next to the name
            for (Hero m : party) {
                Ui.heroChip(g, m, 0, y, getWidth() - 2, h - 6);
                if (m == hero && !over && turnLabel.getText().contains("turn") && !turnLabel.getText().startsWith("Enemy")) {
                    g.setColor(Ui.GOLD); g.setStroke(new BasicStroke(2.5f)); g.drawRoundRect(0, y, getWidth() - 2, h - 6, 12, 12); g.setStroke(new BasicStroke(1f));
                }
                int sx = h - 6 + 8;
                if (m.isAlive() && !over && order.contains(m)) {
                    boolean done = acted.contains(m);
                    String tag = done ? "DONE" : m == hero && heroPhase ? "NOW" : heroPhase ? "READY" : "";
                    g.setFont(Ui.SMALL_B); g.setColor(done ? Ui.DIM : m == hero ? Ui.GOLD : Ui.GREEN);
                    g.drawString(tag, getWidth() - 8 - g.getFontMetrics().stringWidth(tag), y + 14);
                }
                g.setFont(Ui.SMALL); g.setColor(Ui.DIM);
                int cx = sx, cy = y + 48;
                if (compact) {
                    g.drawString("ATK " + m.getAtk() + " M.ATK " + m.getMatk() + " DEF " + m.getDef() + " M.DEF " + m.getMdef(), sx, y + 31);
                    g.setFont(Ui.BOLD); cx = sx + g.getFontMetrics().stringWidth(m.getName() + (m.isAlive() ? "" : "  KO")) + 6; cy = y + 6;
                } else {
                    g.drawString("ATK " + m.getAtk() + "   M.ATK " + m.getMatk(), sx, y + 31);
                    g.drawString("DEF " + m.getDef() + "   M.DEF " + m.getMdef(), sx, y + 44);
                }
                g.setFont(Ui.SMALL_B);
                for (String[] s : m.statusInfo()) {
                    int w = g.getFontMetrics().stringWidth(s[0]) + 8;
                    if (cx + w > getWidth()) break;
                    Color c = Ui.statusColor(s[0]);
                    g.setColor(c); g.fillRoundRect(cx, cy, w, 13, 6, 6);
                    g.setColor(Color.WHITE); g.drawString(s[0], cx + 4, cy + 10);
                    partyTips.add(new Object[]{new Rectangle(cx, cy, w, 13), tipHtml(s[0], s[1])});
                    cx += w + 3;
                }
                y += h;
            }
            g.dispose();
        }
    }

    // arena drawing + clicking
    private static Color intentColor(EnemyMove.Kind k) {
        switch (k) {
            case ATTACK: return new Color(210, 70, 70);
            case MAGIC: return new Color(150, 90, 220);
            case AOE: return new Color(235, 130, 40);
            case DEBUFF: return new Color(100, 170, 60);
            case BUFF: return new Color(70, 130, 220);
            case HEAL: return new Color(60, 180, 120);
            case CHARGE: return new Color(230, 180, 30);
            default: return new Color(120, 120, 140);
        }
    }
    private static String intentIcon(EnemyMove.Kind k) {
        switch (k) {
            case ATTACK: return "ATK";
            case MAGIC: return "MAG";
            case AOE: return "ALL";
            case DEBUFF: return "DEB";
            case BUFF: return "BUF";
            case HEAL: return "HEAL";
            case CHARGE: return "!!";
            default: return "SUM";
        }
    }

    private class Arena extends JPanel {
        private final javax.swing.Timer timer = new javax.swing.Timer(40, e -> repaint());

        Arena() {
            setPreferredSize(new Dimension(1000, 470));
            setToolTipText("");                                  // turn Swing tooltips on (text comes from getToolTipText)
            ToolTipManager.sharedInstance().setInitialDelay(120);
            ToolTipManager.sharedInstance().setDismissDelay(15000);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    hover = enemyAt(e.getPoint());
                    setCursor(Cursor.getPredefinedCursor(targeting && hover != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (!targeting) {                                   // click a hero to let them act first
                        for (Hero h : party) { Rectangle r = rects.get(h); if (r != null && r.contains(e.getPoint())) switchTo(h); }
                        return;
                    }
                    Enemy t = enemyAt(e.getPoint());
                    if (t == null) return;
                    Consumer<Enemy> c = pending;
                    targeting = false; pending = null;
                    c.accept(t);
                }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }
        @Override public void addNotify() { super.addNotify(); timer.start(); }
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

        private int ground() { return getHeight() - 92; }
        @Override public String getToolTipText(MouseEvent e) { return findTip(arenaTips, e.getPoint()); }

        /** Positions of living enemies, spread over the right side. */
        private Map<Enemy, Rectangle> enemyRects() {
            Map<Enemy, Rectangle> m = new LinkedHashMap<>();
            List<Enemy> alive = battle.livingEnemies();
            int n = alive.size();
            if (n == 0) return m;
            // up to 6 monsters: they shrink to fit, bosses are 1.6x wider
            int areaX = party.size() >= 3 ? 410 : n >= 4 ? 360 : 400, areaW = getWidth() - areaX - 14;
            double weights = 0;
            for (Enemy e : alive) weights += e.isBoss() ? 1.6 : 1.0;
            int unit = (int) Math.min(118, (areaW - 6 * (n + 1)) / weights);
            int total = 0;
            for (Enemy e : alive) total += e.isBoss() ? (int) (unit * 1.6) : unit;
            int gap = Math.max(6, (areaW - total) / (n + 1));
            int x = areaX + gap;
            for (Enemy e : alive) {
                int w = e.isBoss() ? (int) (unit * 1.6) : unit;
                int h = e.isBoss() ? Math.min(220, (int) (w * 1.16)) : (int) (w * 1.27);
                m.put(e, new Rectangle(x, ground() - h, w, h));
                x += w + gap;
            }
            return m;
        }
        private Enemy enemyAt(Point p) {
            for (Map.Entry<Enemy, Rectangle> en : enemyRects().entrySet())
                if (en.getValue().contains(p)) return en.getKey();
            return null;
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Ui.antialias(g);
            int w = getWidth(), h = getHeight(), gy = ground();
            Image bg = Assets.background(stage.getNumber());
            if (bg != null) Assets.cover(g, bg, 0, 0, w, h);
            else { g.setPaint(new GradientPaint(0, 0, boss ? new Color(70, 22, 32) : stage.getBg().brighter(), 0, h, Ui.BG)); g.fillRect(0, 0, w, h); }
            g.setColor(new Color(8, 10, 20, 100)); g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, gy, new Color(0, 0, 0, 60), 0, h, new Color(0, 0, 0, 190))); g.fillRect(0, gy, w, h - gy);
            g.setColor(stage.getAccent()); g.drawLine(0, gy, w, gy);

            // top banner
            g.setColor(new Color(10, 12, 24, 190)); g.fillRect(0, 0, w, 40);
            g.setFont(Ui.BOLD); g.setColor(stage.getAccent()); g.drawString((stage.isSecret() ? "SECRET STAGE" : "STAGE " + stage.getNumber()) + "  " + stage.getName(), 14, 26);
            Ui.center(g, boss ? "BOSS BATTLE" : "BATTLE", w / 2, 27, Ui.H1, boss ? Ui.RED : Ui.TEXT);
            g.setFont(Ui.BOLD); g.setColor(Ui.TEXT);
            drawFp(g, w / 2 + 110, 12);
            String rs = "Round " + battle.getRound() + "   Mora " + gs.getMora();
            g.drawString(rs, w - 14 - g.getFontMetrics().stringWidth(rs), 26);

            rects.clear();
            arenaTips.clear();
            for (int i = 0; i < party.size(); i++) {
                Hero member = party.get(i);
                Rectangle r = party.size() >= 3 ? new Rectangle(30 + i * 122, gy - 140, 110, 140)    // 3 heroes: tighter
                                                : new Rectangle(60 + i * 165, gy - 150, 120, 150);
                rects.put(member, r);
                if (!member.isAlive()) { Ui.center(g, member.getName() + " - KO", r.x + r.width / 2, gy - 20, Ui.BOLD, Ui.RED); continue; }
                boolean active = member == hero && !over && !turnLabel.getText().startsWith("Enemy");
                if (active) {
                    g.setColor(new Color(244, 196, 64, 60)); g.fillOval(r.x - 14, gy - 16, r.width + 28, 30);
                    Ui.center(g, "YOUR TURN", r.x + r.width / 2, r.y - 10, Ui.SMALL_B, Ui.GOLD);
                }
                drawFighter(g, member, r, false);
            }
            int idx = 0;
            Map<Enemy, Rectangle> ers = enemyRects();
            boolean compact = ers.size() >= 4;
            for (Map.Entry<Enemy, Rectangle> en : ers.entrySet()) {
                rects.put(en.getKey(), en.getValue());
                drawFighter(g, en.getKey(), en.getValue(), true);
                drawIntent(g, en.getKey(), en.getKey().getIntent(), en.getValue(), compact, idx, 0, "");
                if (en.getKey().getIntent2() != null && !en.getKey().isStunned())        // enraged boss: second move shown too
                    drawIntent(g, en.getKey(), en.getKey().getIntent2(), en.getValue(), compact, idx, 24, "2nd ");
                idx++;
            }
            drawEffects(g);
            drawFloaters(g);
            if (targeting) {
                g.setColor(new Color(10, 12, 24, 200)); g.fillRoundRect(w / 2 - 170, 48, 340, 28, 12, 12);
                Ui.center(g, targetPrompt, w / 2, 67, Ui.BOLD, Ui.GOLD);
            } else if (hover != null && hover.isAlive()) drawMoveInfo(g, hover);
            g.dispose();
        }

        private void drawIntent(Graphics2D g, Enemy e, EnemyMove m, Rectangle r, boolean compact, int idx, int up, String prefix) {
            if (m == null) return;
            boolean stunned = e.isStunned();
            String txt = stunned ? "Stunned - skips turn" : m.describe(e);
            if (compact) {                              // short form: just the numbers, full text on hover
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\\d+( x\\d+)?").matcher(txt);
                txt = stunned ? "zzz" : m.getKind() == EnemyMove.Kind.CHARGE ? "CHARGING" : mt.find() ? mt.group() : "";
            }
            Color c = stunned ? Ui.DIM : intentColor(m.getKind());
            g.setFont(Ui.SMALL_B);
            FontMetrics fm = g.getFontMetrics();
            String icon = stunned ? "STUN" : prefix + intentIcon(m.getKind());
            int iw = fm.stringWidth(icon) + 10, tw = txt.isEmpty() ? 0 : fm.stringWidth(txt) + 10;
            int bw = iw + tw, x = r.x + r.width / 2 - bw / 2, y = r.y - 30 - up - (compact && idx % 2 == 1 ? 22 : 0);
            boolean blink = !stunned && m.getKind() == EnemyMove.Kind.CHARGE && (System.currentTimeMillis() / 300) % 2 == 0;
            arenaTips.add(new Object[]{new Rectangle(x, y, bw, 20), tipHtml(e.getName() + (prefix.isEmpty() ? " - next move" : " - SECOND move this round"),
                    stunned ? "Stunned: this monster skips its turn." : m.explain(e))});
            g.setColor(new Color(10, 12, 24, 220)); g.fillRoundRect(x, y, bw, 20, 10, 10);
            g.setColor(blink ? Color.WHITE : c); g.fillRoundRect(x, y, iw, 20, 10, 10);
            g.drawRoundRect(x, y, bw, 20, 10, 10);
            g.setColor(blink ? Color.BLACK : Color.WHITE); g.drawString(icon, x + 5, y + 14);
            g.setColor(Ui.TEXT); g.drawString(txt, x + iw + 5, y + 14);
            g.setColor(new Color(10, 12, 24, 220));
            g.fillPolygon(new int[]{x + bw / 2 - 5, x + bw / 2 + 5, x + bw / 2}, new int[]{y + 20, y + 20, y + 26}, 3);
        }

        private void drawMoveInfo(Graphics2D g, Enemy e) {
            List<String> lines = new ArrayList<>();
            lines.add(e.getName() + (e instanceof Enemy.Boss && ((Enemy.Boss) e).isEnraged() ? "  (ENRAGED)" : ""));
            StringBuilder sb = new StringBuilder("Moves: Attack");
            for (EnemyMove m : e.getMoves()) sb.append(", ").append(m.getName().equals("Charging") ? m.describe(e).replace("Charging ", "").replace("!", "") : m.getName());
            lines.add(sb.toString());
            lines.add("DEF " + e.getDef() + "  M.DEF " + e.getMdef() + "  ATK " + e.getAtk() + "  M.ATK " + e.getMatk() + "   Reward " + e.getReward() + " Mora");
            lines.add(e.isStunned() ? "Next: STUNNED (skips its turn)" : e.getIntent() != null ? "Next: " + e.getIntent().describe(e)
                    + (e.getIntent2() != null ? "  then  " + e.getIntent2().describe(e) : "") : "");
            g.setFont(Ui.SMALL);
            int bw = 0;
            for (String l : lines) bw = Math.max(bw, g.getFontMetrics().stringWidth(l));
            bw += 20;
            int x = getWidth() - bw - 12, y = 48;
            g.setColor(new Color(10, 12, 24, 220)); g.fillRoundRect(x, y, bw, 74, 10, 10);
            g.setColor(e.getColor()); g.drawRoundRect(x, y, bw, 74, 10, 10);
            g.setFont(Ui.SMALL_B); g.setColor(Ui.GOLD); g.drawString(lines.get(0), x + 10, y + 16);
            g.setFont(Ui.SMALL); g.setColor(Ui.TEXT); g.drawString(lines.get(1), x + 10, y + 32);
            g.setColor(Ui.DIM); g.drawString(lines.get(2), x + 10, y + 48);
            g.setFont(Ui.SMALL_B); g.setColor(Ui.ORANGE); g.drawString(lines.get(3), x + 10, y + 64);
        }

        /** Fantasy Points: 5 shared crystals. */
        private void drawFp(Graphics2D g, int x, int y) {
            g.setFont(Ui.BOLD); g.setColor(new Color(127, 216, 255));
            g.drawString("FP", x, y + 14);
            for (int i = 0; i < GameState.MAX_FP; i++) {
                int cx = x + 26 + i * 18;
                Polygon gem = new Polygon(new int[]{cx + 7, cx + 14, cx + 7, cx}, new int[]{y, y + 8, y + 16, y + 8}, 4);
                g.setColor(i < battle.getFp() ? new Color(127, 216, 255) : new Color(60, 70, 90));
                g.fillPolygon(gem);
                g.setColor(Color.WHITE); g.drawPolygon(gem);
            }
        }

        /** Status chips under a fighter; hovering one shows what it does. */
        private void drawChips(Graphics2D g, Entity e, int cx, int y, List<Object[]> tips) {
            List<String[]> info = e.statusInfo();
            if (info.isEmpty()) return;
            g.setFont(Ui.SMALL_B);
            FontMetrics fm = g.getFontMetrics();
            int total = 0;
            for (String[] s : info) total += fm.stringWidth(s[0]) + 16;
            int x = cx - total / 2;
            for (String[] s : info) {
                int w = fm.stringWidth(s[0]) + 12;
                Color c = Ui.statusColor(s[0]);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 210)); g.fillRoundRect(x, y, w, 15, 8, 8);
                g.setColor(Color.WHITE); g.drawString(s[0], x + 6, y + 12);
                tips.add(new Object[]{new Rectangle(x, y, w, 15), tipHtml(s[0], s[1])});
                x += w + 4;
            }
        }

        private void drawEffects(Graphics2D g) {
            long now = System.currentTimeMillis();
            Iterator<Effect> it = effects.iterator();
            while (it.hasNext()) {
                Effect fx = it.next();
                double t = (now - fx.start) / 750.0;
                if (t >= 1) { it.remove(); continue; }
                Rectangle r = rects.get(fx.who);
                if (r == null) continue;
                int iw = fx.img.getWidth(null), ih = fx.img.getHeight(null);
                double size = Math.max(r.width, r.height) * (0.7 + 0.6 * t);
                double sc = size / Math.max(iw, ih);
                int w = (int) (iw * sc), h = (int) (ih * sc);
                Composite old = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, 1.6 * (1 - t))));
                g.drawImage(fx.img, r.x + r.width / 2 - w / 2, r.y + r.height / 2 - h / 2, w, h, null);
                g.setComposite(old);
            }
        }

        private void drawFloaters(Graphics2D g) {
            long now = System.currentTimeMillis();
            Iterator<Floater> it = floaters.iterator();
            while (it.hasNext()) {
                Floater f = it.next();
                long age = now - f.start;
                if (age > (f.small ? 1600 : 1000)) { it.remove(); continue; }
                Rectangle r = rects.get(f.who);
                if (r == null) r = new Rectangle(getWidth() / 2, ground() - 100, 0, 0);
                int alpha = (int) Math.max(0, Math.min(255, 255 - (age - (f.small ? 600 : 0)) / 4));
                int y = r.y + 30 + f.yOff - (int) (age / 18);
                Font font = f.small ? Ui.BOLD.deriveFont(15f) : Ui.H1.deriveFont(26f);
                g.setFont(font);
                int tw = g.getFontMetrics().stringWidth(f.text);
                int x = r.x + r.width / 2 - tw / 2;
                g.setColor(new Color(0, 0, 0, alpha)); g.drawString(f.text, x + 2, y + 2);
                g.setColor(new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), alpha)); g.drawString(f.text, x, y);
            }
        }

        private void drawFighter(Graphics2D g, Entity e, Rectangle r, boolean isEnemy) {
            long now = System.currentTimeMillis();
            int off = shake.getOrDefault(e, 0L) > now ? ((now / 40) % 2 == 0 ? 6 : -6) : 0;
            int x = r.x + off;
            g.setColor(new Color(0, 0, 0, 90)); g.fillOval(x - 6, r.y + r.height - 10, r.width + 12, 20);
            Image sprite = isEnemy ? Assets.enemy(((Enemy) e).getBaseName()) : Assets.hero(e.getName());
            if (isEnemy && ((Enemy) e).isElite()) {
                g.setColor(new Color(244, 196, 64, 50 + (int) (40 * Math.sin(now / 200.0)))); g.fillOval(x - 10, r.y + 10, r.width + 20, r.height);
            }
            if (sprite != null) Assets.contain(g, sprite, x, r.y, r.width, r.height);
            else {
                g.setColor(e.getColor()); g.fillRoundRect(x, r.y + r.height / 3, r.width, r.height * 2 / 3, 24, 24);
                int head = r.width * 6 / 10; g.fillOval(x + (r.width - head) / 2, r.y, head, head);
                g.setColor(Color.WHITE); g.fillOval(x + r.width / 2 - 15, r.y + head / 3, 9, 11); g.fillOval(x + r.width / 2 + 6, r.y + head / 3, 9, 11);
            }
            if (isEnemy && targeting) {
                g.setStroke(new BasicStroke(e == hover ? 4f : 2f));
                g.setColor(e == hover ? Ui.GOLD : Ui.DIM);
                g.drawRoundRect(x - 8, r.y - 8, r.width + 16, r.height + 16, 18, 18);
                g.setStroke(new BasicStroke(1f));
            }
            int cx = x + r.width / 2, ty = r.y + r.height + 20;
            Font nf = r.width < 105 ? Ui.SMALL_B : Ui.BOLD;
            Ui.center(g, Ui.fit(g, e.getName(), nf, r.width + 14), cx, ty, nf, isEnemy && ((Enemy) e).isElite() ? Ui.GOLD : Ui.TEXT);
            int bw = Math.min(150, r.width + 6);
            Ui.hpBar(g, cx - bw / 2, ty + 6, bw, 14, e);
            drawChips(g, e, cx, ty + 25, arenaTips);
            if (isEnemy) Ui.center(g, "DEF " + e.getDef() + "  M.DEF " + e.getMdef(), cx, ty + (e.statusLabels().isEmpty() ? 36 : 54), Ui.SMALL, Ui.DIM);
        }
    }
}
