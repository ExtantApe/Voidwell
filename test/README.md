# Live tests

Bot tests on a throwaway Paper server on 127.0.0.1:25621. Each run makes a fresh server folder in a temp directory and deletes it afterwards. No real server is ever copied.

```bash
npm install
node fetch.mjs                 # Paper, OneBlock, Multiverse, LuckPerms, Via: each checked against its published hash
gh release download v0.1.1 -R ExtantApe/HouseRules -p '*.jar' -D work/cache
(cd .. && ./gradlew build)     # Voidwell and the Waystation stand-in
node suite.mjs 26.2            # everything
node suite.mjs 26.2 payouts    # one test
SERVER_ONLY=1 node suite.mjs 26.3   # checks without players
```

The bots are mineflayer 26.1 clients. On 26.2 they connect through ViaVersion and ViaBackwards. Paper 26.3 disconnects them as soon as they join, so 26.3 gets the server-only checks.

`test/standin` is a stand-in for Waystation 0.4.0's hand-over event and landing service, with the same class names and signatures. `/standin gate <player> oneblock` plays the part of walking through the OneBlock gate, and the landing spot is a stone pad in the main world. It's never shipped.

The test config (in `suite.mjs`) shortens every power to 1 minute and makes "the void gives back" always win, so both can be checked quickly.
