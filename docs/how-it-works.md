# How it works

Notes from studying how Motorola's Personalize app (`com.motorola.personalize`) behaves on a Motorola Edge 60 Pro running Android 17. Everything here was observed on that phone: its logs, its manifest, and how it responds. Other models or later updates may differ.

## The pieces

| Piece | Owner | Role |
|---|---|---|
| Personalize | Motorola (system app) | Lock screen settings; stores each Modern style's photo and cut-out |
| SystemUI plugin from Personalize | Motorola | Draws the clock on the lock screen in two layers |
| Depth-effect app (`com.motorola.deptheffect`) | Motorola on some models; **this project** elsewhere | Supplies the photo, the cut-out, the style and the colour |

On the lock screen, from bottom to top:

1. The lock wallpaper: the background photo, drawn by the depth-effect app's live wallpaper.
2. The clock's **back** layer.
3. The **cut-out**: a full-screen image, transparent except for the subject.
4. The clock's **front** layer.

## Why the Modern clock is hidden

Personalize shows the Modern option only when both of these are true:

1. An app named `com.motorola.deptheffect` is installed and contains a live wallpaper service.
2. Personalize holds the permission `com.motorola.permission.depthwallpaper.BIND_DEPTH_WALLPAPER`.

Personalize already requests that permission, but on phones without the depth-effect app nothing defines it. Android grants it automatically once an app that defines it (with normal protection) is installed. Personalize doesn't check who signed the depth-effect app.

## How Personalize gets the photo

Personalize asks the depth-effect app for its images through a content provider:

- Authority: `com.motorola.deptheffect.depth_wallpaper`
- Call: `request_current_wallpaper`
- Reply (a `Bundle`):

| Key | Type | Meaning |
|---|---|---|
| `current_modern_clock_face_id` | int | Modern style, `101`–`106` |
| `current_modern_color_id` | int (ARGB) | Clock colour; white if missing |
| `current_modern_wallpaper_pfd_bg` | ParcelFileDescriptor | Full photo |
| `current_modern_wallpaper_pfd_fg` | ParcelFileDescriptor | Subject cut-out (PNG with transparency) |
| `current_animated_wallpaper_pfd_thumb` | ParcelFileDescriptor | Thumbnail |

Personalize copies these into its own storage, and the lock screen reads them from there.

### When Personalize asks

- **Only while it starts up**, which in practice means after a phone restart.
- At startup it switches its Modern style to whatever `current_modern_clock_face_id` says. This is why the style is chosen in this app.
- **In Modern mode** it saves the images only if that style has no saved files yet. A style's first photo sticks.
- **In Classic mode** it re-saves the images into the reported style on every start. That's the way to replace a used style's photo without wiping data.

Personalize also asks again when the wallpaper's colours change, but that only refreshes its in-memory copy, not the files the lock screen uses.

### The push route Motorola's own app uses

Motorola's depth-effect app can push a new cut-out straight into Personalize (a `write_foreground_pfd` call on Personalize's `lockscreenbackup` provider). That provider requires a signature-level Motorola permission, and Android enforces it before the call is delivered, so a third-party app can't use it.

### Opening the editor

Personalize's pencil button on the Modern page launches the intent action `com.motorola.deptheffect.INTERNAL_SCREEN_EDIT`. If the editor returns `RESULT_OK`, Personalize closes its own screen and expects the depth-effect app to have applied Modern itself (via the push route above). This app returns `RESULT_CANCELED` so the user can apply Modern in Personalize instead.

## Styles

The six Modern styles render as three layouts on the Edge 60 Pro:

| Style id | Layout | Back layer | Front layer |
|---|---|---|---|
| 101 | One row | 1st and 3rd digits, in the chosen colour | 2nd and 4th digits, white |
| 102, 104, 105, 106 | Stacked, day pill | Hours | Minutes, day pill |
| 103 | Date on top | Hours | Minutes, date |

Personalize's built-in defaults are identical for 102 and 104–106, so those four look the same; each still keeps its own photo.

The clock fonts are assets inside Personalize (for example `font/Moto_Light_1.ttf` for style 1 and `font/Moto_Line_Stacked.ttf` for the stacked styles). The editor preview and the widget load them from Personalize at runtime with `createPackageContext`. They aren't bundled here.

## Brightness

The lock screen dims the wallpaper layer but not the cut-out. Measured on the Edge 60 Pro, the wallpaper appears at about **0.89** of the photo's brightness and the cut-out at about 1.01. Uncorrected, subjects (and brush strokes) look slightly too bright and "pasted on". The app darkens the saved cut-out by 0.89 so both layers match (measured afterwards: 0.900 vs 0.895).

## What doesn't work

- **Clock resizing:** gated behind a system feature (`com.motorola.deptheffect.clock_resize`) that only a system image can declare.
- **Instant updates:** the push route is permission-protected, so every change waits for Personalize to restart.
- **Minutes behind the subject** in the stacked styles: they're in the front layer, above the cut-out.
- **Always-on display:** uses its own clock, unaffected by this.
- **ADB feature flags:** nothing related to the depth clock is on Android's list of flags that can be changed without root.

## Recovering from a mess

If styles end up with mismatched photos, wiping Personalize's data resets everything. This **permanently deletes all of Personalize's settings**, including lock screen choices and possibly saved themes:

```bash
adb shell pm clear --user 0 com.motorola.personalize
```

Then use **⋮ › Mark all styles empty** in Depth Clock.
