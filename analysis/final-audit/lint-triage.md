# Triaje de lint para la auditoría final

Fuente: `app/build/reports/lint-results-debug.sarif` y `core/cloud/build/reports/lint-results-debug.sarif`, generados con `lintDebug` antes de la modernización. Estado: 0 errores, 38 avisos de app y 1 de core.

## Correcciones funcionales o de mantenimiento

- `PrivateResource`: renombrar el recurso propio `selected` para no sobrescribir un recurso privado de Compose. Actualizar referencias y plural si corresponde.
- `PluralsCandidate`: convertir el conteo seleccionado en plural ES/EN si la UI conserva ese texto.
- `DataExtractionRules`: definir política explícita de backup y transferencia acorde con secretos, sesiones y Keystore. No copiar las políticas inseguras del APK original.
- `TypographyEllipsis`: sustituir tres puntos por el carácter tipográfico de elipsis en recursos propios.
- `IconMissingDensityFolder`: revisar recursos rasterizados y densidad. No duplicar imágenes sin necesidad; preferir vectores propios o `nodpi` cuando sea correcto.
- `UseTomlInstead`: mover dependencias de test declaradas manualmente al catálogo cuando se toque cada módulo.

## Avisos de versiones

Hay avisos para Gradle, AGP, AndroidX, coroutines, serialización y Ktor. No se actualizan automáticamente durante cambios de protocolo o UI. Cada actualización debe ser independiente, con build, tests unitarios, instrumentación y revisión de compatibilidad. Un aviso de versión nueva no demuestra que la versión actual sea insegura o incompatible.

## Criterio de cierre

La revisión final debe volver a ejecutar `lintDebug`. Los avisos restantes deben estar clasificados y no puede afirmarse que un warning es irrelevante sin revisar su ubicación y efecto.
