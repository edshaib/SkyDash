# SkyDash development reference

## Build and test

Use Java 25 and run `./gradlew build`. `./gradlew test` also invokes the plain Java `coreTest` harness. Fabric API and Mod Menu are the only declared mod dependencies; Gson is supplied by Minecraft. The tests use a fake HTTP transport and synthetic profile fixtures, with no live credentials or network requests.

## Client architecture

Each module lives in its own package behind `Module` (`id`, `name`, `enabled`, `tick`, `render`, `config`). The shared dashboard lists modules, and the HUD draws pinned panels. `ApiClient` is the only HTTP owner. Network I/O, response parsing and request-ledger writes run on its daemon worker. Tick and rendering never wait for a response.

The client has no Hypixel application-key field and never sends an `API-Key` header. Legacy key fields are ignored when loading older configs and omitted when the config is saved. Existing `proxyUrl` values are not migrated automatically. The settings screen contains no application-key or backend-URL input.

## Public data

While Hypixel SkyBlock is detected, the client refreshes public Bazaar, fire sales, news, items, collections, election, skill-threshold and Bingo-board data. Requests send no credentials. Caches honor `Cache-Control: max-age`, deduct `Age`, and use the configurable public fallback otherwise. Public refreshes are spaced at least 30 seconds apart after a successful response. Fresh public caches survive manual refresh actions.

## Profile service integration

**No backend is included or deployed.** The default `backendUrl` is empty; profile modules show a service-unavailable message. A production distribution may configure an HTTPS SkyDash service base in this maintainer setting. It is intentionally absent from the player settings screen. HTTP, credential-bearing URLs and the direct Hypixel API host are rejected for profile access.

Opening the dashboard or pressing Refresh starts one bounded profile batch. Tick only refreshes public resources. Neither expiry nor token refill schedules private work, and there is no delayed automatic private retry. Authorization failures stop private requests until the configured service changes or the client restarts. A later user action is required after transient failures or backoff.

The service contract follows the Hypixel-compatible paths below its configured base:

- `skyblock/profiles?uuid=<local-account>` validates membership and seeds one shared profile cache.
- `skyblock/profile?profile=<validated-profile>` refreshes that shared snapshot.
- `skyblock/garden?profile=<validated-profile>` loads Garden data.
- `skyblock/museum?profile=<validated-profile>` loads Museum data; the module reads the local member only.
- `skyblock/bingo?uuid=<local-account>` loads personal Bingo data.

These paths describe a SkyDash integration contract, not a deployed or authenticated service. The backend must authenticate the signed-in account, authorize its own profiles, keep the application key server-side, restrict routes to SkyDash features, and enforce application-wide caching and rate limits. A claimed UUID from the client is not proof of account ownership. Account verification and server deployment remain to be implemented; a general-purpose third-party API proxy is not provided.

## Client request limits

One local token bucket allows at most three profile-service request starts per rolling 300 seconds. Capacity is three, refill is one token per 100 seconds, and an additional rolling-window guard prevents overlapping bursts. The ledger is persisted before dispatch and is not reset by changing accounts or service configuration. Every attempted service request consumes the same local budget, including failures. This is a client safeguard, not an application-wide upstream limiter; that must also be enforced on the backend.

Manual/dashboard refresh actions share a 30-second cooldown. Shared profile cache lifetime is configurable from 120 to 300 seconds, default 180. Related modules reuse the same snapshot. Garden, Museum and Bingo get separate snapshots under the shared local budget. Endpoint rotation advances only when a request starts, so waiting modules do not starve behind repeated cache checks. An action can leave some modules unavailable when the budget runs out; nothing keeps polling to fill them later.

HTTP 429 honors `RateLimit-Reset` with a 60-second fallback. A 401/403 stops service access. Response bodies and exception details are never displayed. Cached data retains its age label; missing data is unavailable.

## Local progress and estimates

XP/hour comes from locally observed chat/action-bar XP. Garden earnings use locally observed received-crop messages and Bazaar values. Remote profile or Garden snapshots are not compared to infer session gains or build history. Session purse change is observed from the sidebar. Supported English messages and visible sack lore are incomplete observations and can miss gains or become stale.

Garden displays returned visitor status, unlocked plot IDs and crop milestone totals. Exact milestone thresholds and planting states are not invented. Museum donation costs require a Bazaar listing and omit non-Bazaar equipment, upgrades and inferred parent/group completion. Inventory estimates omit upgrades, taxes and slippage. Calendar timers are estimates. Personal Bingo data exposes completed goals; community totals are labeled separately.

## Configuration

`profileRefreshSeconds` now controls only cache eligibility for explicit profile requests; it never schedules profile polling. `publicRefreshSeconds` controls the public cache fallback. Goals (`goalSkill`, `goalLevel` or `goalXp`, `goalDate`), item `lookup`, `priceAlerts`, custom `timers`, observed `sackCounts`, and module enabled/pinned coordinates retain their existing formats. `profileId` can select a validated own profile. Profile goal calculations use the most recent explicit snapshot, while XP rates use local session observations.

SkyDash is independent and is not affiliated with or endorsed by Hypixel. Approval of the proposed server integration has not been obtained. See `APPLICATION.md` for the application proposal.
