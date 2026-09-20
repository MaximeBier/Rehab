package rehab.rules

object Fixtures {
    fun load(name: String, packageName: String): Snapshot {
        val text = Fixtures::class.java.getResource("/captures/raw/$name")?.readText()
            ?: error("Fixture introuvable : $name (voir Task 2 du plan)")
        return if (name.endsWith(".json")) SnapshotJson.decode(text) else UiAutomatorXml.toSnapshot(text, packageName)
    }
}
