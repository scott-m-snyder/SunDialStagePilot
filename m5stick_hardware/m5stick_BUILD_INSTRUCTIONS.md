# M5StickC PLUS2 Firmware Build Instructions

## ⚠️ IMPORTANT: Build From VS Code Terminal ⚠️

Due to local system environment configurations, the PlatformIO CLI (`pio` command) for the M5StickC PLUS2 firmware **must be built and flashed from a VS Code terminal** where PlatformIO is already known to be working (e.g., from your other M5 app's project).

Android Studio's integrated terminal or standalone Windows batch files will likely return "command not found" for `pio`.

---

### **How to Build & Flash the M5StickC PLUS2 Firmware:**

1.  **Open your *other* M5 app's project in VS Code** (the one where PlatformIO *does* work).

2.  **Open a new terminal *within that VS Code project*.** This terminal session has the correct environment to recognize the `pio` command.

3.  **Navigate to *this* `SunDial StagePilot` project's `m5stick_hardware` folder from *that specific terminal*.**
    *   Example: If this project is at `C:\Dropbox\CODE 2026\SunDial StagePilot app\SunDialStagePilot`, you would type:
        `cd "C:\Dropbox\CODE 2026\SunDial StagePilot app\SunDialStagePilot\m5stick_hardware"`
    *   (Remember to use the full path and quotation marks if there are spaces in the directory names.)

4.  **Plug your M5StickC PLUS2 into a USB port on your computer.**

5.  **In that same terminal, run the PlatformIO upload command:**
        `pio run --target upload`

    *   This will compile the C++ firmware and flash it to your M5StickC PLUS2.

---

### **Project Structure:**

*   `platformio.ini`: PlatformIO configuration for the M5StickC PLUS2.
*   `src/main.cpp`: The C++ Arduino sketch for the tap sensor.
*   (Other PlatformIO-generated folders like `.pio`, `.vscode`, `lib`, `include`, `test` will appear after the first build.)

**Note:** The `flash_m5stick.bat` and `install_platformio.bat` files in the project root are deprecated and may not work on your system; please disregard them for the M5Stick build process. Always use the VS Code terminal method as described above.