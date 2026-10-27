import java.util.*;

/** Item catalogue. */
public final class Items {
    private Items() {}
    public static final Item POTION = new Item.Heal("Potion", "Most wounded ally restores 20 HP.", 25, 20);
    public static final Item HI_POTION = new Item.Heal("Hi-Potion", "Most wounded ally restores 30 HP.", 50, 40);
    public static final Item ELIXIR = new Item.PartyHeal("Elixir", "Every ally restores 20 HP.", 50, 20);
    public static final Item PHOENIX = new Item.Revive("Phoenix Feather", "Revive a fallen ally with 60% HP.", 150, 0.6);
    public static final Item ANTIDOTE = new Item.Cleanse("Antidote", "Cure ALL bad effects on the party, +5 HP each.", 25, 10);
    public static final Item BOMB = new Item.Bomb("Bomb", "15 damage to one enemy (ignores DEF).", 30, 15);
    public static final Item MEGA_BOMB = new Item.MegaBomb("Mega Bomb", "10 damage to ALL enemies (ignores DEF).", 60, 10);
    public static final Item ETHER = new Item.Ether("Ether", "Reset all skill cooldowns of the user.", 60);
    public static final Item DRAUGHT = new Item.Draught("Power Draught", "User ATK/M.ATK +2 for 3 turns.", 70, 2, 3);
    public static final Item WARD = new Item.Ward("Guardian Ward", "Party DEF/M.DEF +2 for 3 turns.", 70, 2, 3);

    /** Key item: bought from Myrtle (Stage 3), given to Yato (Stage 4) to reach the secret Primal Forest. */
    public static final Item TOPO_MAP = new Item.KeyItem("Topographical Map", "An old map of a forest that isn't on any other map. Someone might want it.", 200, "Topographical_Map.png");

    /** Key item: Noir's gift (Stage 1 event, costs 5 Max HP). Give it to Yato in the Primal Forest to fight the Divine Kirin and recruit her. */
    public static final Item NOIR_NOTES = new Item.KeyItem("Noir Corne's Personal Notes", "A worn notebook full of hunting notes about a lightning beast. Noir wanted it to reach someone 'at the right time'.", 0, "NoirC2.png");

    private static final List<Item> SPECIALS = Arrays.asList(ELIXIR, PHOENIX, MEGA_BOMB, ETHER, DRAUGHT, WARD);

    private static final List<Item> TONICS = Arrays.asList(
        new Item.Tonic("Whetstone", "Permanently ATK +1.", 90, Hero.Stat.ATK),
        new Item.Tonic("Arcane Dust", "Permanently M.ATK +1.", 90, Hero.Stat.MATK),
        new Item.Tonic("Iron Charm", "Permanently DEF +1 (max 10).", 70, Hero.Stat.DEF),
        new Item.Tonic("Spirit Charm", "Permanently M.DEF +1 (max 10).", 70, Hero.Stat.MDEF),
        new Item.Tonic("Vital Fruit", "Permanently Max HP +5.", 100, Hero.Stat.HP));

    public static List<Item> tonics() { return TONICS; }
    public static List<Item> specials() { return SPECIALS; }
    public static Item randomTonic(Random r) { return TONICS.get(r.nextInt(TONICS.size())); }
    public static Item randomSpecial(Random r) { return SPECIALS.get(r.nextInt(SPECIALS.size())); }
}
