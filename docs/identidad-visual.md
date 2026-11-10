# Identidad visual · "Obsidian & Ion"

Concepto: un núcleo oscuro, profundo y silencioso, con un acento luminoso que
sigue al foco. Lujo por contención, no por saturación.

## Color

| Token | Valor | Uso |
|---|---|---|
| `background` | `#07080C` | Fondo base (obsidiana) |
| `surface` | `#10131A` | Tarjetas, barras |
| `surfaceRaised` | `#171B24` | Elementos elevados, menús |
| `onBackground` | `#F2F4F8` | Texto principal |
| `onSurfaceMuted` | `#9AA3B2` | Texto secundario |
| `ionStart` → `ionEnd` | `#5B8CFF` → `#8A6BFF` | Acento Ion: **solo** en foco y selección |
| `champagne` | `#E8C48A` | Detalle cálido puntual (hora, estado destacado) |

Contraste mínimo WCAG AA (4.5:1) para todo texto sobre su fondo.

## Tipografía

Inter o Manrope (variable, licencia OFL), empaquetada en el APK.

| Rol | Tamaño | Peso |
|---|---|---|
| Display (hero) | 48 sp | SemiBold, tracking −1 % |
| Título | 32 sp | SemiBold |
| Encabezado de fila | 20 sp | Medium |
| Cuerpo | 16–18 sp | Regular |

Legible a 3 m: nada por debajo de 14 sp.

## Foco (sello de la marca)

- Escala 1.08, borde Ion de 2 dp y halo exterior suave.
- El resto de la fila baja a 70 % de opacidad.
- Transición 150–200 ms, curva `FastOutSlowIn`.

## Disposición

1. **Hero** (parte superior): fondo e información de la app enfocada.
2. **Filas:** Favoritos · Aplicaciones · Sistema.
3. **Barra superior mínima:** hora, estado de red, acceso a Ajustes.

Márgenes de seguridad de TV: 48 dp horizontales, 27 dp verticales.
Sin anuncios ni contenido patrocinado.

## Movimiento

- Fundido cruzado del fondo hero al cambiar de foco (≤ 300 ms).
- Entrada escalonada de filas al arrancar (≤ 300 ms en total).
- Respetar la escala de animación del sistema.

## Implementación

Los tokens viven en `:core:designsystem` como objetos Kotlin
(`NexiumColors`, `NexiumTypography`, `NexiumSpacing`, `NexiumMotion`) y se
exponen mediante `NexiumTheme`.
