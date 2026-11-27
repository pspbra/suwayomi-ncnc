package mihon.core.common

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object FeatureFlags {
    @OptIn(ExperimentalUuidApi::class)
    fun newInstallationId(): String = Uuid.random().toHexDashString()
}
