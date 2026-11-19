package com.blackeye.klipnote

import android.app.Application

class KlipNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocales.applyFromPrefs(this)
        RemoteRouterConfig.loadCached(this)
        CookieStore.migrateLegacy(this)
        SealDownloader.initAsync(this)
    }
}
