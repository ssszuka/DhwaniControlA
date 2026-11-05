package io.github.rajnishkmehta.dhwanicontrol.core.locale

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleManager {

    /**
     * Retrieves the supported locales for the application.
     * With AGP generateLocaleConfig = true, it automatically generates
     * locales_config.xml at build time based on res/values-* folders.
     */
    fun getSupportedLocales(context: Context): List<Locale> {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val localeConfig = android.app.LocaleConfig(context)
            if (localeConfig.status == android.app.LocaleConfig.STATUS_SUCCESS) {
                val supportedLocales = localeConfig.supportedLocales
                if (supportedLocales != null) {
                    val locales = mutableListOf<Locale>()
                    for (i in 0 until supportedLocales.size()) {
                        locales.add(supportedLocales.get(i))
                    }
                    return locales.distinctBy { it.toLanguageTag() }
                }
            }
        }

        val locales = mutableListOf<Locale>()
        try {
            val resId = context.resources.getIdentifier("locales_config", "xml", context.packageName)
            if (resId != 0) {
                val parser = context.resources.getXml(resId)
                var eventType = parser.eventType
                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                        val lang = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        if (lang != null) {
                            locales.add(Locale.forLanguageTag(lang))
                        }
                    }
                    eventType = parser.next()
                }
            }
 else {
                locales.add(Locale.forLanguageTag("en"))
                locales.add(Locale.forLanguageTag("hi"))
                locales.add(Locale.forLanguageTag("zh-CN"))
            }
        }
 catch (e: Exception) {
            locales.add(Locale.forLanguageTag("en"))
            locales.add(Locale.forLanguageTag("hi"))
            locales.add(Locale.forLanguageTag("zh-CN"))
        }
        return locales.distinctBy { it.toLanguageTag() }
    }

    fun getCurrentLocale(context: Context): Locale {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            return appLocales[0] ?: Locale.getDefault()
        }
        return context.resources.configuration.locales[0] ?: Locale.getDefault()
    }

    fun setLocale(locale: Locale) {
        val localeList = LocaleListCompat.create(locale)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun resetLocale() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    fun getDisplayName(locale: Locale): String {
        return locale.getDisplayName(locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}
