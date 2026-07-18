# Connect Four

MineClub-style wall-board **Connect Four** for **Paper 26.1.2+**. Two players — **Red** vs **Yellow** — click join blocks, then drop discs into a giant 7×6 board on the wall.

Data folder: `plugins/GenesiCore/games/ConnectFour/` (via GenesiGamesApi).

## Materials

Closest Minecraft stand-in for real Connect Four:

| Role | Material | Why |
|------|----------|-----|
| Empty slot | `AIR` | Keeps blue frame holes see-through |
| Red disc | `RED_CONCRETE` | Solid, high-contrast “plastic” disc |
| Yellow disc | `YELLOW_CONCRETE` | Matching yellow disc |
| Win flash | `GLOWSTONE` | Highlights the four-in-a-row |

Your build supplies the blue frame; the plugin only paints the playable cells. Override under `materials:` in `config.yml`.

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
./gradlew build
```

Jar: `build/libs/ConnectFour-1.0.0.jar` — **JDK 25+**. Depends on `GenesiGamesApi`.

## Admin setup

Point the plugin at your existing wall board (bottom-left empty cell = column 1, row 1):

```
/cfadmin create lounge
/cfadmin setlobby lounge
/cfadmin setorigin lounge          # look at bottom-left empty cell
/cfadmin setfacing lounge          # face the board (or NORTH/SOUTH/EAST/WEST)
/cfadmin setjoin lounge yellow     # look at yellow join block
/cfadmin setjoin lounge red        # look at red join block
```

Giant discs (e.g. 2×2 cells with a 1-block gap):

```
/cfadmin setcellsize lounge 2 2 1 1
```

Optional column buttons (left→right). If unset, players click the board column itself:

```
/cfadmin bindcolumns lounge
```

Clear / reset paint:

```
/cfadmin clearboard lounge
```

## Play

1. Click the **yellow** or **red** join block (or `/cf join lounge yellow`)
2. When both seats are filled, countdown starts
3. On your turn, click a column (board or button) — disc drops to the lowest open slot
4. First to connect four (horizontal, vertical, or diagonal) wins

## Player commands

```
/connectfour join <arena> [red|yellow]
/connectfour leave
/connectfour start
/connectfour points
/connectfour arenas
```

Aliases: `/cf`, `/c4`, `/connect4` — admin: `/cfadmin`
