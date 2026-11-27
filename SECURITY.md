# Security Policy

## Supported versions

Only the [latest release](https://github.com/Poyraxx/NeatInk/releases/latest) receives security fixes.

## How NeatInk handles your data

- Notes, PDFs, images, the mistake notebook and the study log are stored **only on your device**, in the app's private storage. Nothing is uploaded.
- The app uses the network for exactly two things:
  - the one-time download of the handwriting recognition model (Google ML Kit);
  - an optional update check, when the device is online, that reads [`version.json`](version.json) from this repository.
- On Xiaomi / HyperOS devices, the optional `WRITE_SECURE_SETTINGS` permission is used **only** to turn on the `gb_boosting` flag while a note is open, so that pen button presses reach the app. The flag is turned off again when you leave the note.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report it privately on GitHub instead: open the repository's **Security** tab and choose **Report a vulnerability**. Include:

- a description of the issue and its impact,
- steps to reproduce or a proof of concept,
- the affected version.

You can expect a first response within a week. When a fix is released, you will be credited in the release notes, unless you prefer not to be.
