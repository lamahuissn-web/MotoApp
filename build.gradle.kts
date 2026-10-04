// MotoApp — the collector's and the manager's phone, for the office's own generator panel.
// Versions come from gradle/libs.versions.toml so a bump is one line, in one file.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
