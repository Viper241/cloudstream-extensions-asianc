package com.asianc

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AsianC : MainAPI() {
    override var mainUrl = "https://asianc.id"
    override var name = "AsianC"
    override var lang = "en"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf("" to "Home")

    private val episodeRegex = Regex("""(?i)\b(?:episode|ep)\s*(\d+)""")
    private val episodeHrefRegex = Regex("""-episode-(\d+)""")
    private val yearRegex = Regex("""\((\d{4})\)""")
    private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$""")
    private val onclickRegex = Regex("""window\.location\s*=\s*['"]([^'"]+)['"]""")

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val home = app.get("$mainUrl/").document
        val lists = mutableListOf<HomePageList>()

        home.selectFirst("div.tab-content.left-tab-1 ul.list-episode-item")
            ?.episodeItems()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lists += HomePageList("Recently Updated", it) }

        home.selectFirst("div.tab-content.left-tab-2 ul.list-episode-item")
            ?.episodeItems(TvType.Movie)
            ?.takeIf { it.isNotEmpty() }
            ?.let { lists += HomePageList("Recently Added Movies", it) }

        home.selectFirst("div.tab-content.left-tab-3 ul.list-episode-item")
            ?.episodeItems()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lists += HomePageList("Recently Added K-Shows", it) }

        runCatching {
            app.get("$mainUrl/most-popular-drama").document
                .selectFirst("ul.switch-block.list-episode-item")
                ?.detailItems()
                .orEmpty()
        }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lists += HomePageList("Most Popular", it) }

        home.selectFirst("div.tab-content.right-tab-3 ul")
            ?.plainLinkItems()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lists += HomePageList("Ongoing", it) }

        home.selectFirst("div.tab-content.right-tab-2 ul")
            ?.plainLinkItems()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lists += HomePageList("Coming Soon", it) }

        return newHomePageResponse(lists, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return fetchSearch(query, 1).first
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val (items, hasNext) = fetchSearch(query, page)
        return newSearchResponseList(items, hasNext)
    }

    private suspend fun fetchSearch(
        query: String,
        page: Int,
    ): Pair<List<SearchResponse>, Boolean> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = app.get("$mainUrl/search?type=search&keyword=$encoded&page=$page").document

        val items = document.selectFirst("ul.switch-block.list-episode-item")
            ?.detailItems()
            ?: document.selectFirst("ul.list-episode-item")?.detailItems()
            ?: emptyList()

        val hasNext = document.selectFirst("ul.pagination li.next a") != null
        return items to hasNext
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val body = app.get(
                "$mainUrl/api?a=search&keyword=$encoded&type=movies",
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to "$mainUrl/",
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                ),
            ).text

            val array = JSONArray(body)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val href = item.optString("url")
                    if (href.isBlank()) continue
                    val title = item.optString("name").ifBlank { item.optString("value") }
                    if (title.isBlank()) continue
                    add(
                        newTvSeriesSearchResponse(title, fixUrl(href)) {
                            this.posterUrl = fixUrlNull(item.optString("cover"))
                        }
                    )
                }
            }
        }.getOrNull()
    }

    override suspend fun load(url: String): LoadResponse? {
        var document = app.get(url).document

        // Movie episode pages contain only the watch block and episode list
        // (no "details" metadata block). Follow their category link to the
        // movie's detail page so the user gets a proper detail screen instead
        // of an "Error loading" page.
        if (document.selectFirst("div.details") == null) {
            val detailHref = document.selectFirst("div.info-drama div.category a")
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
            if (detailHref != null) {
                val detailUrl = fixUrl(detailHref)
                if (detailUrl != url) {
                    document = app.get(detailUrl).document
                }
            }
        }

        val details = document.selectFirst("div.details") ?: return null
        val info = details.selectFirst("div.info") ?: details

        val title = info.selectFirst("h1")?.text()?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val poster = details.selectFirst("div.img img")?.attr("src")
            ?.takeIf { it.isNotBlank() }

        val seriesHref = document.selectFirst("div.info-drama div.category a")?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?: info.selectFirst("p.other_name a")?.attr("href")?.takeIf { it.isNotBlank() }
        val seriesUrl = seriesHref?.let { fixUrl(it) } ?: url

        val paragraphs = info.select("p")
        val plot = paragraphs.plotText()
        val year = yearRegex.find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: paragraphValue(paragraphs, "Released")?.let { value ->
                yearRegex.find(value)?.groupValues?.get(1)?.toIntOrNull()
                    ?: value.trim().toIntOrNull()
            }
        val tags = paragraphs.firstOrNull {
            it.selectFirst("span")?.text()?.contains("Genre", ignoreCase = true) == true
        }?.select("a")?.map { it.text().trim() }?.filter { it.isNotBlank() }?.distinct()
        val contentRating = paragraphValue(paragraphs, "Content Rating")?.trim()
            ?.takeIf { it.isNotBlank() }
        val status = paragraphValue(paragraphs, "Status")?.trim()

        val episodes = parseEpisodes(document, poster)
        // Dramacool lists movies as a single "episode". A real series has an
        // "Episodes:" metadata row even when it currently has one episode.
        // Check only the label spans so a plot mentioning "episodes:" cannot
        // turn a movie into a series.
        val hasEpisodeMetadata = paragraphs.any {
            it.selectFirst("span")?.text()?.contains("Episodes", ignoreCase = true) == true
        }
        val isMovie = episodes.size <= 1 && !hasEpisodeMetadata

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                seriesUrl,
                TvType.Movie,
                episodes.firstOrNull()?.data ?: "",
            ) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = plot
                this.year = year
                this.tags = tags
                this.contentRating = contentRating
            }
        } else {
            newTvSeriesLoadResponse(title, seriesUrl, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = plot
                this.year = year
                this.tags = tags
                this.contentRating = contentRating
                this.showStatus = when {
                    status == null -> null
                    status.contains("Ongoing", ignoreCase = true) -> ShowStatus.Ongoing
                    else -> ShowStatus.Completed
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(data).document
        val iframeSources = document
            .select("div.watch-iframe iframe[src], iframe[src]")
            .mapNotNull { it.attr("src").trim().takeIf { source -> source.isNotBlank() } }
            .distinct()

        if (iframeSources.isEmpty()) return false

        var found = false
        for (source in iframeSources) {
            val iframeUrl = fixUrl(source)
            if (iframeUrl.contains("vidbasic.", ignoreCase = true)) {
                found = loadVidBasic(iframeUrl, callback) || found
            } else {
                found = loadExtractor(iframeUrl, data, subtitleCallback, callback) || found
            }
        }
        return found
    }

    private suspend fun loadVidBasic(
        embedUrl: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(embedUrl, referer = "$mainUrl/").document
        val servers = document.select("li.linkserver")
        if (servers.isEmpty()) return false

        var found = false
        for (server in servers) {
            val provider = server.attr("data-provider").ifBlank { "VidBasic" }
            val rawVideo = server.attr("data-video").trim()
            if (rawVideo.isBlank()) continue

            val videoUrl = resolveUrl(embedUrl, rawVideo)
            when {
                videoUrl.contains("3rdplayer", ignoreCase = true) ||
                    provider.contains("standard", ignoreCase = true) ->
                    found = loadVidBasicStandard(videoUrl, provider, callback) || found

                // Upns is a separate Vidstack SPA with an encrypted API; skip it
                // instead of producing a dead link.
                videoUrl.contains("upns.pro", ignoreCase = true) -> Unit

                else -> found = loadExtractor(videoUrl, embedUrl, { _ -> }, callback) || found
            }
        }
        return found
    }

    private suspend fun loadVidBasicStandard(
        playerUrl: String,
        provider: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(playerUrl, referer = "https://vidbasic.top/").document
        val key = document.selectFirst("script[data-name=crypto]")?.attr("data-value")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("script[data-value]")?.attr("data-value")
                ?.takeIf { it.isNotBlank() }
            ?: return false

        val videoUrl = decryptVidBasic(key)?.takeIf { it.startsWith("http") } ?: return false
        val type = if (videoUrl.contains(".m3u8", ignoreCase = true)) {
            ExtractorLinkType.M3U8
        } else {
            ExtractorLinkType.VIDEO
        }

        val displayName = provider.trim()
            .ifBlank { "VidBasic" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        callback.invoke(
            newExtractorLink(
                source = "VidBasic",
                name = displayName,
                url = videoUrl,
                type = type,
            ) {
                this.referer = "https://vidbasic.top/"
            }
        )
        return true
    }

    private fun decryptVidBasic(data: String): String? {
        return runCatching {
            val keySpec = SecretKeySpec(VIDBASIC_KEY.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(VIDBASIC_IV.toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            String(cipher.doFinal(base64DecodeArray(data)), Charsets.UTF_8).trim()
        }.getOrNull()
    }

    private fun parseEpisodes(
        document: Document,
        poster: String?,
    ): List<Episode> {
        return document.select("ul.all-episode li a").mapNotNull { link ->
            val href = link.attr("href").ifBlank {
                onclickRegex.find(link.selectFirst("h3")?.attr("onclick").orEmpty())
                    ?.groupValues?.get(1).orEmpty()
            }
            if (href.isBlank()) return@mapNotNull null

            val fullName = link.selectFirst("h3.title")?.text()?.trim()
                .orEmpty()
                .ifBlank { link.text().trim() }
            val episodeNumber = episodeRegex.find(fullName)?.groupValues?.get(1)?.toIntOrNull()
                ?: episodeHrefRegex.find(href)?.groupValues?.get(1)?.toIntOrNull()
            val date = link.selectFirst("span.time")?.text()?.trim()

            newEpisode(fixUrl(href)) {
                this.name = if (episodeNumber != null) "Episode $episodeNumber" else fullName
                this.episode = episodeNumber
                this.season = 1
                this.posterUrl = fixUrlNull(poster)
                if (date != null && dateRegex.matches(date)) {
                    addDate(date, "yyyy-MM-dd HH:mm:ss")
                }
            }
        }.sortedBy { it.episode ?: Int.MAX_VALUE }
    }

    private fun Element.episodeItems(type: TvType = TvType.TvSeries): List<SearchResponse> {
        return select("li > a").mapNotNull { link ->
            val href = link.attr("href").ifBlank {
                onclickRegex.find(link.selectFirst("h3")?.attr("onclick").orEmpty())
                    ?.groupValues?.get(1).orEmpty()
            }
            if (href.isBlank()) return@mapNotNull null

            val title = link.selectFirst("h3.title")?.text()?.trim()
                .orEmpty()
                .ifBlank { link.attr("title").trim() }
                .ifBlank { link.text().trim() }
            if (title.isBlank()) return@mapNotNull null

            val poster = link.selectFirst("img")?.posterUrl()
            if (type == TvType.Movie) {
                newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
                    this.posterUrl = fixUrlNull(poster)
                }
            } else {
                newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                    this.posterUrl = fixUrlNull(poster)
                }
            }
        }
    }

    private fun Element.detailItems(): List<SearchResponse> {
        return select("li a").mapNotNull { link ->
            val href = link.attr("href")
            if (!href.contains("/drama-detail/")) return@mapNotNull null

            val title = link.selectFirst("h3")?.text()?.trim()
                .orEmpty()
                .ifBlank { link.attr("title").trim() }
                .ifBlank { link.text().trim() }
            if (title.isBlank()) return@mapNotNull null

            newTvSeriesSearchResponse(title, fixUrl(href)) {
                this.posterUrl = fixUrlNull(link.selectFirst("img")?.posterUrl())
            }
        }.distinctBy { it.url }
    }

    private fun Element.plainLinkItems(): List<SearchResponse> {
        return select("li a").mapNotNull { link ->
            val href = link.attr("href")
            if (!href.contains("/drama-detail/")) return@mapNotNull null
            val title = link.attr("title").trim().ifBlank { link.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            newTvSeriesSearchResponse(title, fixUrl(href))
        }.distinctBy { it.url }
    }

    private fun Element.posterUrl(): String? {
        val value = attr("data-original").ifBlank { attr("src") }.trim()
        return value.takeIf { it.isNotBlank() && !it.contains("background.jpg") }
    }

    private fun List<Element>.plotText(): String? {
        val index = indexOfFirst {
            it.selectFirst("span")?.text()?.contains("Description", ignoreCase = true) == true
        }
        val afterDescription = if (index >= 0) {
            getOrNull(index + 1)?.text()?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return afterDescription ?: firstOrNull {
            it.selectFirst("span") == null &&
                it.text().length > 80 &&
                !it.hasClass("other_name")
        }?.text()?.trim()
    }

    private fun paragraphValue(
        paragraphs: List<Element>,
        label: String,
    ): String? {
        val paragraph = paragraphs.firstOrNull {
            it.selectFirst("span")?.text()?.contains(label, ignoreCase = true) == true
        } ?: return null

        val text = paragraph.text()
        val value = text.substringAfter(':', "").trim()
        return value.ifBlank { paragraph.selectFirst("a")?.text()?.trim() }
    }

    private fun resolveUrl(base: String, url: String): String {
        return try {
            URI(base).resolve(url).toString()
        } catch (_: Exception) {
            url
        }
    }

    companion object {
        // Hard-coded key/IV used by the "Standard Server" player on vidbasic.top.
        private const val VIDBASIC_KEY = "94588293375053432799222445521289"
        private const val VIDBASIC_IV = "5259228356829423"
    }
}
