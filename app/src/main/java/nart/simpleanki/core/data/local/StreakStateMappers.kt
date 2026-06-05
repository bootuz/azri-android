package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.streak.StreakState

fun StreakStateEntity.toDomain(): StreakState = StreakState(
    freezeTokens = freezeTokens,
    frozenDays = frozenDays.split(",").filter { it.isNotBlank() }.map { it.toLong() }.toSet(),
    freezesAwardedForRun = freezesAwardedForRun,
    lastReconciledDay = lastReconciledDay,
    lastRepairDay = lastRepairDay,
)

fun StreakState.toEntity(lastModified: Long, dirty: Boolean): StreakStateEntity = StreakStateEntity(
    id = "current",
    freezeTokens = freezeTokens,
    frozenDays = frozenDays.sorted().joinToString(","),
    freezesAwardedForRun = freezesAwardedForRun,
    lastReconciledDay = lastReconciledDay,
    lastRepairDay = lastRepairDay,
    lastModified = lastModified,
    dirty = dirty,
)
