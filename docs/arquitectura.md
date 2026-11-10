# Arquitectura de Nexium Launcher

## Visión general

```
┌────────────────────────────── :app ────────────────────────────────┐
│ MainActivity (HOME + LEANBACK_LAUNCHER) · safety/ (CrashGuard,      │
│ SafeModeView) · HomeViewModel · ui/shell (NexiumShell, TopBar)      │
│ ui/home (Hero, HomeTab, AppCard, diálogos) · ui/library · ui/settings│
│ ui/performance (PerformancePill, PerformanceSheet,                  │
│ PerformanceCenter, PerformanceFormat, SystemSources)                │
└───────────────┬────────────────────────────────────┬────────────────┘
                │                                    │
┌──────── :core:designsystem ─────────┐  ┌──────── :core:data ─────────────┐
│ Tokens (color, tipo, espacio,        │  │ AppRepository (PackageManager,  │
│ movimiento) · NexiumTheme (Inter) ·  │  │ LauncherApps callbacks)         │
│ NexiumIcons · NexiumButtonDefaults · │  │ Favorites/RecentsRepository     │
│ NexiumChart (líneas y sparklines)    │  │ (DataStore) · NetworkRepository │
│ Sin dependencias de datos            │  │ metrics/ (SystemMonitor,        │
│                                      │  │ SystemMetrics, parsers)         │
└──────────────────────────────────────┘  └─────────────────────────────────┘
```

Las dependencias solo apuntan hacia abajo: `:app` → `:core:*`. Los módulos
`core` no se conocen entre sí.

`:baselineprofile` (módulo `com.android.test`, etapa 6) maneja `:app` desde
fuera con UiAutomator: genera el Baseline Profile y mide el arranque
(Macrobenchmark). No forma parte del APK. Ver [rendimiento.md](rendimiento.md).

## Decisiones

| Área | Decisión | Motivo |
|---|---|---|
| Distribución | App de usuario con categorías `HOME` y `LEANBACK_LAUNCHER` | Sin root ni reflasheo; reversible. [ADR-001](decisiones/ADR-001-launcher-app-usuario.md) |
| UI | Jetpack Compose + `androidx.tv:tv-material` | Leanback está deprecado. [ADR-002](decisiones/ADR-002-compose-for-tv.md) |
| Patrón | MVVM + flujo de datos unidireccional (`StateFlow` de estado inmutable) | Foco y recomposición predecibles y testeables |
| Concurrencia | Kotlin coroutines + Flow | Estándar de AndroidX |
| Persistencia | DataStore Preferences | Datos pequeños (favoritos, orden); no requiere Room |
| DI | Manual (contenedor en `Application`) | Proyecto pequeño; Hilt añade coste de compilación sin beneficio todavía |
| Imágenes | Banners/iconos vía `PackageManager` + `LruCache`; Coil 3 solo para fondos | Mínimas dependencias y memoria controlada |
| Build | Gradle Kotlin DSL, version catalog, R8 en release, Baseline Profile versionado (`app/src/main/generated/baselineProfiles/`) instalado con `profileinstaller` | APK pequeño; sin jank por JIT tras instalar |
| Variantes | `debug` con paquete `dev.nexium.launcher.debug`; `release` con `dev.nexium.launcher` | Las pruebas instrumentadas instalan y desinstalan la app: nunca deben tocar el launcher en uso |
| Pruebas | Unitarias (JVM) para lógica pura; de UI (Compose + eventos de tecla reales) en la Pi para las reglas de foco | El foco con D-pad es lo que más se rompe y lo que más importa en TV |
| CI | GitHub Actions: tests, lint, build release y compilación de las pruebas de dispositivo | Lo que necesita la Pi se ejecuta a mano ([rendimiento.md](rendimiento.md)) |
| SDK | `minSdk 31`; `compileSdk`/`targetSdk` al máximo soportado por el AGP estable (objetivo 37) | El dispositivo es SDK 37; portable a otros TV |
| Package | `dev.nexium.launcher` | — |

## Descubrimiento de apps

- Se listan actividades `MAIN` + `LEANBACK_LAUNCHER` (prioridad) y `MAIN` +
  `LAUNCHER` como reserva, declaradas en `<queries>` del manifiesto.
  **No** se usa `QUERY_ALL_PACKAGES`.
- Cambios de paquetes: `LauncherApps.Callback` → el repositorio emite un nuevo
  `Flow<List<AppEntry>>`.
- Para cada app se prefiere `android:banner` (320×180 dp); si no hay, se
  compone una tarjeta con icono y nombre.

## Estructura de la interfaz (etapas 4 y 5)

`HomeScreen` decide el estado: cargando / error / vacío (pantalla mínima con
"Abrir ajustes") o catálogo listo → `NexiumShell`.

`NexiumShell` contiene la barra superior (logotipo, pestañas, panel de
rendimiento, red y hora) y cuatro pestañas:

| Pestaña | Contenido |
|---|---|
| Inicio | Hero (app destacada o enfocada) + filas Favoritos y Aplicaciones (12 + "Ver todas") |
| Biblioteca | Catálogo completo en cuadrícula de 5 columnas |
| Multimedia | Apps con categoría video, audio o imagen (estado vacío con acceso a Biblioteca) |
| Ajustes | Mosaico doble «Sistema y rendimiento» (valores en directo) + accesos a Red, Pantalla y sonido, App de inicio, Ajustes de Android y "Acerca de Nexium". «Sistema y rendimiento» es una subpágina de la pestaña (`SettingsPage.Performance`) |

Hero (carrusel de hasta 5 apps, `buildReadyState`):
1. Apps abiertas recientemente (la más reciente: *Continuar*; el resto:
   *Usada recientemente*).
2. Favoritos.
3. Apps del catálogo (*Destacado*) hasta completar al menos 3.

Las apps utilitarias quedan fuera del hero: el paquete que resuelve
`Settings.ACTION_SETTINGS` se detecta en tiempo de ejecución (tiene su propia
pestaña). Solo se usa si no hay ninguna otra app. Al recorrer las filas, el hero
muestra la app enfocada; tras 9 s sin interacción vuelve al carrusel.

El carrusel corre en un `LaunchedEffect` con `repeatOnLifecycle(RESUMED)`: con
otra app delante no hace nada. Las pulsaciones del mando se registran en un
`InteractionClock` (campo simple, sin estado de Compose: no recompone).

Rendimiento de la UI:
- Las filas de Inicio están en un `Column` sin desplazamiento propio; un
  `translationY` animado (fase de dibujo) alinea la fila enfocada arriba.
- El tinte del hero (color de acento animado) se dibuja en `drawBehind`.
- Reloj: un solo `produceState` alineado al minuto; red: `NetworkCallback`.

## Monitor del sistema (etapa 5)

Decisiones y fuentes: [ADR-004](decisiones/ADR-004-metricas-del-sistema.md).

```
SystemMonitor (:core:data) ── muestra cada 2 s (IO) ──► Flow<SystemMetrics>
NetworkRepository.observeDetails() ─────────────────► Flow<NetworkDetails>
        │  shareIn(WhileSubscribed(3 s), replay = 1) en NexiumApp
        ▼
SystemSources ──► PerformancePill (barra superior, todas las pestañas)
              ──► PerformanceSheet (vista ampliada, diálogo)
              ──► PerformanceTile (mosaico en Ajustes)
              ──► PerformanceCenter (Ajustes › Sistema y rendimiento)
```

- `SystemMetrics` = instantánea actual + historial de CPU, RAM, temperatura y
  tráfico (120 muestras = 4 min) + qué métricas están disponibles.
- Los parsers del kernel (`/proc/stat`, `/proc/loadavg`, zona térmica,
  `cpufreq`) son funciones puras con tests (`KernelParsersTest`); el formato de
  valores con configuración regional, en `PerformanceFormat` (`PerformanceFormatTest`).
- Cada vista recoge el flujo con `collectAsStateWithLifecycle`: con Nexium en
  segundo plano no hay suscriptores y el muestreo se detiene.
- Una fuente bloqueada (p. ej. por SELinux) se marca como no disponible y la UI
  muestra «No disponible»; nunca se estima.

| Vista | Contenido |
|---|---|
| Panel (barra superior) | CPU %, RAM % y MB, temperatura, almacenamiento libre. Valor en color de aviso con CPU ≥ 85 %, temperatura ≥ 75 °C o disco ≥ 95 % |
| Vista ampliada | Tres métricas seleccionables (CPU, RAM, temperatura) con sparkline; gráfico grande con actual, máximo y media de los últimos 4 min; botón al centro de rendimiento |
| Centro «Sistema y rendimiento» | Carril de categorías (Procesador, Memoria, Almacenamiento, Sistema, Red) y detalle de solo lectura: tarjetas de valores, gráficos y nota de fuente |

## Favoritos

- Se guardan en Preferences DataStore (`nexium_preferences`) como lista
  ordenada de claves `paquete/actividad`.
- Un archivo corrupto se reemplaza por preferencias vacías: nunca bloquea el
  arranque.
- **No se purgan** los favoritos de apps desinstaladas: durante una
  actualización el paquete desaparece unos instantes y se perdería el favorito.
  La UI simplemente oculta los que no tienen app instalada; si la app vuelve,
  reaparece en su posición.
- Backup en la nube y transferencia entre dispositivos desactivados
  (`data_extraction_rules.xml`).

## Navegación e interacción

| Entrada | Acción |
|---|---|
| D-pad / flechas | Mover el foco: pestañas ↔ hero ↔ filas; dentro de cada fila o cuadrícula |
| Foco en una pestaña | La selecciona (convención TV); OK entra en su contenido |
| OK / Enter | Abrir la app |
| OK mantenido o tecla Menú | Diálogo: Abrir · Añadir/Quitar favorito · Reordenar (desde la fila de Favoritos) · Gestionar favoritos · Información de la app · Cancelar |
| Tarjeta "+ Añadir" | Selector de favoritos: todas las apps con estrella; OK añade o quita sin cerrar |
| Modo reordenar | ←/→ mueve la tarjeta en directo · ↑/↓ bloqueados · OK o Back termina |
| Back | Termina el modo reordenar si está activo. Después, jerárquico: contenido → su pestaña → Inicio → botón *Abrir* del hero. Ahí no se consume: como HOME no hace nada; como app normal, sale |
| Panel de rendimiento + OK | Vista ampliada: ←/→ elige métrica, ↓ llega a *Abrir el centro de rendimiento*, Back cierra |
| Centro de rendimiento | ↑/↓ recorre las categorías (se seleccionan al enfocarlas); Back vuelve al mosaico de Ajustes |
| HOME con Nexium delante | Cierra diálogos (también la vista ampliada), sale del centro de rendimiento, vuelve a Inicio y enfoca *Abrir* del hero |

Reglas de foco:
- Al mostrarse, el foco va a *Abrir* del hero.
- Si el elemento enfocado desaparece (app desinstalada, favorito quitado), el
  foco vuelve al hero o, si no existe, a la pestaña activa: nunca queda la
  pantalla sin foco.
- Al volver de otra app o de un diálogo, se conserva el elemento enfocado.
- Cada fila recuerda su última tarjeta enfocada (`focusRestorer`).
- ↓ (u OK) desde la pestaña Ajustes lleva a «Sistema y rendimiento», no al
  mosaico que quede debajo de la pestaña (Red, que abriría los ajustes de
  Android). Se declara con `focusProperties { down = … }` en la pestaña
  seleccionada (`TopBar(contentEntry = …)`): `focusRestorer` no sirve aquí,
  porque la búsqueda direccional entra directamente en el mosaico más cercano.
- Cambiar entre la rejilla de Ajustes y el centro de rendimiento elimina el
  elemento enfocado. Antes del cambio, el foco se aparca en la pestaña Ajustes
  (presente en ambas páginas); si no, Compose lo daría a la primera pestaña y,
  como las pestañas se seleccionan al enfocarse, saltaría a Inicio.
- Reordenar favoritos cuenta solo los **visibles**: las claves ocultas (apps no
  instaladas) conservan su lugar y nunca se "intercambian" en silencio.

"Información de la app" abre la pantalla del sistema (forzar cierre,
almacenamiento, desinstalar): Nexium no necesita `REQUEST_DELETE_PACKAGES`.

## Permisos

Ninguno privilegiado ni peligroso. Único permiso: `ACCESS_NETWORK_STATE`
(normal, para el indicador de red; [ADR-003](decisiones/ADR-003-red-y-tipografia.md)).
El monitor de rendimiento no añade ninguno: `/proc`, `/sys`, `ActivityManager`,
`StatFs` y `TrafficStats` no requieren permisos ([ADR-004](decisiones/ADR-004-metricas-del-sistema.md)).
No hace falta `RECEIVE_BOOT_COMPLETED`: el sistema lanza HOME por sí mismo.
Cualquier permiso nuevo requiere un ADR.

## Rendimiento (presupuesto)

| Métrica | Objetivo |
|---|---|
| Arranque en frío hasta primer frame | < 600 ms |
| Frames perdidos al navegar filas | < 1 % (`dumpsys gfxinfo`) |
| PSS en reposo | < 120 MB |
| APK release | < 10 MB |
| Monitor del sistema | Una muestra cada 2 s solo con una vista visible; 0 lecturas en segundo plano |

Reglas para la GPU V3D:
- Prohibido `RenderEffect`/blur a pantalla completa en tiempo real.
- Animaciones de foco solo con `graphicsLayer` (escala, alfa, traslación).
- Profundidad con degradados dibujados y sombras moderadas solo en el foco.
- Listas perezosas con `key` estable; estado con `@Immutable`/`@Stable`.

## Robustez

- Catapult permanece instalado y habilitado como respaldo.
- `MainActivity` captura fallos de carga de datos y siempre muestra al menos la
  fila de Ajustes, para que el usuario nunca quede sin salida.
- **CrashGuard:** un `UncaughtExceptionHandler` registra cada fallo
  (SharedPreferences con escritura síncrona, porque el proceso muere justo
  después). Con 3 fallos en 2 minutos, `MainActivity` arranca en **modo seguro**:
  una pantalla con Views nativas (sin Compose ni repositorios) que ofrece elegir
  otro launcher (`ACTION_HOME_SETTINGS`), abrir Ajustes o reintentar.
- HOME con Nexium delante cierra cualquier diálogo y vuelve arriba.
- Procedimiento de recuperación: [recuperacion.md](recuperacion.md).
