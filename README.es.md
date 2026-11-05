# hack_tv

[English](README.md) · **Español**

Launcher personalizado para Android TV: pantalla de inicio sin anuncios, remapeo de los botones del control y recomendaciones de streaming. Hecho con Kotlin y Compose for TV.

Empezó como una forma de acelerar y limpiar una TV Hisense con Android TV (Android 12, MediaTek MT9689, 2.4 GB de RAM) sin comprar apps de terceros.

**[▶ Prueba la maqueta interactiva](https://diegopq.github.io/hack_tv/?lang=es)** ([Español](https://diegopq.github.io/hack_tv/?lang=es) · [English](https://diegopq.github.io/hack_tv/?lang=en)): usa el teclado como control remoto, o haz clic o toca la pantalla.

![Pantalla de inicio con el banner de recomendaciones](docs/screenshots/es/home.jpg)

| Reproducido recientemente | Todas las apps |
|---|---|
| ![Fila de reproducido recientemente](docs/screenshots/es/recently-played.jpg) | ![Cuadrícula con todas las apps](docs/screenshots/es/all-apps.jpg) |
| **Las recomendaciones siguen a la app enfocada** | **Accesos directos a los ajustes** |
| ![Banner con recomendaciones de Disney+](docs/screenshots/es/home-disney.jpg) | ![Accesos directos a los ajustes](docs/screenshots/es/settings.jpg) |

> Las capturas son de la maqueta HTML, todavía no de la app Android. La interfaz está disponible en español e inglés.

## Estado

| Parte | Estado |
|---|---|
| Diseño de la interfaz (maqueta HTML) | ✅ Listo: [`diseno/maqueta.html`](diseno/maqueta.html) |
| Script para revertir las optimizaciones de la TV | ✅ Listo: [`revert_tv.sh`](revert_tv.sh) |
| App launcher para Android | 🚧 Sin empezar |

## Funciones planeadas

- **Pantalla de inicio sin anuncios** con tus apps, las entradas HDMI y accesos directos del sistema.
- **Banner de recomendaciones** con películas y series disponibles en las apps de streaming que tienes instaladas, desde [TMDB](https://www.themoviedb.org/). Se filtra según la app que tengas enfocada.
- **Fila de reproducido recientemente**, armada con el historial local propio del launcher a partir de lo que reproducen tus apps (sesiones multimedia de Android).
- **Remapeo de botones del control** mediante un servicio de accesibilidad, por ejemplo los botones de Ajustes, Lista de apps, YouTube, Prime Video y Disney+.
- **Ligero**: optimizado para TVs con hardware modesto.

## Maqueta del diseño

Pruébala en línea en **[diegopq.github.io/hack_tv](https://diegopq.github.io/hack_tv/?lang=es)**, o abre [`diseno/maqueta.html`](diseno/maqueta.html) localmente en un navegador. Se dibuja a 1920×1080 y se escala para caber en la ventana. Cada cambio que se sube a `diseno/` se vuelve a publicar con [GitHub Pages](.github/workflows/pages.yml).

Usa el teclado como control remoto:

| Tecla | Acción |
|---|---|
| Flechas | Navegar |
| Enter | Abrir |
| Esc | Volver al inicio |
| ← → en los botones del banner | Recomendación anterior o siguiente |
| A / S | Simula los botones de Lista de apps y Ajustes del control |
| N / Y / P / D | Simula los botones de Netflix, YouTube, Prime Video y Disney+ |
| L | Cambiar idioma (español / inglés) |
| H | Mostrar u ocultar la barra de ayuda |
| Clic o toque | Enfoca un elemento; otro clic lo abre |

### Idiomas

La maqueta está en español e inglés. Elige el idioma en este orden:

1. El parámetro `?lang=es` o `?lang=en` de la URL.
2. El último idioma que elegiste con **L** o con la tarjeta **Idioma** en Ajustes. Se guarda en tu navegador.
3. El idioma de tu navegador; si no es español ni inglés, usa inglés.

Todos los textos de la interfaz están en el diccionario `I18N` de [`diseno/maqueta.html`](diseno/maqueta.html). Para agregar un idioma, copia el bloque `en`, tradúcelo y agrega las traducciones a los campos `{es:…, en:…}` de los datos de muestra. La app Android usará en su lugar los recursos de texto estándar de Android y pedirá los datos de TMDB en el idioma de la TV.

### Parámetros de la URL

Los parámetros de la URL definen un estado inicial, útil para demos y capturas: `?lang=es`, `?app=max`, `?focus=recent&recent=2`, `?focus=hero`, `?view=apps`, `?view=settings`, `?hint=0`, `?autoplay=0`, `?still=1` (apaga las animaciones).

Las imágenes de muestra vienen de [TVmaze](https://www.tvmaze.com/) (CC BY-SA) y se cargan desde sus servidores.

## Configuración

Los ajustes locales y las claves de API van en un archivo `.env` que Git ignora:

```bash
cp .env.example .env
```

| Variable | Descripción |
|---|---|
| `TV_IP` | Dirección IP de tu TV en la red local |
| `TV_PORT` | Puerto de adb (por defecto `5555`) |
| `TMDB_API_KEY` | Clave de API gratuita de TMDB para las recomendaciones ([consíguela aquí](https://www.themoviedb.org/settings/api)) |

Nunca subas `.env` al repositorio. Solo `.env.example` va en el repositorio.

## Revertir las optimizaciones de la TV

Las optimizaciones desactivan algunos paquetes preinstalados (extras de Hisense, AirPlay, el protector de pantalla ambiental de Google) y aceleran las animaciones. Para deshacerlas, ejecuta esto con la depuración por red activada en la TV y `TV_IP` configurada en `.env`:

```bash
./revert_tv.sh               # reactiva todo
./revert_tv.sh com.vt.alexa  # reactiva un solo paquete
```

Necesitas [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`). El script lo busca en `~/Library/Android/sdk/platform-tools/adb`. Define `ADB` para usar otra ubicación.

## Créditos

- Datos de recomendaciones: [TMDB](https://www.themoviedb.org/). Este producto usa la API de TMDB, pero no está avalado ni certificado por TMDB.
- Imágenes de muestra de la maqueta: [TVmaze](https://www.tvmaze.com/).
- Los nombres y logos de las apps pertenecen a sus respectivos dueños. No se incluyen en este repositorio; el launcher usa el banner propio de cada app instalada.

## Licencia

[Apache License 2.0](LICENSE)
