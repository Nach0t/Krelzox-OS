# Krelzox Store

Tienda de aplicaciones de Krelzox OS: una app de Android TV independiente
(`dev.krelzox.store`, módulo `:store`) que instala, actualiza y desinstala apps
de repositorios con formato F-Droid. Decisiones y motivos:
[ADR-006](decisiones/ADR-006-krelzox-store.md).

## Qué hace

| Sección | Contenido |
|---|---|
| Buscar | Búsqueda local por nombre, resumen, categoría y paquete (sin acentos ni mayúsculas) |
| Inicio | Hechas para TV, De las categorías que usas, Actualizadas recientemente, Nuevas en el catálogo, Juegos |
| Aplicaciones / Juegos | Todo el catálogo, lo más reciente primero |
| Categorías | Categorías del repositorio con su número de apps |
| Biblioteca | Resumen e historial de instalaciones, actualizaciones y desinstalaciones |
| Instaladas | Apps del catálogo instaladas en el dispositivo |
| Actualizaciones | Actualizaciones seguras disponibles; «Actualizar todas» |
| Favoritos | Apps marcadas como favoritas |
| Configuración | Repositorios, permiso de instalación, dispositivo y cómo se verifica todo |

La ficha de cada app muestra icono, desarrollador, resumen, versión, tamaño,
fechas, licencia, categorías, requisitos, capturas, descripción, novedades,
permisos, avisos del repositorio (*anti-features*) e historial de versiones. Los
botones (Instalar, Actualizar, Abrir, Desinstalar, Favorito) reflejan el estado
real que da `PackageManager`. La última sección abierta se recuerda al volver.

No hay valoraciones ni número de descargas: F-Droid no los publica y no se
inventan.

## Arquitectura

```
store/src/main/kotlin/dev/krelzox/store/
  model/      Catalog, CatalogApp, ApkVersion, RepoConfig
  index/      IndexParser (JSON en streaming) · IndexVerifier (firma JAR + huella fijada)
  domain/     Compatibility, VersionSelector, AppStatusResolver, Search  (lógica pura, con tests)
  net/        Http: solo HTTPS, redirecciones solo a HTTPS, descarga verificada (tamaño + SHA-256)
  data/       RepoClient (caché + anti-rollback), CatalogRepository, DeviceApps,
              StorePreferences (DataStore), HistoryStore (JSON lines), Codecs
  install/    InstallManager (descarga → verificación → instalador), SystemInstaller
              (sesiones de PackageInstaller), InstallResultReceiver
  ui/         StoreActivity, StoreViewModel, riel de navegación, pantallas, ImageLoader
```

Metadatos y binarios están separados: el índice firmado describe las apps y
cada APK se descarga aparte desde el repositorio y se verifica contra su hash.

## Usar la tienda

1. Instala la tienda: `./gradlew :store:assembleRelease`, firma el APK y
   `adb install -r krelzox-store.apk` (igual que el launcher, ver el README).
2. Ábrela desde la Biblioteca de Krelzox. La primera vez descarga el índice de
   F-Droid (~18 MB) y tarda unos segundos en procesarlo.
3. Al pulsar **Instalar** por primera vez, la ficha pide permiso: **Permitir**
   abre *Instalar apps desconocidas*; activa Krelzox Store y vuelve.
4. Cada instalación y desinstalación se confirma en el diálogo del sistema.

## Añadir aplicaciones y publicar versiones

La tienda no tiene panel web: el catálogo lo gestiona quien mantiene cada
repositorio.

- **En F-Droid:** las apps se proponen en
  [fdroiddata](https://gitlab.com/fdroid/fdroiddata) siguiendo sus normas
  de inclusión; F-Droid compila, firma y publica cada versión.
- **Repositorio propio** con [fdroidserver](https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/):
  1. `fdroid init` crea el repositorio y la clave que firma el índice.
  2. Copia los APK firmados en `repo/` y ejecuta `fdroid update -c`; edita los
     metadatos (`metadata/<paquete>.yml`: nombre, resumen, categorías…) y vuelve a
     ejecutar `fdroid update`.
  3. Publica la carpeta `repo/` por **HTTPS**.
  4. En Krelzox Store › Configuración › **Añadir repositorio**, introduce la
     dirección y la huella SHA-256 de la clave del índice.
  5. Para publicar una versión nueva, añade el APK (mismo paquete, misma clave de
     firma, `versionCode` mayor) y repite `fdroid update`. La tienda la ofrecerá
     como actualización.
  6. Para retirar una versión vulnerable, elimínala del repositorio y vuelve a
     generar el índice; la tienda no ofrece versiones que ya no estén en él.

## Seguridad

| Amenaza | Protección |
|---|---|
| Índice manipulado o de otro origen | Firma JAR verificada + huella fijada por repositorio |
| Índice antiguo servido de nuevo (rollback) | Se rechaza un `timestamp` anterior al aceptado |
| APK alterado en tránsito o en el servidor | SHA-256 y tamaño comparados con el índice antes de instalar |
| APK de otro desarrollador con el mismo nombre | Certificado de firma comparado con el índice y con la app instalada |
| Tráfico en claro | Solo HTTPS (`network_security_config` y comprobación en el cliente) |
| Operaciones privilegiadas desde la interfaz | No existen: instala el `PackageInstaller` del sistema con confirmación del usuario |
| Descargas a medias | Se borran al arrancar y tras cada operación; nunca se instalan |

Riesgos que quedan:

- La tienda confía en las claves fijadas: si un repositorio pierde su clave, hay
  que actualizar la huella a mano.
- F-Droid incluye apps con *anti-features* (publicidad, rastreo, servicios no
  libres); la ficha las muestra, pero no se ocultan.
- Las apps instaladas pueden pedir permisos amplios; la ficha los enumera.

## Pruebas

- **Unitarias** (`./gradlew :store:testDebugUnitTest`, 25 pruebas): lectura del
  índice e idiomas, URL resueltas contra la dirección configurada, firma
  aceptada/rechazada (clave distinta, JAR manipulado, sin firmar), compatibilidad
  por ABI/API/características, selección de versión, estado de instalación,
  actualizaciones solo con la misma firma, búsqueda, descarga verificada
  (hash y tamaño), solo HTTPS y codecs de historial y repositorios. Los JAR de
  prueba están en `store/src/test/resources/`, firmados con una clave de prueba
  desechable que no se versiona.
- **En la Raspberry Pi** (2026-11-12): índice real de F-Droid verificado y
  cargado (4987 apps); búsqueda con el teclado en pantalla; permiso de
  instalación; instalación real de *BlocksTV* (descarga, verificación,
  confirmación del sistema); **Abrir**; desinstalación cancelada y confirmada;
  favoritos; historial; persistencia de la última sección; la tienda aparece en
  la Biblioteca del launcher.
- **No probado en el dispositivo:** una actualización real (requiere una app
  instalada desactualizada) y añadir un repositorio propio. La lógica de
  actualización está cubierta por pruebas unitarias.

## Pendiente

- Valoraciones, reseñas y moderación; cuentas de usuario; panel web de
  administración y estadísticas: requieren un servidor (ADR-006, decisión 4).
- Comprobación de actualizaciones en segundo plano y notificaciones.
- Actualizar varias apps sin confirmación individual (depende de que Android
  permita omitir la confirmación para apps instaladas por la tienda).
- Pagos: no hay sistema comercial.
