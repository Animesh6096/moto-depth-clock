# Moto Depth Clock

Unlock Motorola's **Modern depth clock** (the lock screen clock that sits *behind* the subject of your photo) on Motorola phones that ship without it. No root.

> **Unofficial.** This project is not affiliated with, endorsed by, or supported by Motorola or Lenovo. "Motorola" and "Moto" are trademarks of their owners. A Motorola update can change the behaviour this relies on and break it at any time.

## Why this exists

Some Motorola phones on Android 17 (tested: **Edge 60 Pro**) already contain the whole Modern depth clock inside Motorola's *Personalize* app. Only one piece is missing: Motorola's separate depth-effect app, which supplies the photo and the cut-out of its subject. Personalize hides the Modern clock until that app exists.

Moto Depth Clock fills that gap. It installs under the package name Personalize looks for, answers Personalize in the format it expects, and gives you an editor to make the depth photo. The clock itself is still Motorola's own, drawn by the phone's lock screen.

## Features

- Turns on the **Modern** lock screen clock in Personalize.
- Photo editor with on-device subject cut-out (Google ML Kit).
- Pinch to zoom and drag to position the photo, with a live preview of the clock.
- **Front** and **Back** brushes to choose which parts of the photo cover the clock, plus an **Hours in front** switch.
- Clock colour picked from your photo (suggested swatches or an eyedropper).
- All six Modern styles, each keeping its own photo.
- A home screen widget in the style of Modern clock 1.

## Requirements

- A Motorola phone whose Personalize app has the Modern clock but no depth-effect app. Check over USB:
  ```bash
  adb shell pm list packages com.motorola.deptheffect
  ```
  No output means the depth-effect app is missing, which is what this project needs.
- Google Play services (for the ML Kit cut-out model).
- A computer with ADB to install it (it's not on the Play Store).
- No root.

Tested on: Motorola Edge 60 Pro, Android 17, build `A171VVH.36-23`, Personalize `01.0.8.268`.

## Build and install

You need JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew assembleDebug
```

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

The package name must stay `com.motorola.deptheffect`, because Personalize looks for exactly that name. That's also why the app can't be installed on phones that already have Motorola's own depth-effect app (and those phones don't need it).

To sign with your own key so new builds install over the old one, create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## First-time setup

1. Install the app.
2. Open **Settings › Personalize › Lock screen**. A **Modern** page now appears next to Classic. Select it and apply.
3. Open **Depth Clock**, pick a style card, tap **Photo**, position the photo, and tap **Apply**.
4. Restart the phone. Personalize only picks up changes when it starts.

## Using it

**Choose the style in Depth Clock, not in Personalize.** Personalize only offers *Classic* or *Modern*; which of the six Modern styles you get is decided by Depth Clock, and Personalize switches to it on its next start.

| Style | Layout | Look |
|---|---|---|
| 1 | One row | Thin digits. 1st and 3rd digits take your colour and sit behind the subject; 2nd and 4th stay white in front |
| 2, 4, 5, 6 | Stacked | Triple-line digits. Hours behind the subject, minutes and a day pill in front |
| 3 | Date on top | Hours behind, minutes in front, date above |

**Depth controls.** Only parts of the clock in Motorola's back layer can go behind the subject: the hours in stacked styles, and the coloured digits in style 1. Minutes, the day pill and the date are always drawn on top.

- **Move**: pinch and drag the photo.
- **Front**: paint parts of the photo in front of the clock (hair, hands, objects the cut-out missed).
- **Back**: erase parts of the subject so the clock shows over them.
- **Hours in front**: show the whole hours area over the subject.

**Replacing the photo of a style you've used before.** Personalize saves a style's photo only once while in Modern mode, but re-saves it on every start while in Classic mode. So:

1. In Depth Clock, tap **Replace style N**.
2. In Personalize's lock screen settings, switch to **Classic** and apply.
3. Restart the phone.
4. Switch back to **Modern** and apply.

Switching to Classic may also replace your home screen wallpaper with Personalize's cached image; set it again afterwards if needed.

**Home screen widget.** Long-press the home screen › Widgets › Depth Clock › *Modern clock 1*. It uses Motorola's font from Personalize and redraws every minute.

## Limitations

- Every change needs a restart of the phone (Personalize only reads it when it starts).
- Minutes can't go behind the subject in stacked styles.
- Clock resizing isn't available (it needs a system feature flag that can't be set without root).
- The always-on display is unaffected.
- The editor preview approximates Motorola's clock; positions were measured on an Edge 60 Pro.

## Uninstalling

Switch the lock screen to **Classic** in Personalize first, then:

```bash
adb uninstall com.motorola.deptheffect
```

## How it works

See [docs/how-it-works.md](docs/how-it-works.md) for what Personalize expects, how the pieces fit together, and what doesn't work.

## License

[Apache License 2.0](LICENSE). This repository contains only original code. It does not include any Motorola code, apps or fonts; the widget and preview load Motorola's fonts from the phone at runtime.
