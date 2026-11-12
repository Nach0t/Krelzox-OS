# ADR-006 · Krelzox Store: tienda de aplicaciones

- **Estado:** aceptado
- **Fecha:** 2026-11-12

## Contexto

Krelzox necesita un centro de distribución de apps comparable a una tienda
como Google Play, pero la Raspberry Pi no tiene servicios de Google y Krelzox no
tiene backend, base de datos ni cuentas de usuario. El launcher presume de un
único permiso normal y de no usar la red.

Requisitos: instalar apps de verdad (sin simulaciones), verificar origen e
integridad antes de instalar, no ejecutar nada con privilegios desde la
interfaz y no inventar datos (valoraciones, descargas).

## Decisión 1: app independiente (`dev.krelzox.store`, módulo `:store`)

La tienda necesita `INTERNET`, `REQUEST_INSTALL_PACKAGES`,
`REQUEST_DELETE_PACKAGES` y `QUERY_ALL_PACKAGES`. Meterlos en el launcher
rompería su promesa de privacidad y uniría el riesgo de ambos: un fallo de la
tienda podría tumbar la pantalla de inicio. Como app aparte reutiliza
`:core:designsystem` (mismos tokens, tipografía, iconos y foco) y aparece en la
Biblioteca del launcher con su propio icono y banner.

## Decisión 2: catálogo en formato F-Droid (índice v1 firmado)

Se leen repositorios F-Droid. El de f-droid.org ofrece unas 5.000 apps libres
cuyo derecho de redistribución ya está verificado, con hash SHA-256 y huella del
certificado de firma por APK. Construir un servidor propio duplicaría esa
infraestructura sin catálogo.

- El índice (`index-v1.jar`, ~18 MB) se descarga con peticiones condicionales
  (`ETag`/`If-Modified-Since`) y se guarda en caché.
- Su firma JAR se verifica y el firmante debe coincidir con la **huella fijada**
  del repositorio (`43238d51…c9ccab` para F-Droid). Un índice sin firmar,
  manipulado o firmado con otra clave se rechaza.
- **Anti-rollback:** un índice con `timestamp` anterior al ya aceptado se
  rechaza (protege frente a servir una versión vieja con vulnerabilidades).
- El JSON (~75 MB) se lee en streaming (Gson `JsonReader`) quedándose solo con
  los idiomas preferidos; las URL de imágenes y APK se construyen con la
  dirección **configurada**, nunca con la que declara el índice.
- Se pueden añadir otros repositorios del mismo formato (dirección HTTPS + huella).

## Decisión 3: instalación real con verificación previa

`InstallManager` ejecuta una operación cada vez:

1. Comprueba permiso de instalación, compatibilidad (API, ABI, características
   de hardware) y espacio libre.
2. Descarga por HTTPS (sin texto plano: `network_security_config`), con
   progreso, cancelación y límite de tamaño publicado.
3. Verifica **SHA-256**, **nombre de paquete**, **versión** y **certificado de
   firma** del APK frente al índice, y que la firma coincida con la de la app ya
   instalada en una actualización.
4. Solo entonces lo entrega al `PackageInstaller` del sistema. Android muestra
   su propia confirmación (una app normal no puede instalar en silencio).

Si algo falla no se instala nada y la versión instalada no se toca. El APK
descargado se borra siempre. Cada operación queda en el historial.

La tienda no tiene ningún servicio privilegiado ni ejecuta comandos: las
operaciones privilegiadas las hace el instalador del sistema tras la
confirmación del usuario.

## Decisión 4: sin valoraciones, cuentas ni panel web por ahora

Valoraciones, reseñas, moderación, estadísticas y un panel de administración
web necesitan un servidor con base de datos y autenticación que Krelzox no
tiene. No se inventan puntuaciones ni descargas: la ficha lo dice. La
administración del catálogo se hace con las herramientas de F-Droid
(`fdroidserver`), que firman el índice. Ver [tienda.md](../tienda.md).

## Consecuencias

- El launcher conserva su único permiso. La tienda declara cinco, todos
  necesarios para su función.
- La primera instalación requiere que el usuario permita «Instalar apps
  desconocidas» para Krelzox Store; cada instalación y desinstalación se
  confirma en pantalla.
- En Android TV el diálogo de desinstalación del sistema tiene el foco por
  defecto en «Aceptar». Por eso, en la ficha, el foco vuelve a la acción
  principal tras cada operación y «Desinstalar» es siempre el último botón.
- El catálogo de F-Droid está pensado para móviles: solo ~100 apps declaran
  versión para TV. La tienda lo indica en cada ficha.
