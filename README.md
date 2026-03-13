# SunDial Stage Pilot

## Project Summary
SunDial Stage Pilot is a specialized Android application designed for high-performance PDF viewing and reading, optimized for large-screen devices like the Samsung Galaxy Z Fold 7. The application aims to provide a reliable and distraction-free document reading experience by offering robust PDF parsing capabilities, enhanced zoom functionality (up to 4x default and 6x high-resolution rendering), and carefully considered usability features like horizontal scroll locking and recent file management.

## Key Features
* **Optimized PDF Rendering:** High-resolution rendering with a 6x clarity boost and a white background erase feature.
* **Large Screen Ready:** Specifically targeted formatting for foldable devices, including a Width-Fit default scale for the Z Fold 7.
* **Usability Enhancements:** Recent Files list, locked horizontal scrolling for strict vertical navigation, and a 4x default zoom level for superior readability.
* **Diagnostics:** Includes a hidden Diagnostic Self-Test mode and dedicated screens for testing text blocks and PDF parsing (`TestBlocksActivity` and `TestPdfParseActivity`).

---

## Roadmap & TODOs

### High Priority
- [ ] **Performance:** Improve PDF rendering performance to avoid main thread jank.
- [ ] **UI/UX:** Add a "Clear Recents" button to the main screen.
- [ ] **Review:** Scott - Review the UI layout specifically on the Z Fold 7.
- [ ] **Features:** Implement a night mode toggle for the PDF viewer.

### Completed
- [x] Fixed `mkdir` task error in Gradle.
- [x] Fixed `IOException` compilation error in `MainActivity`.
- [x] Added white background erase to PDF rendering.
- [x] Created Help Page with navigation instructions.
- [x] Added hidden Diagnostic Self-Test mode.
- [x] Implemented Width-Fit default scale for Z Fold 7.
- [x] Increased rendering resolution to 6x for clarity.
- [x] Implemented Recent Files list on main screen.
- [x] Locked horizontal scroll in PDF viewer.
- [x] Set default 4x zoom for readability.
