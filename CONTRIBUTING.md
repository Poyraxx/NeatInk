# Contributing to NeatInk

Thanks for taking the time to contribute! Bug reports, ideas, translations and pull requests are all welcome.

## Ways to help

- **Report a bug.** Open an [issue](https://github.com/Poyraxx/NeatInk/issues/new/choose) with the *Bug report* form. Include your device, Android version, NeatInk version (Settings ⚙) and the steps to reproduce.
- **Suggest a feature.** Use the *Feature request* form. Describe the problem it solves for you, not only what to build.
- **Improve translations.** All UI text is written as `L("Türkçe", "English")` (see [`Lang.kt`](app/src/main/java/com/xaser/note/Lang.kt)), so you can fix wording without touching the logic.
- **Send code.** See below.

## Development setup

1. Install the Android SDK with platform **37**, and use **JDK 17 or 21**.
2. Clone the repository and create `local.properties`:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
3. Build and test:
   ```bash
   ./gradlew assembleDebug          # debug APK
   ./gradlew testDebugUnitTest      # unit tests
   ./gradlew assembleRelease        # minified release APK
   ```
4. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   On Xiaomi / HyperOS, `adb install` may be blocked. If so, push the APK to the device and run `adb shell pm install -r <path>`.

## Project structure

| Path | Purpose |
|---|---|
| `ink/` | Canvas (`InkView`), pens, eraser, lasso, shape snapping, image tracing, stylus buttons |
| `pdf/` | PDF import, page render cache, PDF export |
| `recognition/` | Handwriting recognition (ML Kit) and text placement |
| `data/` | File-based storage: notes, mistake notebook, study log, backup |
| `model/` | Data classes, fonts, YKS score calculation |
| `ui/` | Jetpack Compose screens |

## Pull request guidelines

- **One topic per pull request.** Smaller PRs get reviewed faster.
- **Keep it offline-first.** Features must work without internet. Network use is acceptable only when it is optional and clearly visible to the user.
- **Keep old notes working.** If you change the saved JSON format, old files must still load.
- **Add tests for logic.** Pure logic (geometry, scoring, parsing) belongs in `app/src/test`. For UI changes, add a screenshot to the PR.
- **Both languages.** New UI text needs Turkish and English via `L(...)`.
- **Style.** Follow the existing Kotlin style (4-space indent, no wildcard imports). Comments may be in Turkish or English.
- **Assets and licenses.** Only add fonts, images or data whose license allows redistribution, and list them in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Commit messages

Start with a short summary line of 72 characters or fewer, written as a command, e.g. `Add area eraser size preview`. If more detail is needed, add a blank line and then the details.

## Code of conduct

By taking part in this project, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
