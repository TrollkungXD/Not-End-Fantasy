import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Map;

public class ShopDialog extends JDialog {
    private final GameState gs;
    private final Node node;
    private final JLabel mora = new JLabel();
    private final JLabel party = new JLabel();
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 6));

    public ShopDialog(JFrame owner, GameState gs, Node node) {
        super(owner, "Shop", true);
        this.gs = gs; this.node = node;
        getContentPane().setBackground(Ui.BG);
        setLayout(new BorderLayout(8, 8));
        JPanel header = new JPanel(new BorderLayout()); header.setBackground(Ui.BG);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 0, 14));
        JPanel txt = new JPanel(new GridLayout(0, 1)); txt.setOpaque(false);
        JLabel name = new JLabel("Bryota's Shop"); name.setFont(Ui.H1); name.setForeground(Ui.TEXT);
        mora.setFont(Ui.BOLD); mora.setForeground(Ui.GOLD);
        party.setFont(Ui.SMALL); party.setForeground(Ui.DIM);
        txt.add(name); txt.add(mora); txt.add(party);
        header.add(txt, BorderLayout.CENTER);
        Image merchant = Assets.merchant();
        if (merchant != null) header.add(new JLabel(Ui.icon(merchant, 110, 90)), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        list.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false); holder.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        holder.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(); south.setOpaque(false);
        south.add(Ui.button("Leave Shop", e -> dispose()));
        add(south, BorderLayout.SOUTH);
        rebuild();
        setSize(640, 600);
        setLocationRelativeTo(owner);
    }

    private void rebuild() {
        mora.setText("Mora: " + gs.getMora() + "      Bag: " + gs.getInventory().entries().size() + " kinds");
        StringBuilder sb = new StringBuilder();
        for (Hero h : gs.getParty()) sb.append(h.getName()).append(" ").append(h.getHp()).append("/").append(h.getMaxHp()).append(" HP     ");
        party.setText(sb.toString());
        list.removeAll();
        for (Map.Entry<Item, Integer> en : node.getStock().entrySet()) {
            Item it = en.getKey();
            int left = en.getValue();
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(Ui.PANEL);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(it.instantOnBuy() ? Ui.GOLD.darker() : Ui.PANEL),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            JLabel l = new JLabel("<html><b>" + it.getName() + "</b> &nbsp;<span style='font-size:9px;color:#9aa0c0'>[" + it.tag() + "]  stock " + left
                    + "  &nbsp;owned " + gs.getInventory().count(it) + "</span><br><span style='font-size:10px'>" + it.getDesc() + "</span></html>");
            l.setForeground(left > 0 ? Ui.TEXT : Ui.DIM); l.setFont(Ui.BODY);
            JButton buy = Ui.button(left > 0 ? "Buy  " + it.getPrice() : "Sold out", e -> buy(it));
            buy.setEnabled(left > 0 && gs.getMora() >= it.getPrice());
            Image art = Assets.itemArt();
            if (art != null) row.add(new JLabel(new ImageIcon(art.getScaledInstance(38, 38, Image.SCALE_SMOOTH))), BorderLayout.WEST);
            row.add(l, BorderLayout.CENTER); row.add(buy, BorderLayout.EAST);
            list.add(row);
        }
        list.revalidate(); list.repaint();
    }

    private void buy(Item it) {
        Hero target = null;
        if (it.instantOnBuy()) {
            target = Ui.chooseHero(this, gs.getParty(), it.getName());
            if (target == null) return;
        }
        if (!gs.spend(it.getPrice())) return;
        node.getStock().put(it, node.getStock().get(it) - 1);
        if (it.instantOnBuy()) JOptionPane.showMessageDialog(this, it.use(target, null, gs.getParty(), Collections.emptyList()), it.getName(), JOptionPane.INFORMATION_MESSAGE);
        else gs.getInventory().add(it, 1);
        rebuild();
    }
}
