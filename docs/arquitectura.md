# Arquitectura de Nexium Launcher

## Visión general

```
┌────────────────────────────── :app ────────────────────────────────┐
│ MainActivity (HOME + LEANBACK_LAUNCHER) · safety/ (CrashGuard,      │
│ SafeModeView) · HomeViewModel · ui/shell (NexiumShell, TopBar)      │
│ ui/home (Hero, HomeTab, AppCard, diálogos) · ui/library · ui/settings│
└───────────────┬────────────────────────────────────┬────────────────┘
                │                                    │
┌──────── :core:designsystem ─────────┐  ┌──────── :core:data ─────────────┐
│ Tokens (color, tipo, espacio,        │  │ AppRepository (PackageManager,  │
│ movimiento) · NexiumTheme (Inter) ·  │  │ LauncherApps callbacks)         │
│ NexiumIcons · NexiumButtonDefaults   │  │ Favorites/RecentsRepository     │
│ Sin dependencias de datos            │  │ (DataStore) · NetworkRepository │
└──────────────────────────────────────┘  └─────────────────────────────────┘
```

Las dependencias solo apuntan hacia abajo: `:app` → `:core:*`. Los módulos
`core` no se conocen entre sí.

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
| Build | Gradle Kotlin DSL, version catalog, R8 en release, Baseline Profile | Arranque en frío rápido |
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

## Estructura de la interfaz (etapa 4)

`HomeScreen` decide el estado: cargando / error / vacío (pantalla mínima con
"Abrir ajustes") o catálogo listo → `NexiumShell`.

`NexiumShell` contiene la barra superior y cuatro pestañas:

| Pestaña | Contenido |
|---|---|
| Inicio | Hero (app destacada o enfocada) + filas Favoritos y Aplicaciones (12 + "Ver todas") |
| Biblioteca | Catálogo completo en cuadrícula de 5 columnas |
| Multimedia | Apps con categoría video, audio o imagen (estado vacío con acceso a Biblioteca) |
| Ajustes | Accesos a Red, Pantalla y sonido, App de inicio, Sistema y "Acerca de Nexium" |

App destacada del hero: la última abierta (*Continuar*), si no el primer
favorito, si no la primera app. Al recorrer las filas, el hero muestra la app
enfocada.

Rendimiento de la UI:
- Las filas de Inicio están en un `Column` sin desplazamiento propio; un
  `translationY` animado (fase de dibujo) alinea la fila enfocada arriba.
- El tinte del hero (color de acento animado) se dibuja en `drawBehind`.
- Reloj: un solo `produceState` alineado al minuto; red: `NetworkCallback`.

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
| OK mantenido o tecla Menú | Diálogo: Abrir · Añadir/Quitar favorito · Mover izquierda/derecha (favoritos) · Información de la app · Cancelar |
| Back | Jerárquico: contenido → su pestaña → Inicio → botón *Abrir* del hero. Ahí no se consume: como HOME no hace nada; como app normal, sale |
| HOME con Nexium delante | Cierra diálogos, vuelve a Inicio y enfoca *Abrir* del hero |

Reglas de foco:
- Al mostrarse, el foco va a *Abrir* del hero.
- Si el elemento enfocado desaparece (app desinstalada, favorito quitado), el
  foco vuelve al hero o, si no existe, a la pestaña activa: nunca queda la
  pantalla sin foco.
- Al volver de otra app o de un diálogo, se conserva el elemento enfocado.
- Cada fila recuerda su última tarjeta enfocada (`focusRestorer`).
- Reordenar favoritos cuenta solo los **visibles**: las claves ocultas (apps no
  instaladas) conservan su lugar y nunca se "intercambian" en silencio.

"Información de la app" abre la pantalla del sistema (forzar cierre,
almacenamiento, desinstalar): Nexium no necesita `REQUEST_DELETE_PACKAGES`.

## Permisos

Ninguno privilegiado ni peligroso. Único permiso: `ACCESS_NETWORK_STATE`
(normal, para el indicador de red; [ADR-003](decisiones/ADR-003-red-y-tipografia.md)).
No hace falta `RECEIVE_BOOT_COMPLETED`: el sistema lanza HOME por sí mismo.
Cualquier permiso nuevo requiere un ADR.

## Rendimiento (presupuesto)

| Métrica | Objetivo |
|---|---|
| Arranque en frío hasta primer frame | < 600 ms |
| Frames perdidos al navegar filas | < 1 % (`dumpsys gfxinfo`) |
| PSS en reposo | < 120 MB |
| APK release | < 10 MB |

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
