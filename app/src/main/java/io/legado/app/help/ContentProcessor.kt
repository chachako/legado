package io.legado.app.help

import com.github.liuyueyi.quick.transfer.ChineseUtils
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

class ContentProcessor private constructor(
    private val bookName: String,
    private val bookOrigin: String
) {

    companion object {
        private val processors = hashMapOf<String, WeakReference<ContentProcessor>>()

        fun get(bookName: String, bookOrigin: String): ContentProcessor {
            val processorWr = processors[bookName + bookOrigin]
            var processor: ContentProcessor? = processorWr?.get()
            if (processor == null) {
                processor = ContentProcessor(bookName, bookOrigin)
                processors[bookName + bookOrigin] = WeakReference(processor)
            }
            return processor
        }

        fun upReplaceRules() {
            processors.forEach {
                it.value.get()?.upReplaceRules()
            }
        }

    }

    private val titleReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    private val contentReplaceRules = CopyOnWriteArrayList<ReplaceRule>()

    init {
        upReplaceRules()
    }

    fun upReplaceRules() {
        titleReplaceRules.run {
            clear()
            addAll(appDb.replaceRuleDao.findEnabledByTitleScope(bookName, bookOrigin))
        }
        contentReplaceRules.run {
            clear()
            addAll(appDb.replaceRuleDao.findEnabledByContentScope(bookName, bookOrigin))
        }
    }

    fun getTitleReplaceRules(): List<ReplaceRule> {
        return titleReplaceRules
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getContentReplaceRules(): List<ReplaceRule> {
        return contentReplaceRules
    }

    fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true
    ): List<String> {
        var mContent = content
        //??????????????????
        try {
            val name = Pattern.quote(book.name)
            val title = Pattern.quote(chapter.title)
            val titleRegex = "^(\\s|\\p{P}|${name})*${title}(\\s|\\p{P})+".toRegex()
            mContent = mContent.replace(titleRegex, "")
        } catch (e: Exception) {
            AppLog.put("????????????????????????\n${e.localizedMessage}", e)
        }
        if (reSegment && book.getReSegment()) {
            //????????????
            mContent = ContentHelp.reSegment(mContent, chapter.title)
        }
        if (chineseConvert) {
            //????????????
            try {
                when (AppConfig.chineseConverterType) {
                    1 -> mContent = ChineseUtils.t2s(mContent)
                    2 -> mContent = ChineseUtils.s2t(mContent)
                }
            } catch (e: Exception) {
                appCtx.toastOnUi("??????????????????")
            }
        }
        if (useReplace && book.getUseReplaceRule()) {
            //??????
            mContent = replaceContent(mContent)
        }
        if (includeTitle) {
            //??????????????????
            mContent = chapter.getDisplayTitle(getTitleReplaceRules()) + "\n" + mContent
        }
        val contents = arrayListOf<String>()
        mContent.split("\n").forEach { str ->
            val paragraph = str.trim {
                it.code <= 0x20 || it == '???'
            }
            if (paragraph.isNotEmpty()) {
                if (contents.isEmpty() && includeTitle) {
                    contents.add(paragraph)
                } else {
                    contents.add("${ReadBookConfig.paragraphIndent}$paragraph")
                }
            }
        }
        return contents
    }

    fun replaceContent(content: String): String {
        var mContent = content
        getContentReplaceRules().forEach { item ->
            if (item.pattern.isNotEmpty()) {
                try {
                    mContent = if (item.isRegex) {
                        mContent.replace(item.pattern.toRegex(), item.replacement)
                    } else {
                        mContent.replace(item.pattern, item.replacement)
                    }
                } catch (e: Exception) {
                    AppLog.put("${item.name}????????????\n${e.localizedMessage}")
                    appCtx.toastOnUi("${item.name}????????????")
                }
            }
        }
        return mContent
    }

}
