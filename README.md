# Rudues Mod (Minecraft 1.20.1 / Forge 47.3.0) - requires GeckoLib 4.4.x

Build with GitHub Actions (see .github/workflows/build.yml) or `gradlew build`.
GeckoLib must ALSO be in the mods folder: GeckoLib 4.4.9 for Forge 1.20.1 (CurseForge / Modrinth).

Play: use the Fire Fiend item -> hold R, point at a move, release R -> press G (rebindable) to cast.
Use moves to fill the purple gauge above your hearts. When full: flame pillar -> Hero of Hell demon (60s).

Infernal Trial (work in progress): ring 12 crying obsidian like an End portal frame and throw the Fire Fiend
into the middle to open the portal to the arena.

Infernal Shadow (trial boss, in progress): `/rudues shadow summon [player]` (operators) summons one 8 blocks in
front of you, wearing that player's skin. Move 1 "Hellscrape": it sprints at you dragging its sword along the
floor, brakes and tears the blade upward in a rising slash that launches you. Move 2 "Hammer and Anvil": it leaps,
vanishes and re-forms 6 blocks above you; a red circle follows you for 0.75 s, then locks - get out before it
crashes down fist-first 1.5 seconds after the circle appeared. Up close it answers with a combo: a slash to its left,
a slash to its right and a fire punch. Every so often it takes the middle of the arena and raises the Inferno Vortex
(12 s): a fire tornado that keeps pulling you in (sprint to break free; touching it burns) while fire geysers erupt
around the arena 2 s after their warning circles appear, burning for 3 s.
It also leaps up and hurls three fireballs from the air (Hellfire Volley), and charges a wide sweeping slash that
burns a crescent of ground in front of it (Infernal Crescent). The combo, the volley and the crescent have no cooldown;
the Inferno Vortex has a 40 s cooldown; Hellscrape and Hammer and Anvil are only used when you are far away (8+ blocks).
At half health it reels and doubles over as jagged obsidian growths with purple molten veins tear out through the
netherite on its left arm and both legs (shoulder horns, raking forearm spikes and claws, greaves with knee horns
and calf fins), then rises into a roar whose shockwave blasts everything within 7 blocks away (it can't be hurt during
the 2.6 s transformation, and it can't die before it). Phase 2 hits 20% harder, moves 15% faster and pauses half as
long between its quick moves.
All of the Shadow's fire is purple, and what it sets alight burns with purple fire (1 damage a second; water puts it
out). This fire ignores the power's fire immunity and Fire Resistance. The Shadow's fireballs now count as projectile
hits rather than fire, so the power no longer shrugs them off either.

Infernal Hero (trial boss, in progress): `/rudues hero summon [player]` (operators) summons it 16 blocks in front of
you, hunting that player (you by default). It is a darker Titan the size of the Hero of Hell, burning a deeper, darker
purple than the player's fire - and whoever it hunts is pulled into the Hero of Hell form (it doesn't run out while
the Hero lives), so the fight is Titan against Titan. Its moves:
- Hurl: one big dark fireball thrown overhand (bigger, slower and heavier than the Shadow's; it bursts wide).
- Leap Slash: when you are far away (13+ blocks) it coils, launches itself at you and lands claw-first.
- Rend: a slash to its left, a slash to its right, then both claws brought down overhead.
- Hell Beam (30 s cooldown): a long 2.4 s charge, then a 3 s mouth beam that locks on as it fires and then can only
  drag after you - keep running to either side and it falls behind.
- Tail Sweep: a hook from the left, a hook from the right, then a full spin whipping its tail through everything near.
Its animations bend: a two-part spine, a four-link whipping tail, and follow-through on the head, arms, hands and claws.
At 15% health the end fight begins (it can't be finished off before that, or harmed during it): three clashes, filmed
like the reference fight - the camera takes over, the bars come down, and you mash the attack button while the fists
(or beams) are locked to push the bar over to your side.
1. Fists clash in the middle; win and your punch drives through and shoves it back.
2. Again, harder: it is launched far across the arena, lands in a skid and rises snarling...
3. ...into a beam clash. Win and your beam overwhelms its own and it falls.
Lose a clash and you take the hit and are thrown back; a few seconds later it comes at you again.
