Not End Fantasy - Java Swing (JDK 11+)
Run:  double-click NotEndFantasy.jar, or Windows -> run.bat   |   Mac/Linux -> ./run.sh
Or in IntelliJ/Eclipse: add NotEndFantasy/src/ as source folder, add jlayer-1.0.1.jar as a library, run Main.
(jlayer-1.0.1.jar = JLayer MP3 decoder, LGPL - see JLAYER-LICENSE.txt. Java cannot play MP3 by itself.)

How a run works
 - Pick 1 of 4 starting heroes (Vina, Exusiai, Hivia, Phantom). Stages 1-2 are played solo.
 - After clearing Stage 2 pick 1 of 4 companions (Irene, Ingrid, Logos, Shu) for Stages 3-5.
 - Battles have up to 5 monsters. Monsters get more HP and DEF in later stages.
 - 5 stages, played in order. Cleared stages cannot be replayed.
 - SECRET: buy the Topographical Map from Myrtle (an event in Stage 3, 200 Mora) and give it to Yato
   (an event in Stage 4). After clearing Stage 4 the secret stage Primal Forest opens.
   Clearing it unlocks the secret hero Orchid as a 3rd party member.
 - SECRET HERO 2: accept Noir's notebook (event in Stage 1, costs the lead hero 5 Max HP permanently),
   then give it to Yato in the Primal Forest and beat the Divine Kirin. Yato joins as the 3rd member and
   Orchid is locked for that run (max party = 3: leader + companion + one secret hero, GameState.MAX_PARTY).
 - Special events can show a background: drop a picture named like the event title into bg/Event
   (e.g. bg/Event/Auspicious Sign.png). Without one, the stage background is used.
 - OPTIONAL SUPER BOSS: challenge Yato to a duel (Stage 4 event). Win = +250 Mora and one permanent
   Passive for a hero of your choice (Passive.rewards()). Lose = Game Over.
 - Beating Arch Regalia rolls the end credits (CreditsPanel).
 - Music: Ost/*.mp3 (file names in Music.java). Volume -/+ and mute: top-right of the window.
 - Save Spot heals 50% Max HP. Clearing a stage heals 50%.
 - If the whole party falls it is GAME OVER: everything is wiped and you start from the beginning.
 - Monsters show their next move (intent) above their heads: attack, magic, hit-all,
   poison/weaken, guard/rally, heal, CHARGE (huge hit next round - defend!), summon.
   Bosses become ENRAGED below 50% HP.

OOP map:
 Encapsulation  Entity (private stats/status), Node, Inventory, GameState
 Inheritance    Entity -> Hero / Enemy -> Physical, Magic, Boss
                Skill -> Damage (-> MultiHit, StunStrike, Gust, Judgment, Synecdoche, ...), Buff, Heal,
                         Ward, Camouflage, Protect, Thorns, Barrier, Clone, Dodge, Evasion, Taunt, Regen, ...
                EnemyMove -> Strike, Sweep, Venom, Weaken, Guard, Rally, Mend, Drain, Charge, Summon, Explode
 Abstract       Entity, Enemy, EnemyMove, Skill, Item, Event, Passive
 Polymorphism   Passive.onBattleStart()/onRoundEnd(), Skill.apply(), EnemyMove.perform(), Enemy.basic(), Item.use(), Event.getOptions()
 Composition    Hero has Skills, Enemy has EnemyMoves, Battle has party + enemies,
                GameState has party + Inventory, Stage has Nodes
 GUI/Events     MenuPanel, CharSelectPanel, StagePanel, MapPanel, BattlePanel, EventDialog, ShopDialog, InventoryDialog
 Thread         MapPanel.moveTo() (pin movement), BattlePanel.enemyTurn(), Music.Player (OST playback)

Tuning: hero stats in Hero.starters() / Hero.companions() / Hero.orchid(), monsters/events/layouts in Stage.java,
        shared events in Event.common(), items + prices in Items.java,
        heal rates / recruit stage in GameState. GameState.UNLOCK_ALL = true unlocks all stages for testing.
