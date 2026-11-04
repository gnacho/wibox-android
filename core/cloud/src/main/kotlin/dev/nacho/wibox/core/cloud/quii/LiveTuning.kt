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
}
