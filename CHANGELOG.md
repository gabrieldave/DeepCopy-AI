# Changelog — KlipNote

## 1.8.0

- **Encrypted keys backup:** export/import from Settings → My keys with a password (PBKDF2 + AES-GCM). File `.knbackup`; never includes cookies or scripts.
- **Scripts backup (separate):** export/import UTF-8 JSON from the Guiones ⋮ menu; import merges by ID (no duplicates) and reports how many new scripts were added.
- **ES — Respaldo cifrado de llaves:** exportar/importar desde Ajustes → Mis llaves con contraseña (PBKDF2 + AES-GCM). Archivo `.knbackup`; nunca incluye cookies ni guiones.
- **ES — Respaldo separado de guiones:** exportar/importar JSON UTF-8 desde el menú ⋮ de Guiones; al importar fusiona por ID (sin duplicar) e indica cuántos guiones nuevos se añadieron.

## 1.7.5

- **UX / a11y (P3):** minimum secondary text at 12 sp; more UI neutrals centralized in `KlipNeutrals`; expand/collapse icons get content descriptions.
- **Logs & tests:** debug-only auto-heal yt-dlp log; JVM unit tests for `UpdateChecker`, `CookieStore.siteForUrl`, and `RemoteRouterConfig.defaultSnapshot` (paid fallback last).
- **ES — UX / accesibilidad (P3):** textos secundarios mínimo 12 sp; neutros de UI en `KlipNeutrals`; descripciones en iconos expandir/contraer.
- **ES — Logs y pruebas:** log de auto-heal yt-dlp solo en debug; tests JVM de actualización, cookies por URL y orden del snapshot remoto.

## 1.7.4

- **Security hardening (P2):** tighter WebView login settings (no file/content access, HTTPS-only navigation, dispose on leave); HTTPS required for Microsoft/MAI endpoint; update downloads limited to this GitHub repo; unit tests run in the release workflow before signing.
- **Docs:** publish flow matches real gates (tags `v*` on an ancestor of `main`, or `workflow_dispatch` on `main`; no Release overwrite); privacy notes cover Releases check and yt-dlp/ejs updates via youtubedl-android.
- **ES — Seguridad (P2):** WebView de login más estricto; endpoint Microsoft/MAI solo HTTPS; URLs de actualización solo desde este repo; tests unitarios en el workflow de release antes de firmar.
- **ES — Docs:** flujo de publicación alineado con el workflow real; privacidad actualizada (Releases + yt-dlp/ejs).

## 1.7.3

- **Security / CI & open-source licenses:** closes the pre-release audit for Actions hardening and third-party attribution (`THIRD_PARTY_NOTICES.md` + full texts under `LICENSES/`).
- **Organizational signing (BLACK EYE Software):** release builds move to the corporate PKCS12 identity. **If you already installed v1.7.2 (or any earlier signed build), uninstall once and install v1.7.3** — Android will not update over an APK signed with a different certificate; local data is wiped on uninstall unless you backed it up.
- **ES — Seguridad / CI y licencias:** cierre de la auditoría previa a release (Actions + avisos de terceros en `THIRD_PARTY_NOTICES.md` y `LICENSES/`).
- **ES — Firma organizacional (BLACK EYE Software):** si ya tenías **v1.7.2**, **desinstala e instala v1.7.3 una sola vez** por el cambio de certificado de firma.

## 1.7.2

- **IA con entregables directos:** 16 acciones en inglés y español. Infografía visual, diagramas, web HTML, miniapp, panel interactivo, presentación, línea de tiempo, tarjetas, pódcast y checklist.
- **8 en 1 corregido:** ocho entregables enumerados. Solicita imagen o SVG, diagramas y guion limpio, no indicaciones vagas.
- **Enviar al primer toque:** cada botón copia la instrucción completa y abre el selector Android de aplicaciones de IA.
- **Interfaz sin saturación:** seis acciones básicas visibles y diez herramientas visuales agrupadas en un desplegable.
- **Pruebas de contrato:** cobertura JVM para 16 modos, entregables 8 en 1, infografía y HTML.
- **Licencias:** archivo de avisos para componentes y requisitos pendientes de auditoría del APK, sin alterar la compilación o las llaves.


## 1.7.1

- Ajustes: **Check for updates / Buscar actualizaciones** (consulta GitHub Releases latest y abre el APK en el navegador).
- Notas de Release bilingües (EN + ES) en el workflow de build.

## 1.7.0

- **i18n:** inglés por defecto (`values/`) + español (`values-es/`); selector **App language** en Ajustes (sistema / English / Español), aparte del idioma de transcripción.
- Docs bilingües: `README.md` (EN), `README.es.md` (ES) y guías en `docs/`.
- AppCompat + `localeConfig` para idioma por app (Android 8–15).
- Firma release sin ruta fija de keystore local: `ANDROID_KEYSTORE_PROPS` opcional (CI igual con secrets).

## 1.6.1

Arreglos de interfaz (sin tocar cascada, Modo $0 ni cifrado de llaves):

- **Capturar:** pill «Sin llaves» / «Usando: Groq» sin salirse de pantalla; tarjeta «Paso 1» con borde, flecha y botón Empezar.
- **Guiones:** estado vacío con icono grande, texto claro y botón «Ir a Capturar» (sin buscador ni «0 guiones»).
- **Ajustes · Tus servicios gratis:** sin llave → título «Sin llave» + Conectar (sin barra gris ni punto); con 0 llaves → un solo botón desactivado «Conecta una llave primero» (sin «Actualizar medidores»); singular/plural «1 servicio listo» / «N servicios listos».
- **Apariencia:** swatch Dinámico multicolor y nombre bajo cada color (sin repetir el nombre abajo).
- Textos públicos: «Ajustes → Mis llaves» / «Videos que piden iniciar sesión» (en vez de Motores / API Keys / cookies).

## 1.6.0

- Interfaz clara: Capturar / Guiones / Ajustes, temas de color, asistente de inicio.
- Capturas y docs públicas alineadas con la UI nueva.

## 1.5.2 / 1.5.1

- Ajustes menores de avisos y estabilidad de descargas.

## 1.5.0

- Config remota de ofertas desde el repo (`config/stt-router.json`).
- Catálogo de proveedores, medidores honestos e interruptores por servicio.
- Cookies multi-sitio (Instagram, YouTube, TikTok, Facebook, X).
- BYOK solo en el teléfono; builds de release firmados en GitHub Actions.
