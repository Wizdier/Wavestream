package com.wizdier.wavestream.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Built-in demo provider that pulls from the Public Domain Movies archive
 * (https://publicdomainmovie.net). Ships with WaveStream so the app is
 * immediately usable out of the box; serves as the reference implementation
 * for [Provider].
 *
 * Real content sources are installed from user-supplied repository URLs —
 * see [com.wizdier.wavestream.data.repository.RepoRepository].
 */
class PublicDomainProvider : Provider {

    override val id: String = ID
    override val name: String = "Public Domain Movies"
    override val supportedTypes: Set<CatalogType> = setOf(CatalogType.MOVIES, CatalogType.DOCUMENTARIES)
    override val languages: Set<String> = setOf("en")

    private val base = "https://publicdomainmovie.net"

    override suspend fun getMainPage(page: Int): HomePageResponse = withContext(Dispatchers.IO) {
        val doc = runCatching { Jsoup.connect("$base/movie").get() }.getOrNull() ?: return@withContext HomePageResponse(emptyList())

        val items = doc.select("article").take(20).mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.absUrl("href").ifEmpty { base + a.attr("href") }
            val title = el.selectFirst("h2, h3, .title")?.text() ?: a.text()
            val img = el.selectFirst("img")?.absUrl("src")
            SearchResponse(
                id = href,
                name = title,
                url = href,
                type = CatalogType.MOVIES,
                posterUrl = img,
                providerName = name,
                providerId = id,
                description = el.selectFirst("p")?.text()
            )
        }
        HomePageResponse(listOf(HomePageList("Public Domain Movies", items)))
    }

    override suspend fun search(query: String, filter: SearchFilter): List<SearchResponse> = withContext(Dispatchers.IO) {
        val doc = runCatching { Jsoup.connect("$base/search/$query").get() }.getOrNull() ?: return@withContext emptyList()
        doc.select("article").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.absUrl("href")
            val title = el.selectFirst("h2, h3, .title")?.text() ?: a.text()
            SearchResponse(
                id = href,
                name = title,
                url = href,
                type = CatalogType.MOVIES,
                posterUrl = el.selectFirst("img")?.absUrl("src"),
                providerName = name,
                providerId = id
            )
        }
    }

    override suspend fun load(url: String): LoadResponse? = withContext(Dispatchers.IO) {
        val doc = runCatching { Jsoup.connect(url).get() }.getOrNull() ?: return@withContext null
        val title = doc.selectFirst("h1")?.text() ?: "Untitled"
        val desc = doc.selectFirst("p, .description, .synopsis")?.text()
        val poster = doc.selectFirst("img")?.absUrl("src")
        LoadResponse(
            id = url,
            name = title,
            url = url,
            type = CatalogType.MOVIES,
            posterUrl = poster,
            backdropUrl = poster,
            description = desc,
            providerName = name,
            providerId = id
        )
    }

    override suspend fun loadLinks(url: String): LoadLinksResponse = withContext(Dispatchers.IO) {
        val doc = runCatching { Jsoup.connect(url).get() }.getOrNull() ?: return@withContext LoadLinksResponse(emptyList())
        val videoSrc = doc.selectFirst("video source[src]")?.absUrl("src")
            ?: doc.selectFirst("a[href$=.mp4], a[href$=.webm]")?.absUrl("href")
        val videos = if (videoSrc != null) listOf(
            VideoLink(
                name = "Public Domain",
                url = videoSrc,
                quality = Quality.fromString(doc.selectFirst("video source[label]")?.attr("label")),
                extractorType = ExtractorType.DIRECT
            )
        ) else emptyList()
        LoadLinksResponse(videos)
    }

    companion object {
        const val ID = "com.wizdier.wavestream.provider.publicdomain"
    }
}
