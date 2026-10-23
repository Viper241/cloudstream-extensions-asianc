package com.asianc

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AsianCPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AsianC())
        registerExtractorAPI(HgLink())
        registerExtractorAPI(Minochinos())
    }
}
