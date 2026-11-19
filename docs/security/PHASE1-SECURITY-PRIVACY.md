# Fase 1 — Security & Privacy Remediation (evidencias)

Rama: `fix/pre-release-audit-privacy-licenses-ux-2026`  
Repo: `gabrieldave/KlipNote`  
Correo empresarial local del repo: `blackeye@iareal.net`  
Fecha de auditoría: 2026-11-19

**DETENIDO antes de:** force push, reescritura remota, borrado de releases/tags, publicación de nuevas versiones, subida de secretos de firma a GitHub Actions.

## 1. Hallazgos originales

### Identidad Git
- 66/66 commits del historial usaban un correo personal (dominio `gmail.com`; valor no revelado).
- Autores (nombres): dos identidades de display conservadas en la simulación.
- Tags afectados: `v1.7.2`.
- Ramas/refs afectadas: `main`, ramas `fix-*` remotas, tag `v1.7.2` (todas las refs del repo espejo).

### Firma Android / Llaves Android
- `~/.blackeye-keys/android/blackeye-release.jks` existía (llave previa). **No se abrió ni copió el material privado.** Se conserva.
- `Llaves Android` generaba **PKCS12** con extensión **`.jks`** (formato/extensión inconsistentes).
- `owner_name()` usaba `id -F` / `whoami` → el **nombre personal de la Mac** entraba en el DN (`O=`).

### GitHub Actions
- Un solo workflow `build-apk.yml` con `permissions: contents: write` también en `pull_request`.
- Secretos de firma disponibles en el mismo job que corre en contexto de push; riesgo de privilegio excesivo del `GITHUB_TOKEN`.
- Pin `gradle/actions/setup-gradle@48b5f213…` **no existe** en el repositorio oficial `gradle/actions` (SHA inválido).

### Protección de archivos
- `.gitignore` ya cubría `*.jks`, `*.keystore`, `*.p12`, `.env*`, `*.pem`, `*.key`, `local.properties`.
- Faltaban: `*.pfx`, `keystore.properties`, `key.properties`.

## 2. Cambios realizados

1. `git config --local user.email blackeye@iareal.net` y `user.name "BLACK EYE Software"` en este clone.
2. Espejo independiente + simulación `git filter-repo` (solo correo → `blackeye@iareal.net`; nombres y fechas conservados).
3. Nueva llave PKCS12 KlipNote fuera del repo + contraseña en Keychain; DN sin datos personales.
4. Parche local de `Llaves Android` (DN org + `.p12`).
5. Workflows separados: PR / Debug / Release; SHA de Actions verificados.
6. `.gitignore` endurecido.
7. Escaneos y compilación de validación (ver §4).

## 3. Archivos modificados (repo KlipNote)

- `.gitignore`
- `.github/workflows/pr-checks.yml` (nuevo)
- `.github/workflows/debug-apk.yml` (nuevo)
- `.github/workflows/release-apk.yml` (nuevo)
- `.github/workflows/build-apk.yml` (eliminado)
- `docs/security/PHASE1-SECURITY-PRIVACY.md` (este informe)

Fuera del repo (local):
- `~/Downloads/LlavesAndroid/llaves-android.sh` (parche DN + `.p12`)
- `~/.blackeye-keys/android/apps/klipnote/klipnote-release.p12`
- Keychain service `Llaves Android` / account `klipnote`
- Espejo: `Documents/Android apps/KlipNote-history-rewrite-mirror` (+ `.git` bare)

## 4. Resultados de pruebas

| Comprobación | Resultado |
|---|---|
| Identidad local del repo | `user.email=blackeye@iareal.net` |
| Simulación rewrite (espejo) | 66 commits; emails → `iareal.net`; nombres preservados (2); tree paths idénticos (81 archivos) |
| gitleaks (git history) | 1 hallazgo `generic-api-key` = **falso positivo** (nombre de preferencia `migrated_billing_to_guiones_v163`). Sin API keys/keystore en historial escaneado. |
| gitleaks (workdir con build/) | Ruido en `app/build/intermediates/**` (IDs de recursos). Mitigado con `.gitleaks.toml` excluyendo `build/`. |
| `./gradlew testDebugUnitTest` | **PASS** |
| `./gradlew assembleDebug` | **PASS** |
| Cert nuevo DN | `CN=KlipNote Release, OU=Release Engineering, O=BLACK EYE Software` |
| Formato llave nueva | PKCS12 `.p12`, perms `600` |
| Llave antigua preservada | `blackeye-release.jks` sigue presente |
| Subida a GitHub Actions | **No realizada** (según alcance) |

## 5. Estado de la nueva identidad de firma

| Campo | Estado |
|---|---|
| Archivo | `~/.blackeye-keys/android/apps/klipnote/klipnote-release.p12` |
| Alias | `klipnote` |
| Storetype | PKCS12 |
| DN | CN=KlipNote Release, OU=Release Engineering, O=BLACK EYE Software |
| Keychain | SET (`Llaves Android` / `klipnote`) |
| Secretos GHA | **No subidos** |
| Llave previa | Conservada |

Huella pública SHA-256 del certificado (no es secreto):  
`106C72878669D4417D105C6DE20E0F1BE6FF2F234490AEA2054DA9B1A120F34B`

## 6. Estado de GitHub Actions

| Workflow | Trigger | Permissions | Secretos |
|---|---|---|---|
| `pr-checks.yml` | `pull_request` → main | `contents: read` | Ninguno |
| `debug-apk.yml` | push main / dispatch | `contents: read` | Ninguno |
| `release-apk.yml` | push main / dispatch | job: `contents: write` + `environment: release` | Firma vía secrets del environment |

Pins verificados en GitHub (commit SHA):
- `actions/checkout@11bd7190…` (v4.2.2)
- `actions/setup-java@c5195efe…` (v4.7.1)
- `actions/upload-artifact@ea165f8d…` (v4.6.2)
- `gradle/actions/setup-gradle@06832c7b…` (v4.3.1) — reemplaza SHA inválido
- `android-actions/setup-android@9fc6c4e9…` (v3.2.2)

`persist-credentials: false` en checkouts de PR/debug/release.

## 7. Historial Git y simulación de reescritura

### Alcance
- Commits afectados: **66**
- Refs afectadas: todas las ramas remotas listadas + tag `v1.7.2`
- Terceros: no hay commits de terceros distintos a reescribir (solo las dos identidades de display del fundador / operador).

### Simulación (local, espejo)
- Herramienta: `git-filter-repo --mailmap` con mapeo **solo de email** (`<blackeye@iareal.net> <correo-personal>`).
- Nombres de autor preservados.
- Fechas preservadas.
- Contenido de archivos (paths) idéntico antes/después.
- Dominios personales ausentes tras la simulación.

### Plan de sincronización GitHub (requiere autorización CEO)

1. Anunciar ventana de mantenimiento (issues/PRs abiertos).
2. Crear backup bare adicional del remoto.
3. Aplicar el mismo `filter-repo` sobre un clone de coordinación (o promover el espejo validado).
4. `git push --force-with-lease` de **todas** las ramas afectadas + tags (solo tras OK explícito).
5. Pedir a colaboradores que re-clonen (no rebase local sobre SHAs viejos).
6. Verificar que `v1.7.2` apunta al tag reescrito y que Releases siguen ligadas al tag (GitHub Release no se borra en este plan).
7. Rotar cualquier token/sesión si se considera necesario por exposición histórica de email (el email en commits no es secreto de firma, pero sí PII).

**No ejecutado en remoto.**

## 8. Riesgos pendientes

1. El APK **v1.7.2** público sigue firmado con la llave **anterior** hasta que se suban los nuevos secretos y se publique un build nuevo (fuera de esta fase).
2. Environment GitHub `release` debe crearse/configurarse (protección de entorno + secrets) antes de que `release-apk.yml` funcione.
3. Secretos GHA actuales (`ANDROID_KEYSTORE_*`) siguen siendo los de la llave vieja hasta rotación autorizada.
4. Email personal permanece en el historial **remoto** hasta force-push autorizado.
5. Posible nombre personal en el DN de `blackeye-release.jks` (llave vieja); no inspeccionado a fondo para no manejar material privado innecesariamente.
6. Push a `main` disparará Debug + Release; Release fallará hasta que exista environment/secrets alineados.

## 9. Acciones que necesitan autorización del CEO

1. Force-push / reescritura remota del historial (emails).
2. Crear environment `release` en GitHub y mover/rotar secrets de firma.
3. Subir la **nueva** llave `.p12` a Actions (reemplazo controlado).
4. Publicar una nueva versión firmada con la llave nueva (si se desea cortar la cadena de la llave anterior para distribución futura).
5. Decidir si se actualiza el DN/documentación pública sobre rotación de firma para usuarios que verifican APKs.
