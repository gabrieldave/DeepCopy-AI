# KlipNote 1.5.0: cambios para la versión pública

Compilado y verificado: `assembleDebug` y `assembleRelease` (con R8) **compilan sin errores**. Falta probarlo en un teléfono real (ver PROMPT_CURSOR.md).

## 1. Cero mantenimiento de ofertas
- **`RemoteRouterConfig.kt`**: ya no depende de `klipnote.iareal.net`. Lee `config/stt-router.json` **del propio repo de GitHub**. La URL se arma sola en GitHub Actions con `GITHUB_REPOSITORY`, así que también funciona en forks.
  - Si no hay URL, no hay internet o el JSON está mal, usa los valores por defecto.
  - Guarda la última config buena para funcionar sin conexión (`loadCached` al iniciar la app).
  - La config **no puede activar el motor de pago**: siempre lo deja al final y lo sigue bloqueando el Modo $0.
  - Nuevo campo `offers` para cambiar el texto de las ofertas sin publicar un APK.
- **`config/stt-router.json`** (nuevo): orden, motores pausados, modelos y textos de ofertas. La comunidad lo actualiza con Pull Requests.
- **`app/build.gradle.kts`**: nuevo `BuildConfig.REMOTE_CONFIG_URL`. Se quitó `DEFAULT_DEEPGRAM_KEY`. Versión **1.5.0 / 122**.
- **`gradle.properties`**: `klipnote.githubRepo=` (opcional, para builds locales).

## 2. Catálogo de proveedores (un solo archivo)
- **`ProviderCatalog.kt`** (nuevo): nombre, oferta, link «Conseguir mi llave», pasos cortos y tipo de medidor (Real / Estimado / Sin medidor) de cada motor. Incluye el aviso «no registres tarjeta».

## 3. Medidor honesto + el usuario elige motores
- **`BillingUi.kt`**:
  - Cada tarjeta muestra la oferta, la etiqueta **Real/Estimado**, de dónde sale el número y un interruptor **«Usar este motor»** (en el de pago: «Permitir gastar dinero»).
  - Aviso fijo de $0 arriba.
  - Corrección: Inworld en minutos usados y **AssemblyAI en saldo USD** (lo que muestra su panel).
  - El botón «Verificar mis llaves y ofertas» ahora explica qué hace.
- **`BillingManager.kt`**: `isEngineEnabled`/`setEngineEnabled` (el de pago queda ligado al Modo $0), `setAssemblyRemainingUsd`, `getAssemblyRemainingUsdEstimate`, y la URL de config sale de BuildConfig.
- **`SttRouter.kt`**:
  - En Auto respeta el interruptor de cada motor. La **prueba manual** funciona aunque el motor esté apagado.
  - Cuando todos fallan, el error ahora explica qué pasó y muestra el recorrido completo.
- **`ApiKeysDialog.kt`**: cada tarjeta trae la oferta, el botón **«Conseguir mi llave»** (abre la web) y **«¿Cómo?»** con los pasos, más el aviso de $0.

## 4. Cookies multi-sitio
- **`CookieStore.kt`** (reescrito):
  - `CookieSite` con Instagram, YouTube, TikTok, Facebook y X, cada uno con su dominio y página de login.
  - Un archivo por sitio, y a yt-dlp **solo se le pasa el del sitio del enlace**.
  - El `cookies.txt` antiguo de Instagram se migra solo.
  - «Desconectar» por sitio.
  - YouTube trae un aviso: Google puede bloquear el login en apps; en ese caso, pegar `cookies.txt` y usar una cuenta secundaria.
- **`MainActivity.kt`**:
  - Sección «Cuentas para descargar» con Conectar/Desconectar por sitio.
  - `SiteCookiesSheet` genérico (reemplaza a `InstagramCookiesSheet`).
  - El botón del error en Capturar abre el login del sitio que falló.
- **`SealDownloader.kt`**:
  - `friendlyDownloadError()`: mensajes entendibles (pide login / enlace no compatible / video privado / sin internet / «el sitio cambió, se arregla solo; mientras, compártelo desde otra app») más el detalle técnico corto.
  - Auto-actualización de yt-dlp **cada 3 días** (antes 7).
  - Auto-reparación ante más errores (403, signature, nsig, javascript, formatos no disponibles).

## 5. Limpieza para publicar
- **`ApiKeyStore.kt`**: se eliminó la lectura de llaves en texto plano desde `Descargas/.klipnote_migrate_once` y el intent de migración. Solo se migran llaves que el propio usuario guardó en versiones anteriores.
- **`MainActivity.kt`**: se quitó `ingestMigrationKey`.
- **`.github/workflows/build-apk.yml`**:
  - Con keystore: APK firmado + Release (sube al Release existente en vez de borrarlo).
  - Sin keystore (forks y PRs): APK **debug** como artefacto, sin fallar.
  - No recompila cuando solo cambia `config/` o archivos `.md`.
- **`README.md`**: reescrito para la comunidad (instalar, conseguir llaves, regla $0, cookies, qué hacer si un sitio falla, privacidad, cómo contribuir editando el JSON, compilar).

## Lo que NO se pudo hacer desde aquí
- Probar en un teléfono real (WebView de login, descargas, transcripción).
- Verificar en vivo las páginas de Inworld (URL exacta de llaves y minutos gratis actuales) y si Deepgram exige rol Admin para leer el saldo.
- Revisar el historial de git del repo original (el zip no lo traía).
- Elegir la licencia (ver PROMPT_CURSOR.md, paso 6).
