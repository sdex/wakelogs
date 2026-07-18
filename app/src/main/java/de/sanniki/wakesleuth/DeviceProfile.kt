package de.sanniki.wakesleuth

import android.os.Build
import java.util.Locale

enum class DeviceFamily {
    ONEPLUS,
    SAMSUNG,
    GENERIC_ANDROID
}

data class DeviceProfile(
    val family: DeviceFamily,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val profileLabel: String,
    val platformLabel: String,
    val displayCoverage: String,
    val backgroundCoverage: String,
    val wakeLockCoverage: String,
    val summary: String
) {
    companion object {
        fun detect(): DeviceProfile {
            val manufacturer =
                Build.MANUFACTURER
                    .orEmpty()
                    .trim()
                    .ifBlank {
                        "Unbekannt"
                    }

            val brand =
                Build.BRAND
                    .orEmpty()
                    .trim()
                    .ifBlank {
                        manufacturer
                    }

            val model =
                Build.MODEL
                    .orEmpty()
                    .trim()
                    .ifBlank {
                        "Unbekanntes Modell"
                    }

            val identity =
                "$manufacturer $brand"
                    .lowercase(Locale.ROOT)

            return when {
                identity.contains("oneplus") ||
                    identity.contains("oplus") -> {
                    DeviceProfile(
                        family =
                            DeviceFamily.ONEPLUS,
                        manufacturer =
                            manufacturer,
                        brand =
                            brand,
                        model =
                            model,
                        profileLabel =
                            "OnePlus-Profil",
                        platformLabel =
                            "OxygenOS / OPLUS",
                        displayCoverage =
                            "hoch",
                        backgroundCoverage =
                            "gut",
                        wakeLockCoverage =
                            "gut bis mittel",
                        summary =
                            "Für OnePlus und OxygenOS optimierte Auswertung."
                    )
                }

                identity.contains("samsung") -> {
                    DeviceProfile(
                        family =
                            DeviceFamily.SAMSUNG,
                        manufacturer =
                            manufacturer,
                        brand =
                            brand,
                        model =
                            model,
                        profileLabel =
                            "Samsung-Profil",
                        platformLabel =
                            "Samsung One UI",
                        displayCoverage =
                            "gut",
                        backgroundCoverage =
                            "gut",
                        wakeLockCoverage =
                            "mittel",
                        summary =
                            "Für Samsung One UI angepasste Auswertung; direkte Power-Tasten-Erkennung bleibt geräteabhängig."
                    )
                }

                else -> {
                    DeviceProfile(
                        family =
                            DeviceFamily.GENERIC_ANDROID,
                        manufacturer =
                            manufacturer,
                        brand =
                            brand,
                        model =
                            model,
                        profileLabel =
                            "Allgemeines Android-Profil",
                        platformLabel =
                            "Android",
                        displayCoverage =
                            "grundlegend",
                        backgroundCoverage =
                            "eingeschränkt",
                        wakeLockCoverage =
                            "geräteabhängig",
                        summary =
                            "Grundfunktionen verfügbar; die Datenqualität hängt vom Hersteller ab."
                    )
                }
            }
        }
    }
}
