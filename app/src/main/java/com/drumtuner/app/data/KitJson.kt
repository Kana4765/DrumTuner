package com.drumtuner.app.data

import com.drumtuner.app.tuning.BandKey
import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.tuning.HeadPly
import com.drumtuner.app.tuning.Shell
import com.drumtuner.app.tuning.StarPattern
import com.drumtuner.app.tuning.TuningStyle

/**
 * Versioned JSON format for kit backups:
 * {
 *   "format": "drumtuner-kits", "version": 1,
 *   "kits": [ { "id", "name", "key", "style", "shells": [ { "drum", "diameterIn", ... } ] } ]
 * }
 */
object KitJson {
    const val FORMAT = "drumtuner-kits"
    const val VERSION = 1

    fun encode(kits: List<KitProfile>): String = MiniJson.stringify(
        linkedMapOf(
            "format" to FORMAT,
            "version" to VERSION,
            "kits" to kits.map { kitToMap(it) },
        )
    )

    /** Accepts the wrapper object, a bare array of kits, or a single kit object. */
    fun decode(text: String): List<KitProfile> {
        val root = MiniJson.parse(text)
        val list: List<*> = when (root) {
            is Map<*, *> -> (root["kits"] as? List<*>) ?: listOf(root)
            is List<*> -> root
            else -> throw MiniJson.ParseException("Not a kit file")
        }
        return list.mapNotNull { (it as? Map<*, *>)?.let(::kitFromMap) }
    }

    private fun kitToMap(k: KitProfile): Map<String, Any?> = linkedMapOf(
        "id" to k.id,
        "name" to k.name,
        "key" to k.key.name,
        "style" to k.style.name,
        "shells" to k.shells.map { s ->
            linkedMapOf<String, Any?>(
                "drum" to s.drum.name,
                "diameterIn" to s.diameterIn,
                "depthIn" to s.depthIn,
                "ply" to s.ply.name,
                "lugCount" to s.lugCount,
                "enabled" to s.enabled,
                "nudgeHz" to s.nudgeHz,
            ).apply {
                s.refPitchHz?.let { put("refPitchHz", it) }
                s.refDecayMs?.let { put("refDecayMs", it) }
            }
        },
    )

    private fun kitFromMap(m: Map<*, *>): KitProfile? {
        val id = m["id"] as? String ?: return null
        val name = m["name"] as? String ?: "Kit"
        val key = enumOr(m["key"], BandKey.E_MINOR)
        val style = enumOr(m["style"], TuningStyle.PUNCHY)
        val shells = (m["shells"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::shellFromMap) }
        return KitProfile(id, name, shells, key, style).normalized()
    }

    private fun shellFromMap(m: Map<*, *>): Shell? {
        val drum = enumOrNull<DrumType>(m["drum"]) ?: return null
        val lugs = (m["lugCount"] as? Number)?.toInt()?.takeIf { it in StarPattern.SUPPORTED_LUG_COUNTS } ?: 8
        return Shell(
            drum = drum,
            diameterIn = (m["diameterIn"] as? Number)?.toDouble()?.coerceIn(6.0, 28.0) ?: 12.0,
            depthIn = (m["depthIn"] as? Number)?.toDouble()?.coerceIn(2.0, 26.0) ?: 8.0,
            ply = enumOr(m["ply"], HeadPly.ONE_PLY_10),
            lugCount = lugs,
            enabled = m["enabled"] as? Boolean ?: true,
            nudgeHz = (m["nudgeHz"] as? Number)?.toDouble() ?: 0.0,
            refPitchHz = (m["refPitchHz"] as? Number)?.toDouble(),
            refDecayMs = (m["refDecayMs"] as? Number)?.toDouble(),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrNull(v: Any?): E? =
        (v as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private inline fun <reified E : Enum<E>> enumOr(v: Any?, default: E): E = enumOrNull<E>(v) ?: default
}
