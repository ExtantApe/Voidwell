# Test audit

Kept by this repo's owning lead (Minecraft: Plugins Lead), per `~/Code/CLAUDE.md`, "Test self-audits". Newest first.

## 2026-11-10: the respawn after a bell trip (0.1.1)

The server session's rehearsal on a copy of the real server (Paper 26.2, Waystation 0.6.0, OneBlock 1.6.4) found a void death after a bell trip and a return putting the player on their island while Voidwell said "You're back at the Waystation", in 3 of 3 runs. In this repo's suite the same steps respawn at the gate; a teleport trace showed only Voidwell's respawn there. So something on the real server moves the player afterwards.

| Promise | Covered by |
| --- | --- |
| After a bell trip and back, a void death still respawns outside the gate, and says so | bots "way bell: after a bell trip and back, a void death still respawns outside the gate", "and says so" (the rehearsal's repro) |
| If another plugin moves the player back into OneBlock after the respawn, Voidwell still ends with them at the gate and the console says so | bots "if another plugin moves them back after the respawn…", "and the console says what happened" (the console plays the other plugin) |

Gap: which plugin on the real server does it isn't known yet. `-Dvoidwell.trace=true` logs every teleport with its cause, for the server session's next rehearsal.
 Taken over from code-44 with its agreement on 2026-11-10.

## 2026-11-10: first full audit (at 0.1.0)

What Voidwell promises (README "What it does" and "Tested", `docs/decisions.md`), and what proves it. "Bots" is `test/suite.mjs` on a throwaway Paper 26.2 server with the real Waystation, OneBlock, Multiverse, LuckPerms and HouseRules; "unit" is `src/test`.

### Covered

**Kids first** (always covered)
- **Travel and teams only connect two online players who share a circle; no circles, or a check that can't be made, means no.** Bots: "a kid sees a kid's open island", "a kid never sees an adult's island", "an adult never sees kids", "teams: an adult isn't offered". **New in this audit:** unit `ShareCircleTest` (5 tests) for the cases the bots can't set up: no circles at all, someone offline, nobody in any circle, with a positive control. The rule moved into a static helper so it can be tested without a server; behavior is unchanged.
- **Visitors look but don't touch.** Bots: "a visitor can't break blocks (server's view)", "the owner can send visitors home". Placing, opening, hurting and buckets all go through the same `guarded()` check as breaking, so the break check covers that path.

**Nothing lost or duplicated**
- **Island deaths keep drops for 30 minutes; void deaths charge the meter; the lobby inventory is never touched:** bots, island and void death sections.
- **Crashes:** bots, `kill -9` after a death, after the bell (with drops picked back up), and mid-payout; a clean restart keeps meters, bells and powers.
- **Saves write a temporary file and rename it.** Exercised by the crash checks, which found nothing lost; a kill in the middle of a write isn't tested on its own.

**The meter and rewards**
- **Worth by material and enchantments, repeats worth less, a daily cap, carry-over:** unit `meter` tests (6).
- **An item counts only once it's really gone, and only for whoever threw it:** bots, throwing and abuse sections.
- **Rewards rolled once, chosen then confirmed, each ending on time; "the void gives back" both ways:** bots, payouts section.

**The bell and the way in and out**
- **One ring, slow rings, hitting it and TNT do nothing; three rings go to the Waystation; back where the bell was rung with the same inventory, XP, health and hunger:** bots.
- **`/ob` is refused and hidden for players, still there for admins:** unit `catchesEveryFormOfOb`; bots.

After the refactor, the bot suite's 82 checks all passed on Paper 26.2 (2026-11-10, with the released Waystation 0.5.0), and the 5 new unit tests pass.

### Gaps

- **A visitor picking up an item that isn't theirs.** Pickup has its own rules (your own throws and death drops are allowed), so the break check doesn't cover it. A bot check was tried on 2026-11-10: items summoned at a visitor's feet on another island never appeared, and the suite keeps only the last server's console, so the cause wasn't found. Not covered yet. Next try: the island owner's bot drops an item next to the visitor, with a control for the visitor's own throw. Later finding (2026-11-10): nothing in Voidwell removes an item entity except the void, so the emerald most likely fell off the island's edge; it isn't data loss.

- **Anything with a player on Paper 26.3.** ViaVersion clients are disconnected there when they move. Checked again on 2026-11-10: the newest ViaVersion and ViaBackwards builds on Modrinth are the ones already in use, and the bot library has no native 26.3 support. Server-only checks run there. This waits for a newer bot client or a real one (T21).
- **A real client:** how the bell, meter and labels look and feel (T21).

### Skipped, with reasons

- **Each visitor action separately (place, open, hurt, buckets, hanging items).** They share `guarded()` with breaking, which is tested. A bot can't reliably right-click blocks through ViaBackwards.
- **Admins with `voidwell.admin` not being held back on islands.** It's convenience for support, and protects nothing.
