package de.hotelremote.wizard

import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal

interface WizardData {
    fun codeSetsForBrand(brandId: Long): List<Long>
    fun keys(codeSetId: Long): Map<String, IrSignal>
    fun inputVariants(codeSetId: Long, brandId: Long): List<Map<String, IrSignal>>
    fun scanEntries(): List<ScanEntry>
    fun brandName(brandId: Long): String

    /**
     * Andere Code-Sätze, deren POWER/POWER_OFF dasselbe Signal ist wie das Suchlauf-Signal von [codeSetId]
     * (der Suchlauf sendet jedes Signal nur einmal), nach Verbreitung absteigend.
     */
    fun powerSiblings(codeSetId: Long): List<Long>

    /** Hauptmarke eines Code-Satzes (eigene Marke vor Familienmarke), null ohne Zuordnung. */
    fun topBrand(codeSetId: Long): Long?
}
