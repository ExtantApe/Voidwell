# Decisions

The owner's decisions for Voidwell, with dates. Later work follows these without asking again.

## 2026-11-08: from the brief

- Name: Voidwell, kept by the owner on 2026-11-08 (relayed by Helm), one word everywhere.
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
- Waystation's `landing()` is two blocks in front of the station gate, clear of its trigger (Waystation session, 2026-11-08).
- The bell is a vanilla bell on a crying obsidian post, two blocks from the one block, and never replaces a block. A label above it says what it does. Rings closer together than 8 ticks don't count, so holding the button down for about a second also works. That is still deliberate.
- Sneak-ringing the bell opens the island menu: the meter and rewards, visits on or off, sending visitors home, inviting and managing teammates, and travel.
- Island death drops that later fall into the void charge the meter of the player who died: lost to the void by accident, the same as a void death. Items nobody threw or dropped by dying, such as one-block drops, never count.
- Items that can never be picked up (for example the copy `/give` shows and removes at once) are left to despawn normally. Keeping them made duplicates.
- Visitors can look but not touch on an island that isn't theirs. This holds whether they came by island travel or walked or bridged there, because OneBlock's `protection: false` lets players reach other islands.
- Powers run in real time, so logging out or a restart neither stops nor stretches them.
- After a trip out by the bell, Voidwell saves the OneBlock world as well as the player, as it does after a death (`crash-safety.save-world`). Otherwise a crash could bring back, on the ground, items the player had already picked up and carried out: found with the real Waystation 0.4.0, 2026-11-09.
- "The void gives back" with no gear in the pool keeps the meter full and offers again, rather than spending it on nothing.
