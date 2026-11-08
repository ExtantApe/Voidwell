# Decisions

The owner's decisions for Voidwell, with dates. Later work follows these without asking again.

## 2026-11-08: from the brief

- Voidwell is a working title. If it's renamed, the repo, folder, plugin name, `rootProject.name`, jar and package all change together.
- Players enter OneBlock through the Waystation's OneBlock gate. On the first trip they get an island. After that they arrive where they last left, at their way bell.
- Players leave by the island's way bell. Leaving saves their state in the OneBlock world and puts them at the Waystation, outside the OneBlock gate.
- Every OneBlock death respawns the player outside the OneBlock gate, even with a bed set. Items drop normally where the player died.
- The OneBlock inventory never leaves the OneBlock world. It's its own Multiverse-Inventories group.
- Players use no `/ob` commands. Admins keep them. Voidwell does whatever players still need, such as making their island.
- Items given to the void charge a meter, whether thrown in or lost in a void death, and it works the same on purpose or by accident. Stronger items charge it faster, armour included.
- Payouts: not too strong, but with a chance of a big one. The owner offered ideas, not fixed details: a short special power, a chance that half the sacrificed armour and weapons come back with special enchantments, and travel between islands for a while.

## 2026-11-08: the owner's answers to the open questions

- **Way bell:** a bell, rung three times. One ring does nothing. (Chosen over a placed block with a two-second hold, and over a lodestone ritual.)
- **Meter values:** each item's value comes from its material tier and enchantments, and common blocks are worth almost nothing. There's a daily cap, and repeats of the same item type are worth less each time. The meter shows on the action bar, not a boss bar, because OneBlock's level already uses the boss bar.
- **Payouts:** a full meter offers a choice of 1 of 3 rewards. Any chance involved states its odds plainly. No hidden gamble.
- **Island travel:** only to islands whose owner allows visits, and only between players who share a HouseRules circle. Travel ends by itself, and the owner can send visitors home.
- **Team play:** included in version 1. An island owner invites teammates from the way bell. (Chosen over leaving teams out of version 1.)
- **Dropped items in the OneBlock world:** last 30 minutes. Vanilla is 5.

## 2026-11-08: agreed with the Waystation session (Waystation 0.4.0)

- Waystation fires `com.extantape.waystation.WaystationHandOverEvent` (a `PlayerEvent`: `getGateId()`, `getDestination()`, `isHandled()`, `setHandled(boolean)`) on the main thread for gates with `hand-over: true`, after its own access checks. Voidwell handles it and moves the player itself.
- Waystation registers the `com.extantape.waystation.LandingService` interface: `Optional<Location> landing(String world)`. Voidwell asks it every time and never caches the answer. If the service is null, Waystation isn't running.
- Voidwell depends on Waystation: `load: BEFORE`, `required: true`, `join-classpath: true`.
- At install: `island-posts.enabled: false` in Waystation, and the waystation world joins the Lobby Multiverse-Inventories group.

## 2026-11-08: implementation choices (not owner decisions; can be changed)

- OneBlock's `autojoin` must be `false`. In 1.6.4 it cancels every teleport into the OneBlock world that doesn't land exactly on the island block, which would stop players returning to their bell.
- OneBlock's `rebirth_on_the_island` must be `false`. Voidwell also overrides the respawn spot itself.
- Voidwell blocks `/ob` only when a player types it, and hides it from tab-complete. Players keep `oneblock.join`, so the `ob join` Voidwell runs on their behalf still works.
- Circles come from the `houserules.circle.<name>` permissions HouseRules registers. If no circles exist, or either player is offline, travel and invites are refused. When a check can't decide, the answer is no.
