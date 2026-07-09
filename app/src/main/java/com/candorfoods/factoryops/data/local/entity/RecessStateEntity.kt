package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Operator-side state of a production "session" that spans recesses (pauses) for one
 * machine. A session is made of one or more run segments (rows in `production_runs`);
 * this row only remembers what those segments can't: whether the operator is currently
 * on recess, and the active time accumulated from already-finished segments (so the
 * on-screen timer can show continuous elapsed time across pauses). It is NOT synced —
 * recess is operator-local; to others the machine simply shows as not-running during a
 * break. Deleted when the session ends (Stop).
 */
@Entity(tableName = "recess_state")
data class RecessStateEntity(
    @PrimaryKey val machineId: String,        // = assetId
    val operatorId: String,
    val onRecess: Boolean,
    val accumulatedActiveMs: Long,            // sum of finished segments this session (excludes recess)
    val recessStartedAt: Long?,               // when the current recess began (for "On recess Xm")
    val updatedAt: Long,
)
