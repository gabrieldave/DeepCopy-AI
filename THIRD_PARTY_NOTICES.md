# Third-party attribution and license notes

KlipNote is distributed under the **GNU GPL-3.0**. See [LICENSE](LICENSE).

## Seal
- Upstream: https://github.com/JunkFood02/Seal
- License: GNU GPL-3.0.
- KlipNote refers to Seal for implementation ideas and download patterns. The downloader component in this repository is called `SealDownloader.kt`; the distributed app is named **KlipNote**, not Seal.
- Any source taken or adapted from Seal must retain its original copyright and license notices, with modifications identified.
- Seal's upstream README contains an explicit name/branding restriction. KlipNote does not claim to be an official build, fork, or affiliated project of Seal.

## youtubedl-android
- Upstream: https://github.com/yausername/youtubedl-android
- Dependency coordinates: `io.github.junkfood02.youtubedl-android:{library,ffmpeg,aria2c}:0.18.1`
- License: GNU GPL-3.0. Refer to the actual resolved artifacts and their embedded notices when distributing a binary.

## yt-dlp and other media components
- yt-dlp: https://github.com/yt-dlp/yt-dlp
- FFmpeg: https://ffmpeg.org/legal.html
- aria2: https://github.com/aria2/aria2
- Verify license and attribution obligations for the **exact binary artifacts** packaged in each release, including transitive dependencies and build flags.

## Redistribution checklist
1. Provide complete corresponding source for KlipNote and include its GPL-3.0 license.
2. Preserve upstream copyright notices for any copied or adapted source code.
3. Publish third-party license texts and notices required by the exact packaged binaries.
4. Do not present KlipNote as Seal, nor use Seal's name/logo for the app.
5. Keep signing keys, API secrets, cookies, user data, and local configuration files outside releases and source archives.
6. Review release APK contents and Git history before public distribution.

This attribution file documents known components, not a substitute for a dependency-by-dependency license audit.
