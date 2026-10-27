import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Lets the player pick one Passive and the hero who learns it (Yato's reward). */
public final class PassiveDialog {
    private PassiveDialog() {}

    /** Returns a short text about what was learned. The player must pick (closing the window picks again). */
    public static String choose(Component parent, GameState gs, List<Passive> choices, String title) {
        StringBuilder html = new StringBuilder("<html><body style='width:380px'><b>Choose one permanent Passive:</b><br><br>");
        String[] names = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            Passive p = choices.get(i);
            names[i] = p.getName();
            html.append("<b>").append(p.getName()).append("</b><br>").append(p.getDesc()).append("<br><br>");
        }
        html.append("</body></html>");
        Image npc = Assets.npc("Yato.png");
        Icon icon = npc == null ? null : Ui.icon(npc, 120, 160);
        int pick = -1;
        while (pick < 0) pick = JOptionPane.showOptionDialog(parent, html.toString(), title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, icon, names, names[0]);
        Passive chosen = choices.get(pick);
        Hero h = null;
        while (h == null) h = Ui.chooseHero(parent, gs.getParty(), "Who learns " + chosen.getName() + "?");
        h.addPassive(chosen);
        return h.getName() + " learned " + chosen.getName() + ": " + chosen.getDesc();
    }
}
