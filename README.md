# DeepCopy AI

App Android nativa (`com.blackeye.deepcopy`) para transcribir audio/video local con **Deepgram Nova-3** y copiar el texto listo para cualquier IA.

## Características

- Selección de archivos (`audio/*`, `video/*`) o compartir desde Galería / Archivos / Seal / WhatsApp
- Extracción de audio de video sin re-encode (`MediaExtractor` + `MediaMuxer`)
- Modos Auto (detect language) y Spanglish/Multi
- Diarización opcional (`[Voz X]: …`)
- Auto-copiado al portapapeles + prompts para IA
- **Panel Auditor de Crédito ($200 USD)**: saldo oficial, gasto app, Δ, cotizador pre-vuelo, hard-stop y bitácora JSON

## Panel Auditor (resumen)

1. Arriba: barra de crédito (verde >$50 · ámbar $15–$50 · rojo <$15).
2. Al elegir/compartir media: cotizador con duración local y costo estimado a 4 decimales → **Transcribir Ahora**.
3. Candados por defecto: reserva $5 + tope app $195; archivos >$0.50 piden confirmación.
4. Historial: recupera/copia texto sin re-transcribir.
5. Key Member sin `billing:read`: badge ámbar y auditoría local (sin crash).

## Requisitos

- Android 8.0+ (API 26)
- API Key de [Deepgram](https://deepgram.com/) (Admin para saldo oficial en vivo)

## Configurar API Key (sin hardcode)

1. **En la app**: ícono de llave en la TopBar → pegar key → Guardar (SharedPreferences privadas).
2. **Build local opcional**: en `local.properties` (no se sube a git):

```properties
sdk.dir=/path/to/Android/sdk
DEEPGRAM_API_KEY=tu_key_aqui
```

3. **CI**: secreto de repositorio `DEEPGRAM_API_KEY` (inyecta `BuildConfig.DEFAULT_DEEPGRAM_KEY`).

Prioridad en runtime: SharedPreferences → `BuildConfig.DEFAULT_DEEPGRAM_KEY`.

## Build local

```bash
# Con Gradle 8.9 instalado (sin wrapper.jar en el repo)
export DEEPGRAM_API_KEY=...   # opcional
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/`.

Versión local sin `GITHUB_RUN_NUMBER`: `versionName = 1.1.2`, `versionCode = 2`.

## GitHub Actions

En cada push a `main` (o `workflow_dispatch`):

1. Compila debug con Gradle 8.9
2. Publica release `DeepCopy-v1.1.<run_number>.apk` (tag `v1.1.<run_number>`)
3. `versionCode = 100 + run_number`, `versionName = 1.1.<run_number>`

Secretos necesarios:

| Secret | Uso |
|--------|-----|
| `DEEPGRAM_API_KEY` | Default embebido en BuildConfig (puede quedar vacío si solo usas la UI) |

`GITHUB_TOKEN` se usa automáticamente para crear el release.

## Stack

- Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10.01) + Material3
- OkHttp 4.12 streaming (bloques 8KB)
- AGP 8.7.2 · compile/targetSdk 35 · minSdk 26 · Java 17
