# Handoff notes (paste into a new chat)

Project: One UI TV launcher for a projector (Android 10 box, 32-bit, so no blur APIs). Owner: Wes, Windhoek.
Repo name: one-ui-tv. Package: com.wes.oneuitv. Plain Android Views, Kotlin 1.9, AGP 8.6.1, minSdk 24, targetSdk 29.

History: an earlier app (com.wes.projos, repo S-m-wsly/projector-home) got an iOS liquid-glass design. The user scrapped that and chose the One UI TV design in design/OneUI_TV_preview.html. This repo is the Android port of it.

Status: the code was written without an Android SDK and has NOT been compiled yet. First step in a new chat: check the GitHub Actions result and fix any compile errors, then check the look on the device (adb exec-out screencap -p > screen.png).

Design decisions the user made: no eye comfort / DND / night mode / game mode / volume / brightness. Quick panel is vertical, from the right, with the settings list inside.

Gotchas from the old project: Kotlin files must be in app/src/main/java/com/wes/oneuitv/. GitHub web upload cannot take empty folders. Use `adb uninstall` first if an install ever fails with a signature mismatch.
