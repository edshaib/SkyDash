# SkyDash — developer application

SkyDash is a client-only Fabric dashboard for a signed-in player's own Hypixel SkyBlock progress. It presents Garden, skills, Museum, collections and Bingo alongside public market/news data and local session observations. It does not perform gameplay actions, inspect arbitrary player accounts or require a server mod.

The account UUID comes from the local Minecraft session. Profile discovery checks that this UUID is a member of the selected profile. Skills, collections and Museum views select that member only. API credentials are supplied privately through local configuration, never embedded in the distribution, displayed in-game, logged, sent in query strings, or sent with public requests. An optional HTTPS proxy base may supply upstream credentials; local keys are never forwarded to it. All requests identify `User-Agent: SkyDash/0.1.0`.

Authenticated endpoints:

- `GET /v2/skyblock/profiles?uuid=<self>` — discover/validate own selected profile and seed the shared profile cache.
- `GET /v2/skyblock/profile?profile=<validated-own-profile>` — one shared response for skills, collections and other profile consumers.
- `GET /v2/skyblock/garden?profile=<validated-own-profile>` — own profile Garden.
- `GET /v2/skyblock/museum?profile=<validated-own-profile>` — own member's donations.
- `GET /v2/skyblock/bingo?uuid=<self>` — own event progress.

Public endpoints (no API key header):

- `GET /v2/skyblock/bazaar`
- `GET /v2/skyblock/firesales`
- `GET /v2/skyblock/news`
- `GET /v2/resources/skyblock/items`
- `GET /v2/resources/skyblock/collections`
- `GET /v2/resources/skyblock/election`
- `GET /v2/resources/skyblock/skills` — public level XP thresholds.
- `GET /v2/resources/skyblock/bingo` — event goal names and requirements.

One shared API client owns all HTTP traffic and cached responses. Polling only runs on Hypixel SkyBlock. Requests run on a background worker and fail softly.

**Budget: at most 3 authenticated request starts in any rolling 5 minutes per local account/instance.** A capacity-3 token bucket refills one token per 100 seconds; an additional rolling 300-second guard prevents burst/refill overlap. Request timestamps are persisted before dispatch, so a restart cannot immediately reset the budget. All authenticated endpoint types, failures, manual requests and proxy calls share this budget. Multiple simultaneous processes are outside this local coordination scope; an upstream proxy should additionally enforce an account-wide budget.

Profile cache TTL is configurable from **120 to 300 seconds**, default **180**. Garden and Museum are separate Hypixel endpoints and do not come in the shared profile response. Private endpoints rotate under the single budget; exhausted budget retains cached data and displays its age. Manual refresh is limited to once per **30 seconds** and does not bypass the budget. Public endpoints honor `Cache-Control: max-age` and `Age`, with a default 60-second fallback, and send no key. HTTP 429 honors `RateLimit-Reset`; authenticated 401/403 stops retrying until credentials change. No real key is included in fixtures or build artifacts.
