package nart.simpleanki

import android.app.Application
import nart.simpleanki.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class AzriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@AzriApplication)
            modules(appModule)
        }
    }
}
