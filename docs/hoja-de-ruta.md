# Hoja de ruta

| Etapa | Contenido | Estado |
|---|---|---|
| 0 | Higiene del repo (`.gitignore`, `.gitattributes`), documentación, ADR-001/002, JDK 21 y SDK 37 | ✅ Hecho |
| 1 | Esqueleto Gradle (`:app`, `:core:designsystem`, `:core:data`), `NexiumTheme`, `MainActivity` con `HOME` + `LEANBACK_LAUNCHER`, lista de apps. Instalación por `adb install` **sin** asignar rol HOME | ✅ Hecho (en revisión) |
| 2 | Filas de apps, favoritos (DataStore), foco D-pad/teclado, reacción a instalación/desinstalación | Pendiente |
| 3 | Asignación reversible del rol HOME; pruebas de reinicio, tecla Home, fallos y recuperación | Pendiente |
| 4 | Capa premium: hero, fondos, animaciones de foco, barra de estado | Pendiente |
| 5 | Baseline Profile, R8, Macrobenchmark de arranque, pruebas de UI, CI en GitHub Actions | Pendiente |
| 6 | Opcional: canales TvProvider, accesos a ajustes KonstaKANG, salvapantallas, temas | Pendiente |

Cada etapa requiere autorización explícita antes de empezar.
