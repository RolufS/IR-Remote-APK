package de.hotelremote.wizard

import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal

interface WizardData {
    fun codeSetsForBrand(brandId: Long): List<Long>
    fun keys(codeSetId: Long): Map<String, IrSignal>
    fun inputVariants(codeSetId: Long, brandId: Long): List<Map<String, IrSignal>>
    fun scanEntries(): List<ScanEntry>
    fun brandName(brandId: Long): String
}
