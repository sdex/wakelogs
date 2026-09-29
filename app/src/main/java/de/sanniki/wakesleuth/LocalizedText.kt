package de.sanniki.wakesleuth

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Event titles, details and archived sessions are stored as rendered text
 * in the language that was active when they were recorded (older data is
 * always German). Parsers use these helpers to recognize a label in every
 * supported language instead of only the current one.
 */
object LocalizedText {

    private val supportedLocales =
        listOf(
            Locale.ENGLISH,
            Locale.GERMAN
        )

    private val resourcesByLocale =
        ConcurrentHashMap<String, Resources>()

    fun variants(
        context: Context,
        @StringRes id: Int
    ): List<String> =
        (
            listOf(context.getString(id)) +
                supportedLocales.map { locale ->
                    resourcesFor(
                        context,
                        locale
                    ).getString(id)
                }
            )
            .distinct()

    fun startsWithAny(
        text: String,
        context: Context,
        @StringRes id: Int
    ): Boolean =
        variants(context, id).any {
            text.startsWith(it)
        }

    fun containsAny(
        text: String,
        context: Context,
        @StringRes id: Int,
        ignoreCase: Boolean = false
    ): Boolean =
        variants(context, id).any {
            text.contains(
                it,
                ignoreCase = ignoreCase
            )
        }

    fun equalsAny(
        text: String,
        context: Context,
        @StringRes id: Int
    ): Boolean =
        variants(context, id).any {
            text == it
        }

    fun removeAnyPrefix(
        text: String,
        context: Context,
        @StringRes id: Int
    ): String {
        val prefix =
            variants(context, id)
                .firstOrNull {
                    text.startsWith(it)
                }
                ?: return text

        return text.removePrefix(prefix)
    }

    fun substringAfterAny(
        text: String,
        context: Context,
        @StringRes id: Int,
        missing: String = text
    ): String {
        val delimiter =
            variants(context, id)
                .firstOrNull {
                    text.contains(it)
                }
                ?: return missing

        return text.substringAfter(delimiter)
    }

    private fun resourcesFor(
        context: Context,
        locale: Locale
    ): Resources =
        resourcesByLocale.getOrPut(
            locale.toLanguageTag()
        ) {
            val configuration =
                android.content.res.Configuration(
                    context.resources.configuration
                )

            configuration.setLocale(locale)

            context.applicationContext
                .createConfigurationContext(
                    configuration
                )
                .resources
        }
}
