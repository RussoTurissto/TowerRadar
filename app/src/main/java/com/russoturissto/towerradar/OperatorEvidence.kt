package com.russoturissto.towerradar

/** Avoid guessing an operator from signal technology or a nearest mast. */
object OperatorEvidence {
    data class Result(val operator: String?, val explanation: String)
    fun inspect(site: TowerSite): Result {
        site.operator?.takeIf { it.isNotBlank() }?.let {
            return Result(it, "Оператор указан в источнике")
        }
        val tag = Regex("(?:^| · )Сеть: ([^·]+)").find(site.extraInfo.orEmpty())
            ?.groupValues?.getOrNull(1)?.trim()
        val normalized = TowerData.operator(tag)
        if (normalized != null && normalized in listOf(
            "T2 / Tele2", "МТС", "МегаФон", "Билайн"
        )) return Result(normalized,
            "Предположительно: из сетевого тега OSM (не подтверждено владельцем)")
        return Result(null, "Оператор не определён по доступным открытым данным")
    }
}
