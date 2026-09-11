Schematic Synchronizer
====================
Schematic Synchronizer is a client-and-server Fabric mod for Minecraft 1.21.4 that integrates directly into [Litematica](https://github.com/sakura-ryoko/litematica).
It allows server administrators and players to host schematics on the server side, browse them via a dedicated Litematica menu, download them on demand, and synchronize placements between players in real time.

Requirements
============
### Client
* [Fabric Loader](https://fabricmc.net/) (0.16.10+)
* [Fabric API](https://modrinth.com/mod/fabric-api)
* [MaLiLib](https://github.com/sakura-ryoko/malilib) (26.2 / 0.29.4+)
* [Litematica](https://github.com/sakura-ryoko/litematica) (26.2 / 0.28.5+)

### Server
* [Fabric Loader](https://fabricmc.net/) (0.16.10+)
* [Fabric API](https://modrinth.com/mod/fabric-api)

Usage & Features
================

### Server Schematics Browser
Open the main Litematica menu (default hotkey `M`). A **"Server Schematics"** button is added right below the Configuration button.
* **Directory Navigation**: Browse folders and subdirectories on the server with entry counts and navigation.
* **Detailed Info Box**: Inspect the schematic author, creation date, volume, total block count, enclosing dimensions, schema data version, and whether the file is cached locally.
* **Material List**: Direct button to inspect the material list without having to place the schematic in the world.

### Placement Synchronization
* **Like Player ("Как у игрока")**: If another player has placed a server schematic in the world, you can click the placement button to automatically load and place it with the exact coordinates, rotation, and mirror orientation used by that player. A cycle button (`↺`) is provided when multiple placements are available.
* **Persistent Placements**: Active placements are stored on the server (`config/schematic-synchronizer/placements.json`) and persist across player reconnections and server restarts.

### Server Setup
Place your `.litematic` or `.schem` files into the `schematics/` folder in your server directory. Clients connecting to the server will automatically receive the catalog.

Compiling
=========
* Clone the repository:
  `git clone https://github.com/xiader-45/Schematic-Synchronizer.git`
* Open a command prompt or terminal in the repository directory
* Run `gradlew build` (or `./gradlew build` on Linux/macOS)
* The built jar file will be located in `build/libs/`

Credits
=======
* [masa](https://github.com/maruohon) for the original Litematica and MaLiLib
* [sakura-ryoko](https://github.com/sakura-ryoko) for modern ports of the Masa ecosystem
