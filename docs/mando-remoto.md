# Krelzox Remote

Ver y controlar la tele desde el navegador de un móvil o un PC de la misma red
local, sin instalar nada en ellos. Es una app de Android TV independiente
(`dev.krelzox.remote`, módulo `:remote`). Decisiones y motivos:
[ADR-007](decisiones/ADR-007-krelzox-remote.md).

## Qué hace

**En la tele** (Krelzox › Ajustes › Krelzox Remote, o desde la Biblioteca):

| Opción | Contenido |
|---|---|
| Krelzox Remote | Interruptor general. Al apagarlo se desconectan todos los navegadores |
| Ver la pantalla | Permite o bloquea que los navegadores emparejados vean la pantalla |
| Control remoto | Permite o bloquea el control; cada navegador se aprueba además en la tele |
| Servicio de control | Estado del servicio de accesibilidad; abre Ajustes › Accesibilidad |
| Calidad | Baja (960×540, 15 fps), Media (1280×720, 24 fps) o Alta (1920×1080, 30 fps) |
| Dispositivos conectados | Cada navegador con su estado; desconectar uno o todos (cambia el PIN) |

La tarjeta de la derecha muestra la dirección que hay que abrir
(`http://<IP>:8080`, o el siguiente puerto libre hasta 8089) y el PIN.

**En el navegador:**

- Imagen de la tele en directo, a pantalla completa si se quiere.
- Tres modos de control: **Táctil** (tocar la imagen), **Trackpad** (mover un
  cursor, con clic, doble clic y clic largo) y **Mando** (D-pad y OK).
- Atrás, Inicio, Recientes, reproducir/pausa, volumen y silencio.
- Escribir en el campo de texto enfocado de la tele, con Intro.
- Lista de apps instaladas para abrirlas.
- Interfaz en español o inglés según el navegador.

## Primer uso

1. Instala la app: `./gradlew :remote:assembleRelease`, firma el APK y
   `adb install -r krelzox-remote.apk` (igual que el launcher, ver el README).
2. Abre Krelzox Remote y pulsa OK en el interruptor. Android pide:
   - acceso a **dispositivos de la red local** (solo la primera vez);
   - permiso para **capturar la pantalla** (cada vez que se activa; elige la
     pantalla completa).
3. Para controlar, activa una vez **Ajustes › Accesibilidad › Control de
   Krelzox Remote**. Solo para ver no hace falta.
4. En el móvil o el PC, abre la dirección que muestra la tele y escribe el PIN
   que aparece en una esquina de la pantalla.
5. Pulsa **Pedir el control** en el navegador. En la tele aparece un diálogo con
   «Denegar» seleccionado: elige con izquierda/derecha y confirma con OK. Atrás
   deniega, y sin respuesta en 60 s también.

## Arquitectura

```
remote/src/main/
  kotlin/dev/krelzox/remote/
    RemoteService      Servicio en primer plano: servidor + captura; sigue los cambios de red
    RemoteHub          Estado del proceso: ajustes (DataStore), sesiones, panel y superposiciones
    net/               RemoteServer (HTTP + WebSocket sin dependencias), WebSocket, LanGuard
    security/          Sessions: PIN, límite de intentos, tokens, aprobación y caducidad del control
    capture/           ScreenCapture: MediaProjection → ImageReader → JPEG
    control/           Commands (protocolo JSON) y RemoteAccessibilityService
    ui/                RemoteActivity: panel de la tele en Compose for TV
  assets/web/          Página del navegador: index.html, app.js, app.css (sin dependencias)
```

El servidor no depende de Android (la captura y la entrada llegan por la
interfaz `Device`), así que se prueba en la JVM con un dispositivo falso.

## Seguridad

| Amenaza | Protección |
|---|---|
| Acceso desde fuera de la LAN | Servidor enlazado a la IP privada; conexiones de otra subred cerradas sin respuesta |
| Página web maliciosa en la LAN (*DNS rebinding*, CSRF) | `Host` debe ser la IP del servidor; `Origin` comprobado en el WebSocket y en los POST; cookie `SameSite=Strict` |
| Adivinar el PIN | 6 dígitos de un solo uso; bloqueo de 1 minuto tras 5 fallos por dirección; el PIN cambia tras 20 fallos en total |
| Robo del token | Solo se guarda su hash; cookie `HttpOnly`; ligado a la dirección del cliente; caduca tras 10 min sin actividad |
| Control sin permiso | Emparejar solo permite ver; el control se aprueba en la tele («Denegar» por defecto) y caduca tras 10 min sin usarse |
| Página manipulada o incrustada | Página dentro del APK; CSP `default-src 'self'`, `X-Frame-Options: DENY`, `nosniff` |
| Teclas de la tele capturadas | El servicio de accesibilidad solo filtra teclas mientras el diálogo de aprobación está en pantalla |

Riesgos que quedan:

- **Sin TLS:** alguien que controle la red local puede leer el tráfico de una
  sesión ya aprobada. Úsalo solo en redes de confianza y apágalo al terminar.
- El servicio de accesibilidad puede actuar sobre cualquier app. Solo ejecuta
  órdenes de sesiones aprobadas mientras Krelzox Remote está encendido, pero
  queda activado hasta que se desactive en Ajustes › Accesibilidad.
- Mientras se comparte, Android muestra el aviso de captura y la notificación
  del servicio.

## Pruebas

- **Unitarias** (`./gradlew :remote:testDebugUnitTest`, 15 pruebas): reglas de
  red local, PIN (un solo uso, bloqueo, rotación), tokens y caducidad, flujo de
  aprobación del control, protocolo de órdenes y el servidor real en loopback
  (rechazo de `Host`/`Origin`, emparejamiento, WebSocket).
- **En la Raspberry Pi** (2026-11-14): activar el servicio y aceptar la
  captura de pantalla; emparejar con el PIN desde el PC; pedir el control; el
  diálogo aparece con «Denegar» seleccionado; izquierda/derecha cambian la
  opción y OK concede el control; sin respuesta, se deniega a los 60 s; tras
  cerrarse, el D-pad vuelve a mover el foco con normalidad.
  Las teclas se probaron con un teclado virtual (`uinput`): `adb shell input
  keyevent` inyecta los eventos por detrás del filtro de accesibilidad y no
  sirve para probar el diálogo.
- **No probado en el dispositivo:** Atrás para denegar y las órdenes de control
  (toques, texto, abrir apps, volumen) desde un navegador real.

## Pendiente

- Vídeo H.264/VP8 en lugar de JPEG si el hardware lo permite (menos ancho de
  banda).
- Audio de la tele en el navegador.
- TLS con un certificado que el navegador acepte sin avisos.
