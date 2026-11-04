# Wario Land: Shake It! — Android port

> ⚠️ **Experimental.** The game boots and plays, but parts of it are still untested: expect graphical glitches,
> crashes or features that don't respond yet. If you find one, please open an *issue* and attach the log
> (`Android/data/com.wariorecomp.app/files/Logs/.../console.log`).

An unofficial native Android port of *Wario Land: Shake It!* (Wii, 2008, US release `RWLE01`). It is a fork of
[WarioRecomp](https://github.com/santiagovalencia109/Wario-Land-Shake-It-Native-PC) by **Santiago109**, where the
PC version lives. The game's PowerPC code is **statically recompiled** to C++ with
[WiiCompiled](https://github.com/patchzyy/Wiicompiled) and runs as a regular Android app, **not in an emulator**.

This fork builds the port for Android phones. It runs natively, not in an emulator, with the same recompiled
game code as the PC version.

There is **no APK to download, and none will be published**: an APK contains code and data from the game, so
every user builds their own on a PC, from **their own copy** of the game, and keeps it for their own devices.
Please don't share it.

## Requirements

- A 64-bit phone with Android 11 or later and Vulkan. Tested on one phone so far: ASUS Zenfone 10 (Adreno 740,
  Android 15).
- A Linux PC with clang, CMake 3.25+, Ninja, the .NET 8 SDK and SDL's development libraries (`tools/build-wario.sh`
  says what is missing), plus the Android SDK with NDK 28.2, JDK 17 and Gradle 8.9 or later.
- Your own *Wario Land: Shake It!* (US, `RWLE01`, unmodified).

## Building

```bash
tools/build-wario.sh --disc /path/to/game.iso   # translates the game (once)
tools/build-android.sh                          # builds android/app/build/outputs/apk/release/app-release.apk
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

**First start:** open the app and, on the **Game** tab, press **IMPORT DISC IMAGE** and pick your `.iso`, `.rvz`
or `.wbfs` (it needs about 8 GB free while importing), or **CHOOSE FOLDER** for an already extracted game. Then
press **PLAY**.

## In the app

| Tab | What's there |
|---|---|
| Game | Game folder, disc import, short intro |
| Graphics | Resolution, aspect ratio, V-Sync, FPS counter, GPU compatibility, shader cache |
| Audio | Volume, mute |
| Controls | On-screen button size, opacity and layout, phone tilt and shake, gamepad mapping |
| Saves | Automatic and manual backups, restore |
| GPU Driver | Custom Vulkan drivers for Adreno, imported from a `.zip` or downloaded from GitHub |

The on-screen buttons are a sideways Wii Remote: **JUMP** is 2, **RUN** is 1. Shaking the phone shakes the
remote, and tilting it tilts the remote. **A**, **JUMP** or **+** skip the intro. **EXIT** or Android's Back
returns to the launcher.

Custom GPU drivers are third-party code that runs inside the game. If the game stops starting after you choose
one, select **System default** on the **GPU Driver** tab.

## Status

Working on the tested phone: boot, intro (and skipping it), menus, the on-screen buttons, audio, custom GPU drivers
and disc import. Still to test or to do:

- Playing through the whole game, and how it performs in every level.
- Other phones and GPUs.
- Tilt and shake everywhere the game asks for them.
- Gamepads, and leaving the app and coming back.

## Repository

| Folder | Contents |
|---|---|
| `android/` | The Android app: launcher, touch controls, motion (Java) |
| `fw/` | The parts of WiiCompiled the build uses: the C# translator and the dependency scripts |
| `projects/wario/` | Translation setup: `recomp.yml`, the function map and the verified Wario addresses |
| `runtime/` | The runtime adapted to Wario (adapted from WiiCompiled's runtime) |
| `aurora/` | Aurora, the Wii GX graphics layer (adapted from WiiCompiled's copy) |
| `tools/` | Build scripts |

The PC launchers and scripts from the original project are still in the repository, unchanged.

## Credits

- **Santiago109** — [WarioRecomp](https://github.com/santiagovalencia109/Wario-Land-Shake-It-Native-PC), the PC port this fork is based on.
- **Dreaminerryday** — the Wario fan art used for the launcher header and the icon.
- **[WiiCompiled](https://github.com/patchzyy/Wiicompiled)** — patchzyy and contributors: recompiler and runtime (GPL-3.0).
- **Aurora** — Luke Street: Wii GX graphics on WebGPU (MIT). Dawn, SDL3 and the other components are listed in
  [`fw/THIRD-PARTY-NOTICES.md`](fw/THIRD-PARTY-NOTICES.md).
- **[nod / nodtool](https://github.com/encounter/nod)** — Luke Street: Wii disc image reading (MIT).
- **[libadrenotools](https://github.com/bylaws/libadrenotools)** — Billy Laws: custom GPU drivers on Android (BSD-2-Clause).
- **[Dolphin Emulator](https://dolphin-emu.org)** — Wii SDK function signature database (GPL-2.0+).
- **CMake**, **Ninja**, **.NET** and the **Android NDK** — building.

## License

GPL-3.0 (see [`LICENSE`](LICENSE)), like WiiCompiled. Third-party components keep their own licenses.

## Disclaimer

This is an **unofficial** fan project. It is not affiliated with, or endorsed by, Nintendo or Good-Feel. It
contains no game code or data: you need your own legal copy. *Wario Land: Shake It!* and Wario are trademarks of
Nintendo.
