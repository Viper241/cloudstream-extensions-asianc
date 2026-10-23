package com.asianc

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * vidhide mirror used by AsianC's embed page.
 */
class Minochinos : VidHidePro() {
    override var name = "VidHide"
    override var mainUrl = "https://minochinos.com"
}

/**
 * hglink.to is a StreamWish/HGLink mirror that redirects to hanerix.com.
 * Hanerix's player is a packed JW Player config, so read the generated
 * hls4/hls3/hls2 URLs straight from the unpacked script.
 */
class HgLink : ExtractorApi() {
    override val name = "HGLink"
    override val mainUrl = "https://hglink.to"
    override val requiresReferer = false

    private val redirectUrl = "https://hanerix.com"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringAfterLast('/').substringBefore('?').trim()
        if (id.isBlank()) return

        val target = "$redirectUrl/e/$id"
        val page = app.get(target, referer = referer).text
        val script = if (getPacked(page).isNullOrEmpty()) page else getAndUnpack(page)

        val links = linkedMapOf<String, String>()
        Regex("\"(hls\\d+)\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(script)
            .forEach { links[it.groupValues[1]] = it.groupValues[2] }

        val chosen = listOf("hls4", "hls3", "hls2")
            .firstNotNullOfOrNull { links[it] }
            ?: return

        val resolved = if (chosen.startsWith("http")) {
            chosen
        } else {
            "$redirectUrl/${chosen.trimStart('/')}"
        }
        val type = if (resolved.contains(".m3u8", ignoreCase = true)) {
            ExtractorLinkType.M3U8
        } else {
            ExtractorLinkType.VIDEO
        }

        callback.invoke(
            newExtractorLink(name, name, resolved, type) {
                this.referer = "$redirectUrl/"
            }
        )
    }
}
