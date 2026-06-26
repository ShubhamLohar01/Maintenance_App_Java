package com.candorfoods.factoryops.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the Set<String> of machine IDs tagged as "OTHERS" by an admin.
 * Operators don't see tagged machines in their Assigned list — they show up
 * under the read-only Others section instead.
 *
 * Storage: plain SharedPreferences (these IDs are not secret). Exposed as a
 * [StateFlow] so screens that observe it (HomeViewModel, AdminHomeViewModel)
 * recompose live when admin tags or untags a machine.
 *
 * Phase 3: swap this for a backend column + PATCH endpoint when ready.
 */
@Singleton
class OthersTagPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _tags = MutableStateFlow(loadFromDisk())
    val tags: StateFlow<Set<String>> = _tags.asStateFlow()

    /** True if the admin has never written to this prefs file (used for first-launch seeding). */
    fun hasNeverBeenSeeded(): Boolean = !prefs.contains(KEY_SEEDED)

    fun markSeeded() = prefs.edit().putBoolean(KEY_SEEDED, true).apply()

    fun tag(machineId: String) {
        val updated = _tags.value + machineId
        persist(updated)
    }

    fun untag(machineId: String) {
        val updated = _tags.value - machineId
        persist(updated)
    }

    fun replaceAll(ids: Set<String>) = persist(ids)

    fun isTagged(machineId: String): Boolean = _tags.value.contains(machineId)

    private fun loadFromDisk(): Set<String> =
        prefs.getStringSet(KEY_TAGGED_IDS, emptySet())?.toSet() ?: emptySet()

    private fun persist(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_TAGGED_IDS, ids).apply()
        _tags.value = ids
    }

    companion object {
        private const val PREFS_NAME      = "others_tag_prefs"
        private const val KEY_TAGGED_IDS  = "tagged_machine_ids"
        private const val KEY_SEEDED      = "seeded_v1"
    }
}
