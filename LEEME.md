# Mi trading · app Android para tablet (horizontal)

App nativa (Kotlin + Jetpack Compose) con:

- **Favoritos** con cotización y variación del día.
- **Gráfico de velas** (1h, 4h, día, semana, mes, año) con medias móviles 20/50/200, volumen, RSI 14 o Estocástico, soportes y resistencias automáticos. Pellizca para hacer zoom, desliza para moverte y toca una vela para ver sus datos.
- **Tendencia a 6 y 12 meses** (regresión lineal), señales técnicas, **precio objetivo y consenso de analistas** y noticias del valor.
- **Buscar** acciones de EE. UU. y Europa, índices (equivalentes a los CFD), futuros y ETF/ETC.
- **Cartera**: importa extractos de XTB (Excel/CSV) y Trade Republic (CSV), o añade posiciones a mano; calcula ganancia/pérdida.
- **Alertas** de precio, RSI, variación diaria y cruce de la MM50, con notificaciones cada ~15 min aunque la app esté cerrada.
- **Noticias** clasificadas (resultados, analistas, materias primas, macro) y calendario de resultados.
- **Ajustes** para las claves de Twelve Data y Financial Modeling Prep (opcionales).

## Opción A · Compilar con GitHub (sin instalar nada en el ordenador)

1. Crea una cuenta en github.com (gratis) y un repositorio nuevo, por ejemplo `mi-trading` (puede ser privado).
2. En el repositorio pulsa **Add file → Upload files** y arrastra **todo el contenido** de esta carpeta (incluida la carpeta oculta `.github`). Pulsa **Commit changes**.
   - Si tu navegador no deja subir la carpeta `.github`, créala a mano: **Add file → Create new file**, escribe como nombre `.github/workflows/build.yml` y pega el contenido de ese archivo.
3. Ve a la pestaña **Actions**. Verás el proceso «Compilar APK» en marcha (unos 5–8 minutos).
4. Cuando termine con ✓ verde, entra en él y descarga **MiTrading-apk** (un .zip que contiene `app-debug.apk`).
5. Pasa el `app-debug.apk` a la tablet (Google Drive, correo, cable USB…), ábrelo y acepta **Instalar aplicaciones de origen desconocido** cuando Android lo pida.

## Opción B · Android Studio

1. Instala Android Studio (developer.android.com/studio).
2. **File → Open** y elige esta carpeta. Espera a que termine la sincronización.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. El APK queda en `app/build/outputs/apk/debug/`.
4. O conecta la tablet por USB con la depuración USB activada y pulsa ▶ Run.

## Primeros pasos en la app

- Funciona sin claves (datos de Yahoo Finance; Europa con ~15 min de retraso).
- Para precio objetivo, consenso, noticias de empresas y calendario de resultados: crea una clave gratuita en financialmodelingprep.com y pégala en **Ajustes**.
- Para cotizaciones de EE. UU. en tiempo real: clave gratuita de twelvedata.com en **Ajustes**. Con un plan Pro de Twelve Data también tendrás Europa en tiempo real.

Aviso: herramienta de análisis personal; no es asesoramiento financiero.
