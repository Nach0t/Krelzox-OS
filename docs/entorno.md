# Entorno de desarrollo (Windows)

## Herramientas

| Herramienta | Versión | Ubicación |
|---|---|---|
| JDK | Temurin 21.0.13 LTS | `%LOCALAPPDATA%\Programs\Java\jdk-21.0.13+9` |
| Android SDK | — | `%LOCALAPPDATA%\Android\Sdk` |
| SDK Platform | android-37.0 (también android-34) | `Sdk\platforms` |
| Build-tools | 37.0.0 (también 34.0.0, 35.0.0) | `Sdk\build-tools` |
| Command-line tools / Android CLI | latest (16111833) | `Sdk\cmdline-tools\latest` |
| ADB | platform-tools 37.0.1 | `<repo>\platform-tools` (no versionado) |

El JDK 21 **no** está en el `PATH` ni en `JAVA_HOME` globales, para no alterar
otras herramientas del equipo. Gradle lo usará mediante
`org.gradle.java.home` en `%USERPROFILE%\.gradle\gradle.properties` (fuera del
repo) o el toolchain de Gradle.

El SDK de Android se gestiona con el nuevo Android CLI (`sdkmanager` está
deprecado):

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Java\jdk-21.0.13+9"
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\android.exe" --no-metrics sdk list
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\android.exe" --no-metrics sdk install platforms/android-37.0
```

## Conexión con la Raspberry Pi

Usar siempre el ADB local del repositorio:

```cmd
cd /d "<repo>\platform-tools"
adb connect <IP_DE_LA_PI>:5555
adb devices -l
```

El estado debe ser `device` (no `unauthorized` ni `offline`). La IP real no se
versiona.

## Reglas de trabajo con el dispositivo

- Diagnóstico solo de lectura salvo autorización explícita.
- Nunca `pm disable`, `pm uninstall` ni `su` sobre paquetes del sistema.
- Antes de cambiar el rol HOME, tener a mano [recuperacion.md](recuperacion.md).
