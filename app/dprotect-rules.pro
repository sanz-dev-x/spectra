# dProtect (open-obfuscator/dProtect) — aturan siap pakai. BELUM dipasang ke build:
# plugin Gradle dProtect dibuat untuk AGP 4.1 (Transform API) dan tidak kompatibel dengan AGP/Gradle project ini.
-obfuscate-strings class com.spectra.app.utils.AppIntegrityGuard
-obfuscate-control-flow class com.spectra.app.utils.AppIntegrityGuard
-obfuscate-arithmetic,low class ru.lazard.tamperingprotection.**
