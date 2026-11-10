# ADR-004 · Métricas del sistema: fuentes reales, sin permisos nuevos

- **Estado:** aceptado
- **Fecha:** 2026-11-10

## Contexto

La etapa 5 añade un monitor de rendimiento permanente (barra superior), una
vista ampliada con gráficos y el centro «Sistema y rendimiento» en Ajustes.
Nexium es una app de usuario (ADR-001): no tiene root ni permisos de sistema, y
el dispositivo ejecuta una ROM (KonstaKANG, Android 17) con SELinux en modo
**permisivo**, que no es lo habitual.

## Decisión 1: solo valores reales, nunca estimados

Cada métrica sale de una fuente concreta y se indica en pantalla («Fuente: …»):

| Métrica | Fuente | Permiso |
|---|---|---|
| Uso de CPU | `/proc/stat` (diferencia entre dos lecturas) | Ninguno |
| Carga 1/5/15 min | `/proc/loadavg` | Ninguno |
| Temperatura | Zona térmica `cpu-thermal` (`/sys/class/thermal`) | Ninguno |
| Frecuencia actual y máxima | `cpufreq` (`/sys/devices/system/cpu`) | Ninguno |
| Ventilador | `hwmon` (`/sys/class/hwmon`) | Ninguno |
| RAM | `ActivityManager.getMemoryInfo` | Ninguno |
| Almacenamiento | `StatFs` sobre la partición de datos | Ninguno |
| Tráfico de red | `TrafficStats` (contadores totales) | Ninguno |
| IP, tipo de red, validación | `ConnectivityManager` / `LinkProperties` | `ACCESS_NETWORK_STATE` (ADR-003) |
| Enlace Wi-Fi (Mbps) | `WifiInfo.linkSpeed` vía `NetworkCapabilities.transportInfo` | `ACCESS_NETWORK_STATE` |

Si una fuente no existe o está bloqueada, la métrica se muestra como
**«No disponible»**: no se aproxima con otra fuente ni se inventa. No hay
pruebas de velocidad de red: solo se mide el tráfico que ya existe.

## Decisión 2: aceptar que SELinux puede bloquear `/proc/stat` y `/proc/loadavg`

La política SELinux de Android deniega a las apps normales (`untrusted_app`)
la lectura de `/proc/stat` y `/proc/loadavg`. En este dispositivo funcionan
solo porque SELinux está en modo permisivo (la denegación queda en el log, una
vez por fuente, no en cada lectura).

- En un sistema en modo estricto, **uso de CPU y carga aparecerán como
  «No disponible»**. El diseño ya lo contempla.
- Temperatura, frecuencia y ventilador (`/sys`) no tienen esa restricción en
  este dispositivo.
- No hay alternativa equivalente sin privilegios: no se recurre a permisos de
  sistema ni a ejecutar herramientas del shell.

## Decisión 3: coste cero cuando no se ve

- Un único `SystemMonitor` toma una muestra cada **2 s** en el dispatcher de
  IO; el almacenamiento se lee cada ~30 s.
- El flujo se comparte con `shareIn(WhileSubscribed(3 s), replay = 1)`: con
  Nexium en segundo plano, o con otra app delante, no se lee nada.
- Historial en memoria de **120 muestras (4 min)**; se reinicia en cada
  reanudación (las diferencias nunca abarcan una pausa).
- Solo el panel de rendimiento recompone con cada muestra, no la barra ni la
  pantalla.

## Consecuencias

- Sin permisos nuevos ni dependencias nuevas. APK release: 2,5 MB → 2,7 MB.
- La pantalla de Red muestra la **IP local y la IPv6** (que puede ser
  pública). Es información del propio dispositivo y no sale de él, pero las
  capturas para documentación deben ocultarla.
- Portabilidad: en otros dispositivos Android TV, las métricas de `/proc` y
  `/sys` pueden faltar; el resto (RAM, almacenamiento, red) funciona siempre.
