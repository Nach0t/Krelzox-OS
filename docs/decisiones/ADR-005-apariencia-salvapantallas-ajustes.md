# ADR-005 · Temas, salvapantallas y ajustes del dispositivo

- **Estado:** aceptado
- **Fecha:** 2026-11-10

## Contexto

La etapa 7 añade temas, un salvapantallas propio y acceso a los ajustes de
Raspberry Pi (KonstaKANG). Requisito: que parezcan partes de un mismo sistema
operativo, sin permisos innecesarios, con D-pad y sin alterar HOME, CrashGuard
ni el centro de rendimiento.

## Decisión 1: un tema es solo la luz de acento

«Graphite & Ion» se mantiene: grafito, tipografía, formas y movimiento no
cambian. Un tema (`NexiumAccent`) es un par de colores, **primario** (superficies
rellenas con texto blanco) y **secundario** (iconos, rótulos y líneas sobre
grafito), más sus degradados.

| Tema | Primario | Secundario | Blanco sobre primario | Secundario sobre fondo |
|---|---|---|---|---|
| Ion (original) | `#2F6BFF` | `#22D3EE` | 4,1:1 | 10,8:1 |
| Aurora | `#7C3AED` | `#C4B5FD` | 5,2:1 | 10,5:1 |
| Ámbar | `#B45309` | `#FBBF24` | 4,6:1 | 11,6:1 |
| Jade | `#047857` | `#34D399` | 5,0:1 | 10,1:1 |

Los temas nuevos cumplen WCAG AA (≥ 4,5:1) para texto blanco sobre el primario;
Ion conserva sus valores originales. El color de error y los colores de datos
fijos (temperatura) no cambian con el tema.

**Implementación:** `NexiumColors.accent` es estado de Compose (snapshot) y los
tokens de acento (`Electric`, `Cyan`, `IonBrush`, `IonHorizontal`) lo leen al
usarse. Así, un tema nuevo se aplica al instante en todas las pantallas sin
tocar sus ~55 usos ni reiniciar. Única adaptación: el enum `PerfMetric` del panel
de rendimiento leía dos colores al cargarse; ahora los lee al usarse (mismo
comportamiento).

## Decisión 2: el tema se guarda en SharedPreferences

Se lee **de forma síncrona en `Application.onCreate`**, antes de que el launcher o
el salvapantallas dibujen su primer frame: sin parpadeo del tema por defecto.
DataStore (usado para favoritos) es asíncrono y obligaría a retrasar el primer
frame o a mostrar Ion un instante. Es un único valor pequeño; no justifica
ninguna dependencia nueva.

## Decisión 3: salvapantallas como `DreamService`, sin permisos

- `NexiumDreamService` muestra el mismo composable que la vista previa de
  Ajustes › Apariencia: hora, fecha, marca y la luz del tema sobre grafito
  profundo.
- Contra el marcado de pantalla, cada minuto el bloque se funde y reaparece en
  otra zona del área segura (6 posiciones alejadas entre sí).
- Coste medido en la Pi: 0,2 s de CPU en 65 s y solo dibuja durante los cambios
  de minuto o de posición (0 % de jank, GPU 3 ms).
- `android:permission="android.permission.BIND_DREAM_SERVICE"` en el manifiesto
  garantiza que solo el sistema puede usarlo. Nexium no necesita ningún permiso.

**Limitación:** una app normal no puede elegir el salvapantallas activo
(requiere `WRITE_SECURE_SETTINGS`) y Android TV no exporta la pantalla
«Protector de pantalla». Nexium muestra la ruta exacta (Ajustes › Sistema ›
Protector de pantalla › Nexium) y un botón que abre Ajustes.

## Decisión 4: ajustes de Raspberry Pi mediante una guía, no un acceso directo

`com.konstakang.settings.device/.RaspberrySettingsActivity` es una app del
sistema **no exportada**: Android deniega abrirla desde otra app (comprobado,
incluso desde ADB). Solo aparece dentro de Ajustes › Sistema.

- El mosaico «Raspberry Pi» solo se muestra si ese paquete existe (se declara
  en `<queries>` para poder comprobarlo).
- Al pulsarlo, un diálogo con el estilo de Nexium explica dónde están (Sistema ›
  Raspberry Pi settings) y abre Ajustes.

## Decisión 5: TvProvider queda fuera de esta etapa

Una fila de canales o «Seguir viendo» requiere el permiso de ejecución
`READ_EPG_DATA` y, hoy, ninguna app de la Pi publica canales: la fila estaría
vacía. Se pospone hasta que haya contenido que mostrar.

## Consecuencias

- Sin permisos nuevos. `<queries>` añade un único paquete.
- La interfaz completa sigue el tema elegido, incluidos el logotipo, el
  salvapantallas y los gráficos del monitor de rendimiento.
- Activar el salvapantallas sigue siendo un paso del usuario en los Ajustes del
  sistema.
