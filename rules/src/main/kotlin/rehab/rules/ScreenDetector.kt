package rehab.rules

class ScreenDetector(private val catalog: RuleCatalog) {

    fun detect(snapshot: Snapshot, degraded: Boolean = false): Detection {
        val rules = catalog.forPackage(snapshot.packageName) ?: return Detection.NONE
        val nodes = snapshot.nodes
        val navBar = rules.navBarMatcher?.firstMatch(nodes)?.bounds
        val homeTab = rules.homeTabMatcher?.firstMatch(nodes) != null

        for (rule in rules.targets.sortedByDescending { it.priority }) {
            if (!rule.screenMatchers.all { it.firstMatch(nodes) != null }) continue
            if (rule.excludeMatchers.any { it.firstMatch(nodes) != null }) continue
            val triggered = rule.triggerMatchers.isEmpty() ||
                rule.triggerMatchers.any { it.firstMatch(nodes) != null } ||
                (degraded && rule.degradedFallback)
            return Detection(if (triggered) rule.id else null, rule.screenId, navBar, unknownScreen = false, homeTabSelected = homeTab)
        }

        val known = rules.knownScreens.firstOrNull { k -> k.matchers.all { it.firstMatch(nodes) != null } }
        return Detection(null, known?.screenId, navBar, unknownScreen = known == null, homeTabSelected = homeTab)
    }
}
