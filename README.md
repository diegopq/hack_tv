# hack_tv

Custom Android TV launcher: ad-free home screen, remote button remapping, and streaming recommendations. Built with Kotlin and Compose for TV.

It started as a way to speed up and clean up a Hisense Android TV (Android 12, MediaTek MT9689, 2.4 GB RAM) without buying third-party apps.

**[▶ Try the interactive mockup](https://diegopq.github.io/hack_tv/)** ([English](https://diegopq.github.io/hack_tv/?lang=en) · [Español](https://diegopq.github.io/hack_tv/?lang=es)): use your keyboard as the remote, or click and tap.

![Home screen with the recommendations banner](docs/screenshots/home.jpg)

| Recently played | All apps |
|---|---|
| ![Recently played row](docs/screenshots/recently-played.jpg) | ![All apps grid](docs/screenshots/all-apps.jpg) |
| **Recommendations follow the focused app** | **Settings shortcuts** |
| ![Banner showing Disney+ recommendations](docs/screenshots/home-disney.jpg) | ![Settings shortcuts](docs/screenshots/settings.jpg) |

> Screenshots come from the HTML mockup, not the Android app yet. The UI is available in English and Spanish.

## Status

| Part | Status |
|---|---|
| UI design (HTML mockup) | ✅ Done: [`diseno/maqueta.html`](diseno/maqueta.html) |
| Revert script for the TV optimizations | ✅ Done: [`revert_tv.sh`](revert_tv.sh) |
| Android launcher app | 🚧 Not started |

## Planned features

- **Ad-free home screen** with your apps, HDMI inputs, and system shortcuts.
- **Recommendations banner** with movies and series available on the streaming apps you have installed, from [TMDB](https://www.themoviedb.org/). It filters by the app you're focused on.
- **Recently played** row, built from the launcher's own local history of what your apps play (Android media sessions).
- **Remote button remapping** through an accessibility service, for example the Settings, App list, YouTube, Prime Video, and Disney+ buttons.
- **Lightweight**: tuned for low-end TV hardware.

## Design mockup

Try it online at **[diegopq.github.io/hack_tv](https://diegopq.github.io/hack_tv/)**, or open [`diseno/maqueta.html`](diseno/maqueta.html) locally in a browser. It renders at 1920×1080 and scales to fit the window. Every push to `diseno/` redeploys it through [GitHub Pages](.github/workflows/pages.yml).

Use the keyboard as the remote:

| Key | Action |
|---|---|
| Arrow keys | Navigate |
| Enter | Open |
| Esc | Back to home |
| ← → on the banner buttons | Previous or next recommendation |
| A / S | Simulates the remote's App list and Settings buttons |
| N / Y / P / D | Simulates the Netflix, YouTube, Prime Video, and Disney+ buttons |
| L | Switch language (English / Spanish) |
| H | Show or hide the help bar |
| Click or tap | Focus an item; click again to open it |

### Languages

The mockup supports English and Spanish. It picks the language in this order:

1. The `?lang=en` or `?lang=es` URL parameter.
2. The language you last chose with **L** or the **Language** card in Settings. It's saved in your browser.
3. Your browser's language, falling back to English.

All UI text lives in the `I18N` dictionary in [`diseno/maqueta.html`](diseno/maqueta.html). To add a language, copy the `en` block, translate it, and add the translations to the `{es:…, en:…}` fields of the sample data. The Android app will use standard Android string resources instead, and request TMDB data in the TV's language.

### URL parameters

URL parameters set a starting state, which is handy for demos and screenshots: `?lang=en`, `?app=max`, `?focus=recent&recent=2`, `?focus=hero`, `?view=apps`, `?view=settings`, `?hint=0`, `?autoplay=0`, `?still=1` (turns off animations).

The sample artwork comes from [TVmaze](https://www.tvmaze.com/) (CC BY-SA) and loads from their servers.

## Configuration

Local settings and API keys live in a `.env` file that Git ignores:

```bash
cp .env.example .env
```

| Variable | Description |
|---|---|
| `TV_IP` | Your TV's IP address on the local network |
| `TV_PORT` | adb port (default `5555`) |
| `TMDB_API_KEY` | Free TMDB API key for recommendations ([get one here](https://www.themoviedb.org/settings/api)) |

Never commit `.env`. Only `.env.example` belongs in the repository.

## Revert the TV optimizations

The optimizations disable some preinstalled packages (Hisense extras, AirPlay, Google ambient screensaver) and speed up animations. To undo them, run this with network debugging enabled on the TV and `TV_IP` set in `.env`:

```bash
./revert_tv.sh               # re-enable everything
./revert_tv.sh com.vt.alexa  # re-enable a single package
```

Requires [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`). The script looks for it in `~/Library/Android/sdk/platform-tools/adb`. Set `ADB` to point somewhere else.

## Credits

- Recommendations data: [TMDB](https://www.themoviedb.org/). This product uses the TMDB API but is not endorsed or certified by TMDB.
- Sample images in the mockup: [TVmaze](https://www.tvmaze.com/).
- App names and logos belong to their respective owners. They aren't included in this repository; the launcher uses each installed app's own banner.

## License

[Apache License 2.0](LICENSE)
