# ADR-003 · Permiso de estado de red y tipografía empaquetada

- **Estado:** aceptado
- **Fecha:** 2026-11-10

## Contexto

La etapa 4 (diseño premium) añade a la barra superior el estado de conexión y
adopta Inter como tipografía de marca. La arquitectura exige un ADR para
cualquier permiso nuevo.

## Decisión 1: `ACCESS_NETWORK_STATE`

Se declara `android.permission.ACCESS_NETWORK_STATE` en `:core:data`.

- Es un permiso **normal** (concedido en la instalación, sin diálogo).
- Solo permite saber si hay red y su tipo (Wi-Fi, Ethernet). No da acceso a
  SSID, ubicación ni tráfico.
- No se usa `ACCESS_WIFI_STATE` ni se muestra la intensidad de señal: no
  aportaba lo suficiente para justificar otro permiso.

## Decisión 2: Inter empaquetada

Se incluye Inter (variable, SIL OFL 1.1) en el APK.

- El dispositivo **no tiene Google Mobile Services**: las fuentes descargables
  no están disponibles.
- Un único archivo variable (≈ 860 KB, ≈ 0,6 MB comprimido en el APK) cubre
  los pesos 400–700.
- La licencia OFL permite empaquetarla; el texto está en
  `third_party/inter/OFL.txt` y el crédito aparece en "Acerca de Nexium".

## Consecuencias

- APK release: 1,9 MB → 2,5 MB.
- Ningún permiso peligroso; sin cambios en la superficie de privacidad.
