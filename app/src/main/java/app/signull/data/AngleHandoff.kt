package app.signull.data

import app.signull.core.angle.AngleResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Carries an angle-scan result from the angle finder to the map when the user saves it as a spot. */
class AngleHandoff {
    private val _pending = MutableStateFlow<AngleResult?>(null)
    val pending: StateFlow<AngleResult?> = _pending.asStateFlow()

    fun offer(result: AngleResult) {
        _pending.value = result
    }

    fun consume(): AngleResult? = _pending.value.also { _pending.value = null }

    fun clear() {
        _pending.value = null
    }
}
