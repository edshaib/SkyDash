# SkyDash — API application proposal

SkyDash is an independent client-side Fabric dashboard for a player's own Hypixel SkyBlock progress. It is not affiliated with or endorsed by Hypixel. It provides customizable progress views, market information, timers and local session observations. Project: https://github.com/edshaib/SkyDash

The public client does not accept players' Hypixel API keys, embed an application key, or send authenticated requests directly to Hypixel. The client currently runs public-data and local features; its optional profile-service integration has no deployed backend yet.

I am requesting review of a planned SkyDash-only backend integration. Before deployment, this backend will authenticate each player's account, restrict access to their own profiles, keep the registered application's key server-side, cache responses and enforce a shared application-wide request limit. The backend and account-verification flow are still to be implemented; this application does not claim they already exist.

Proposed authenticated endpoints: `/v2/skyblock/profiles`, `/v2/skyblock/profile`, `/v2/skyblock/garden`, `/v2/skyblock/museum`, and `/v2/skyblock/bingo`. The client requests profile snapshots only on dashboard opening or explicit Refresh actions. Shared profile data is cached for 120–300 seconds. There is no recurring private polling, remote session-gain detection or historical player tracking. XP rates and session gains use local observations.

The planned backend budget is at most three authenticated upstream requests per rolling five minutes across the application, including failures, with no increased limit requested. It will honor rate-limit reset headers and stop authenticated retries after authorization failures. The client already enforces an additional three-request local budget, persistent across restarts, and a 30-second cooldown for dashboard refresh actions; this does not replace the required backend-wide limit.

Public Bazaar, fire sales, news, item, collection, election, skill and Bingo resource endpoints are fetched without a key and cached using response cache headers. SkyDash does not automate gameplay, identify nicked players, track unrelated accounts, offer gambling or provide an API proxy for other developers. No monetization is proposed in this application.

Please confirm that this proposed access pattern is acceptable before the authenticated backend is deployed.
