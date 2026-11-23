package com.drumtuner.app.data

import com.drumtuner.app.tuning.BandKey
import com.drumtuner.app.tuning.DrumAcoustics
import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.tuning.Shell
import com.drumtuner.app.tuning.TuningStyle

/** A saved drum-kit profile (shells + band key + tuning style). */
data class KitProfile(
    val id: String,
    val name: String,
    val shells: List<Shell> = DrumAcoustics.defaultShells(),
    val key: BandKey = BandKey.E_MINOR,
    val style: TuningStyle = TuningStyle.PUNCHY,
) {
    fun shell(drum: DrumType): Shell =
        shells.firstOrNull { it.drum == drum }
            ?: DrumAcoustics.defaultShells().first { it.drum == drum }

    fun withShell(updated: Shell): KitProfile {
        val exists = shells.any { it.drum == updated.drum }
        val list = if (exists) shells.map { if (it.drum == updated.drum) updated else it } else shells + updated
        return copy(shells = list.sortedBy { it.drum.ordinal })
    }

    /** Guarantees one shell per drum slot, in tab order. */
    fun normalized(): KitProfile = copy(shells = DrumType.values().map { shell(it) })
}
