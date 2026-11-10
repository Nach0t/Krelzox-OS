# Identidad visual · "Graphite & Ion"

Concepto: un espacio grafito, profundo y silencioso, donde la luz eléctrica
(azul → cian) marca exclusivamente lo que importa: la marca, el foco y la
selección. Premium por contención, cinematográfico sin efectos costosos.

Capturas del dispositivo: [capturas/refinamiento/](capturas/refinamiento/)
(versión actual) y [capturas/etapa4/](capturas/etapa4/) (primera versión).

## Color

| Token (`NexiumColors`) | Valor | Uso |
|---|---|---|
| `Background` | `#0B0D12` | Fondo grafito |
| `BackgroundDeep` | `#07080B` | Fondos de arte, viñetas |
| `Surface` | `#141821` | Tarjetas, hero, píldora seleccionada |
| `SurfaceRaised` | `#1C212C` | Tarjeta enfocada, diálogos |
| `Outline` | `#2A303C` | Bordes sutiles |
| `OnBackground` | `#F3F5F9` | Texto principal |
| `OnSurfaceMuted` | `#9AA3B2` | Texto secundario |
| `OnSurfaceSubtle` | `#6B7385` | Pistas y créditos |
| `Electric` | `#2F6BFF` | Identidad, botón enfocado |
| `Cyan` | `#22D3EE` | Identidad, iconos activos, sobretítulos, marca de favorito |
| `IonBrush` | `Electric → Cyan` | **Solo** foco, selección y marca |

Contraste mínimo WCAG AA (4,5:1) para todo texto sobre su fondo.

## Tipografía

**Inter** (variable, SIL OFL 1.1), empaquetada en `:core:designsystem`
(`res/font/inter_variable.ttf`, licencia en `third_party/inter/OFL.txt`).

| Rol | Tamaño | Peso |
|---|---|---|
| Display (título del hero) | 36 sp | SemiBold, tracking −2 % |
| Headline (título de pestaña) | 28 sp | SemiBold |
| Title (filas, reloj) | 18–22 sp | SemiBold |
| Body | 15–17 sp | Regular |
| Label | 14–16 sp | Medium |
| Overline | 12 sp | SemiBold, tracking +14 %, mayúsculas |

Logotipo: marca "N" con degradado Ion (24×26 dp, ≈ 1,5 × la altura de las
mayúsculas) + `NEXIUM` en Inter SemiBold 19 sp, tracking +26 %, separados 12 dp.
El interlineado del texto se recorta (`LineHeightStyle.Trim.Both`) para que las
mayúsculas queden ópticamente centradas sobre la marca.

## Iconografía

Familia propia `NexiumIcons` (vector, 24×24, trazo 1,8, extremos y uniones
redondeados): Inicio, Biblioteca, Multimedia, Ajustes, Wi-Fi, Ethernet, Sin
conexión, Reproducir, Estrella (contorno y rellena), Más, Reordenar,
Información, flechas. Sin dependencia de `material-icons-extended`.

**Iconos de apps unificados:** todas las tarjetas muestran el icono de la app
sobre la misma placa grafito, teñida un 20 % con el color de acento de la app,
y el icono ocupa siempre el 46 % de la altura de la tarjeta. Los banners de TV
(de formas y fondos muy distintos) se reservan para el arte del hero.

## Componentes y foco

| Componente | Reposo | Foco |
|---|---|---|
| Tarjeta de app (16:9) | `Surface`, esquinas 16 dp | Escala 1,06 · borde Ion 2 dp · brillo azul 14 dp · nombre en blanco |
| Píldora de navegación | Transparente (seleccionada: `Surface` + borde `Outline`, icono cian) | `SurfaceRaised` · borde Ion · escala 1,04 |
| Botón | `Surface` | `Electric` · borde cian · brillo |
| Mosaico de ajustes | `Surface`, icono cian | Igual que tarjeta |

### Rejilla (`NexiumGrid`)

Todas las pantallas comparten una rejilla: separación de 20 dp y anchos
calculados a partir del ancho disponible, nunca fijos.

| Uso | Columnas |
|---|---|
| Favoritos (Inicio) | 4 |
| Aplicaciones (Inicio), Biblioteca, Multimedia, Ajustes | 5 |

Las listas se ensanchan 14 dp por lado (espacio para la escala y el brillo del
foco) para que los bordes de las tarjetas coincidan con los del hero, los
títulos y la barra superior. Hero: 188 dp de alto.

## Disposición (960×540 dp)

1. **Barra superior** en tres zonas independientes: logotipo (inicio),
   navegación **centrada en pantalla** (Inicio, Biblioteca, Multimedia, Ajustes)
   y estado (red, hora y fecha) al final.
2. **Hero** dinámico: carrusel de apps relevantes con indicador de posición fijo
   bajo el arte; sobretítulo, título, descripción, acciones *Abrir* y
   *Favorito*. El fondo se tiñe con el color de acento de la app.
3. **Filas**: Favoritos (4 columnas, con tarjeta "+ Añadir") y Aplicaciones
   (5 columnas, con "Ver todas").

Márgenes de seguridad TV: 48 dp horizontales, 27 dp verticales.
Sin anuncios ni contenido patrocinado.

## Movimiento

- Foco: 160 ms. Hero: fundido de 280 ms; sigue al foco con 140 ms de retardo
  para no saltar al recorrer filas rápido.
- Carrusel del hero: avanza cada 9 s solo con Nexium en primer plano, sin foco
  en las filas y sin pulsaciones del mando durante ese tiempo.
- Reordenar favoritos: la tarjeta se mueve en directo; marco cian y chevrones
  ← → mientras dura el modo.
- Cambio de fila en Inicio: desplazamiento de 260 ms que alinea la fila
  enfocada arriba (nunca filas cortadas).
- Pestañas: cambio instantáneo, sin transición.

## Reglas de rendimiento visual

- **Sin blur** en ningún sitio. La profundidad viene de degradados lineales y
  radiales dibujados (`drawBehind`) y de sombras moderadas en el foco.
- Animaciones de color y desplazamiento leídas en la fase de dibujo
  (`drawBehind`, `graphicsLayer`): no recomponen.
- El color de acento de cada app se calcula una vez (8×8 px) al cargar su arte.
