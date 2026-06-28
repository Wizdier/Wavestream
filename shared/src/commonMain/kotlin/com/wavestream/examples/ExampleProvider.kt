package com.wavestream.examples

import com.wavestream.api.*
import com.wavestream.plugins.BasePlugin
import com.wavestream.plugins.WavestreamPlugin

/**
 * Example CloudStream-style provider.
 *
 * This is a minimal example showing how to write a Wavestream plugin.
 * It returns hardcoded data — in a real plugin, you'd scrape a website
 * or call an API.
 *
 * Compile this against `com.wavestream:shared` and package as a .ws3 file
 * (zip with manifest.json + classes.dex).
 *
 * manifest.json:
 *   {
 *     "name": "ExampleProvider",
 *     "pluginClassName": "com.wavestream.examples.ExamplePlugin",
 *     "version": 1,
 *     "requiresResources": false
 *   }
 */
class ExampleProvider : MainAPI() {
    override var name = "ExampleProvider"
    override var mainUrl = "https://example.com"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = listOf(
        MainPageData("Trending", "trending", horizontalImages = false),
        MainPageData("Popular Movies", "popular/movies", horizontalImages = false),
        MainPageData("Popular Series", "popular/series", horizontalImages = false),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        // In a real plugin, fetch from the website based on `request.data`
        val items = when (request.data) {
            "trending" -> listOf(
                newMovieSearchResponse("Dune: Part Two", "/movie/dune-part-two") {
                    posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg"
                    year = 2024
                    quality = SearchQuality.FourK
                },
                newTvSeriesSearchResponse("House of the Dragon", "/series/house-of-dragon") {
                    posterUrl = "https://image.tmdb.org/t/p/w500/example.jpg"
                    year = 2024
                },
            )
            "popular/movies" -> listOf(
                newMovieSearchResponse("Oppenheimer", "/movie/oppenheimer") {
                    posterUrl = "https://image.tmdb.org/t/p/w500/example2.jpg"
                    year = 2023
                    quality = SearchQuality.HD
                },
                newMovieSearchResponse("Barbie", "/movie/barbie") {
                    posterUrl = "https://image.tmdb.org/t/p/w500/example3.jpg"
                    year = 2023
                },
            )
            "popular/series" -> listOf(
                newTvSeriesSearchResponse("Breaking Bad", "/series/breaking-bad") {
                    posterUrl = "https://image.tmdb.org/t/p/w500/example4.jpg"
                    year = 2008
                },
                newTvSeriesSearchResponse("The Bear", "/series/the-bear") {
                    posterUrl = "https://image.tmdb.org/t/p/w500/example5.jpg"
                    year = 2022
                },
            )
            else -> emptyList()
        }

        return HomePageResponse(
            items = listOf(HomePageList(request.name, items, request.horizontalImages)),
            hasNext = false,
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // In a real plugin, search the website
        return listOf(
            newMovieSearchResponse("$query — Result 1", "/movie/$query-1") {
                posterUrl = "https://image.tmdb.org/t/p/w500/example.jpg"
                year = 2024
            },
            newMovieSearchResponse("$query — Result 2", "/movie/$query-2") {
                posterUrl = "https://image.tmdb.org/t/p/w500/example2.jpg"
                year = 2023
            },
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        // In a real plugin, scrape the detail page
        val title = url.substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }
        return newMovieLoadResponse(title, url, data = url) {
            plot = "A description of $title. This is example content from ExampleProvider."
            year = 2024
            tags = listOf("Example", "Demo")
            duration = 120
            score = 8.5f
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // In a real plugin, fetch actual stream URLs from the website
        callback(
            newExtractorLink(
                source = name,
                name = "ExampleProvider - 1080p",
                url = "https://cdn.example.com$data/1080p/index.m3u8",
                type = ExtractorLinkType.M3U8,
            ) {
                this.quality = Qualities.P1080.value
            }
        )
        callback(
            newExtractorLink(
                source = name,
                name = "ExampleProvider - 720p",
                url = "https://cdn.example.com$data/720p/index.m3u8",
                type = ExtractorLinkType.M3U8,
            ) {
                this.quality = Qualities.P720.value
            }
        )
        // Add a subtitle
        subtitleCallback(
            SubtitleFile(
                lang = "English",
                url = "https://cdn.example.com$data/subtitles.en.srt",
            )
        )
        return true
    }
}

/**
 * Plugin entry point — registered in manifest.json.
 */
@WavestreamPlugin(name = "ExampleProvider", version = 1)
class ExamplePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(ExampleProvider())
    }
}
