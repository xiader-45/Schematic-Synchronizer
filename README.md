# Schematic Synchronizer

**Schematic Synchronizer** is a Fabric mod for Minecraft (1.21.4) that enables seamless synchronization of schematics between a server and its clients, integrating directly into the [Litematica](https://github.com/sakura-ryoko/litematica) interface.

---

## ✨ Features

- **📂 Server Schematics Browser in Litematica**:
  - Adds a dedicated **"Server Schematics"** button to the main Litematica menu (`M`).
  - Full directory navigation with subfolders, folder contents count, and back navigation.
  - Native Litematica UI style (side-by-side browser list, detailed metadata panel on the right, selection highlights).

- **📑 Rich Schematic Metadata**:
  - Shows Author, Date Created, Region count, Total volume, Total blocks, Enclosing bounding box, Minecraft schema version, File size, and Local Cache status.

- **👥 Multi-Player Placement Synchronization**:
  - Automatically synchronizes schematic placements across players on the server.
  - **"Like Player" ("Как у игрока")** button: load and place the schematic at the exact position, rotation, and mirror orientation as another player (with a cycle button `↺` if multiple placements exist).
  - Placements persist on the server across player disconnections and server restarts (`config/schematic-synchronizer/placements.json`).

- **📦 Instant Material List**:
  - View the required materials list directly for any server schematic before placing it.

- **⚡ Fast Chunked Streaming & Local Cache**:
  - Schematics placed in the server's `schematics/` folder are transferred to clients in chunks over custom network packets and cached locally (`schematics/.server_cache/`).

- **🌍 Localization**:
  - Full translations for **English**, **Русский**, and **Українська**, seamlessly utilizing standard Litematica/MaLiLib localization keys.

---

## 🛠 Installation & Requirements

1. Install **Fabric Loader** (0.16.10+ / Minecraft 1.21.4).
2. Install dependencies:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [MaLiLib](https://github.com/sakura-ryoko/malilib) (26.2 / 0.29.4+)
   - [Litematica](https://github.com/sakura-ryoko/litematica) (26.2 / 0.28.5+)
3. Place `schematic-synchronizer` jar file into both the server and client `mods/` folder.
4. Put your `.litematic` or `.schem` files into the `schematics/` directory on your server.

---

## 🏗 Building from Source

```bash
git clone https://github.com/xiader-45/Schematic-Synchronizer.git
cd Schematic-Synchronizer
./gradlew build
```
The compiled jar will be available in `build/libs/`.
