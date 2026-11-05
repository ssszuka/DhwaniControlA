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
     * We don't read the XML directly as it might be unstable. We fallback to
     * building the list from available resources configurations or manual parsing if necessary.
     */
    fun getSupportedLocales(context: Context): List<Locale> {
        val locales = mutableListOf<Locale>()
        locales.add(Locale.ENGLISH) // Always add english as fallback

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
            } else {
                // Hardcode fallbacks if generated XML is not available dynamically
                locales.add(Locale.forLanguageTag("hi"))
                locales.add(Locale.forLanguageTag("zh-rCN"))
            }
        } catch (e: Exception) {
            // Fallback
            locales.add(Locale.forLanguageTag("hi"))
            locales.add(Locale.forLanguageTag("zh-rCN"))
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

    fun getDisplayName(locale: Locale): String {
        return locale.getDisplayName(locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}
