# Krelzox OS

[![Android CI](https://github.com/Nach0t/Krelzox-OS/actions/workflows/android.yml/badge.svg)](https://github.com/Nach0t/Krelzox-OS/actions/workflows/android.yml)
![Plataforma](https://img.shields.io/badge/plataforma-Android%20TV%2017-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose%20for%20TV-7F52FF)
![Licencia](https://img.shields.io/badge/licencia-Apache%202.0-blue)

[English](README.md) · **Español**

Una experiencia Android TV premium para Raspberry Pi 5. Su primer componente es
**Krelzox Launcher** (`dev.krelzox.launcher`): una pantalla de inicio en Kotlin y
Jetpack Compose for TV que se instala como una app normal, sin root, sin
modificar la ROM y conservando siempre el launcher del sistema como red de
seguridad.

*Antes se llamaba Nexium OS.*

![Pantalla de inicio de Krelzox](docs/images/home.png)

## Contenido

- [Capturas](#capturas)
- [Características](#características)
- [Tecnología](#tecnología)
- [Arquitectura](#arquitectura)
- [Requisitos](#requisitos)
- [Compilar e instalar](#compilar-e-instalar)
- [Configurar la Raspberry Pi](#configurar-la-raspberry-pi)
- [Acceso remoto desde la red local](#acceso-remoto-desde-la-red-local)
- [Administración](#administración)
- [Solución de problemas](#solución-de-problemas)
- [Desarrollo](#desarrollo)
- [Documentación](#documentación) · [Contribuir](#contribuir) · [Seguridad](#seguridad) · [Licencia](#licencia)

## Capturas

Tomadas en la Raspberry Pi 5 de referencia con el tema **Aurora** (interfaz en
inglés; también está disponible en español).

| Biblioteca | Ajustes |
|---|---|
| ![Biblioteca con todas las apps instaladas](docs/images/library.png) | ![Mosaicos de Ajustes](docs/images/settings.png) |

| Sistema y rendimiento | Apariencia |
|---|---|
| ![Detalle del procesador con gráficos en tiempo real](docs/images/performance-center.png) | ![Selector de tema y vista previa del salvapantallas](docs/images/appearance.png) |

| Salvapantallas |
|---|
| ![Salvapantallas de Krelzox](docs/images/screensaver.png) |

## Características

**Pantalla de inicio**
- Carrusel dinámico: la app que estabas usando (*Continuar*), apps recientes,
  favoritos y sugerencias.
- Fila de favoritos con reordenación en directo y fila de aplicaciones;
  pestañas **Biblioteca** (todas las apps) y **Multimedia** (apps de vídeo,
  audio e imagen).
- Pensada para el mando: todo funciona con el D-pad, el foco nunca se pierde y
  Back es jerárquico.

**Monitor de rendimiento**
- Panel permanente en la barra superior: CPU, RAM, temperatura y almacenamiento
  libre.
- Vista ampliada con gráficos en tiempo real de los últimos 4 minutos (actual,
  máximo y media).
- Centro **Sistema y rendimiento** en Ajustes: procesador (uso, temperatura,
  frecuencia, carga, ventilador), memoria, almacenamiento, sistema y red.
- Solo valores reales, con su fuente en pantalla. Si el sistema bloquea una
  fuente, la métrica indica *No disponible*: nunca se estima.

**Apariencia y salvapantallas**
- Cuatro temas (Ion, el original; Aurora, Ámbar y Jade) que recolorean todo el
  sistema a la vez: foco, marca, gráficos y salvapantallas. Cada tarjeta de tema
  muestra su propio foco antes de elegirlo.
- Salvapantallas de Krelzox: hora, fecha y la luz del tema sobre grafito
  profundo. Cambia de posición cada minuto para cuidar la pantalla y apenas usa
  la CPU.
- Mosaico Raspberry Pi que indica dónde están exactamente los ajustes de
  hardware de KonstaKANG (Android no permite que otras apps los abran
  directamente).

**Fiabilidad**
- Funciona como app de inicio (HOME), con **modo seguro**: tras 3 cierres por
  error en 2 minutos abre una pantalla mínima para elegir otro launcher o abrir
  Ajustes.
- El launcher del sistema (Catapult) nunca se deshabilita; la
  [guía de recuperación](docs/recuperacion.md) cubre todas las vías de vuelta.

**Rendimiento y privacidad**
- Baseline Profile + R8: APK release de 2,8 MB; navegación con D-pad con < 1 %
  de frames con tirones incluso justo después de instalar; arranque en frío muy
  por debajo de 600 ms.
- Un único permiso, normal (`ACCESS_NETWORK_STATE`); los temas y el
  salvapantallas no añaden ninguno. Sin root, sin telemetría, sin anuncios y
  sin servidor de red.
- Interfaz en inglés y español.

## Tecnología

| Área | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.4 (JVM 17) |
| Interfaz | Jetpack Compose (BOM 2026.09) + Compose for TV Material 1.1, tipografía Inter |
| Estado y almacenamiento | AndroidX Lifecycle / ViewModel, corrutinas de Kotlin, DataStore |
| Compilación | Gradle 9.9 (wrapper), Android Gradle Plugin 9.5, R8, Baseline Profiles |
| Pruebas | JUnit 4 (unitarias), UI Automator + AndroidX Test (dispositivo), Macrobenchmark |
| CI | GitHub Actions, Dependabot, CodeQL, grafo de dependencias |

## Arquitectura

Krelzox es una única app Android dividida en módulos de Gradle con dependencias
en un solo sentido:

```
app/                 Launcher: actividad, shell, Inicio / Biblioteca / Multimedia / Ajustes,
                     UI de rendimiento, salvapantallas (DreamService), modo seguro
 ├─ core/designsystem/   Tokens, temas, iconos, gráficos — sin dependencias de datos
 └─ core/data/           Apps instaladas, favoritos, recientes, red, métricas del sistema
baselineprofile/     Generador del Baseline Profile y benchmarks de arranque (en el dispositivo)
docs/                Arquitectura, identidad visual, hoja de ruta, ADR (español), imágenes
scripts/             Scripts de ayuda (acceso remoto)
```

El launcher lee la información del sistema en local (`/proc`, zonas térmicas,
`PackageManager`, `ConnectivityManager`) y guarda las preferencias solo en el
dispositivo; no tiene backend ni abre puertos de red. Detalles:
[docs/arquitectura.md](docs/arquitectura.md).

## Requisitos

**Equipo de desarrollo**
- JDK 21 y el SDK de Android con la plataforma 37.
- ADB (platform-tools de Android). Los scripts lo esperan en `platform-tools/`,
  en la raíz del repositorio (no se versiona).
- Opcional, para el acceso remoto: [scrcpy](https://github.com/Genymobile/scrcpy).

**Dispositivo**

| | |
|---|---|
| Hardware | Raspberry Pi 5 Model B Rev 1.1, 16 GB de RAM |
| Sistema | LineageOS 24.0 (Android TV 17, SDK 37), build `rpi5_tv` de KonstaKANG |
| Pantalla | 1920×1080 a 60 Hz, 320 dpi (960×540 dp) |
| GPU | Broadcom V3D (Mesa GLES 3.1 / Vulkan 1.4) |

El dispositivo no tiene servicios de Google. Krelzox debería funcionar en otros
dispositivos Android TV con SDK 31+, pero solo se diseña y prueba en este.

## Compilar e instalar

```sh
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release-unsigned.apk
```

Firma el APK (para desarrollo basta la clave de depuración local) e instálalo:

```sh
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --ks-key-alias androiddebugkey --out krelzox.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
adb install -r krelzox.apk
```

Antes de convertirlo en la app de inicio, comprueba que arranca como app normal:

```sh
adb shell am start -n dev.krelzox.launcher/.MainActivity
```

Después, conviértelo en la app de inicio (totalmente reversible; ver la
[guía de recuperación](docs/recuperacion.md)):

```sh
adb shell cmd role add-role-holder android.app.role.HOME dev.krelzox.launcher
```

Los equivalentes para Windows están en [docs/entorno.md](docs/entorno.md).

## Configurar la Raspberry Pi

1. Instala la build LineageOS 24.0 `rpi5_tv` de KonstaKANG siguiendo sus
   propias instrucciones.
2. Activa las **opciones de desarrollador** (pulsa siete veces el número de
   compilación en la pantalla *Información* del sistema) y, en ellas, el
   **ADB por red**.
3. Anota la IP del dispositivo (Ajustes › Red). Crea una **reserva DHCP** en el
   router para que la dirección no cambie.
4. Desde el equipo de desarrollo, conéctate y acepta la autorización en la tele:

   ```sh
   adb connect <IP_DE_LA_PI>:5555
   adb devices -l          # el estado debe ser "device"
   ```

5. Instala Krelzox y conviértelo en la app de inicio ([Compilar e instalar](#compilar-e-instalar)).
6. Opcional: elige el salvapantallas de Krelzox en Ajustes › Sistema ›
   Protector de pantalla.

Puede que haya que reactivar el ADB por red tras reiniciar
([docs/recuperacion.md](docs/recuperacion.md)).

## Acceso remoto desde la red local

Krelzox no tiene interfaz web: es una app de TV. Para verlo y controlarlo desde
un PC de la misma red, usa **scrcpy**, que replica la pantalla de la tele y
envía el teclado y el ratón por ADB.

Instálalo una vez (Windows: `winget install --id Genymobile.scrcpy -e`; otros
sistemas: ver la documentación de scrcpy) y ejecuta desde la raíz del repositorio:

```powershell
.\scripts\remote.ps1                                # busca la Pi por mDNS
.\scripts\remote.ps1 -Address <IP_DE_LA_PI>:5555    # o indica la dirección
.\scripts\remote.ps1 --max-size 1280                # los demás argumentos van a scrcpy
```

El script usa el ADB del repositorio para no chocar con el ADB que incluye
scrcpy. En otros sistemas: `adb connect <IP_DE_LA_PI>:5555` y después
`scrcpy -s <IP_DE_LA_PI>:5555`.

| En la tele | En la ventana de scrcpy |
|---|---|
| D-pad | Flechas |
| OK | Intro |
| Atrás | Clic derecho, o `Alt`+`B` |
| Inicio | Clic central, o `Alt`+`H` |

**Seguridad.** ADB da control total del dispositivo a cualquier equipo cuya
clave autorices, y su tráfico no va cifrado. Úsalo solo en una red doméstica de
confianza, no redirijas nunca el puerto 5555 en el router y desactiva el ADB por
red cuando no lo necesites. Puedes revocar los equipos autorizados en Opciones
de desarrollador › *Revocar autorizaciones de depuración*.

## Administración

| Tarea | Comando |
|---|---|
| Ver la versión instalada | `adb shell dumpsys package dev.krelzox.launcher \| grep versionName` |
| Actualizar | Compilar, firmar y `adb install -r krelzox.apk` (conserva los datos) |
| Reiniciar el launcher | `adb shell am force-stop dev.krelzox.launcher` y pulsar Inicio |
| Ver la app de inicio actual | `adb shell cmd role get-role-holders android.app.role.HOME` |
| Volver al launcher del sistema | `adb shell cmd role add-role-holder android.app.role.HOME org.lineageos.tv.launcher` |
| Leer los cierres por error | `adb logcat -d -b crash` |
| Hacer una captura | `adb exec-out screencap -p > pantalla.png` |

## Solución de problemas

| Problema | Solución |
|---|---|
| `adb connect` no responde | Comprueba que la Pi y el PC están en la misma red y que el ADB por red sigue activo (puede desactivarse al reiniciar). |
| El dispositivo aparece como `unauthorized` | Acepta el aviso en la tele, o revoca las autorizaciones en Opciones de desarrollador y vuelve a conectar. |
| El dispositivo aparece como `offline` | `adb disconnect` y de nuevo `adb connect <IP_DE_LA_PI>:5555`. |
| scrcpy se cierra al momento o reinicia ADB | Usa `scripts/remote.ps1`, o define la variable de entorno `ADB` con el mismo `adb` que usas en el resto. |
| Krelzox se cierra una y otra vez | El modo seguro se abre solo tras 3 cierres en 2 minutos; elige allí otro launcher. Ver la [guía de recuperación](docs/recuperacion.md). |
| Una métrica indica *No disponible* | El sistema bloquea esa fuente a las apps normales; Krelzox nunca estima valores. |
| El salvapantallas no se inicia | Elige *Krelzox* en Ajustes › Sistema › Protector de pantalla. |

## Desarrollo

| Comando | Qué hace | Dónde |
|---|---|---|
| `./gradlew testDebugUnitTest lintDebug` | Tests unitarios y lint | CI + local |
| `./gradlew assembleRelease` | Build release (R8 + Baseline Profile) | CI + local |
| `./gradlew :app:connectedDebugAndroidTest` | Pruebas de UI de foco con D-pad | Dispositivo |
| `./gradlew :app:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` | Regenera el Baseline Profile (el flag conserva Krelzox y sus datos) | Dispositivo |
| `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` | Macrobenchmark de arranque en frío | Dispositivo |

Las builds debug usan el paquete `dev.krelzox.launcher.debug`, así que las
pruebas nunca sustituyen ni desinstalan el launcher en uso. Procedimientos y
resultados en el dispositivo: [docs/rendimiento.md](docs/rendimiento.md).

El CI (GitHub Actions) ejecuta los tests unitarios, el lint, la build release y
compila las pruebas de dispositivo en cada push y pull request, y publica un
resumen (tests, lint, tamaño del APK, Baseline Profile). Dependabot propone
actualizaciones de dependencias cada semana.

## Documentación

La documentación detallada está en **español**:

- [Arquitectura](docs/arquitectura.md) · [Identidad visual](docs/identidad-visual.md) · [Hoja de ruta](docs/hoja-de-ruta.md)
- [Entorno de desarrollo](docs/entorno.md) · [Rendimiento y pruebas en dispositivo](docs/rendimiento.md)
- [Recuperación del dispositivo](docs/recuperacion.md) · [Dispositivo de referencia](docs/dispositivo.md)
- Registros de decisiones de arquitectura: [docs/decisiones/](docs/decisiones/)

## Contribuir

Las contribuciones son bienvenidas. Lee la [guía de contribución](CONTRIBUTING.md)
y el [código de conducta](CODE_OF_CONDUCT.md) (en inglés) antes de abrir un issue
o un pull request. El estado de la accesibilidad y sus limitaciones conocidas
están en [ACCESSIBILITY.md](ACCESSIBILITY.md).

El código, los identificadores y los mensajes de commit van en inglés; el README
en inglés y español, y la documentación detallada de `docs/` en español.

## Seguridad

Para informar de una vulnerabilidad, sigue la [política de seguridad](SECURITY.md)
(en inglés); nunca uses issues públicos.

No subas claves de firma (`*.jks`, `*.keystore`), contraseñas, tokens,
`local.properties` ni logs del dispositivo. El `.gitignore` los excluye, pero
revisa siempre `git status` antes de un commit. Las capturas de la pantalla de
Red muestran las direcciones IP del dispositivo y no deben publicarse.

## Licencia

Apache License 2.0; ver [LICENSE](LICENSE).

Incluye la tipografía **Inter** © The Inter Project Authors, bajo la SIL Open
Font License 1.1 ([third_party/inter/OFL.txt](third_party/inter/OFL.txt)).
