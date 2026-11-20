<p align="center">
  <img src="docs/icon.png" width="112" alt="Folio icon">
</p>

<h1 align="center">Folio for Android</h1>

<p align="center">
  A PDF reader that can also sign, fill in, highlight and rearrange your documents. It's the Android companion to <a href="https://github.com/wellsey001/Folio">Folio for Windows</a>.
</p>

<p align="center">
  <a href="https://github.com/wellsey001/Folio-For-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wellsey001/Folio-For-Android?label=download&color=B2452E" alt="Latest release"></a>
  <a href="https://github.com/wellsey001/Folio-For-Android/actions/workflows/build-apk.yml"><img src="https://github.com/wellsey001/Folio-For-Android/actions/workflows/build-apk.yml/badge.svg" alt="Build status"></a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/internet-none-555" alt="No internet permission">
</p>

---

## Why Folio?

- **The everyday PDF jobs in one app.** Read, search, sign, fill in a form, scribble a note, fix a sideways page or join files together.
- **Safe editing.** Changes go onto a working copy with up to 15 steps of undo. The original only changes when you tap **Save**, or use **Save a copy** to leave it untouched.
- **No account, no ads, no internet.** Folio doesn't ask for any permissions; it only opens files you choose.

## Install

1. On your phone, open the **[latest release](https://github.com/wellsey001/Folio-For-Android/releases/latest)** and tap **Folio.apk**.
2. If Android asks, allow your browser to *install unknown apps*.

**Make it your default PDF app:** open a PDF from Files, WhatsApp or your email, choose **Folio**, then tap **Always**.

Updates install over the top of the old version, keeping your recent files and saved signature. To be told about new versions, tap **Watch → Custom → Releases** on this page in the GitHub app.

## Features

### Reading
| | |
|---|---|
| **Scrolling** | All pages in one continuous scroll, with pinch to zoom. |
| **Library** | Recent files, each reopening at the page you were on. |
| **Search** | Find text in the document, with every match highlighted and arrows to step through them. |
| **Comfort** | Night mode, Go to page, and a page counter. |

### Editing
| | |
|---|---|
| **Pen and highlighter** | Four colours each, thin or thick lines, and undo for the last stroke. |
| **Text** | Tap anywhere to add plain text or a yellow sticky note, in four sizes. |
| **Sign** | Draw your signature once, tap to place it, then drag and resize it. It's remembered for next time. |
| **Forms** | Fill in text boxes, tick boxes and drop-downs in PDFs that have fillable fields. |
| **Pages** | Rotate, move earlier or later, or delete pages. |
| **Merge** | Pick several PDFs, put them in order and combine them into one file. |
| **Save and share** | **Save** over the original, **Save a copy** as a new file, or **Share** the current version. |

## Known limits

- Password-protected PDFs can't be opened yet.
- Added text uses a standard PDF font, so non-Latin scripts and emoji appear as "?".
- Scanned PDFs can't be searched unless they've already been through text recognition (OCR).
- Drawings, text and signatures become part of the page when saved, rather than separate comments that can be edited later in other apps.

## Troubleshooting

- **"App not installed"**: Folio needs Android 11 or newer. If you installed a build from before 20 November 2026, uninstall it once first, because it was signed with a different key.
- **Blocked by Play Protect**: tap **More details → Install anyway**.
- **"Can't be changed in place"** when saving: some apps share read-only copies of files. Choose where to save a copy instead.
- **A recent file won't open**: the app that shared it no longer allows access. Open the file again from your Files app.

## Building it yourself

Every push to `main` builds a signed APK with GitHub Actions and publishes it under **Releases**. To build locally, open the folder in Android Studio and press **Run**.

<details>
<summary>Tech and project layout</summary>

Kotlin and Jetpack Compose (Material 3). Pages are drawn with Android's built-in `PdfRenderer`. Edits, text search and merging use [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), which is Apache 2.0 licensed.

```
app/src/main/java/com/wellsey/folio/
  MainActivity.kt          navigation, "Open with Folio"
  data/PdfSession.kt       working copy, page drawing, edits, undo, saving
  data/Edits.kt            drawing, text boxes, signatures, pages, forms, search, merge
  data/Recents.kt          recent files and last page
  ui/ReaderViewModel.kt    app state and actions
  ui/screens/              Library, Reader, page view, tool dialogs, Merge
```

Signing uses the `SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD` repository secrets. Without them, builds still work but use a temporary key.
</details>

## More apps

- **[Glimpse](https://github.com/wellsey001/Glimpse)**: private photo gallery with editing and a photo map
- **[Burrow](https://github.com/wellsey001/Burrow)**: file manager with storage cleanup
