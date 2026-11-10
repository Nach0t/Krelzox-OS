package dev.nexium.launcher.ui.performance

import dev.nexium.launcher.core.data.NetworkDetails
import dev.nexium.launcher.core.data.metrics.SystemMetrics
import kotlinx.coroutines.flow.Flow

/**
 * Shared, lifecycle-aware system data streams. Passed as flows (not values) so each
 * consumer collects them itself and only that component recomposes on a new sample.
 */
data class SystemSources(
    val metrics: Flow<SystemMetrics>,
    val network: Flow<NetworkDetails>,
)
