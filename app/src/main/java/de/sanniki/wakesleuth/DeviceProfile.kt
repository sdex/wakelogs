package de.sanniki.wakesleuth

import android.content.Context
import android.os.Build
import java.util.Locale

enum class DeviceFamily {
    ONEPLUS,
    SAMSUNG,
    GENERIC_ANDROID,
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
    val summary: String,
) {
    companion object {
        fun detect(context: Context): DeviceProfile {
            val manufacturer = Build.MANUFACTURER
                .orEmpty()
                .trim()
                .ifBlank { context.getString(R.string.device_unknown_manufacturer) }

            val brand = Build.BRAND
                .orEmpty()
                .trim()
                .ifBlank { manufacturer }

            val model = Build.MODEL
                .orEmpty()
                .trim()
                .ifBlank { context.getString(R.string.device_unknown_model) }

            val identity = "$manufacturer $brand".lowercase(Locale.ROOT)

            return when {
                identity.contains("oneplus") ||
                    identity.contains("oplus") -> {
                    DeviceProfile(
                        family = DeviceFamily.ONEPLUS,
                        manufacturer = manufacturer,
                        brand = brand,
                        model = model,
                        profileLabel = context.getString(R.string.device_profile_oneplus),
                        platformLabel = "OxygenOS / OPLUS",
                        displayCoverage = context.getString(R.string.device_coverage_high),
                        backgroundCoverage = context.getString(R.string.device_coverage_good),
                        wakeLockCoverage = context.getString(R.string.device_coverage_good_to_medium),
                        summary = context.getString(R.string.device_summary_oneplus),
                    )
                }

                identity.contains("samsung") -> {
                    DeviceProfile(
                        family = DeviceFamily.SAMSUNG,
                        manufacturer = manufacturer,
                        brand = brand,
                        model = model,
                        profileLabel = context.getString(R.string.device_profile_samsung),
                        platformLabel = "Samsung One UI",
                        displayCoverage = context.getString(R.string.device_coverage_good),
                        backgroundCoverage = context.getString(R.string.device_coverage_good),
                        wakeLockCoverage = context.getString(R.string.device_coverage_medium),
                        summary = context.getString(R.string.device_summary_samsung),
                    )
                }

                else -> {
                    DeviceProfile(
                        family = DeviceFamily.GENERIC_ANDROID,
                        manufacturer = manufacturer,
                        brand = brand,
                        model = model,
                        profileLabel = context.getString(R.string.device_profile_generic),
                        platformLabel = "Android",
                        displayCoverage = context.getString(R.string.device_coverage_basic),
                        backgroundCoverage = context.getString(R.string.device_coverage_limited),
                        wakeLockCoverage = context.getString(R.string.device_coverage_device_dependent),
                        summary = context.getString(R.string.device_summary_generic),
                    )
                }
            }
        }
    }
}
