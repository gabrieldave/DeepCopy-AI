# FAQ

<p>🇺🇸 <strong>English</strong> · 🇪🇸 <a href="preguntas-frecuentes.md">Español</a></p>

## Is it really free?

You can use it **without paying KlipNote**: the app is free and open source. The “free” transcription comes from **credits or minutes other services give away** (Groq, Inworld, AssemblyAI, Deepgram). Those offers belong to each company and **can change or run out**.

To avoid spending money: **do not add a credit card** on those services and leave **$0 mode** on (it blocks the paid engine).

## Are my keys safe?

As far as is reasonable on a phone: they are stored **encrypted** on your device. The APK **ships with no keys**. KlipNote has no server of its own that receives them — it only uses them to call the provider you chose.

## Why isn’t it on the Play Store?

For now it ships via **GitHub Releases** (APK). Anyone can audit the code and download without a Google Play account. If another store is added later, it will be announced in the repo.

## What if a service stops being free?

The app can skip it automatically when the reply is “no credit” or similar. The community can also update [`config/stt-router.json`](../config/stt-router.json) (see [Contribute offers](contributing-offers.md)) to change copy, disable a service, or adjust order **without shipping a new APK**.

## Why does a video ask me to sign in?

Some videos (private, closed accounts, or when Instagram/YouTube ask you to log in) can only be downloaded with a session. Under **Settings → Videos that need sign-in** you can connect **your** account. Cookies stay on your phone. Use it only when you see that prompt; a secondary account is better.

## What if a site stops downloading?

It usually fixes itself in a few days: the download engine (**yt-dlp**) **updates itself**. Meanwhile, download the video with another app and **share it to KlipNote** — transcription still works.

## Do I need all four free keys?

No. One is enough. Groq is usually the best place to start.

## Does KlipNote keep my videos?

In **Text only** mode you get the text and **the video is not saved**. Other modes may save audio or video depending on what you choose. **Transcripts** (text) stay in the app’s local history.

Back to the [guides](README.md).
