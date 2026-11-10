# Nexium-OS · Estructura propuesta y revisión inicial

> Borrador para revisión. No se ha hecho commit, push ni cambio en la Raspberry Pi.

## 1. Estado del repositorio (revisado)

| Punto | Resultado |
|---|---|
| Remoto `origin` | `https://github.com/Nach0t/Nexium-OS.git` (fetch y push) |
| Conexión a GitHub | OK: `git ls-remote` responde |
| Rama actual | `main`, rastrea `origin/main` |
| Sincronización | Local y remoto en el mismo commit (`71db53c Initial commit`) |
| Archivos versionados | `LICENSE` (Apache 2.0), `README.md` (solo el título) |
| `LICENSE` modificado | Solo cambian los finales de línea (CRLF vs LF); el texto es igual |
| `platform-tools/` | Sin versionar: binarios de Android (adb, fastboot 37.0.1), ~15 MB |
| `.gitignore` / `.gitattributes` | No existían |

## 2. Estructura propuesta

```
Nexium-OS/
├── README.md              # Qué es, estado, cómo empezar
├── LICENSE                # Apache 2.0 (ya existe)
├── CONTRIBUTING.md        # Flujo de ramas, commits, revisión
├── SECURITY.md            # Cómo reportar vulnerabilidades
├── CHANGELOG.md
├── .gitignore
├── .gitattributes
├── docs/
│   ├── arquitectura.md
│   ├── instalacion.md
│   ├── hardware.md        # Raspberry Pi: modelo, periféricos, pinout
│   └── decisiones/        # ADR: una nota por decisión técnica
├── src/                   # Código principal
├── scripts/               # Instalación, despliegue, utilidades
├── config/                # Plantillas SIN secretos (*.example)
├── systemd/               # Unidades de servicio
├── tests/
└── tools/                 # Herramientas auxiliares (documentadas, no binarios)
```

Se crean las carpetas de código solo cuando haya contenido; no hace falta
subir carpetas vacías.

## 3. Archivos sensibles que nunca deben publicarse

- Contraseñas, tokens y claves API: `.env`, `*token*`, `credentials*`.
- Claves y certificados: `*.pem`, `*.key`, `id_rsa*`, `id_ed25519*`, `*.p12`, `*.pfx`.
- Redes: `wpa_supplicant.conf`, cualquier archivo con contraseñas Wi-Fi.
- Bases de contraseñas: `*.kdbx`.
- Datos de la Raspberry Pi: imágenes de tarjeta SD (`*.img`), backups, logs con IP/usuarios.
- Configuración real: se versiona solo `config/*.example` con valores ficticios.

Si alguno llegó a subirse: borrarlo del repo no basta, queda en el historial.
Hay que **rotar la credencial** y limpiar el historial.

## 4. Decisiones pendientes (necesito tu confirmación)

1. `platform-tools/`: lo dejé en `.gitignore`. Conviene moverlo fuera del repo.
2. Normalización de finales de línea (`.gitattributes`): hará que `LICENSE` deje de aparecer modificado.
3. Idioma de la documentación: español (actual) o inglés.
4. Descripción real del proyecto para completar el README.
