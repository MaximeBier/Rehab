package rehab.app

import android.app.Application
import rehab.app.di.AppGraph
import rehab.app.service.AndroidRulesNotifier

class RehabApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        AndroidRulesNotifier.createChannel(this)
    }
}
