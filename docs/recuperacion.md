# Recuperación del dispositivo

Si Nexium Launcher falla al arrancar, se cuelga o deja la TV sin salida, estos
pasos devuelven el control a Catapult. Ninguno requiere root.

## 1. Volver a Catapult como launcher

```cmd
adb shell cmd role add-role-holder android.app.role.HOME org.lineageos.tv.launcher
adb shell input keyevent KEYCODE_HOME
```

Comprobar:

```cmd
adb shell cmd role get-role-holders android.app.role.HOME
```

Debe responder `org.lineageos.tv.launcher`.

## 2. Si el launcher entra en bucle de fallos

```cmd
adb shell am force-stop dev.nexium.launcher
adb shell pm clear dev.nexium.launcher
```

`pm clear` borra solo los datos de Nexium (favoritos, orden).

## 3. Eliminar Nexium por completo

```cmd
adb uninstall dev.nexium.launcher
```

Android vuelve automáticamente a Catapult o, en último caso, a
`com.android.tv.settings/.system.FallbackHome`.

## 4. Sin ADB por red

Conectar teclado USB, abrir Ajustes (si Catapult no aparece, `FallbackHome`
ofrece acceso a Ajustes) → Aplicaciones → Aplicaciones predeterminadas →
Aplicación de inicio → Catapult.

## Prevención

- Catapult nunca se deshabilita ni desinstala.
- Toda versión nueva se prueba primero lanzándola como actividad normal
  (`adb shell am start -n dev.nexium.launcher/.MainActivity`) antes de
  asignarle el rol HOME.
