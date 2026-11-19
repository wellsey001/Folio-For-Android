# Folio for Android

A PDF reader with editing, the Android companion to Folio for Windows. It has no ads, no account, and no internet permission.

## Reading
- Continuous scrolling through pages, with pinch to zoom.
- Remembers the page you were on, and keeps a list of recent files.
- **Search** the text, with every match highlighted and arrows to step through them.
- **Night mode**, **Go to page**, and a page counter.
- You can set Folio as your **default PDF app**: open any PDF, choose Folio, then tap *Always*.

## Editing
All edits go onto a working copy first, so nothing changes until you tap **Save**. **Undo** goes back up to 15 steps.

- **Pen** and **Highlighter**, each with 4 colours, a thin or thick line, and undo for the last stroke.
- **Text**: tap anywhere to add plain text or a yellow sticky note, in four sizes.
- **Sign**: draw your signature once, place it with a tap, drag it into position and resize it. It's saved for next time.
- **Fill in form**: works with PDFs that have fillable fields (text boxes, tick boxes, drop-downs).
- **Pages**: rotate, move earlier or later, or delete pages.
- **Merge PDFs**: pick several files, put them in order and combine them into one.
- **Save** writes over the original file. **Save a copy** creates a new file. **Share** sends the current version.

## Limits in this version
- Password-protected PDFs can't be opened yet.
- Text you add uses a standard PDF font, so non-Latin scripts and emoji show as "?".
- Scanned PDFs have no text to search unless they've been through text recognition (OCR).

## Build and install
Push this folder to a GitHub repository. The included workflow builds `Folio.apk` and publishes it on the **Releases** page. Folio needs Android 11 or later.

Pages are drawn with Android's built-in PDF renderer, and edits are made with [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), which is Apache-licensed.
