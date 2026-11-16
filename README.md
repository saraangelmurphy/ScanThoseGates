# Scan Those Gates

**Original creator and main author: [AERO](https://github.com/AEROassault/ScanThoseGates). Maintainer: saraangelmurphy.**

[![Install with TriOS](https://img.shields.io/badge/Install_with-TriOS-6750A4?style=for-the-badge)](https://trilink.wispborne.com/open.html?mod=%7B%22id%22%3A%22scan_those_gates%22%2C%22url%22%3A%22https%3A%2F%2Fraw.githubusercontent.com%2Fsaraangelmurphy%2FScanThoseGates%2Frefs%2Fheads%2Fmaster%2FScanThoseGates.version%22%7D&dep=%7B%22version%22%3A%223.0.0%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FLazyWizard%2Flazylib%2Freleases%2Fdownload%2F3.0%2FLazyLib.3.0.zip%22%2C%22id%22%3A%22lw_lazylib%22%7D)

## Features

Scan Those Gates adds three campaign abilities:

- **Remote Gate Scan** scans gates in systems with non-hidden markets and adds them to the intel screen. It requires the Janus Device and an unlocked gate network. Optional settings can reveal or scan gates in systems without markets.
- **Remote Hypershunt Survey** reveals Coronal Hypershunts, adds their locations to **Megastructures** intel and marks them on the map. This also reveals the basic layout of their systems.
- **Remote Cryosleeper Survey** reveals Domain-era Cryosleepers and adds their locations to **Megastructures** intel and the map.

The abilities are available when loading an existing campaign. Hypershunt and Cryosleeper surveys are one-time discoveries; Remote Gate Scan becomes available again when there are more eligible gates to scan or report. Megastructures discovered during normal exploration also appear in the intel list.

## Requirements

- Starsector **0.98.5a**, using Java 17 or newer.
- **LazyLib 3.0.0 or newer**.
- Optional: **LunaLib** for in-game settings.
- Optional: **Console Commands** for the uninstall command.

## Installation

Download **ScanThoseGates.zip** from the [release page](https://github.com/saraangelmurphy/ScanThoseGates/releases/latest). Import it into TriOS, or extract it into Starsector's `mods` directory so `mods/ScanThoseGates/mod_info.json` exists. Enable **Scan Those Gates** and **LazyLib**. Enable only one copy of the mod.

Use Install with TriOS above or the named mod ZIP under Assets. Version Checker provides update notifications, direct downloads and the [changelog](changelog.txt).

## Settings

Use LunaLib's settings menu, or edit `data/config/settings.json` before loading the campaign:

- `RevealInactiveGates`: also show gates in systems without eligible markets. Default: `false`.
- `ActivateInactiveGates`: scan all gates regardless of market presence. Default: `false`.
- `GateScanUsableCheckInterval`: seconds between checks for additional eligible gates. Default: `1`.
- `GateScanDiagnosticsInterval`: seconds between diagnostic timing reports. Default: `60`.

## Removing the mod

With Console Commands installed, load the campaign and run **RemoveScanThoseGates**. Save the game, then disable or remove the mod. This clears its abilities and custom megastructure intel from the save.

## Authors and license

Original mod by **AERO**. Starsector 0.98.5a maintenance by **saraangelmurphy**.

Thanks to **stormbringer951** for Captain's Log code, and **Wispborne, Timid, SafariJohn and ruddygreat** for help and bug fixes.

Based on [AEROassault/ScanThoseGates](https://github.com/AEROassault/ScanThoseGates). Licensed under [CC BY-NC-SA 4.0](LICENSE).

**This maintained version will be removed upon request from the original author.**

<details>
<summary>AI use disclaimer</summary>

Some code and documentation changes in this maintained version were created using AI tools:

- **Code and documentation:** OpenAI Codex, used for compatibility updates, scanner-state bug fixes, and documentation.

This disclosure covers maintenance work in this version; original mod credit remains with AERO and the credited contributors.

</details>
