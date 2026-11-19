# Contribute offers (no coding)

<p>🇺🇸 <strong>English</strong> · 🇪🇸 <a href="contribuir-ofertas.md">Español</a></p>

When a promotion changes or a service stops being free, you can update the repo config file. Installed apps can pick up the change **without a new APK**.

File: [`config/stt-router.json`](../config/stt-router.json)

## What you can edit

- **`offers`**: the offer text shown in the app.
- **`disabled`**: list of services to skip. Example: `["inworld"]`.
- **`priority`**: Automatic mode order. Paid (`paid`) must stay **last**.

### Important rule

Remote config **can never turn on** the paid engine. Only each person decides that with the **$0 mode** switch in the app.

## How to send the change

1. Open the repo on GitHub: [gabrieldave/KlipNote](https://github.com/gabrieldave/KlipNote).
2. Open `config/stt-router.json`.
3. Click the pencil (edit) — GitHub will ask you to **fork** if you don’t have write access.
4. Change only what’s needed. Save and open a **Pull Request** explaining which offer changed and where you saw it.
5. Wait for review.

If the file is missing or malformed, the app falls back to its built-in catalog defaults.

## Want to contribute code?

Issues and code Pull Requests are welcome too. Read [`AGENTS.md`](../AGENTS.md) (also if you use an AI to help) and open an Issue before large changes. Changes to `.github/workflows` are reviewed by hand.

Back to the [guides](README.md).
