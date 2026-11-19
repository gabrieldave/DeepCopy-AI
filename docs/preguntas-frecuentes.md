# Preguntas frecuentes

<p>🇺🇸 <a href="faq.md">English</a> · 🇪🇸 <strong>Español</strong></p>

## ¿Es gratis de verdad?

Puedes usarla **sin pagar a KlipNote**: la app es gratis y de código abierto. El «gratis» de la transcripción viene de **créditos o minutos que regalan otros servicios** (Groq, Inworld, AssemblyAI, Deepgram). Esas ofertas son de cada empresa y **pueden cambiar o acabarse**.

Para no gastar dinero: **no registres tarjeta** en esos servicios y deja activo el **Modo $0** (evita el motor de pago).

## ¿Mis llaves están seguras?

Sí, en la medida de lo razonable en un teléfono: se guardan **cifradas** en tu dispositivo. El APK **no lleva ninguna llave**. KlipNote no tiene servidor propio que las reciba: solo las usa para llamar al proveedor que elegiste.

## ¿Por qué no está en Play Store?

De momento se distribuye por **GitHub Releases** (APK). Así cualquiera puede auditar el código y descargar sin cuenta de Google Play. Si en el futuro hay otra tienda, se avisará en el repo.

## ¿Qué pasa si un servicio deja de ser gratis?

La app puede saltarlo sola cuando responde «sin crédito» o similar. Además, la comunidad puede actualizar [`config/stt-router.json`](../config/stt-router.json) (ver [Contribuir ofertas](contribuir-ofertas.md)) para cambiar textos, desactivar un servicio o ajustar el orden **sin publicar un APK nuevo**.

## ¿Por qué un video pide iniciar sesión?

Algunos videos (privados, cuentas cerradas, o cuando Instagram/YouTube piden entrar) solo se pueden bajar con sesión. En **Ajustes → Videos que piden iniciar sesión** puedes conectar **tu** cuenta. Las cookies se quedan en tu teléfono. Usa solo si te sale ese aviso; preferible una cuenta secundaria.

## ¿Qué hago si un sitio deja de descargar?

Casi siempre se arregla en unos días: el motor de descargas (**yt-dlp**) **se actualiza solo**. Mientras tanto, descarga el video con otra app y **compártelo a KlipNote**: la transcripción sigue funcionando.

## ¿Necesito las cuatro llaves gratis?

No. Con una basta. Groq suele ser el mejor punto de partida.

## ¿KlipNote guarda mis videos?

En el modo **Solo texto** te da el texto y **no guarda el video**. Otros modos pueden guardar audio o video según lo que elijas. Los **guiones** (texto) se quedan en el historial local de la app.

Volver a las [guías](README.es.md).
