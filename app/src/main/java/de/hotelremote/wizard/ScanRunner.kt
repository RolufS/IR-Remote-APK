package de.hotelremote.wizard

import de.hotelremote.ir.IrSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Taktgeber des Suchlaufs: sendet das Signal der aktuellen Position, wartet, rückt vor. */
class ScanRunner(
    private val machine: WizardMachine,
    private val send: (IrSignal) -> Unit,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 1200,
) {
    private var job: Job? = null
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun start() {
        if (job?.isActive == true) return
        _running.value = true
        val launched = scope.launch {
            try {
                while (machine.state is WizardState.Scanning) {
                    machine.currentScanSignal()?.let(send)
                    delay(intervalMs)
                    machine.scanAdvance()
                }
            } finally {
                _running.value = false
            }
        }
        job = launched
    }

    fun stop() {
        val j = job ?: return
        job = null
        j.cancel()
        _running.value = false
        if (machine.state is WizardState.Scanning) machine.stopScan()
    }
}
