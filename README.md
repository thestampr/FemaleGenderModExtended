<div align="center">

<img src="https://raw.githubusercontent.com/thestampr/FemaleGenderModExtended/main/banner.png" alt="Female Gender Mod Extended" width="100%">

# FemaleGenderModExtended

An extended Fabric edition of Female Gender Mod for Minecraft 1.21.11.

![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62B47A)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4)
![Java 21](https://img.shields.io/badge/Java-21-E76F00)
![LGPL-3.0-or-later](https://img.shields.io/badge/License-LGPL--3.0--or--later-blue)

</div>

> [!IMPORTANT]
> FemaleGenderModExtended is an unofficial fork of
> [Female Gender Mod](https://github.com/FemaleGenderMod/FemaleGenderMod). It keeps the upstream
> `wildfire_gender` mod ID for compatibility, so it replaces the original mod and must not be installed alongside it.

## What Extended adds

- Separate breast and butt appearance controls, including size, position, separation, and depth.
- Independent or linked breast and butt physics with intensity and momentum controls.
- Classic and experimental realistic body rendering with soft-body deformation.
- Armor-aware breast and butt rendering, including armor textures, trims, glint, and configurable hiding and physics.
- An in-game texture editor for body, overlay, jacket, and pants UV layouts.
- Optional Player Animation Library integration so supported custom animations can influence body physics.
- Multiplayer synchronization of the extended player profile when the server also runs FemaleGenderModExtended.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.15.0 or newer
- Fabric API modules required by the mod
- Java 21
- Player Animation Library 1.1.10 or newer is optional

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
2. Download the latest FemaleGenderModExtended JAR from this repository's
   [Releases](https://github.com/thestampr/FemaleGenderModExtended/releases) page when a release is available.
3. Place the JAR in the Minecraft `mods` folder.
4. Remove the original Female Gender Mod JAR if it is installed.

Press **H** in game to open Character Personalization.

## Synchronization and compatibility

FemaleGenderModExtended is primarily client-sided. Other players need a compatible profile source before their
extended appearance can be shown correctly:

- On a server with FemaleGenderModExtended installed, the extended profile can be synchronized between players.
- The upstream public cloud service understands the original breast profile but not the Extended butt data. Butt
  settings therefore require server-assisted FemaleGenderModExtended synchronization.
- Animation mods that transform the vanilla torso model are supported through that transform path. Mods that replace
  the player renderer completely may require a dedicated compatibility adapter.
- The mod currently declares conflicts with 3D Skin Layers and Essential.

## Building from source

Clone the repository and run the Gradle build from its root:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The built JAR is written to `build/libs`.

## Credits and attribution

FemaleGenderModExtended is based on
[Female Gender Mod](https://github.com/FemaleGenderMod/FemaleGenderMod), originally created by **WildfireRomeo** and
developed by its maintainers and contributors. This fork began from upstream release
[`5.0.0-Beta.3+1.21.11`](https://github.com/FemaleGenderMod/FemaleGenderMod/tree/5.0.0-Beta.3%2B1.21.11).

Extended development is maintained by [thestampr](https://github.com/thestampr). This project is not affiliated with
or supported by the upstream maintainers; issues specific to Extended should be reported in this repository's
[issue tracker](https://github.com/thestampr/FemaleGenderModExtended/issues).

Additional third-party asset attribution is retained in
[`CREDITS.txt`](./src/main/resources/assets/wildfire_gender/CREDITS.txt).

## License

FemaleGenderModExtended retains the upstream copyright notices and is distributed under the
[GNU Lesser General Public License version 3 or later](./LICENSE). Modified versions must preserve the applicable
license and attribution notices.
