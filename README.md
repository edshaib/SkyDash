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
2. Download `skydash-0.1.0.jar` from [Releases](https://github.com/edshaib/SkyDash/releases).
3. Place SkyDash and Fabric API in your Minecraft `mods` folder. Add Mod Menu if you want its config shortcut.
4. Launch Minecraft, join Hypixel SkyBlock, and press **O** to open SkyDash.

## Getting started

Select a module in the dashboard to see its details. Use **On/Off** to toggle it, **Pin** to add it to your HUD, and **HUD layout** to move pinned panels. Settings include goals, alerts, refresh preferences, and item lookup. The dashboard key can be changed in Minecraft's Controls menu.

To connect your SkyBlock profile, close Minecraft and add your Hypixel API key to `apiKey` in `.minecraft/config/skydash.json`, which is created on first launch. Keep this file private. A configured backend connection can also be used. Check your in-game API visibility settings if profile information is missing.

SkyDash is currently a prerelease. Market values and countdowns are estimates, and some information depends on what Hypixel makes available. Sack values use the latest observed or saved counts.

For building from source, advanced configuration, and technical details, see the [development reference](docs/DEVELOPMENT.md).
