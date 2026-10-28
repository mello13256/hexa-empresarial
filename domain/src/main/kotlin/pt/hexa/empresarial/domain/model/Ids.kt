package pt.hexa.empresarial.domain.model

import java.util.UUID

object Ids {
    fun item(): String = "i" + UUID.randomUUID().toString().replace("-", "").take(12)
    fun group(): String = "g" + UUID.randomUUID().toString().replace("-", "").take(12)
}
