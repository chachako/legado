package io.legado.app.help

import androidx.annotation.Keep
import com.jayway.jsonpath.JsonPath
import io.legado.app.constant.AppConst
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.*
import io.legado.app.utils.*
import timber.log.Timber
import java.util.regex.Pattern

@Suppress("RegExpRedundantEscape")
object SourceAnalyzer {
    private val headerPattern = Pattern.compile("@Header:\\{.+?\\}", Pattern.CASE_INSENSITIVE)
    private val jsPattern = Pattern.compile("\\{\\{.+?\\}\\}", Pattern.CASE_INSENSITIVE)

    fun jsonToBookSources(json: String): List<BookSource> {
        val bookSources = mutableListOf<BookSource>()
        if (json.isJsonArray()) {
            val items: List<Map<String, Any>> = jsonPath.parse(json).read("$")
            for (item in items) {
                val jsonItem = jsonPath.parse(item)
                jsonToBookSource(jsonItem.jsonString())?.let {
                    bookSources.add(it)
                }
            }
        }
        return bookSources
    }

    fun jsonToBookSource(json: String): BookSource? {
        val source = BookSource()
        val sourceAny = try {
            GSON.fromJsonObject<BookSourceAny>(json.trim())
        } catch (e: Exception) {
            null
        }
        try {
            if (sourceAny?.ruleToc == null) {
                source.apply {
                    val jsonItem = jsonPath.parse(json.trim())
                    bookSourceUrl = jsonItem.readString("bookSourceUrl") ?: return null
                    bookSourceName = jsonItem.readString("bookSourceName") ?: ""
                    bookSourceGroup = jsonItem.readString("bookSourceGroup")
                    loginUrl = jsonItem.readString("loginUrl")
                    loginUi = jsonItem.readString("loginUi")
                    loginCheckJs = jsonItem.readString("loginCheckJs")
                    bookSourceComment = jsonItem.readString("bookSourceComment") ?: ""
                    bookUrlPattern = jsonItem.readString("ruleBookUrlPattern")
                    customOrder = jsonItem.readInt("serialNumber") ?: 0
                    header = uaToHeader(jsonItem.readString("httpUserAgent"))
                    searchUrl = toNewUrl(jsonItem.readString("ruleSearchUrl"))
                    exploreUrl = toNewUrls(jsonItem.readString("ruleFindUrl"))
                    bookSourceType =
                        if (jsonItem.readString("bookSourceType") == "AUDIO") BookType.audio else BookType.default
                    enabled = jsonItem.readBool("enable") ?: true
                    if (exploreUrl.isNullOrBlank()) {
                        enabledExplore = false
                    }
                    ruleSearch = SearchRule(
                        bookList = toNewRule(jsonItem.readString("ruleSearchList")),
                        name = toNewRule(jsonItem.readString("ruleSearchName")),
                        author = toNewRule(jsonItem.readString("ruleSearchAuthor")),
                        intro = toNewRule(jsonItem.readString("ruleSearchIntroduce")),
                        kind = toNewRule(jsonItem.readString("ruleSearchKind")),
                        bookUrl = toNewRule(jsonItem.readString("ruleSearchNoteUrl")),
                        coverUrl = toNewRule(jsonItem.readString("ruleSearchCoverUrl")),
                        lastChapter = toNewRule(jsonItem.readString("ruleSearchLastChapter"))
                    )
                    ruleExplore = ExploreRule(
                        bookList = toNewRule(jsonItem.readString("ruleFindList")),
                        name = toNewRule(jsonItem.readString("ruleFindName")),
                        author = toNewRule(jsonItem.readString("ruleFindAuthor")),
                        intro = toNewRule(jsonItem.readString("ruleFindIntroduce")),
                        kind = toNewRule(jsonItem.readString("ruleFindKind")),
                        bookUrl = toNewRule(jsonItem.readString("ruleFindNoteUrl")),
                        coverUrl = toNewRule(jsonItem.readString("ruleFindCoverUrl")),
                        lastChapter = toNewRule(jsonItem.readString("ruleFindLastChapter"))
                    )
                    ruleBookInfo = BookInfoRule(
                        init = toNewRule(jsonItem.readString("ruleBookInfoInit")),
                        name = toNewRule(jsonItem.readString("ruleBookName")),
                        author = toNewRule(jsonItem.readString("ruleBookAuthor")),
                        intro = toNewRule(jsonItem.readString("ruleIntroduce")),
                        kind = toNewRule(jsonItem.readString("ruleBookKind")),
                        coverUrl = toNewRule(jsonItem.readString("ruleCoverUrl")),
                        lastChapter = toNewRule(jsonItem.readString("ruleBookLastChapter")),
                        tocUrl = toNewRule(jsonItem.readString("ruleChapterUrl"))
                    )
                    ruleToc = TocRule(
                        chapterList = toNewRule(jsonItem.readString("ruleChapterList")),
                        chapterName = toNewRule(jsonItem.readString("ruleChapterName")),
                        chapterUrl = toNewRule(jsonItem.readString("ruleContentUrl")),
                        nextTocUrl = toNewRule(jsonItem.readString("ruleChapterUrlNext"))
                    )
                    var content = toNewRule(jsonItem.readString("ruleBookContent")) ?: ""
                    if (content.startsWith("$") && !content.startsWith("$.")) {
                        content = content.substring(1)
                    }
                    ruleContent = ContentRule(
                        content = content,
                        replaceRegex = toNewRule(jsonItem.readString("ruleBookContentReplace")),
                        nextContentUrl = toNewRule(jsonItem.readString("ruleContentUrlNext"))
                    )
                }
            } else {
                source.bookSourceUrl = sourceAny.bookSourceUrl
                source.bookSourceName = sourceAny.bookSourceName
                source.bookSourceGroup = sourceAny.bookSourceGroup
                source.bookSourceType = sourceAny.bookSourceType
                source.bookUrlPattern = sourceAny.bookUrlPattern
                source.customOrder = sourceAny.customOrder
                source.enabled = sourceAny.enabled
                source.enabledExplore = sourceAny.enabledExplore
                source.concurrentRate = sourceAny.concurrentRate
                source.header = sourceAny.header
                source.loginUrl = when (sourceAny.loginUrl) {
                    null -> null
                    is String -> sourceAny.loginUrl.toString()
                    else -> JsonPath.parse(sourceAny.loginUrl).readString("url")
                }
                source.loginUi = if (sourceAny.loginUi is List<*>) {
                    GSON.toJson(sourceAny.loginUi)
                } else {
                    sourceAny.loginUi?.toString()
                }
                source.loginCheckJs = sourceAny.loginCheckJs
                source.bookSourceComment = sourceAny.bookSourceComment
                source.lastUpdateTime = sourceAny.lastUpdateTime
                source.respondTime = sourceAny.respondTime
                source.weight = sourceAny.weight
                source.exploreUrl = sourceAny.exploreUrl
                source.ruleExplore = if (sourceAny.ruleExplore is String) {
                    GSON.fromJsonObject(sourceAny.ruleExplore.toString())
                } else {
                    GSON.fromJsonObject(GSON.toJson(sourceAny.ruleExplore))
                }
                source.searchUrl = sourceAny.searchUrl
                source.ruleSearch = if (sourceAny.ruleSearch is String) {
                    GSON.fromJsonObject(sourceAny.ruleSearch.toString())
                } else {
                    GSON.fromJsonObject(GSON.toJson(sourceAny.ruleSearch))
                }
                source.ruleBookInfo = if (sourceAny.ruleBookInfo is String) {
                    GSON.fromJsonObject(sourceAny.ruleBookInfo.toString())
                } else {
                    GSON.fromJsonObject(GSON.toJson(sourceAny.ruleBookInfo))
                }
                source.ruleToc = if (sourceAny.ruleToc is String) {
                    GSON.fromJsonObject(sourceAny.ruleToc.toString())
                } else {
                    GSON.fromJsonObject(GSON.toJson(sourceAny.ruleToc))
                }
                source.ruleContent = if (sourceAny.ruleContent is String) {
                    GSON.fromJsonObject(sourceAny.ruleContent.toString())
                } else {
                    GSON.fromJsonObject(GSON.toJson(sourceAny.ruleContent))
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return source
    }

    @Keep
    data class BookSourceAny(
        var bookSourceName: String = "",                // ??????
        var bookSourceGroup: String? = null,            // ??????
        var bookSourceUrl: String = "",                 // ??????????????? http/https
        var bookSourceType: Int = BookType.default,     // ?????????0 ?????????1 ??????
        var bookUrlPattern: String? = null,             // ?????????url??????
        var customOrder: Int = 0,                       // ??????????????????
        var enabled: Boolean = true,                    // ????????????
        var enabledExplore: Boolean = true,             // ????????????
        var concurrentRate: String? = null,             // ?????????
        var header: String? = null,                     // ?????????
        var loginUrl: Any? = null,                      // ????????????
        var loginUi: Any? = null,                       // ??????UI
        var loginCheckJs: String? = null,               //????????????js
        var bookSourceComment: String? = "",            //????????????
        var lastUpdateTime: Long = 0,                   // ?????????????????????????????????
        var respondTime: Long = 180000L,                // ???????????????????????????
        var weight: Int = 0,                            // ?????????????????????
        var exploreUrl: String? = null,                 // ??????url
        var ruleExplore: Any? = null,                   // ????????????
        var searchUrl: String? = null,                  // ??????url
        var ruleSearch: Any? = null,                    // ????????????
        var ruleBookInfo: Any? = null,                  // ?????????????????????
        var ruleToc: Any? = null,                       // ???????????????
        var ruleContent: Any? = null                    // ???????????????
    )

    // default????????????
    // #??????#???????????? ????????? ##??????##????????????
    // | ????????? ||
    // & ????????? &&
    private fun toNewRule(oldRule: String?): String? {
        if (oldRule.isNullOrBlank()) return null
        var newRule = oldRule
        var reverse = false
        var allinone = false
        if (oldRule.startsWith("-")) {
            reverse = true
            newRule = oldRule.substring(1)
        }
        if (newRule.startsWith("+")) {
            allinone = true
            newRule = newRule.substring(1)
        }
        if (!newRule.startsWith("@CSS:", true) &&
            !newRule.startsWith("@XPath:", true) &&
            !newRule.startsWith("//") &&
            !newRule.startsWith("##") &&
            !newRule.startsWith(":") &&
            !newRule.contains("@js:", true) &&
            !newRule.contains("<js>", true)
        ) {
            if (newRule.contains("#") && !newRule.contains("##")) {
                newRule = oldRule.replace("#", "##")
            }
            if (newRule.contains("|") && !newRule.contains("||")) {
                if (newRule.contains("##")) {
                    val list = newRule.split("##")
                    if (list[0].contains("|")) {
                        newRule = list[0].replace("|", "||")
                        for (i in 1 until list.size) {
                            newRule += "##" + list[i]
                        }
                    }
                } else {
                    newRule = newRule.replace("|", "||")
                }
            }
            if (newRule.contains("&")
                && !newRule.contains("&&")
                && !newRule.contains("http")
                && !newRule.startsWith("/")
            ) {
                newRule = newRule.replace("&", "&&")
            }
        }
        if (allinone) {
            newRule = "+$newRule"
        }
        if (reverse) {
            newRule = "-$newRule"
        }
        return newRule
    }

    private fun toNewUrls(oldUrls: String?): String? {
        if (oldUrls.isNullOrBlank()) return null
        if (oldUrls.startsWith("@js:") || oldUrls.startsWith("<js>")) {
            return oldUrls
        }
        if (!oldUrls.contains("\n") && !oldUrls.contains("&&")) {
            return toNewUrl(oldUrls)
        }
        val urls = oldUrls.split("(&&|\r?\n)+".toRegex())
        return urls.map {
            toNewUrl(it)?.replace("\n\\s*".toRegex(), "")
        }.joinToString("\n")
    }

    private fun toNewUrl(oldUrl: String?): String? {
        if (oldUrl.isNullOrBlank()) return null
        var url: String = oldUrl
        if (oldUrl.startsWith("<js>", true)) {
            url = url.replace("=searchKey", "={{key}}")
                .replace("=searchPage", "={{page}}")
            return url
        }
        val map = HashMap<String, String>()
        var mather = headerPattern.matcher(url)
        if (mather.find()) {
            val header = mather.group()
            url = url.replace(header, "")
            map["headers"] = header.substring(8)
        }
        var urlList = url.split("|")
        url = urlList[0]
        if (urlList.size > 1) {
            map["charset"] = urlList[1].split("=")[1]
        }
        mather = jsPattern.matcher(url)
        val jsList = arrayListOf<String>()
        while (mather.find()) {
            jsList.add(mather.group())
            url = url.replace(jsList.last(), "$${jsList.size - 1}")
        }
        url = url.replace("{", "<").replace("}", ">")
        url = url.replace("searchKey", "{{key}}")
        url = url.replace("<searchPage([-+]1)>".toRegex(), "{{page$1}}")
            .replace("searchPage([-+]1)".toRegex(), "{{page$1}}")
            .replace("searchPage", "{{page}}")
        for ((index, item) in jsList.withIndex()) {
            url = url.replace(
                "$$index",
                item.replace("searchKey", "key").replace("searchPage", "page")
            )
        }
        urlList = url.split("@")
        url = urlList[0]
        if (urlList.size > 1) {
            map["method"] = "POST"
            map["body"] = urlList[1]
        }
        if (map.size > 0) {
            url += "," + GSON.toJson(map)
        }
        return url
    }

    private fun uaToHeader(ua: String?): String? {
        if (ua.isNullOrEmpty()) return null
        val map = mapOf(Pair(AppConst.UA_NAME, ua))
        return GSON.toJson(map)
    }

}
