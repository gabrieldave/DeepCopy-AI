# Llaves gratis

KlipNote **no trae llaves**. Cada persona crea la suya en el servicio y la pega en la app (**Ajustes → Mis llaves**). Se guardan cifradas en tu teléfono.

**No necesitas todas.** Con una sola (por ejemplo Groq) ya puedes transcribir. Cuantas más pongas, más rato puedes usar el «modo gratis».

### ⚠️ Para gastar $0

**No registres tarjeta** en Groq, Inworld, AssemblyAI ni Deepgram. Si pones tarjeta y se acaba el regalo, el proveedor puede cobrarte y la app no puede impedirlo.

Las ofertas las publica cada empresa y **pueden cambiar**. Los textos de abajo salen del catálogo de la app y de [`config/stt-router.json`](../config/stt-router.json).

---

## 1 · Groq (recomendado para empezar)

**Qué regala:** gratis con límite (~2 h de audio por hora y ~8 h por día). Archivos de hasta 25 MB.

**Dónde:** [console.groq.com/keys](https://console.groq.com/keys)

1. Entra a console.groq.com y crea tu cuenta (correo o Google).
2. Ve a «API Keys» y pulsa «Create API Key».
3. Copia la llave (empieza con `gsk_`) y pégala en KlipNote → **Mis llaves**.

---

## 2 · Inworld

**Qué regala:** hasta ~400 min de STT gratis una vez (plan On-Demand). Archivos de hasta ~16 MB.

**Dónde:** [platform.inworld.ai](https://platform.inworld.ai/)

1. Entra a platform.inworld.ai y crea tu cuenta.
2. Ve a Manage → API Keys → Create Inworld API key (tipo Standard).
3. Copia la llave con «Copy key» (ya viene en Basic/Base64) y pégala en **Mis llaves**.

---

## 3 · AssemblyAI

**Qué regala:** crédito de regalo una sola vez (~$50 USD al momento de publicar la app), sin tarjeta y sin vencimiento (según la oferta actual).

**Dónde:** [assemblyai.com/app](https://www.assemblyai.com/app)

1. Entra a assemblyai.com y crea tu cuenta (**no pongas tarjeta**).
2. En el panel principal verás tu API Key.
3. Cópiala y pégala en **Mis llaves**.

---

## 4 · Deepgram

**Qué regala:** crédito de regalo una sola vez (~$200 USD al momento de publicar la app), sin tarjeta (según la oferta actual).

**Dónde:** [console.deepgram.com](https://console.deepgram.com/)

1. Entra a console.deepgram.com y crea tu cuenta (**no pongas tarjeta**).
2. Ve a «API Keys» → «Create a New API Key».
3. Para ver el saldo real, dale rol «Admin» o «Owner» a la llave.
4. Cópiala y pégala en **Mis llaves**. El Project ID se vincula solo.

---

## 5 · Respaldo de pago (opcional)

Aquí **sí pagas**. Solo se usa si apagas **Modo $0** y fallaron todos los gratuitos.

Opciones típicas: facturación en Groq, o un recurso de voz en Microsoft Azure. Los pasos están dentro de la app en **Mis llaves**.

---

## Orden automático

Con **Automático** activado, KlipNote prueba en este orden:

1. Groq → 2. Inworld → 3. AssemblyAI → 4. Deepgram → 5. Pago (solo si lo permites)

Si uno responde sin crédito o con límite, pasa al siguiente sin que hagas nada.

Volver a las [guías](README.md).
