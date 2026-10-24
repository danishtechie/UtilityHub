package com.utilityhub.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.utilityhub.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UtilityHubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PDFBoxResourceLoader.init(applicationContext)
        container.appScope.launch(Dispatchers.IO) {
            container.outputStore.purgeStale()           // stale results are removed automatically
            container.entitlement.refresh()
            container.history.load()
        }
    }
}
