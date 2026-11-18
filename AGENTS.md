# AGENTS.md: instrucciones para asistentes de IA (Cursor, OpenCode, Antigravity, etc.)

Lee esto completo antes de tocar el proyecto. El dueño no es programador profesional: explícale en español sencillo y pide confirmación antes de cualquier cosa irreversible.

## Qué es
**KlipNote**: app Android (Kotlin + Jetpack Compose, minSdk 26, targetSdk 35) que transcribe audio y video a texto, gratis y de código abierto (GPL-3.0). Repo: `gabrieldave/KlipNote`. Package: `com.blackeye.klipnote`; el debug usa `com.blackeye.klipnote.debug`.

- **Cascada de transcripción:** Groq Free → Inworld → AssemblyAI → Deepgram → Pago (este último solo si el usuario apaga «Modo $0»). Salta sola cuando el proveedor responde 402/403/429.
- **BYOK:** cada usuario pone sus propias API keys, que se cifran en su teléfono (`ApiKeyStore`). El APK **no lleva ninguna llave**.
- **Ofertas** en `config/stt-router.json` (se puede actualizar sin publicar un APK) y en `ProviderCatalog.kt`.
- **Descargas** con yt-dlp (youtubedl-android 0.18.x + QuickJS) y cookies por sitio en `CookieStore.kt`.

## Reglas (obligatorias)
1. **Nunca** leas, copies, muestres ni subas el keystore ni sus contraseñas (`~/.blackeye-keys/`, `*.jks`, `keystore.properties`). La firma del release la hace GitHub Actions con secrets. No necesitas firmar en local.
2. **Nunca** pongas API keys, cookies ni datos personales del dueño en el código, el repo, los logs o los commits.
3. **No modifiques `.github/workflows/`** sin explicar el cambio y pedir permiso primero.
4. **Una versión a la vez**, cada una en su rama. No mezcles cambios de versiones distintas. No hagas `reset --hard`, `push --force` ni borres ramas sin permiso.
5. **No cambies la lógica** de la cascada, el Modo $0 ni el cifrado de llaves salvo que el dueño lo pida explícitamente. La config remota nunca puede activar el motor de pago.
6. La versión sale **solo** de `localVersionCode` / `localVersionName` en `app/build.gradle.kts`. Súbela en cada release (el `versionCode` siempre debe subir).
7. Antes de pedirle al dueño que pruebe algo: compila (`./gradlew assembleDebug`), instala y **verifica tú con capturas** (ver abajo). Muéstrale las capturas.
8. No publiques un Release sin el «ok» del dueño.

## Comandos útiles
```bash
./gradlew assembleDebug                      # compilar debug
adb devices                                  # ver el teléfono (Pixel 10 Pro, por cable y/o Wi-Fi)
adb -d install -r app/build/outputs/apk/debug/*.apk   # -d = cable; usa -e si solo hay Wi-Fi
adb shell pm clear com.blackeye.klipnote.debug        # borra datos SOLO de la Debug (ver asistente de inicio)
adb exec-out screencap -p > captura.png      # captura de pantalla para verificar
```
Si `adb devices` muestra el mismo teléfono dos veces (cable + Wi-Fi), usa `-d`.

## Publicar
Push o merge a `main` → GitHub Actions compila el APK firmado y crea o actualiza el Release `v<versionName>`. Revisa con `gh run watch` y `gh release view`.

## Estilo de la app
- Fondo oscuro. Textos en español, sin jerga técnica: decir «Mis llaves», no «BYOK»; «Automático», no «Cascada»; «Videos que piden iniciar sesión», no «cookies».
- Textos secundarios de 12 sp como mínimo y con buen contraste.
- Colores centralizados en el tema; nada de colores escritos a mano en las pantallas.

## Seguridad del repo (no cambiar sin permiso)
- `main` protegida contra force push y borrado.
- Actions con aprobación obligatoria para contribuidores externos.
- Secrets: solo `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD` y `ANDROID_KEY_ALIAS`.
