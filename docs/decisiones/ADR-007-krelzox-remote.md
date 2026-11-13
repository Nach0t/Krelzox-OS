# ADR-007 · Krelzox Remote: ver y controlar la tele desde un navegador

- **Estado:** aceptado
- **Fecha:** 2026-11-13

## Contexto

Queremos ver la pantalla de la Raspberry Pi y controlarla desde un móvil o un
PC de la misma red, sin instalar nada en ellos: con un navegador. Krelzox no
tiene backend ni cuentas, y el launcher presume de un único permiso normal y de
no abrir puertos.

Requisitos: funcionar solo en la red local, que nadie pueda ver ni controlar la
tele sin que lo autorice la persona que está delante, no usar privilegios de
sistema (la Pi corre una build de LineageOS sin firmar con la clave de Krelzox)
y que todo se pueda apagar desde la tele.

## Decisión 1: app independiente (`dev.krelzox.remote`, módulo `:remote`)

Ver y controlar requiere abrir un servidor, capturar la pantalla y un servicio
de accesibilidad. Nada de eso entra en el launcher: como en la tienda
([ADR-006](ADR-006-krelzox-store.md)), una app aparte aísla los permisos y el
riesgo. Reutiliza `:core:designsystem` y se abre desde Krelzox › Ajustes ›
Krelzox Remote (también aparece en la Biblioteca).

## Decisión 2: servidor HTTP + WebSocket propio, solo en la LAN

- Se enlaza a la **dirección IPv4 privada** del dispositivo (no a `0.0.0.0`), en
  el primer puerto libre entre 8080 y 8089. Sin salida a Internet.
- Cada conexión debe venir de la **misma subred** (RFC 1918); las demás se
  cierran sin respuesta. `Host` debe ser la IP y el puerto del servidor (frena
  el *DNS rebinding*) y el `Origin` del WebSocket, la propia página.
- Cabeceras estrictas: CSP `default-src 'self'`, `X-Frame-Options: DENY`,
  `nosniff`, `no-referrer`. La página (HTML, JS y CSS sin dependencias) va
  dentro del APK.
- Si la tele cambia de red, el servidor se reinicia en la nueva dirección o se
  detiene si ya no hay LAN.
- Android 16+ bloquea en silencio el tráfico con la LAN si la app no tiene
  `ACCESS_LOCAL_NETWORK`; se pide al activar Krelzox Remote.

Se descartó un servicio en la nube o un relé: añadiría un servidor, cuentas y
una superficie expuesta a Internet que este caso no necesita.

**HTTP sin TLS.** Un certificado para una IP privada sería autofirmado: el
navegador mostraría un aviso que enseña a aceptar avisos. Se asume que la LAN
doméstica es de confianza para el transporte y se limita lo que un atacante en
ella puede hacer: el PIN es de un solo uso, el token viaja en una cookie
`HttpOnly; SameSite=Strict` ligada a la dirección del cliente y el control
requiere aprobación en la tele. Es un riesgo aceptado y documentado.

## Decisión 3: emparejar con PIN y aprobar el control en la tele

1. Al abrir la página, la tele muestra un **PIN temporal de 6 dígitos**.
   Escribirlo demuestra que quien está en el navegador ve la pantalla. Cambia
   tras cada emparejamiento y tras demasiados fallos; los intentos se limitan
   por dirección (bloqueo de 1 minuto) y en total.
2. Emparejar solo permite **ver**. Para **controlar**, el navegador lo pide y la
   tele muestra un diálogo con **«Denegar» seleccionado**: un OK accidental nunca
   concede el control. Sin respuesta en 60 s, se deniega.
3. El control caduca tras 10 minutos sin usarse y las sesiones tras 10 minutos
   sin actividad. Como mucho 4 sesiones. Desde el panel se pueden desconectar
   una o todas (lo que además cambia el PIN).

## Decisión 4: captura con MediaProjection y JPEG

La pantalla se captura con `MediaProjection` (Android pide permiso con su
propio diálogo, cada vez que se activa) en un servicio en primer plano con
notificación. Los fotogramas se envían como **JPEG** por WebSocket: la Pi 5 no
tiene codificador H.264 por hardware y un códec por software no da tiempo real.
Tres calidades (960×540 a 15 fps, 1280×720 a 24 fps, 1920×1080 a 30 fps). Solo
se captura mientras hay alguien mirando, y siempre se envía la imagen más
reciente, nunca una cola.

## Decisión 5: control con un servicio de accesibilidad

Una app normal no puede inyectar eventos de entrada (`INJECT_EVENTS` es de
sistema). El servicio de accesibilidad hace lo mismo con APIs públicas:
gestos en coordenadas (toques, arrastres, desplazamiento), acciones globales
(D-pad, Atrás, Inicio, Recientes) y texto en el campo enfocado. El volumen y
las teclas multimedia usan `AudioManager`, sin accesibilidad.

- El usuario lo activa una vez en Ajustes › Accesibilidad y Android indica que
  está activo. No hace nada si Krelzox Remote está apagado o ninguna sesión
  tiene el control.
- Dibuja el cursor remoto, el PIN y el diálogo de aprobación como
  **superposiciones de accesibilidad**, sin `SYSTEM_ALERT_WINDOW`.
- En TV, una superposición no recibe el foco del D-pad de forma fiable. Por eso
  el servicio **filtra las teclas solo mientras el diálogo de aprobación está en
  pantalla** (izquierda/derecha eligen, OK confirma, Atrás deniega, el resto se
  descarta para que no llegue a la app de debajo) y desactiva el filtro en
  cuanto se cierra. Fuera del diálogo no lee ninguna tecla.

## Consecuencias

- El launcher sigue con un único permiso. Krelzox Remote declara red, red local,
  servicio en primer plano de captura y un servicio de accesibilidad.
- Activarlo requiere tres pasos la primera vez: permiso de red local, captura de
  pantalla (en cada activación) y el servicio de accesibilidad (una vez).
- Sin TLS, alguien que controle la LAN puede leer la imagen de una sesión ya
  aprobada. Por eso todo empieza apagado y se apaga desde la tele.
- La latencia y la calidad dependen de la Wi-Fi: JPEG ocupa más que un vídeo.
