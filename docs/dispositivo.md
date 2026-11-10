# Dispositivo de referencia

Datos obtenidos por ADB en la auditoría inicial (2026-11-09), solo lectura.

## Sistema

| Propiedad | Valor |
|---|---|
| `ro.product.model` | Pi 5 Model B Rev 1.1 |
| CPU | 4 × Cortex-A76 (`0xd0b`), `arm64-v8a` únicamente |
| RAM | 16 GB |
| Android | 17 (SDK 37), parche 2026-10-01 |
| `ro.lineage.version` | `24.0-20261016-UNOFFICIAL-KonstaKANG-rpi5_tv` |
| Tipo de build | `userdebug`, `dev-keys` |
| Kernel | 6.18.33-v8 |
| `dalvik.vm.heapgrowthlimit` / `heapsize` | 192m / 512m |
| Almacenamiento | `/data` 10 GB · `/system` 2,9 GB |
| Idioma | `es-CL` |

## Gráficos y pantalla

| Propiedad | Valor |
|---|---|
| EGL / Vulkan | Mesa / Broadcom, Vulkan 1.4 nivel 1 |
| OpenGL ES | 3.1 |
| Resolución | 1920×1080 @ 60 Hz (también 30 y 20 Hz) |
| Densidad | 320 dpi (xhdpi) → 960×540 dp |
| HDR | No |

## Funciones relevantes

- `android.software.leanback`, `leanback_only`, `live_tv`, `android.hardware.type.television`.
- **Sin Google Mobile Services**: no hay Play Store ni Play Services.
- WebView 155.
- Red: solo Wi-Fi activo (`eth0` caído en la auditoría).
- Entrada detectada: teclado y ratón USB. **Sin mando ni HDMI-CEC registrado.**

## Launcher del sistema

- `org.lineageos.tv.launcher/.MainActivity` (Catapult), app privilegiada en
  `/system/system_ext/priv-app/Catapult`, titular del rol `android.app.role.HOME`.
- Respaldo de último recurso: `com.android.tv.settings/.system.FallbackHome`.

## Paquetes

104 paquetes, todos de sistema; 0 de terceros. Deshabilitados de fábrica:
`com.android.server.telecomui`, `com.android.virtualization.terminal`,
`com.android.nfc`, `com.android.devicelockcontroller`.

**Política del proyecto:** no se deshabilitan ni desinstalan paquetes del
sistema. El ahorro de memoria es despreciable con 16 GB y el riesgo de romper
OTA o dependencias es real.

## Conexión ADB

ADB por red (TCP 5555) con autorización RSA (`ro.adb.secure=1`). El tráfico no
va cifrado: usar solo en una LAN de confianza. La dirección IP del dispositivo
no se versiona; ver [entorno.md](entorno.md).
