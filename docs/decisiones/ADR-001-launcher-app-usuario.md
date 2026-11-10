# ADR-001 · El launcher se distribuye como app de usuario

- **Estado:** aceptado
- **Fecha:** 2026-11-09

## Contexto

La ROM (LineageOS 24.0 KonstaKANG, `userdebug`, `dev-keys`) incluye Catapult
como launcher privilegiado en `system_ext`. Un launcher propio podría
integrarse en la imagen del sistema o instalarse como app normal.

## Opciones

1. **App de sistema / privilegiada:** requiere remontar `/system` con root o
   recompilar la ROM; acceso a permisos privilegiados.
2. **App de usuario** con `HOME` + `LEANBACK_LAUNCHER`, activada mediante el
   rol `android.app.role.HOME`.

## Decisión

Opción 2.

## Consecuencias

- ✅ Sin root, sin reflashear, sin tocar particiones del sistema.
- ✅ Reversible con un comando ([recuperacion.md](../recuperacion.md)).
- ✅ Catapult queda como respaldo automático si Nexium falla.
- ✅ Sobrevive a actualizaciones OTA de Lineage.
- ⚠️ Sin permisos privilegiados: no se puede, por ejemplo, ocultar apps del
  sistema globalmente ni controlar la barra del sistema. Se acepta.
- ⚠️ El usuario (o ADB) debe elegir Nexium como app de inicio una vez.
