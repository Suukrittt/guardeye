# GuardEye – YIIC 9 prototype (v0.1)
Passive-style digital safety app: Start/Stop Protection, real-time warning banner, scan history. Matches the Week 1 sketches (closed/open eye, Start/Stop button, "Protection Activated" timer, Log table, top warning banner over a social feed).

## Run (PC)
`cd GuardEye && python3 -m http.server 8000` → open http://localhost:8000 (Chrome/Edge/Firefox). No installs, no paid services.
## Android
Serve over HTTPS (e.g. GitHub Pages / Replit deployment), open in Chrome → menu → "Install app". PWA install requires HTTPS (localhost is exempt on the same device).
## What really works
Screen monitor (PC Chrome/Edge): user-consented getDisplayMedia capture -> duplicate-frame skip -> on-device OCR (Tesseract.js, loaded from jsDelivr on first use) -> rule-based scam-wording check -> warning + history. UNTESTED in a real browser so far.

Protection session (persisted start time, live timer, no duplicate sessions), scam-wording rules for text, hidden-metadata scan for image/video/audio files (AI-tool markers, editing software), warning banner, details/"why" view, persistent history (localStorage) with search/filter/sort/clear, settings, offline shell (service worker).
## Simulated / not built
Demo feed media verdicts (posts 2, 3, 5) are labelled demonstration scenarios; pixel-level image, frame-level video and synthetic-voice detection; Python backend/DB; native Android (MediaProjection) and browser extension for cross-app monitoring.
## Scores
Shown as "Est. score" (risk estimate), never accuracy.

## Not built (needs native work, cannot be done in a browser)
Android MediaProjection foreground service + OCR (e.g. ML Kit), Windows desktop capture app, trained AI-image/video/voice detectors, backend/DB. The demo feed no longer produces scripted media verdicts; only real text analysis runs on it.
