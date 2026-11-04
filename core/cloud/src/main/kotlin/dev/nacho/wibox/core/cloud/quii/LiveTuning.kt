package dev.nacho.wibox.core.cloud.quii

/**
 * Ajustes del PLAY que se pueden cambiar por intent extras sin recompilar.
 * Sirven para reproducir el camino que usa la app oficial en la previsualizacion.
 */
object LiveTuning {
    /** Valor de &ap= en el target del PLAY. null = no enviar el parametro. */
    @Volatile var apParam: Int? = 2

    /** Anade &inner=1 y marca el byte 0x11 del header. */
    @Volatile var inner: Boolean = false

    /** Si es > 0, tras el PLAY envia una orden transparente de prueba (5 = VERSION_GET). */
    @Volatile var probeOrder: Int = 0

    /** Si no es null, no abre video: consulta ese comando al CGI local y lo muestra. */
    @Volatile var cgiCommand: String? = null

    /**
     * Contrasena del PLAY. 0 = encodeDevicePassword(authCode), 1 = dynamicPassword del token.
     * La app oficial usa el dynamic password para cloudType 1.
     */
    @Volatile var passwordMode: Int = 0

    /**
     * Si es > 0, no crea enlace P2P propio: usa ese puerto local ya mapeado
     * (por ejemplo el que mantiene abierto el SDK de la app oficial).
     */
    @Volatile var fixedPort: Int = 0

    /**
     * Aspectos de color del decoder: 0 = no declarar, 1 = BT709 limited SDR,
     * 2 = BT601 NTSC, 3 = BT601 PAL.
     */
    @Volatile var colorMode: Int = 1
}
