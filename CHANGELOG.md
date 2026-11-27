# Changelog

All notable changes to NeatInk are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased] — 1.3.0

### Added
- **Study screen**:
  - exam countdown (default: YKS 2027, TYT on 19 June 2027; editable);
  - Pomodoro timer with subjects and short/long breaks;
  - a "how many questions?" log after each session;
  - today's and the last 7 days' statistics, and a daily streak;
  - a **net progress chart** built from exam-mode results.
- **Digitize images**: when adding an image, tick *Digitize* to turn its text and numbers into editable text boxes (on-device OCR) and its lines and shapes into pen strokes. Clean shapes snap to circles, rectangles, triangles and lines.
- **YKS calculator** (Turkish UI): nets, TYT / SAY / EA / SÖZ raw and placement scores with OBP, estimated ranking from 2025 placement data, and the score needed for a target ranking.
- Contributing guide, code of conduct, security policy, issue and pull-request templates, and a CI workflow.

## [1.2.0] — 2026-11-27

### Added
- **Mistake notebook**: save cut questions without your solution, tag them by subject and topic, review them, and jump back to the source page.
- **Exam mode**: stopwatch with time limit, answer sheet, answer key, correct/wrong/blank counts and net score (YKS / LGS), and sending wrong answers to the mistake notebook.
- **Hide solutions → solve again**, then keep or discard the new attempt.
- **Backup & restore** of all notes as a `.zip` file.

## [1.1.1] — 2026-11-27

### Changed
- The app is renamed from "Not" to **NeatInk**.

## [1.1.0] — 2026-11-27

### Added
- First public release: five pressure pens, eraser, lasso, shape snapping, PDF import and annotation, solution space, cut question, PDF export, handwriting auto-fix with selectable fonts, Xiaomi pen buttons, English/Turkish UI and an update check.

[Unreleased]: https://github.com/Poyraxx/NeatInk/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/Poyraxx/NeatInk/releases/tag/v1.2.0
[1.1.1]: https://github.com/Poyraxx/NeatInk/releases/tag/v1.1.1
[1.1.0]: https://github.com/Poyraxx/NeatInk/releases/tag/v1.1.0
