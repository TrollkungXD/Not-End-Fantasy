import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Story window for map events: portrait, text and one button per choice. */
public class EventDialog extends JDialog {
    private final GameState gs;
    private final Event event;
    private final JTextArea text = new JTextArea();
    private final JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
    private Event.Result result;

    public EventDialog(JFrame owner, GameState gs, Event event) {
        super(owner, event.getTitle(), true);
        this.gs = gs; this.event = event;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);      // a choice must be made
        final Image bgImg = event.hasBg() ? Assets.eventBg(event.getTitle(), event.getBg()) : null;
        JPanel root = new JPanel(new BorderLayout(14, 10)) {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                if (bgImg == null) return;
                Graphics2D g = (Graphics2D) g0.create();
                Assets.cover(g, bgImg, 0, 0, getWidth(), getHeight());
                g.setColor(new Color(10, 12, 24, 150));        // darken so the text stays readable
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        root.setBackground(Ui.BG);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        Image npc = event.getNpc() != null ? Assets.npc(event.getNpc()) : bgImg != null ? null : Assets.npcFor(event.getTitle());
        if (npc != null) {
            JLabel pic = new JLabel(Ui.icon(npc, 190, 400));
            pic.setVerticalAlignment(SwingConstants.TOP);
            root.add(pic, BorderLayout.WEST);
        }
        JPanel right = new JPanel(new BorderLayout(0, 10)); right.setOpaque(false);
        JLabel title = new JLabel(event.getTitle());
        title.setFont(Ui.H1); title.setForeground(Ui.GOLD);
        right.add(title, BorderLayout.NORTH);
        text.setText(event.getIntro());
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setFont(Ui.BODY); text.setForeground(Ui.TEXT); text.setBackground(Ui.PANEL);
        text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane sp = new JScrollPane(text); sp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        right.add(sp, BorderLayout.CENTER);
        buttons.setOpaque(false);
        right.add(buttons, BorderLayout.SOUTH);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);
        showChoices();
        setSize(720, 470);
        setLocationRelativeTo(owner);
    }

    private void showChoices() {
        buttons.removeAll();
        List<Event.Option> opts = event.getOptions();
        for (int i = 0; i < opts.size(); i++) {
            Event.Option o = opts.get(i);
            String req = o.cost > 0 && !o.label.contains("Mora") ? "  [" + o.cost + " Mora]" : o.needs != null ? "  [needs " + o.needs.getName() + "]" : "";
            final int idx = i;
            JButton b = Ui.button("<html><b>" + o.label + "</b>" + req + "<br><span style='font-size:9px;color:#b4b8d4'>" + o.hint + "</span></html>", e -> pick(idx));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setEnabled(o.available(gs));
            buttons.add(b);
        }
        buttons.revalidate(); buttons.repaint();
    }

    private void pick(int i) {
        result = event.choose(i, gs);
        text.setText(event.getIntro() + "\n\n>> " + event.getOptions().get(i).label + "\n\n" + result.text);
        text.setCaretPosition(text.getDocument().getLength());
        buttons.removeAll();
        if (result.again) {                              // just a question: go back to the same choices
            result = null;
            buttons.add(Ui.button("Back", e -> { text.setText(event.getIntro()); text.setCaretPosition(0); showChoices(); }));
            buttons.revalidate(); buttons.repaint();
            return;
        }
        buttons.add(Ui.button(result.fight ? (result.elite ? "Fight the ELITE!" : "To battle!") : "Continue", e -> dispose()));
        buttons.revalidate(); buttons.repaint();
    }

    /** Shows the dialog and returns what happened. */
    public Event.Result run() { setVisible(true); return result; }
}
