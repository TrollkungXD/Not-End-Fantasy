import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Map;

/** Shows party stats and bag. Map-usable items can be used here (outside battle). */
public class InventoryDialog extends JDialog {
    private final GameState gs;
    private final JPanel stats = new JPanel(new GridLayout(0, 1, 0, 2));
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 6));

    public InventoryDialog(JFrame owner, GameState gs) {
        super(owner, "Inventory", true);
        this.gs = gs;
        getContentPane().setBackground(Ui.BG);
        setLayout(new BorderLayout(8, 8));
        stats.setOpaque(true); stats.setBackground(Ui.BG); stats.setBorder(BorderFactory.createEmptyBorder(10, 14, 0, 14));
        add(stats, BorderLayout.NORTH);
        list.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false); holder.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        holder.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder); sp.setBorder(null); sp.getViewport().setOpaque(false); sp.setOpaque(false);
        add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(); south.setOpaque(false);
        south.add(Ui.button("Close", e -> dispose()));
        add(south, BorderLayout.SOUTH);
        rebuild();
        setSize(600, 520);
        setLocationRelativeTo(owner);
    }

    private JLabel label(String s, Font f, Color c) { JLabel l = new JLabel(s); l.setFont(f); l.setForeground(c); return l; }

    private void rebuild() {
        stats.removeAll();
        stats.add(label("Party   -   Mora: " + gs.getMora(), Ui.H1, Ui.GOLD));
        for (Hero h : gs.getParty())
            stats.add(label(h.getName() + "  (" + h.getRole() + ")   HP " + h.getHp() + "/" + h.getMaxHp() + "   DEF " + h.getDef() + "   M.DEF " + h.getMdef()
                    + "   ATK " + h.getAtk() + "   M.ATK " + h.getMatk(), Ui.BODY, Ui.TEXT));
        list.removeAll();
        if (gs.getInventory().entries().isEmpty()) list.add(label("Your bag is empty.", Ui.BODY, Ui.DIM));
        for (Map.Entry<Item, Integer> en : gs.getInventory().entries().entrySet()) {
            Item it = en.getKey();
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(Ui.PANEL); row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            row.add(label("<html><b>" + it.getName() + "</b> x" + en.getValue() + "<br><span style='font-size:10px'>" + it.getDesc() + "</span></html>", Ui.BODY, Ui.TEXT), BorderLayout.CENTER);
            if (it.usableOnMap()) {
                JButton use = Ui.button("Use", e -> useOnMap(it));
                use.setEnabled(it.canUse(gs.getParty()));
                row.add(use, BorderLayout.EAST);
            } else row.add(label(it.tag(), Ui.SMALL, it instanceof Item.KeyItem ? Ui.GOLD : Ui.DIM), BorderLayout.EAST);
            Image icon = it.getIcon() != null ? Assets.item(it.getIcon()) : Assets.itemArt();
            if (icon != null) row.add(new JLabel(Ui.icon(icon, 40, 40)), BorderLayout.WEST);
            list.add(row);
        }
        stats.revalidate(); list.revalidate(); stats.repaint(); list.repaint();
    }

    private void useOnMap(Item it) {
        String msg;
        if (it instanceof Item.Heal) {
            Hero h = Ui.chooseHero(this, gs.getParty(), it.getName());
            if (h == null) return;
            gs.getInventory().remove(it);
            msg = it.use(h, null, Collections.singletonList(h), Collections.emptyList());
        } else {
            gs.getInventory().remove(it);
            msg = it.use(gs.getHero(), null, gs.getParty(), Collections.emptyList());
        }
        JOptionPane.showMessageDialog(this, msg, it.getName(), JOptionPane.INFORMATION_MESSAGE);
        rebuild();
    }
}
