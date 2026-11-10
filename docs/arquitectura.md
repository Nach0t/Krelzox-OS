# Arquitectura de Nexium Launcher

## Visión general

```
┌──────────────────────────── :app ────────────────────────────┐
│  MainActivity (HOME + LEANBACK_LAUNCHER)                     │
│  Navegación · ViewModels · pantallas (home, todas las apps)  │
└───────────────┬───────────────────────────────┬──────────────┘
                │                               │
┌───────── :core:designsystem ────────┐  ┌───── :core:data ──────────────┐
│ Tokens (color, tipo, espacio,        │  │ AppRepository (LauncherApps,  │
│ movimiento) · NexiumTheme ·          │  │ PackageManager, callbacks)    │
│ componentes de foco reutilizables    │  │ PreferencesRepository         │
│ Sin dependencias de datos            │  │ (DataStore: favoritos, orden) │
└──────────────────────────────────────┘  └───────────────────────────────┘
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

## Permisos

Ninguno privilegiado y, de inicio, ningún permiso peligroso. No hace falta
`RECEIVE_BOOT_COMPLETED`: el sistema lanza HOME por sí mismo. Cualquier permiso
nuevo requiere un ADR.

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
- Fondos y "vidrio" como imágenes o degradados precalculados.
- Listas perezosas con `key` estable; estado con `@Immutable`/`@Stable`.

## Robustez

- Catapult permanece instalado y habilitado como respaldo.
- `MainActivity` captura fallos de carga de datos y siempre muestra al menos la
  fila de Ajustes, para que el usuario nunca quede sin salida.
- Procedimiento de recuperación: [recuperacion.md](recuperacion.md).
