# Rendimiento: perfil, benchmarks y pruebas en el dispositivo

Todo lo que necesita la Raspberry Pi conectada por ADB. Lo que no la necesita
(tests unitarios, lint, build release y compilación de estas pruebas) corre en
CI: [`.github/workflows/android.yml`](../.github/workflows/android.yml).

Antes de empezar (PowerShell, desde la raíz del repo):

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Java\jdk-21.0.13+9"
$env:ANDROID_SERIAL = "<IP_DE_LA_PI>:5555"
$env:PATH += ";$PWD\platform-tools"
```

## Pruebas de UI (foco con D-pad)

```powershell
.\gradlew :app:connectedDebugAndroidTest
```

- `app/src/androidTest/.../KrelzoxShellFocusTest`: 4 pruebas con eventos de
  tecla reales (como un mando): foco inicial en el hero, ↓ desde la pestaña
  Ajustes llega a «Sistema y rendimiento», OK abre el centro con el foco en
  «Procesador» y Back vuelve al mosaico, y la vista ampliada abre el centro.
- Desde la etapa 7 hay 7 pruebas: además, Apariencia se abre en el tema en uso
  y Back vuelve a su mosaico, OK en un tema lo aplica, y el mosaico Raspberry Pi
  abre su diálogo.
- Usan la build **debug** (`dev.krelzox.launcher.debug`): Gradle la instala y la
  desinstala al terminar sin tocar el Krelzox de uso diario ni su rol HOME.
- Comprobado que detectan los bugs de la etapa 5: al deshacer cada corrección,
  fallan exactamente las pruebas que dependen de ella.

## Baseline Profile

El perfil (`app/src/main/generated/baselineProfiles/`) se versiona y se regenera
a mano cuando cambian pantallas o recorridos importantes:

```powershell
adb shell cmd role add-role-holder android.app.role.HOME org.lineageos.tv.launcher
.\gradlew :app:generateBaselineProfile "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
adb shell cmd role add-role-holder android.app.role.HOME dev.krelzox.launcher
```

> **Importante:** sin `leaveApksInstalledAfterRun=true`, Gradle **desinstala
> Krelzox** al terminar (la app medida) y se pierden sus datos: favoritos,
> recientes y tema. Ocurrió en la etapa 6; desde la etapa 7 los comandos llevan
> la opción, comprobado en la Pi (el tema elegido sobrevive a la generación).

- **Krelzox no puede ser HOME mientras tanto**: el generador pulsa HOME entre
  iteraciones y, si Krelzox es el launcher, HOME lo relanzaría. Se usa Catapult
  temporalmente ([recuperacion.md](recuperacion.md)) y se devuelve el rol al
  terminar.
- Recorrido (`baselineprofile/.../KrelzoxJourney.kt`): arranque, hero ↔
  Favoritos ↔ Aplicaciones, las cuatro pestañas, el centro de rendimiento con
  sus cinco categorías, Apariencia (solo navegando: nunca pulsa OK en un tema,
  para no cambiar el del usuario) y la vista ampliada del panel. Todo con D-pad.
- `profileinstaller` instala el perfil en el dispositivo (sin Play Store no hay
  perfiles en la nube); el sistema compila con él en segundo plano.
- La conexión Wi-Fi con la Pi es inestable: si ADB falla con `TimeoutException` o
  «No compatible devices connected», reconectar (`adb connect`) y repetir.

## Macrobenchmark de arranque

```powershell
adb shell cmd role add-role-holder android.app.role.HOME org.lineageos.tv.launcher
.\gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark" `
  "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
adb shell cmd role add-role-holder android.app.role.HOME dev.krelzox.launcher
```

Resultados en
`baselineprofile/build/outputs/connected_android_test_additional_output/`.
El benchmark instala la variante `benchmarkRelease` sobre Krelzox (conserva sus
datos): al terminar, reinstalar la release normal ([entorno.md](entorno.md)).

La fluidez al navegar **no** se mide con Macrobenchmark: en esta ROM
`FrameTimingMetric` no captura ningún frame de la app («0 found for
frameDurationCpuMs»). Probablemente la ROM no registra las trazas de la propia
app (no verificado); el arranque sí se mide porque sale de eventos del sistema.
La navegación se mide con `dumpsys gfxinfo`, como desde la etapa 2.

## Resultados (Raspberry Pi 5, 2026-11-10, release)

### Arranque en frío (Macrobenchmark, TTID, 15 iteraciones)

| Compilación | Mediana | Rango |
|---|---|---|
| Ninguna (solo JIT) | 265 ms | 230–336 ms |
| Baseline Profile | 342 ms | 272–414 ms |
| AOT completo | 349 ms | 314–407 ms |

En esta Pi, el código compilado **no acelera el arranque**: con perfil o con
AOT completo es ~80 ms más lento que solo con JIT. Hipótesis no verificada:
cargar el código compilado desde el almacenamiento cuesta más de lo que ahorra.
Con `am start -W` (método de etapas anteriores) no se ve esa diferencia
(113–156 ms sin compilar, 101–170 ms con perfil): el efecto en arranque no es
concluyente y, en todo caso, queda muy lejos del objetivo de 600 ms.

### Navegación justo después de instalar (gfxinfo, primera pasada, proceso nuevo)

Recorrido de `nav.sh` (Inicio: hero, Favoritos, Aplicaciones), ~700 frames:

| Compilación | Jank | p90 | p99 |
|---|---|---|---|
| Solo JIT (`compile -m verify`) | 1,28–1,58 % | 20 ms | 32–38 ms |
| Baseline Profile (`speed-profile`) | **0,57–0,71 %** | **17 ms** | **20 ms** |

El perfil reduce los tirones a la mitad y elimina los frames de 30–40 ms del
JIT: es el problema que las etapas 4 y 4.1 dejaron pendiente («≈ 1,4 % de jank
justo después de instalar»). Por eso se mantiene, aunque no mejore el arranque.

En esta tanda la GPU dio p90 16 ms en ambos estados (6 ms en la etapa 5). No
depende de la compilación (es igual con y sin perfil); queda anotado para
revisar.

### R8 y tamaño

R8 (minificación y reducción de recursos) está activo en release desde la
etapa 1. APK release: 2,8 MB (+0,13 MB por el perfil y `profileinstaller`). El
perfil de arranque reorganiza el dex: el código de arranque va en
`classes.dex` y el resto en `classes2.dex`.
