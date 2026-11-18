# KlipNote

App Android **gratis y de código abierto** para transcribir audio y video a texto, listo para pegarlo en cualquier IA.

Usa servicios de transcripción que **regalan créditos o minutos gratis**. Cuando uno se acaba, salta solo al siguiente. Así puedes transcribir mucho sin pagar nada. Si algún día quieres, puedes agregar un motor de pago al final, y solo se usa si tú lo permites.

> El APK no trae llaves de nadie. **Cada persona pone sus propias llaves** dentro de la app. Se guardan cifradas y nunca salen de tu teléfono.

---

## Qué hace

- Transcribe archivos de audio o video, o lo que compartas desde Galería, Archivos, WhatsApp, etc.
- Descarga y transcribe enlaces de YouTube, TikTok, Instagram, Facebook y X (con yt-dlp).
- Copia el texto al portapapeles y trae prompts listos para IA.
- Te muestra cuánto te queda en cada servicio gratis.

## Instalar

1. Ve a **[Releases](../../releases)** y descarga el `.apk` más reciente.
2. Ábrelo en tu teléfono (Android 8 o superior). Si te lo pide, permite «instalar apps de origen desconocido».

## Configurar las llaves (5 minutos)

En la app: pestaña **Motores → API Keys**. Cada tarjeta tiene un botón **«Conseguir mi llave»** que abre la página correcta, y **«¿Cómo?»** con los pasos.

**No necesitas las 5.** Con una sola ya funciona. Mientras más gratuitas pongas, más puedes transcribir.

| # | Servicio | Qué regala* | Dónde sacar la llave |
|---|---|---|---|
| 1 | Groq | Gratis siempre, con límite (~2 h de audio por hora, ~8 h por día) | console.groq.com/keys |
| 2 | Inworld | Hasta ~400 min STT gratis una vez (On-Demand) | platform.inworld.ai |
| 3 | AssemblyAI | Crédito de regalo una vez (~$50 USD) | assemblyai.com/app |
| 4 | Deepgram | Crédito de regalo una vez (~$200 USD) | console.deepgram.com |
| 5 | De pago (opcional) | Nada: aquí sí pagas | Groq de pago o Microsoft Azure |

\*Las ofertas son de cada empresa y pueden cambiar. La app las verifica sola (ver abajo).

### ⚠️ La regla para gastar $0

**No registres tarjeta de crédito en Groq, Inworld, AssemblyAI ni Deepgram.**
Si pones tarjeta, cuando se acabe el regalo el proveedor te va a cobrar, y la app no puede impedirlo.
El «Modo $0» de la app (activado por defecto) solo evita usar el motor #5 de pago.

## Cómo funciona por dentro (sin tecnicismos)

- **Cascada automática:** intenta en orden 1 → 2 → 3 → 4. Si uno responde «sin crédito» o «límite alcanzado», pasa al siguiente sin que hagas nada.
- **Tú eliges:** cada motor tiene un interruptor «Usar este motor».
- **Medidores:** cada barra dice **Real** (el dato lo da el proveedor: Groq y Deepgram) o **Estimado** (la app cuenta lo que transcribes: Inworld y AssemblyAI). Si un «Estimado» no coincide con tu panel, corrígelo con «Corregir».
- **Verificar mis llaves y ofertas:** prueba cada llave con 0.3 s de silencio y te dice si sigue gratis, en pausa o sin saldo. Se hace sola una vez por semana.

## Descargas de redes sociales

- El motor de descargas (**yt-dlp**) **se actualiza solo** cada 3 días, y también en cuanto una descarga falla porque un sitio cambió. No tienes que hacer nada.
- Si un sitio pide iniciar sesión, ve a **Motores → Cuentas para descargar** y conecta **tu propia cuenta**. Las cookies se quedan en tu teléfono.
- **YouTube** es el más difícil: a veces pide verificar que no eres un robot, y Google puede bloquear el inicio de sesión dentro de apps. Si te pasa, exporta `cookies.txt` desde Firefox en tu PC y pégalo en la app. Usa una cuenta secundaria.
- **Si un sitio deja de funcionar:** casi siempre se arregla solo en unos días, cuando yt-dlp publica el arreglo y la app se actualiza. Mientras tanto, descarga el video con otra app y **compártelo a KlipNote**: la transcripción sigue funcionando.

## Privacidad

- Tus llaves se cifran con el Keystore de Android y nunca salen del teléfono, salvo para llamar al proveedor que elegiste.
- Tus cookies y tu historial son locales. La app no tiene servidor propio ni recopila datos.
- Lo único que descarga de este repo es `config/stt-router.json` (ver abajo). No envía nada.

## Contribuir (sin programar)

¿Cambió una oferta o un servicio dejó de dar gratis? Edita **[`config/stt-router.json`](config/stt-router.json)** y manda un Pull Request:

- `offers`: el texto de la oferta que se ve en la app.
- `disabled`: pon ahí un servicio para que la app lo salte. Por ejemplo, `["inworld"]` si terminó su plan gratis.
- `priority`: el orden de la cascada. El de pago siempre queda último, y la config nunca puede activarlo.

Las apps instaladas toman el cambio solas, sin publicar un APK nuevo. Si el archivo falta o está mal escrito, la app usa sus valores por defecto.

## Compilar

```bash
cp local.properties.example local.properties   # ajusta sdk.dir
./gradlew assembleDebug
```

- APK: `app/build/outputs/apk/debug/`.
- **GitHub Actions:** en este repo compila el APK firmado y crea el Release. En forks y Pull Requests compila un APK *debug* y lo deja como artefacto. No necesitas keystore.
- Para firmar tu propio release: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD` y `ANDROID_KEY_ALIAS` como *secrets* del repo.
- En local, para leer la config remota: `klipnote.githubRepo=usuario/repo` en `gradle.properties`.

**Stack:** Kotlin 2.0 · Jetpack Compose · Material 3 · OkHttp · youtubedl-android (yt-dlp + FFmpeg) · minSdk 26 · targetSdk 35.

Versión actual: **1.5.0** (versionCode 122).

## Licencia

Este proyecto se publica bajo la **[GNU General Public License v3.0](LICENSE)** (GPL-3.0).

Incluye / usa, entre otros:

- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** — Unlicense
- **[youtubedl-android](https://github.com/yausername/youtubedl-android)** (fork junkfood02) — GPL-3.0

Si redistribuyes KlipNote (APK o código), debes cumplir la GPL-3.0: incluir el texto de la licencia, ofrecer el código fuente correspondiente y mantener las mismas libertades para quien lo reciba.

## Aviso

Respeta los términos de cada plataforma y los derechos de autor del contenido que descargas o transcribes. Este proyecto no está afiliado a ninguno de los servicios mencionados.
