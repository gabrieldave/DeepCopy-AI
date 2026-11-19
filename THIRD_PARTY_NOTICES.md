# Third-party notices — KlipNote

KlipNote is free software licensed under the **GNU General Public License v3.0**.
See [LICENSE](LICENSE) and the full text in [LICENSES/GPL-3.0.txt](LICENSES/GPL-3.0.txt).

This file lists third-party components shipped with or required to rebuild KlipNote,
with SPDX-style license identifiers and links to the full license texts under
[`LICENSES/`](LICENSES/).

---

## 1. Direct Gradle dependencies (`app/build.gradle.kts`)

| Coordinate | Version | License |
|---|---|---|
| `androidx.compose:compose-bom` (platform) | 2024.10.01 | Apache-2.0 |
| `androidx.compose.ui:ui` | (BOM) | Apache-2.0 |
| `androidx.compose.ui:ui-tooling-preview` | (BOM) | Apache-2.0 |
| `androidx.compose.material3:material3` | (BOM) | Apache-2.0 |
| `androidx.compose.material:material-icons-extended` | (BOM) | Apache-2.0 |
| `androidx.activity:activity-compose` | 1.9.3 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | Apache-2.0 |
| `androidx.core:core-ktx` | 1.15.0 | Apache-2.0 |
| `androidx.appcompat:appcompat` | 1.7.0 | Apache-2.0 |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | Apache-2.0 |
| `androidx.annotation:annotation` | 1.9.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Apache-2.0 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Apache-2.0 |
| `io.github.junkfood02.youtubedl-android:library` | 0.18.1 | GPL-3.0 |
| `io.github.junkfood02.youtubedl-android:ffmpeg` | 0.18.1 | GPL-3.0 |
| `io.github.junkfood02.youtubedl-android:aria2c` | 0.18.1 | GPL-3.0 |
| `junit:junit` (tests) | 4.13.2 | EPL-1.0 |
| `androidx.compose.ui:ui-tooling` (debug) | (BOM) | Apache-2.0 |
| `androidx.compose.ui:ui-test-manifest` (debug) | (BOM) | Apache-2.0 |

Full texts: [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt),
[LICENSES/GPL-3.0.txt](LICENSES/GPL-3.0.txt).  
JUnit EPL-1.0: https://www.eclipse.org/legal/epl-v10.html

Kotlin / Android Gradle plugins used to build the project are development tools;
runtime app code is covered by the rows above.

---

## 2. Native binaries — `youtubedl-android` 0.18.1

Upstream project: [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android)  
Maven coordinates used by KlipNote: `io.github.junkfood02.youtubedl-android:{library,ffmpeg,aria2c}:0.18.1`  
Package license (library): **GPL-3.0** — see [LICENSES/GPL-3.0.txt](LICENSES/GPL-3.0.txt).

Build/provenance transparency for packaged natives is tracked upstream in
[youtubedl-android#358](https://github.com/yausername/youtubedl-android/issues/358).

Verified in KlipNote release intermediates (`libffmpeg.so` / `libaria2c.so`, arm64-v8a):

### FFmpeg 7.1.1 (`Lavc61.19.101`)
- License: **GPL-3.0** (build enables GPL codecs).
- Configure flags observed in the binary:  
  `--enable-gpl --enable-version3 --enable-gnutls --enable-libx264 --enable-libx265`
  (among others).
- TLS: **GnuTLS** (`--enable-gnutls`). **OpenSSL is not linked.**
- Legal: https://ffmpeg.org/legal.html

### aria2 1.37.0 (`aria2/1.37.0`)
- License: **GPL-2.0-or-later** — [LICENSES/GPL-2.0.txt](LICENSES/GPL-2.0.txt).
- TLS: **GnuTLS** (`libgnutls.so` / `LibgnutlsTLSSession`). **OpenSSL is not linked.**
- Upstream: https://github.com/aria2/aria2

### Python 3.12.x (embedded via youtubedl-android / libpython)
- License: **PSF-2.0** — https://docs.python.org/3/license.html

### Mutagen
- License: **GPL-2.0-or-later** — [LICENSES/GPL-2.0.txt](LICENSES/GPL-2.0.txt).
- Upstream: https://github.com/quodlibet/mutagen

### QuickJS (`libqjs.so`, YouTube EJS support in 0.18.1)
- License: **MIT** — [LICENSES/MIT.txt](LICENSES/MIT.txt).
- Upstream: https://bellard.org/quickjs/

### yt-dlp
- License: **Unlicense** — https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE
- **Runtime download:** KlipNote / youtubedl-android may fetch the **NIGHTLY** yt-dlp
  binary and related **EJS** helper scripts from GitHub at runtime. Those artifacts
  are not frozen inside every APK build; their exact revision can change when the
  app updates its downloaded tools. Users and redistributors should treat that
  channel as dynamic and consult the yt-dlp repository for the current license text.

Some transitive system libraries may be covered by **LGPL-2.1**
([LICENSES/LGPL-2.1.txt](LICENSES/LGPL-2.1.txt)) depending on the Termux/Android
build used upstream.

---

## 3. Attribution — JunkFood02 / Seal

- Project: [JunkFood02/Seal](https://github.com/JunkFood02/Seal)
- License: **GPL-3.0** — [LICENSES/GPL-3.0.txt](LICENSES/GPL-3.0.txt)

KlipNote uses ideas and download-related patterns inspired by Seal. The in-tree
helper is named `SealDownloader.kt` for historical clarity; the shipped product
name is **KlipNote**.

**Brand clarification:** KlipNote is **not** Seal, is **not** an official Seal
build, and is **not** affiliated with or endorsed by JunkFood02 / Seal. Do not
use Seal’s name or logo to brand this app.

---

## 4. Corresponding Source

To rebuild KlipNote and obtain Corresponding Source for GPL/LGPL components:

1. **KlipNote source:** https://github.com/gabrieldave/KlipNote  
   Tag/release matching the distributed APK (for example `v1.7.3`).
2. **Build:** JDK 17 + Android SDK 35; from the repo root run  
   `./gradlew assembleRelease` (or `assembleDebug` for unsigned debug).
3. **youtubedl-android / natives:** source and build scripts at  
   https://github.com/yausername/youtubedl-android (see also issue #358 for
   packaged binary provenance). Maven artifacts:  
   `io.github.junkfood02.youtubedl-android:*:0.18.1`.
4. **Seal (reference patterns):** https://github.com/JunkFood02/Seal
5. **yt-dlp / EJS (runtime):** https://github.com/yt-dlp/yt-dlp

Offers for Corresponding Source of GPL components shipped in a given APK are
satisfied by the public Git repositories above together with this notice file
and the texts under [`LICENSES/`](LICENSES/).

---

## 5. Redistribution checklist

1. Ship this file and the [`LICENSES/`](LICENSES/) directory with binary releases.
2. Keep KlipNote’s GPL-3.0 [LICENSE](LICENSE) with the source and APK notes.
3. Preserve upstream copyright notices for adapted source.
4. Do not present KlipNote as Seal or use Seal branding.
5. Keep signing keys, API secrets, cookies, and user data out of the repo and APK.
