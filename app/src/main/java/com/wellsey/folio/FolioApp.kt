package com.wellsey.folio

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class FolioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Loads the fonts and resources PDFBox needs for editing.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
