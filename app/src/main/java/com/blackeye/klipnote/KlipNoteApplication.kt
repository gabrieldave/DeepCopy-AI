package com.blackeye.klipnote

import android.app.Application

class KlipNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SealDownloader.initAsync(this)
    }
}
