# ADR-002 · Interfaz con Jetpack Compose for TV

- **Estado:** aceptado
- **Fecha:** 2026-11-09

## Contexto

Opciones para la UI de un launcher Android TV en Kotlin:

1. **Leanback** (`androidx.leanback`): basado en Views y Fragments.
2. **Jetpack Compose + `androidx.tv:tv-material`.**
3. **Motor propio** (OpenGL/Vulkan directo).

## Decisión

Opción 2.

## Motivos

- Leanback está deprecado por Google; no recibe novedades.
- `tv-material` resuelve foco D-pad, `Carousel`, `ImmersiveList`, superficies
  con escala/borde en foco y márgenes de TV.
- Compose facilita una identidad visual propia mediante tokens, sin luchar con
  estilos de Leanback.
- Un motor propio multiplica el trabajo y los riesgos sin beneficio real a
  1080p60.

## Consecuencias

- Coste de arranque de Compose: se mitiga con Baseline Profile y R8 (etapa 5).
- La GPU V3D penaliza blur y capas grandes: reglas en
  [arquitectura.md](../arquitectura.md#rendimiento-presupuesto).
- Se fijarán versiones exactas en el version catalog en la etapa 1.
