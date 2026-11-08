# Voidwell

A OneBlock add-on for Paper. Players walk into OneBlock through the Waystation, ring their island's bell to leave, and get something back for whatever they lose to the void.

Voidwell is a working title.

**Status:** pre-release. It passes all 75 of its bot checks on a throwaway Paper 26.2 server, with OneBlock 1.6.4, Multiverse-Core 5.8.2, Multiverse-Inventories 5.3.6, LuckPerms and HouseRules 0.1.1. The bots are 26.1 clients connecting through ViaVersion. On Paper 26.3 it loads, sets up and reports no problems, but no bot could play there: 26.3 disconnects clients that come through ViaVersion ("Invalid move player packet"), even ones standing still. Waystation 0.4.0 isn't released yet, so the tests use a stand-in plugin with the same event and service. They have been checked against the real 0.4.0 branch's source, but not run with it. Nothing has been seen in a real game client yet. See [Tested](#tested) for the details.

## What it does

**In and out.** The Waystation's OneBlock gate hands the player to Voidwell. The first time, OneBlock makes their island and they land on it with an empty OneBlock inventory. After that, they come back to the exact spot where they last rang their way bell. Players never type an `/ob` command.

**The way bell.** Every island gets a bell on a crying obsidian post, two blocks from the one block. It never replaces one of the player's blocks: if that spot is taken, the bell goes to the nearest free one. A label above it says what it's for. Ringing it three times within 6 seconds takes the player to the Waystation, outside the OneBlock gate. One ring does nothing. Two rings, or rings too far apart, do nothing. Rings closer together than 8 ticks don't count, so holding the button down rings it only slowly: holding it for about a second does count, which is still a deliberate act. Hitting it, explosions, projectiles, redstone and other players' rings don't count. Nobody can break it, move it or blow it up, operators included. Sneaking and ringing opens the island menu.

**Deaths.** Every death in OneBlock respawns the player outside the OneBlock gate, even with a bed set. On an island, items drop as normal and last 30 minutes instead of 5, counted only while the island is loaded. In the void, the items charge the void meter.

**The void meter.** Items given to the void charge the player's meter. That covers items thrown in, items lost in a void death, and island death drops that roll in later. An item counts only once it's really gone, and only for whoever threw it. Picking it back up first means it never counts. Worth is set by material and enchantments: a diamond chestplate is 56 points, an iron sword 4 and a cobblestone 0.02. Shulker boxes and bundles count with their contents. Damaged gear is worth less. Each repeat of the same item in a day is worth 90% of the one before, so a stack of 64 diamonds is worth about 10 diamonds. A full meter is 100 points. The meter holds up to 200, and a player can charge at most 200 a day. Changes show on the action bar, because OneBlock already uses the boss bar.

**Rewards.** A full meter offers three rewards, rolled once, so reopening the menu can't reroll them. The player clicks one to choose it and clicks again to confirm.

- **Featherfall:** slow falling for 5 minutes.
- **Void-safe:** for 2 minutes, falling into the void lifts the player back to their island with everything they carry.
- **Bountiful block:** their one block drops double for 5 minutes.
- **The void gives back:** a stated 1 in 3 chance that half of the weapons and armour they've given the void come back, each with one enchantment a level past the usual most (Sharpness VI, Protection V and so on). If not, the meter starts again 25% full and the gear stays for another try. Offered only when there's gear to give back.
- **Island travel:** for 5 minutes, visit the islands of players who are online, share a HouseRules chat circle with them, and have turned visits on.

Powers run in real time, survive a restart, and say when they end.

**Visits and teams.** Visits are off until an island's owner turns them on at the bell. Visitors can look around but can't break, place, open, hurt or pick up anything that isn't theirs, and owners can send them home. From the bell, owners can invite a teammate who shares a circle with them. The invitee gets [Join] and [No thanks] buttons, plus a second warning if joining means leaving an island of their own. Their inventory comes with them. Owners can remove teammates, and teammates can leave.

**Kids.** Travel and teams only ever connect two players who are both online and share a circle. If there are no circles, or a check can't be made, the answer is no. Island data stays on the server, and nothing about players is sent anywhere.

## Needs

Paper 26.1 or later, Java 25. Waystation 0.4.0 or later and OneBlock 1.6.4 (plugin name `Oneblock`) are required. Multiverse-Inventories keeps the OneBlock inventory in the OneBlock world. HouseRules circles are needed for travel and teams.

## Install

With the server stopped:

1. Copy `Voidwell-0.1.0.jar` to `plugins/`.
2. In OneBlock, turn off two settings that fight Voidwell: `/ob autojoin false` and `/ob rebirth_on_the_island false`. With autojoin on, OneBlock cancels the trip back to a player's bell.
3. In Waystation, set the OneBlock world's gate to `hand-over: true` and turn island posts off (`island-posts.enabled: false`). The bell replaces them.
4. Put the OneBlock world in its own Multiverse-Inventories group, and the Waystation's world in a group with the lobby, so a death in OneBlock is a plain OneBlock-to-lobby swap.
5. If Lantern Graves is installed, add the OneBlock world to its `disabled-worlds`. Otherwise it puts island deaths in graves. Voidwell takes void-death items before Lantern Graves sees them, so the two never both take the same items.
6. Start the server and run `/voidwell status`. It lists anything still wrong.

Players keep OneBlock's `oneblock.join` permission. Voidwell refuses a typed `/ob` and hides it from tab-complete, but runs OneBlock's commands for players when they need them.

## Commands

| Command | Who | What |
|---|---|---|
| `/voidwell` | everyone | Your meter, or your reward choice when it's full |
| `/voidwell reward` | everyone | The reward menu. The chat button uses it |
| `/voidwell team accept/decline` | everyone | Used by the invite's chat buttons |
| `/voidwell status` | admins | What's set up and what isn't |
| `/voidwell reload` | admins | Reload `config.yml` |
| `/voidwell meter <player> <points>` | admins | Set a meter, for support |
| `/voidwell offer <player> <rewards…>` | admins | Offer particular rewards, for support and tests |
| `/voidwell bell <player>` | admins | Put an island's bell back, or show where it is |

## Permissions

| Node | Default | What |
|---|---|---|
| `voidwell.use` | everyone | `/voidwell`. The bell needs no permission |
| `voidwell.admin` | op | Admin commands, and changing things on any island |
| `voidwell.oneblock-commands` | op | Typing OneBlock's `/ob` commands |

## Files

`plugins/Voidwell/config.yml` holds the settings, with notes. `players/<uuid>.yml` holds each player's meter, rewards, powers and last bell spot. `islands.yml` records where each bell is. All of them are plain YAML. Each save writes a temporary file and renames it, so a crash leaves the old file or the new one, never half of each.

## Tested

Bot tests (`test/suite.mjs`) on Paper 26.2, on 127.0.0.1, counting items after each step:

- First visit, the bell (one ring, slow rings, hitting it, TNT), the trip out and back, with the same inventory, XP, health and hunger.
- Island deaths: drops kept for 25 minutes and gone after 30, respawn outside the gate despite a respawn point, and the lobby inventory untouched.
- Void deaths and throwing: exact meter values, nothing counted until it's gone, picking items back up, a stack of cobblestone, other players' items.
- Every reward, ending on time. "The void gives back" both ways: a loss gives nothing and starts the meter again 25% full, and the same gear then comes back on a win. Travel between two kids, an adult never offered a kid's island or the other way round, visitors unable to break blocks, sending visitors home. Teams: an invite from the bell, joining, leaving through the shared bell.
- `/ob` refused and hidden for players, still there for admins.
- `kill -9` right after a death, right after the bell, and in the middle of "the void gives back". Nothing was lost or duplicated. A clean restart keeps meters, bells and running powers.

Not tested yet:

- Anything with a player on Paper 26.3, for the reason above. It awaits a newer bot client or a real one.
- The real Waystation 0.4.0 jar, as opposed to the stand-in.
- A real client: whether the bell looks and feels deliberate and is easy to find, how the meter and labels read day and night and for colour-blind players, whether payouts feel exciting and a void death feels like a consolation.

## Build

```bash
./gradlew build
```

The jar is `build/libs/Voidwell-0.1.0.jar`. To run the bot tests: `cd test && npm install && node fetch.mjs && node suite.mjs 26.2`.

## License

MIT. See `LICENSE`. OneBlock is MrMarL's, also MIT. Voidwell calls it but includes none of its code.
