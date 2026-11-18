package com.blackeye.klipnote

import android.app.Application

class KlipNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteRouterConfig.loadCached(this)
        CookieStore.migrateLegacy(this)
        SealDownloader.initAsync(this)
    }
}
