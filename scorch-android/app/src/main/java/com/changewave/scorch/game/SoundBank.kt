package com.changewave.scorch.game

/**
 * Effetti sonori richiesti dal motore. L'implementazione vera vive nel package audio,
 * cosi' la logica di gioco resta indipendente dalle API Android (e testabile headless).
 */
interface SoundBank {

    /** Colpo di cannone, [power] da 0 a 1000. */
    fun fire(power: Float)

    /** Fischio del proiettile: [verticalSpeed] positiva in discesa. */
    fun whistle(verticalSpeed: Float)

    fun whistleStop()

    /** Esplosione con corpo proporzionato al [radius]. */
    fun explosion(radius: Float)

    /** Terra che frana o palla di terra. */
    fun dirt()

    /** Carro distrutto. */
    fun destroyed()

    /** Carro che tocca terra dopo una caduta, [intensity] da 0 a 1. */
    fun thud(intensity: Float)

    /** Inizio turno. */
    fun turnStart()

    /** Tocco su un comando. */
    fun click()

    /** Acquisto al negozio. */
    fun purchase()

    /** Implementazione muta: usata nei test e finche' l'audio non e' pronto. */
    object Silent : SoundBank {
        override fun fire(power: Float) = Unit
        override fun whistle(verticalSpeed: Float) = Unit
        override fun whistleStop() = Unit
        override fun explosion(radius: Float) = Unit
        override fun dirt() = Unit
        override fun destroyed() = Unit
        override fun thud(intensity: Float) = Unit
        override fun turnStart() = Unit
        override fun click() = Unit
        override fun purchase() = Unit
    }
}
