# Free keys

<p>🇺🇸 <strong>English</strong> · 🇪🇸 <a href="llaves-gratis.md">Español</a></p>

KlipNote **does not ship with keys**. Each person creates their own on the provider site and pastes it in the app (**Settings → My keys**). Keys are stored encrypted on your phone.

**You don’t need all of them.** One key (for example Groq) is enough to transcribe. The more you add, the longer you can stay in “free mode.”

### ⚠️ To spend $0

**Do not add a credit card** on Groq, Inworld, AssemblyAI, or Deepgram. If you add a card and the free grant ends, the provider may charge you — the app cannot stop that.

Offers are published by each company and **can change**. The text below comes from the in-app catalog and [`config/stt-router.json`](../config/stt-router.json).

---

## 1 · Groq (recommended to start)

**What you get:** free with limits (~2 h of audio per hour and ~8 h per day). Files up to 25 MB.

**Where:** [console.groq.com/keys](https://console.groq.com/keys)

1. Go to console.groq.com and create an account (email or Google).
2. Open “API Keys” and tap “Create API Key”.
3. Copy the key (starts with `gsk_`) and paste it in KlipNote → **My keys**.

---

## 2 · Inworld

**What you get:** up to ~400 min of free STT once (On-Demand plan). Files up to ~16 MB.

**Where:** [platform.inworld.ai](https://platform.inworld.ai/)

1. Go to platform.inworld.ai and create an account.
2. Open Manage → API Keys → Create Inworld API key (Standard type).
3. Copy the key with “Copy key” (already in Basic/Base64) and paste it in **My keys**.

---

## 3 · AssemblyAI

**What you get:** a one-time gift credit (~$50 USD at the time this app was published), no card and no expiry (per the current offer).

**Where:** [assemblyai.com/app](https://www.assemblyai.com/app)

1. Go to assemblyai.com and create an account (**do not add a card**).
2. Your API Key appears on the main dashboard.
3. Copy it and paste it in **My keys**.

---

## 4 · Deepgram

**What you get:** a one-time gift credit (~$200 USD at the time this app was published), no card (per the current offer).

**Where:** [console.deepgram.com](https://console.deepgram.com/)

1. Go to console.deepgram.com and create an account (**do not add a card**).
2. Open “API Keys” → “Create a New API Key”.
3. To see the real balance, give the key “Admin” or “Owner” role.
4. Copy it and paste it in **My keys**. The Project ID links automatically.

---

## 5 · Paid backup (optional)

Here you **do pay**. It is only used if you turn off **$0 mode** and every free engine failed.

Typical options: Groq billing, or a Microsoft Azure speech resource. Steps are inside the app under **My keys**.

---

## Automatic order

With **Automatic** on, KlipNote tries this order:

1. Groq → 2. Inworld → 3. AssemblyAI → 4. Deepgram → 5. Paid (only if you allow it)

If one replies with no credit or a rate limit, it moves to the next without you doing anything.

Back to the [guides](README.md).
