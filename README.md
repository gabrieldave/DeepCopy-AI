# KlipNote

App Android nativa (`com.blackeye.klipnote`) para transcribir audio/video local con un **Router STT en cascada** (Groq Free → AssemblyAI → Deepgram → Respaldo Pagado) y copiar el texto listo para cualquier IA.

## Características

- Selección de archivos (`audio/*`, `video/*`) o compartir desde Galería / Archivos / Seal / WhatsApp
- Extracción de audio de video sin re-encode (`MediaExtractor` + `MediaMuxer`)
- Router gratis con auto-salto (failover) y Escudo Anti-Gladia (circuit breaker + verificación de ofertas)
- Modos Auto / Spanglish (Deepgram) y Diarize opcional en nivel Deepgram
- **Modo $0 Estricto** por defecto (bloquea cobros si se agotan las capas gratis)
- BYOK multi-proveedor cifrado (`AndroidKeyStore` + AES/GCM); máscara `••••XXXX` sin ojito
- Auto-copiado al portapapeles + prompts para IA

## Motores (prioridad)

1. **Groq Free** — `whisper-large-v3-turbo`, tope 25MB, cuota diaria (~8h)
2. **Inworld Free** — `inworld/inworld-stt-1`, sync `POST /stt/v1/transcribe`, ~16 MiB/request, 400 min/mes renovables
3. **AssemblyAI** — bolsa gratis $50 USD (~185h)
4. **Deepgram Nova-3** — créditos $200 USD con umbral de seguridad $1
5. **Respaldo Pagado** — Microsoft MAI / Groq Paid (switch explícito)

Versión: `versionName = 1.2.4`, `versionCode = 112`.

Selector de ruteo: **Auto (cascada)** o **Prueba Manual** por motor (chips + Probar en tarjetas).

## Requisitos

- Android 8.0+ (API 26)
- Al menos una API Key de Groq, AssemblyAI, Deepgram o respaldo pagado

## Configurar API Keys (sin hardcode)

En la app: ícono de llave → pegar key por motor → Guardar (bóveda cifrada del dispositivo).  
Release comercial: **cero llaves** en `BuildConfig` / APK.

## Build local

```bash
./gradlew assembleRelease --console=plain -q
```

APK: `app/build/outputs/apk/release/`.

Versión local: `versionName = 1.2.0`, `versionCode = 108`.

## Firma

Release usa `$HOME/.blackeye-keys/android/blackeye-release.jks` vía `keystore.properties`, o variables CI (`ANDROID_KEYSTORE_*`).

## Stack

- Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10.01) + Material3
- OkHttp 4.12 streaming
- AGP 8.7.2 · compile/targetSdk 35 · minSdk 26 · Java 17
