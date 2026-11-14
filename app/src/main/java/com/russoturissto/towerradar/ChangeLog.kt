package com.russoturissto.towerradar

/** Bundled release notes always work without connectivity. */
object ChangeLog {
    fun forVersion(version: String): String = when (version) {
        "0.27.0" -> listOf(
            "• Подтверждение установки тестовых APK (Да / Нет).",
            "• Быстрые действия: добавить вышку и узнать высоту центра карты.",
            "• Выбор офлайн-области по масштабу карты; радиусы 10 и 30 км.",
            "• Помощник подключения API-ключей при первом запуске.",
            "• История изменений доступна из настроек."
        ).joinToString("\n")
        else -> "Для этой версии список изменений недоступен."
    }
}
