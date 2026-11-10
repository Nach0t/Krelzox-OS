# Hoja de ruta

| Etapa | Contenido | Estado |
|---|---|---|
| 0 | Higiene del repo (`.gitignore`, `.gitattributes`), documentación, ADR-001/002, JDK 21 y SDK 37 | ✅ Hecho |
| 1 | Esqueleto Gradle (`:app`, `:core:designsystem`, `:core:data`), `NexiumTheme`, `MainActivity` con `HOME` + `LEANBACK_LAUNCHER`, lista de apps. Instalación por `adb install` **sin** asignar rol HOME | ✅ Hecho |
| 2 | Cuadrícula con secciones Favoritos/Aplicaciones, favoritos (DataStore), diálogo de acciones, foco D-pad/teclado robusto, reacción a instalación/desinstalación | ✅ Hecho |
| 3 | Asignación reversible del rol HOME; pruebas de reinicio, tecla Home, fallos y recuperación | ✅ Hecho. Pendiente: prueba de reinicio físico |
| 4 | Diseño premium "Graphite & Ion": Inter, iconos propios, barra superior con navegación/hora/red, hero cinematográfico, filas, Biblioteca, Multimedia, Ajustes, reordenar favoritos | ✅ Hecho |
| 4.1 | Refinamiento visual: logotipo, hero dinámico (carrusel), gestión de favoritos (selector + reordenar en directo), iconos unificados, rejilla común | ✅ Hecho (en revisión) |
| 5 | Baseline Profile, R8, Macrobenchmark de arranque, pruebas de UI, CI en GitHub Actions | Pendiente |
| 6 | Opcional: canales TvProvider, accesos a ajustes KonstaKANG, salvapantallas, temas | Pendiente |

Cada etapa requiere autorización explícita antes de empezar.

## Mediciones (Raspberry Pi 5, 2026-11-09)

| Métrica | Objetivo | Etapa 2 (release) | Etapa 2 (debug) |
|---|---|---|---|
| Arranque en frío | < 600 ms | ~115 ms | ~590 ms |
| Frames con jank navegando con D-pad | < 1 % | 0 % (p99 8 ms) | 1,2 % (p99 17 ms) |
| PSS en reposo | < 120 MB | 37 MB | 77–87 MB |
| APK | < 10 MB | 1,9 MB | 31 MB |

Las métricas de rendimiento se toman siempre con la build release; la debug
desactiva optimizaciones de Compose y R8.

### Etapa 4 (release, 2026-11-10, en estado estable tras calentamiento JIT)

| Métrica | Objetivo | Resultado |
|---|---|---|
| Arranque en frío | < 600 ms | 118–146 ms |
| Jank navegando Inicio (hero ↔ filas) | < 1 % | 0,15–0,30 % (p90 7–8 ms, GPU p90 6 ms) |
| Jank cambiando de pestaña | < 1 % | 1,05 %: 1 frame de ~17 ms GPU al entrar en Biblioteca y 2 al entrar en Inicio; en reposo 2,8–4,8 ms GPU |
| PSS en reposo | < 120 MB | 40–44 MB |
| APK release | < 10 MB | 2,5 MB |

Justo después de instalar, las cifras empeoran (≈ 1,4 % de jank) porque el
código aún corre en JIT: lo resolverá el Baseline Profile de la etapa 5.

### Etapa 4.1 (release, 2026-11-10, en estado estable)

| Métrica | Objetivo | Resultado |
|---|---|---|
| Arranque en frío (relanzado por el sistema como HOME) | < 600 ms | 138–158 ms |
| Jank navegando Inicio | < 1 % | 0,00–0,27 % (p90 8 ms, GPU p90 6 ms) |
| Jank cambiando de pestaña | < 1 % | 1,23 % (mismo patrón que la etapa 4) |
| CPU en reposo en Inicio con carrusel | — | 0,23 s de CPU en 30 s (≈ 0,8 % de un núcleo) |
| PSS en reposo | < 120 MB | 44 MB |
| APK release | < 10 MB | 2,5 MB |

En reposo, cada cambio del carrusel produce ~1 frame lento (como al cambiar de
pestaña): imperceptible, pendiente de la misma optimización de la etapa 5.
