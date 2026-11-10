# Hoja de ruta

| Etapa | Contenido | Estado |
|---|---|---|
| 0 | Higiene del repo (`.gitignore`, `.gitattributes`), documentación, ADR-001/002, JDK 21 y SDK 37 | ✅ Hecho |
| 1 | Esqueleto Gradle (`:app`, `:core:designsystem`, `:core:data`), `NexiumTheme`, `MainActivity` con `HOME` + `LEANBACK_LAUNCHER`, lista de apps. Instalación por `adb install` **sin** asignar rol HOME | ✅ Hecho |
| 2 | Cuadrícula con secciones Favoritos/Aplicaciones, favoritos (DataStore), diálogo de acciones, foco D-pad/teclado robusto, reacción a instalación/desinstalación | ✅ Hecho |
| 3 | Asignación reversible del rol HOME; pruebas de reinicio, tecla Home, fallos y recuperación | ✅ Hecho. Pendiente: prueba de reinicio físico |
| 4 | Diseño premium "Graphite & Ion": Inter, iconos propios, barra superior con navegación/hora/red, hero cinematográfico, filas, Biblioteca, Multimedia, Ajustes, reordenar favoritos | ✅ Hecho |
| 4.1 | Refinamiento visual: logotipo, hero dinámico (carrusel), gestión de favoritos (selector + reordenar en directo), iconos unificados, rejilla común | ✅ Hecho (en revisión) |
| 5 | Monitor de rendimiento: panel permanente en la barra superior (CPU, RAM, temperatura, almacenamiento), vista ampliada con gráficos en tiempo real y centro «Sistema y rendimiento» en Ajustes (procesador, memoria, almacenamiento, sistema, red). [ADR-004](decisiones/ADR-004-metricas-del-sistema.md) | ✅ Hecho |
| 6 | Baseline Profile (`profileinstaller`), Macrobenchmark de arranque, pruebas de UI de foco con D-pad, CI en GitHub Actions (tests, lint, release, compilación de pruebas de dispositivo). R8 ya activo desde la etapa 1. Procedimiento y resultados: [rendimiento.md](rendimiento.md) | ✅ Hecho |
| 7 | Ajustes › Apariencia: 4 temas (Ion, Aurora, Ámbar, Jade) aplicados al instante en todo Nexium; salvapantallas propio (`DreamService`) con protección contra marcado; mosaico Raspberry Pi con guía a los ajustes de KonstaKANG. Sin permisos nuevos. [ADR-005](decisiones/ADR-005-apariencia-salvapantallas-ajustes.md) | ✅ Hecho |
| 8 | Opcional: canales TvProvider y «Seguir viendo» (requiere `READ_EPG_DATA`; hoy ninguna app de la Pi publica canales) | Pendiente |

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
código aún corre en JIT: lo resolverá el Baseline Profile de la etapa 6.

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
pestaña): imperceptible, pendiente de la misma optimización de la etapa 6.

### Etapa 5 (release, 2026-11-10, en estado estable, panel de rendimiento activo)

| Métrica | Objetivo | Resultado |
|---|---|---|
| Jank navegando Inicio (hero ↔ filas ↔ Aplicaciones) | < 1 % | 0,13 % (p90 9 ms, p99 12–13 ms, GPU p90 6 ms) |
| Coste en reposo del panel | — | 1–2 frames de ~15 ms cada 2 s (una muestra); sin movimiento en pantalla, no se percibe |
| PSS en reposo | < 120 MB | 46 MB (+2 MB respecto a la etapa 4.1) |
| APK release | < 10 MB | 2,7 MB |

La primera pasada, unos 4 minutos después de arrancar la Pi, dio p50 22 ms y
GPU 19 ms; las dos siguientes, con el mismo recorrido, volvieron a 7–8 ms y
GPU 6 ms. Es el calentamiento tras el arranque (código aún en JIT), no un
efecto del panel: otro motivo para el Baseline Profile de la etapa 6.

Verificado en el dispositivo: el panel se actualiza solo mientras se ve; el
centro se abre desde el mosaico y desde la vista ampliada con el foco en
«Procesador», y Back vuelve al mosaico.

### Etapa 6 (release, 2026-11-10)

Detalle y procedimiento en [rendimiento.md](rendimiento.md).

| Métrica | Objetivo | Resultado |
|---|---|---|
| Jank navegando justo después de instalar | < 1 % | 0,57–0,71 % con Baseline Profile (solo JIT: 1,28–1,58 %); p99 20 ms (JIT: 32–38 ms) |
| Arranque en frío (Macrobenchmark, TTID) | < 600 ms | 265 ms solo JIT · 342 ms con perfil · 349 ms AOT completo |
| Arranque en frío (`am start -W`) | < 600 ms | 101–170 ms con perfil (113–156 ms solo JIT) |
| APK release | < 10 MB | 2,8 MB |
| Pruebas de UI en el dispositivo | — | 4/4 (foco con D-pad) |

El perfil resuelve el jank tras instalar que quedó pendiente en las etapas 4 y
4.1. No acelera el arranque en esta Pi (el Macrobenchmark indica incluso ~80 ms
más; `am start -W` no lo confirma).

### Etapa 7 (release, 2026-11-10)

| Métrica | Resultado |
|---|---|
| Pruebas de UI en el dispositivo | 7/7 (las 4 anteriores + Apariencia, cambio de tema y diálogo de Raspberry Pi) |
| Salvapantallas en reposo (65 s, con un cambio de posición) | 0,2 s de CPU; 123 frames, todos durante el fundido; 0 % de jank; GPU p90 3 ms |
| Cambio de tema | Inmediato en todas las pantallas, sin reiniciar; persiste tras reinstalar |
