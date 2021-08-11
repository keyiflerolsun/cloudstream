package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.util.ArrayList

class VMoveeProvider : MainAPI() {
    override val name: String
        get() = "VMovee"
    override val mainUrl: String
        get() = "https://www.vmovee.watch"

    override fun search(query: String): ArrayList<SearchResponse>? {
        val url = "$mainUrl/?s=$query"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val searchItems = document.select("div.search-page > div.result-item > article")
        if (searchItems.size == 0) return ArrayList()
        val returnValue = ArrayList<SearchResponse>()
        for (item in searchItems) {
            val details = item.selectFirst("> div.details")
            val imgHolder = item.selectFirst("> div.image > div.thumbnail > a")
            // val href = imgHolder.attr("href")
            val poster = imgHolder.selectFirst("> img").attr("data-lazy-src")
            val isTV = imgHolder.selectFirst("> span").text() == "TV"
            if(isTV) continue // no TV support yet

            val titleHolder = details.selectFirst("> div.title > a")
            val title = titleHolder.text()
            val href = titleHolder.attr("href")
            val meta = details.selectFirst("> div.meta")
            val year = meta.selectFirst("> span.year").text().toIntOrNull()
            // val rating = parseRating(meta.selectFirst("> span.rating").text().replace("IMDb ", ""))
            // val descript = details.selectFirst("> div.contenido").text()
            returnValue.add(
                if (isTV) TvSeriesSearchResponse(title, href, this.name, TvType.TvSeries, poster, year, null)
                else MovieSearchResponse(title, href, this.name, TvType.Movie, poster, year)
            )
        }
        return returnValue
    }

    data class LoadLinksAjax(
        @JsonProperty("embed_url")
        val embedUrl: String,
    )

    data class ReeoovAPIData(
        @JsonProperty("file")
        val file: String,
        @JsonProperty("label")
        val label: String,
    )

    data class ReeoovAPI(
        @JsonProperty("data")
        val data: List<ReeoovAPIData>,
    )

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val url = "$mainUrl/dashboard/admin-ajax.php"
        val post =
            khttp.post(
                url,
                headers = mapOf("referer" to url),
                data = mapOf("action" to "doo_player_ajax", "post" to data, "nume" to "2", "type" to "movie")
            )

        val ajax = mapper.readValue<LoadLinksAjax>(post.text)
        var realUrl = ajax.embedUrl
        if (realUrl.startsWith("//")) {
            realUrl = "https:$realUrl"
        }

        val request = khttp.get(realUrl)
        val prefix = "https://reeoov.tube/v/"
        if (request.url.startsWith(prefix)) {
            val apiUrl = "https://reeoov.tube/api/source/${request.url.removePrefix(prefix)}"
            val apiResponse = khttp.post(apiUrl,headers = mapOf("Referer" to request.url),data = mapOf("r" to "https://www.vmovee.watch/", "d" to "reeoov.tube"))
            val apiData = mapper.readValue<ReeoovAPI>(apiResponse.text)
            for (d in apiData.data) {
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name + " " + d.label,
                        d.file,
                        "https://reeoov.tube/",
                        getQualityFromName(d.label),
                        false
                    )
                )
            }
        }

        return super.loadLinks(data, isCasting, subtitleCallback, callback)
    }

    override fun load(url: String): LoadResponse? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)

        val sheader = document.selectFirst("div.sheader")

        val poster = sheader.selectFirst("> div.poster > img").attr("data-lazy-src")
        val data = sheader.selectFirst("> div.data")
        val title = data.selectFirst("> h1").text()
        val descript = document.selectFirst("div#info > div").text()
        val id = document.select("div.starstruck").attr("data-id")

        return MovieLoadResponse(title, url, this.name, TvType.Movie, id, poster, null, descript, null, null)
    }
}