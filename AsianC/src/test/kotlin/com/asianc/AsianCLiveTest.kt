package com.asianc

import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Live smoke tests. These hit asianc.id; run them with
 *   gradlew :AsianC:testDebugUnitTest
 * Remove this file before publishing if you do not want network tests.
 */
class AsianCLiveTest {
    private val api = AsianC()

    @Test
    fun mainPageHasSections() = runBlocking {
        val page = api.getMainPage(1, MainPageRequest("Home", "", false))
        println("SECTIONS: " + page.items.joinToString { "${it.name}=${it.list.size}" })
        require(page.items.isNotEmpty()) { "no home sections" }
        page.items.forEach { section ->
            println("== ${section.name} ==")
            section.list.take(4).forEach {
                println("  name=${it.name} url=${it.url} poster=${it.posterUrl} type=${it.type}")
            }
        }
        require(page.items.any { it.list.isNotEmpty() }) { "all sections are empty" }
    }

    @Test
    fun searchFindsResults() = runBlocking {
        val results = api.search("squid")
        println("SEARCH RESULTS: ${results.size}")
        results.take(5).forEach { println("  ${it.name} | ${it.url} | ${it.posterUrl}") }
        require(results.isNotEmpty()) { "search returned nothing" }
    }

    @Test
    fun quickSearchFindsResults() = runBlocking {
        val results = api.quickSearch("squid")
        println("QUICK SEARCH: ${results?.size}")
        results.orEmpty().take(3).forEach { println("  ${it.name} | ${it.url} | ${it.posterUrl}") }
        require(!results.isNullOrEmpty()) { "quick search returned nothing" }
    }

    @Test
    fun allGreensMovie() = runBlocking {
        if (extractorApis.none { it is HgLink }) extractorApis.add(HgLink())
        if (extractorApis.none { it is Minochinos }) extractorApis.add(Minochinos())

        val url = "https://asianc.id/all-greens-2026-episode-1.html"
        val loaded = api.load(url)
        println("ALLGREENS load=${loaded?.name} type=${loaded?.type} class=${loaded?.javaClass?.simpleName}")
        println("ALLGREENS data=${(loaded as? com.lagradost.cloudstream3.MovieLoadResponse)?.dataUrl}")

        val links = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
        val ok = api.loadLinks(url, false, {}) { links.add(it) }
        println("ALLGREENS links ok=$ok count=${links.size}")
        links.forEach { println("  ${it.source} | ${it.name} | ${it.type} | ${it.url}") }
        require(ok && links.isNotEmpty()) { "no links for All Greens" }
    }

    @Test
    fun loadsSeries() = runBlocking {
        val response = api.load("https://asianc.id/drama-detail/squid-game-season-3")
            ?: error("load returned null")
        println("LOAD: ${response.name} type=${response.type} year=${response.year}")
        println("PLOT: ${response.plot?.take(200)}")
        println("POSTER: ${response.posterUrl}")
        println("TAGS: ${response.tags}")
        val series = response as? com.lagradost.cloudstream3.TvSeriesLoadResponse
            ?: error("expected TvSeriesLoadResponse, got ${response::class.simpleName}")
        println("EPISODES: ${series.episodes.size}")
        series.episodes.take(3).forEach {
            println("  ep=${it.episode} name=${it.name} data=${it.data} date=${it.date}")
        }
        require(series.episodes.size == 6) { "expected 6 episodes" }
        require(series.episodes.first().episode == 1) { "episodes are not sorted ascending" }
    }

    @Test
    fun loadsMovie() = runBlocking {
        val response = api.load("https://asianc.id/drama-detail/mal-mo-e-the-secret-mission")
            ?: error("load returned null")
        println("MOVIE: ${response.name} type=${response.type} year=${response.year}")
        require(response is com.lagradost.cloudstream3.MovieLoadResponse) {
            "expected MovieLoadResponse, got ${response::class.simpleName}"
        }
    }

    @Test
    fun extractsStandardServer() = runBlocking {
        // In the app these are registered by AsianCPlugin; register them here
        // too so loadExtractor can match the site's custom mirrors.
        if (extractorApis.none { it is HgLink }) extractorApis.add(HgLink())
        if (extractorApis.none { it is Minochinos }) extractorApis.add(Minochinos())

        val links = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
        val ok = api.loadLinks(
            "https://asianc.id/squid-game-season-3-episode-1.html",
            false,
            {},
        ) { links.add(it) }
        println("EXTRACT ok=$ok links=${links.size}")
        links.forEach {
            println("  source=${it.source} name=${it.name} type=${it.type} url=${it.url}")
        }
        require(ok && links.isNotEmpty()) { "no links extracted" }
        require(links.any { it.url.contains("vidbasic") }) { "standard server link missing" }
    }
}
