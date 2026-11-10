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

## 2. Qué pasa si Nexium falla siendo HOME (comprobado en el dispositivo)

Android protege por su cuenta a los launchers que no son de sistema: cuando
Nexium se cierra por error, el sistema **borra su preferencia de app de inicio**
("Clearing package preferred activities") aunque conserva el rol. Al pulsar
HOME aparece el selector **"Seleccionar una aplicación de la pantalla
principal"** con Nexium y Catapult (*Solo una vez* / *Siempre*).

Para que HOME vuelva a ir directo a Nexium sin pasar por el selector:

```cmd
adb shell cmd role add-role-holder android.app.role.HOME dev.nexium.launcher
```

o elegir Nexium → **Siempre** en el selector.

## 3. Modo seguro automático

Si Nexium se cierra por error **3 veces en 2 minutos**, el siguiente arranque
muestra el **modo seguro** (pantalla mínima sin Compose ni carga de datos):

- **Elegir otro launcher** → abre "App de inicio predeterminada" del sistema
  para volver a Catapult con el mando o el teclado.
- **Abrir ajustes** → Ajustes de TV.
- **Reintentar** → borra el registro de fallos y carga Nexium normal.

Los fallos antiguos caducan solos pasados 2 minutos.

## 4. Si el launcher entra en bucle de fallos (por ADB)

```cmd
adb shell am force-stop dev.nexium.launcher
adb shell pm clear dev.nexium.launcher
```

`pm clear` borra solo los datos de Nexium (favoritos, orden).

## 5. Eliminar Nexium por completo

```cmd
adb uninstall dev.nexium.launcher
```

Android vuelve automáticamente a Catapult o, en último caso, a
`com.android.tv.settings/.system.FallbackHome`.

## 6. Sin ADB por red

Con teclado USB o mando:

- Desde Nexium: tarjeta **Configuración**, o el modo seguro → **Elegir otro launcher**.
- Desde Ajustes: Aplicaciones → Aplicaciones predeterminadas → **App de inicio
  predeterminada** → Catapult.

La pantalla del sistema es
`com.android.permissioncontroller/.role.ui.HomeSettingsActivity`
(`Settings.ACTION_HOME_SETTINGS`), verificada en el dispositivo.

> El ADB por red (`service.adb.tcp.port=5555`) **no es una propiedad
> persistente**: tras reiniciar puede ser necesario reactivarlo desde las
> opciones de desarrollador.

## Prevención

- Catapult nunca se deshabilita ni desinstala.
- Toda versión nueva se prueba primero lanzándola como actividad normal
  (`adb shell am start -n dev.nexium.launcher/.MainActivity`) antes de
  asignarle el rol HOME.
