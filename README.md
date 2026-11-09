# Nazarick (Forge 1.20.1)

## Build
JDK 17, then `./gradlew build` (Windows: `gradlew.bat build`). The jar is in `build/libs/`.
The GitHub workflow `.github/workflows/main.yml` does the same and uploads the jar as an artifact.

## The Great Tomb of Nazarick (Overworld)
Generates **once per world**, a few hundred blocks from spawn (`/locate structure nazarick:great_tomb`).
- **Surface:** walled graveyard and white-and-black mausoleum, guarded by two Elder Liches.
- **Spiral stair:** 70 blocks down from the mausoleum floor.
- **Hall of Lemegeton:** demon statues, two Death Knights at the Throne Room doors.
- **Throne Room:** Albedo stands beside the throne. A corridor east leads to the **King's Quarters** (Sebas).
For worlds whose spawn area already exists: `/nazarick place great_tomb` (op) builds the whole tomb with the mausoleum at your feet.

## Playing it (from the design doc)
**Raid path (nobody rules yet):** without the staff, Nazarick is hostile. The undead attack you, and Albedo fights
anyone who intrudes on the Throne Room as a boss (boss bar, halberd, 300 HP). Only intruders can hurt her.

**Rule path:**
1. Craft the **Staff of Ainz Ooal Gown**: gold block, diamond, gold block on top; blaze rod, blaze rod down the middle. Its 7 gem slots start empty.
   While you hold it (and for 3 seconds after switching items), the undead and Albedo leave you alone.
2. Walk to the throne and right-click it with the staff. Confirm **[Claim]** in chat.
3. The ceremony: you are seated, controls lock, the lights dim, the 7 gems appear one by one, the staff takes its full form,
   Sebas walks in and bows with a welcome. Then you are crowned and receive the **Ring of Ainz Ooal Gown**.
   - If enemies are hunting you, the ceremony waits until the hall is clear.
   - Logging out or losing the staff cancels it. Nobody ends up half-ruling.
4. You are now the world's single ruler. The raid path is closed for good.

**After the claim:** undead and guardians are passive to the ruler and anyone on the ruler's scoreboard team.
Everyone else, even with a staff, is an intruder.
- **Ring:** right-click for clickable destinations: Throne Room, King's Quarters, Surface.
- **Throne:** the ruler can right-click it to sit.
- **Replacement staff:** sneak + right-click Sebas. You get "You already have a staff" if one is in your hotbar, inventory or offhand.
- **Eye of Nazarick:** Eye of Ender + Bone + Amethyst Shard + Gold Ingot. Throw it like an Eye of Ender; it flies toward the tomb.

## Commands
| Command | Who | Purpose |
|---|---|---|
| `/nazarick ruler info` / `reset` / `transfer <player>` | op | Show, clear (reopens the raid path) or hand over the ruler. Transfer moves staff access and servant ownership; loyalty stays with the servants. |
| `/nazarick place great_tomb` / `throne_room` / `kings_quarters` | op | Build Nazarick, or a single room, at your feet. |
| `/nazarick claim confirm` / `cancel` | anyone | The throne prompt's buttons. |
| `/nazarick ring <throne_room\|kings_quarters\|surface>` | ruler & team | The Ring's buttons. |

## Creatures
- **Albedo (NPC / boss):** your skin plus horns, wings and a halberd. She curtsies when greeted.
- **Sebas (NPC):** your skin; hands behind his back; bows when greeted.
- **Elder Lich:** hooded caster; 3-bolt volleys; teleports away if you come close.
- **Death Knight:** Warden-sized; roars, swings overhead, blocks projectiles with its shield, and survives its first death blow.

## Not built yet (next phases of the design doc)
Staff UI (Roster, Floors, Summon, Tasks, Reports, Dispatch, Research), Nazarick inventory and dispatch economy,
summoning (Shooting Stars, Arcane Dust, mages), research gear, room upgrades, loyalty, the return scene, invasions,
the other floors as dimensions, the other guardians, and the Iron's Spells integration.

## Source layout
- `tools/rooms`: Python generator for every structure template (`python3 tools/rooms/build_all.py`).
- `tools/modgen`: Python generator for the models, keyframe animations and textures (`python3 tools/modgen/build.py`).
  The `*Model.java` and `*Animations.java` files are generated from it.
