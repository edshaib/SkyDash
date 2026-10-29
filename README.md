# SkyDash

SkyDash brings your Hypixel SkyBlock progress together in one in-game dashboard. Check your Garden, follow skill goals, keep up with collections and Museum donations, and watch market prices without leaving the game.

Choose the modules you want, pin useful information to your HUD, and drag each panel into place.

## Features

- **Progress tracking** — skills, XP rates, goals, collections, Museum donations, and Bingo.
- **Garden overview** — visitors, unlocked plots, crop totals, and estimated crop earnings.
- **Market tools** — Bazaar margins, price alerts, and estimated inventory and sack values.
- **Session stats** — observed XP, items, and changes to your purse during a session.
- **Events and updates** — countdowns, fire sale alerts, and a rotating SkyBlock news ticker.
- **Customizable HUD** — toggle modules, pin panels, and arrange your layout.

## Requirements

- Minecraft Java Edition **26.1.2**
- Java **25**
- Fabric Loader **0.20.1 or newer**
- Fabric API **0.155.3+26.1.2**
- Optional: Mod Menu **18.0.2** for access through the Mods menu

SkyDash runs entirely on your client. Nothing needs to be installed on the server.

## Installation

1. Set up Fabric for Minecraft **26.1.2**.
2. Download `skydash-0.1.1.jar` from [Releases](https://github.com/edshaib/SkyDash/releases).
3. Place SkyDash and Fabric API in your Minecraft `mods` folder. Add Mod Menu if you want its config shortcut.
4. Launch Minecraft, join Hypixel SkyBlock, and press **O** to open SkyDash.

## Getting started

Select a module in the dashboard to see its details. Use **On/Off** to toggle it, **Pin** to add it to your HUD, and **HUD layout** to move pinned panels. Settings include goals, alerts, refresh preferences, and item lookup. The dashboard key can be changed in Minecraft's Controls menu.

SkyDash never asks for a Hypixel API key. Market information, local session stats, timers, and inventory estimates work directly in the mod.

Profile features use the SkyDash service when it is available. **The service is not deployed in this prerelease**, so Garden, skill progress, Museum, collections, and personal Bingo data currently show as unavailable. Opening the dashboard or choosing Refresh requests a snapshot when the service is configured; profile data is not polled in the background. XP rates use local observations.

SkyDash is an independent project and is not affiliated with or endorsed by Hypixel.

SkyDash is currently a prerelease. Market values and countdowns are estimates, and some information depends on what Hypixel makes available. Sack values use the latest observed or saved counts.

For building from source, advanced configuration, and technical details, see the [development reference](docs/DEVELOPMENT.md).
