package com.drumtuner.app.data

import android.content.Context
import android.util.Log

/** Local, offline kit storage: SharedPreferences holding the same JSON used for backups. */
class KitRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadKits(): List<KitProfile> {
        val json = prefs.getString(KEY_KITS, null) ?: return emptyList()
        return try {
            KitJson.decode(json)
        } catch (e: Exception) {
            Log.w(TAG, "Stored kits unreadable, starting fresh", e)
            emptyList()
        }
    }

    fun activeKitId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun save(kits: List<KitProfile>, activeKitId: String) {
        prefs.edit()
            .putString(KEY_KITS, KitJson.encode(kits))
            .putString(KEY_ACTIVE, activeKitId)
            .apply()
    }

    fun loadThreshold(default: Float): Float = prefs.getFloat(KEY_THRESHOLD, default)

    fun saveThreshold(db: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, db).apply()
    }

    private companion object {
        const val TAG = "KitRepository"
        const val PREFS = "drumtuner"
        const val KEY_KITS = "kits_json"
        const val KEY_ACTIVE = "active_kit_id"
        const val KEY_THRESHOLD = "trigger_threshold_db"
    }
}
