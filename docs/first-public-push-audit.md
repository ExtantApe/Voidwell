# First public push audit

Done on 2026-11-09, before the repo goes public, as `~/Code/CLAUDE.md` asks for a Minecraft plugin's first public push. Nothing on GitHub was changed by the audit, and the repo's visibility wasn't touched.

**Audited:** the whole history of every branch and tag up to `30ff195` (tag `v0.1.0`), six commits. The PR that adds this file changes only this file and the README's status line, and both were checked the same way.

## Checks

| Check | How | Result |
| --- | --- | --- |
| Leak check at HEAD | `scripts/leak-check.sh` locally, with the private terms list | Passed (53 files) |
| Private terms in history | Every line of `git log -p --all`, including commit messages, against each private term, case-insensitive | No matches |
| Secrets | History searched for GitHub, OpenAI-style and AWS tokens, private keys, and `password`, `api key` and `token` assignments | None |
| Server addresses | Every IPv4 address in history | Only `127.0.0.1`, the throwaway test servers |
| Hosts and URLs | Every `.com` host in history | github.com, api.modrinth.com, example.com, anthropic.com (commit trailers), patreon.com (npm funding fields in `test/package-lock.json`) |
| Email addresses | Every address in history | Only the GitHub noreply commit address and Claude's `noreply@anthropic.com` co-author trailer |
| Personal names and paths | `mgilardi`, the owner's names, `/Users/` | Only in git's author field, which `CLAUDE.md` allows. No home paths |
| Server, world and player names | The live servers' names and worlds, and the test players | None of the live names. Test worlds are `world`, `oneblock` and `waystation`, test players are made up (Ash, Birch, Cedar, Dell), and HouseRules circles in tests are `kids` and `adults` |
| Build output | Every file ever committed | Source, tests, docs, scripts, workflows and the Gradle wrapper only. No jars, `build/`, `test/work/` or `node_modules/` |
| Leak check in CI | `.github/workflows/build.yml` and `release.yml` | Both run `scripts/leak-check.sh` |
| License | `LICENSE` | MIT, `Copyright (c) 2026 EXTANT APE` |
| README status line | `README.md` | Says what was tested: 82 bot checks on Paper 26.2 with Waystation 0.4.0, server-side checks only on 26.3, not seen in a real game client. Updated from "pre-release" to "0.1.0, the first release" |

## Notes

- Commit messages carry a `Claude-Session` link to a claude.ai session. It opens only for the owner's signed-in account and holds no secret, but anyone could read the link itself once the repo is public.
- The private terms list lives outside the repo and wasn't copied into it or printed during the audit.
- Nothing needed a history rewrite.
