# SkyDash

Client-only Fabric dashboard for **Minecraft 26.1.2 / Java 25**. Press **O** (rebindable in Controls), or open **Mods → SkyDash → Configure**. Toggle modules, pin them to the HUD, and use **HUD layout** to drag panels. Select a module for full details; mouse wheel scrolls its detail list. The dashboard does not pause the game.

## Build and install

1. Install a Java 25 JDK and set `JAVA_HOME` to it.
2. In this directory, run `./gradlew build` (`gradlew.bat build` on Windows). The wrapper downloads Gradle. The first build needs network access.
3. Install Fabric Loader **0.20.1 or newer** for Minecraft **26.1.2**.
4. Put `build/libs/skydash-0.1.0.jar`, Fabric API **0.155.3+26.1.2**, and optionally Mod Menu **18.0.2** in the client's `mods` directory. Do not install the `-sources.jar`.
5. Start Minecraft and enter Hypixel SkyBlock. No server installation is needed. Data collection and API polling run only while the Hypixel SkyBlock sidebar is detected.

No extra runtime or test dependencies were added: Fabric API and Mod Menu are the only declared mod dependencies. Fabric Loader, Loom and Gradle are the required build infrastructure; Gson comes from Minecraft. Mod Menu is optional at runtime.

## Configuration

A first launch creates `.minecraft/config/skydash.json`. **Close Minecraft before editing credentials**, so an in-memory config save cannot overwrite file edits. Set `apiKey` to your own key; never share that file. The key is never shown in the dashboard, included in a URL, sent to public endpoints, or logged by SkyDash. Settings, goals, item lookup, alert thresholds, event deadlines and locally observed sack counts are editable in-game.

`profileId` may identify one of your own profiles. When empty, the API-selected profile is used at discovery. The UUID always comes from the signed-in Minecraft account; there is no other-player UUID setting. Change `profileId` when switching SkyBlock profiles. The client validates profile membership before fetching profile-specific endpoints, and reads only that member's skills, collections and Museum donations.

For a proxy, set `proxyUrl` to an **HTTPS API base**, for example `https://your-service.example/v2`. SkyDash appends the same endpoint paths and own-account query parameters. The proxy must authenticate the user as appropriate for your deployment, enforce own-profile access, supply its upstream key and return Hypixel-compatible JSON/status/rate-limit headers. SkyDash never forwards the local API key to a proxy. Public requests still go directly to Hypixel without credentials. Proxy mode uses the same local authenticated-request budget. A backend is optional and is not included.

Important settings:

- `profileRefreshSeconds`: 120–300, clamped in code; default 180.
- `publicRefreshSeconds`: 30–3600, default 60; fallback when no server cache lifetime is given.
- `goalSkill`: e.g. `SKILL_FARMING`; `goalLevel`: desired level (0 uses `goalXp` instead); `goalDate`: `YYYY-MM-DD`, end of that date in the player's local timezone.
- `lookup`: a Hypixel item/collection ID, e.g. `WHEAT`.
- `priceAlerts`: item ID → maximum buy price. Alerts fire when the cached price is at or below the threshold and rearm after it rises above the threshold. UI format: `WHEAT=5;ENCHANTED_WHEAT=500`.
- `timers`: custom event name → deadline as Unix epoch milliseconds. UI format: `My event=1793505600000`.
- `sackCounts`: last locally observed or manually entered item counts. Opening a sack menu updates recognizable `Stored:` / `Amount:` lore; otherwise the previous counts remain. These are snapshots, not live remote sack totals.
- `modules`: per-module enabled/pinned flags and HUD x/y coordinates in scaled GUI pixels.

## Modules

“Key” also permits a configured backend proxy. Each module has a separate package and implements the shared `Module` interface (`id`, `name`, `enabled`, `tick`, `render`, `config`). Modules do not make network calls.

| # | Module | Key | Data and behavior |
|---|---|---|---|
| 1 | Garden | Yes | Visitor queue/status, unlocked plots, per-crop milestone collection totals, crop revenue/hour between Garden snapshots; shared profile freshness. |
| 2 | Skills & XP | Yes | Own skill XP and levels, XP/hour, next-level ETA, level or XP target by date. Public skill thresholds supplement the own-profile snapshot. |
| 3 | Museum | Yes | Own recorded donations compared with public donation metadata; missing items and Bazaar purchase estimates where a Bazaar listing exists. |
| 4 | Collections | Yes | Own collections ranked by percentage toward the next public collection tier. |
| 5 | Bingo | Yes | Current/upcoming event board, own completed goals, pending goals, separately labeled community progress. |
| 6 | Bazaar HUD | No | Gross buy/sell spread for items in the inventory and threshold alerts. |
| 7 | Fire sales & news | No | Upcoming/live sales, one notification within five minutes of a sale, and rotating news headlines. |
| 8 | Item & collection info | No | Item name/tier and collection thresholds for the configured ID. |
| 9 | Session stats | No | Net purse change from sidebar; XP and received-item observations from system chat/action bar. Reset from Settings. |
| 10 | Timers | No | Mayor name, Jacob start/end countdown, estimated calendar mayor/Spooky/New Year times, optional custom event deadlines. |
| 11 | Inventory value | No | Bazaar-priced local inventory and last observed/saved sack counts; unpriced item count. |
| 12 | Configuration | No | Dashboard toggles, pinning, draggable HUD, refresh rates, alerts, goals, lookup and local values. |

## Exact request budget

**At most 3 authenticated request starts in any rolling 300 seconds per SkyDash instance/account.** A synchronized token bucket has capacity **3** and refills **1 token per 100 seconds**. A rolling-window guard additionally prevents the burst/refill edge case from exceeding three starts in five minutes. The request ledger is persisted as `config/skydash-request-budget.txt` before each authenticated request, so restarting does not restore a fresh burst. Unreadable/corrupt ledger state fails closed for authenticated polling; it does not crash the game. Separate simultaneous Minecraft processes are not coordinated by this local ledger.

All authenticated calls—including discovery, failures, manual refreshes and proxy calls—consume this one budget. Changing a key or profile does not replenish it. Public calls consume no authenticated tokens and never send `API-Key`.

A shared own-profile snapshot is cached for **120–300 seconds** and serves Skills, Collections and the other profile consumers. The initial `/skyblock/profiles` discovery response seeds that cache instead of immediately requesting identical profile data. Subsequent shared refreshes use `/skyblock/profile`. Hypixel supplies Garden and Museum separately; those and Bingo are scheduled round robin under the same budget, with disabled own-profile modules skipped. **The budget takes precedence over refresh intervals:** with all authenticated modules enabled, some snapshots will be older than five minutes. No module fetches its own profile copy.

Manual refresh has a **30-second cooldown**. It can make authenticated data eligible early but cannot bypass the budget, pending-request deduplication, authentication stop, or rate-limit backoff. Fresh public caches are retained even during manual refresh. Public cache lifetimes use `Cache-Control: max-age` with `Age` deducted, or the configured fallback.

HTTP **429** uses `RateLimit-Reset` seconds (60-second fallback); queued work also observes the backoff. An authenticated **401/403** stops authenticated retries until credentials/proxy configuration actually changes or the game is restarted. HTTP error bodies, exception details and credentials are never displayed. Failures retain cached data with an `updated N min ago` label; a missing snapshot is unavailable. Network requests, JSON response parsing and budget persistence run on a daemon worker, never in tick/render. Tick/render never wait for a request.

## Data limits

- Garden exposes crop totals and unlocked plot IDs, not a complete planted/cleared plot state or a public milestone threshold catalog. SkyDash shows the supplied milestone counters; it does not invent milestone levels. Crop revenue estimates use profile crop deltas and Bazaar instant-sell prices, and may include co-op activity. A second Garden snapshot is required.
- Most Museum equipment is not sold on the Bazaar. Those costs explicitly say unavailable; no auction scraping, auction-price service, or inferred upgrade pricing is included. Group/parent donation completion is not inferred from an equipment item's presence. Museum member data hidden by in-game API settings remains unavailable.
- Session counters recognize a conservative set of English server message formats. Purse change is net of spending, withdrawals and deposits, not gross earnings. Persistent action-bar text is deduplicated. Hidden/changed messages and some pickup formats are not observable. Session observations can miss gains and should not be treated as an accounting ledger.
- Inventory value excludes non-Bazaar equipment, upgrades, enchantments, taxes, order depth and slippage. Sacks must be opened locally for supported lore to be observed, or entered manually; offline sack contents cannot be read without an own-profile API snapshot. Saved counts may become stale.
- Jacob uses the hourly `:15`–`:35` clock schedule; no future crop choices are guessed. Calendar estimates use a 372-day SkyBlock year and can drift if the server changes its calendar. Election data frequently lacks an end timestamp; an exact election countdown then says unavailable. Custom event deadlines can be set manually.
- Bingo exposes completed personal goal IDs; incomplete personal goals lack exact intermediate progress. The public resource provides goal names/requirements and community progress, which is labeled separately.
- Live Hypixel play and visual layout were not exercised in this headless build environment. Enable the relevant in-game API visibility settings for private data. No key was used during development or testing.

## Tests and structure

`./gradlew build` runs a dependency-free Java test harness through `coreTest`, wired into `check`; `./gradlew test` also runs that harness. The conventional framework-based Gradle `test` executor is disabled because no additional test framework was authorized. Any assertion throws and fails the build.

Tests cover token refill/boundaries, 64 concurrent acquisitions, the rolling burst guard, persistence across restart, cache expiry/max-age/no-store parsing, own-member profile parsing and foreign-member rejection against saved JSON fixtures, shared discovery cache reuse, no-key public transport, User-Agent, manual cooldown, exhausted budget, 403 shutdown and 429 reset timing. HTTP tests use an injected fake transport and make no live requests. Fixture values are synthetic and contain no player credentials.

`src/main/java/dev/skydash/api` is the only network layer; `src/client/java/dev/skydash/module` contains independent module packages; `src/client/java/dev/skydash/ui` contains the dashboard, settings and HUD layout screens.

API schema reference: [Hypixel Public API](https://api.hypixel.net/). Build compatibility: [Fabric example mod](https://github.com/FabricMC/fabric-example-mod/tree/26.1).
