# Contribuir ofertas (sin programar)

Cuando cambia una promoción o un servicio deja de dar gratis, puedes actualizar el archivo de configuración del repo. Las apps instaladas pueden tomar el cambio **sin un APK nuevo**.

Archivo: [`config/stt-router.json`](../config/stt-router.json)

## Qué se puede editar

- **`offers`**: el texto de la oferta que se ve en la app.
- **`disabled`**: lista de servicios para saltar. Ejemplo: `["inworld"]`.
- **`priority`**: orden del modo Automático. El de pago (`paid`) debe quedar **último**.

### Regla importante

La config remota **nunca puede activar** el motor de pago. Eso solo lo decide cada persona con el interruptor **Modo $0** en la app.

## Cómo mandar el cambio

1. Abre el repo en GitHub: [gabrieldave/KlipNote](https://github.com/gabrieldave/KlipNote).
2. Entra a `config/stt-router.json`.
3. Pulsa el lápiz (editar) — GitHub te pedirá hacer un **fork** si no tienes permiso de escritura.
4. Cambia solo lo necesario. Guarda y crea un **Pull Request** explicando qué oferta cambió y dónde lo viste.
5. Espera la revisión.

Si el archivo falta o está mal escrito, la app usa sus valores por defecto del catálogo interno.

## ¿Quieres aportar código?

También se aceptan Issues y Pull Requests de código. Lee [`AGENTS.md`](../AGENTS.md) (también si usas una IA para ayudar) y abre un Issue antes de cambios grandes. Los cambios a `.github/workflows` se revisan a mano.

Volver a las [guías](README.md).
