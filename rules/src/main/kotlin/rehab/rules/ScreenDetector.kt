package rehab.rules

class ScreenDetector(private val catalog: RuleCatalog) {

    fun detect(snapshot: Snapshot, degraded: Boolean = false): Detection {
        val rules = catalog.forPackage(snapshot.packageName) ?: return Detection.NONE
        val nodes = snapshot.nodes
        val navBar = rules.navBarMatcher?.firstMatch(nodes)?.bounds
        val homeTab = rules.homeTabMatcher?.firstMatch(nodes) != null

        for (rule in rules.targets.sortedByDescending { it.priority }) {
            if (!rule.screenMatchers.all { it.firstMatch(nodes) != null }) continue
            val triggered = rule.triggerMatchers.isEmpty() ||
                rule.triggerMatchers.any { it.firstMatch(nodes) != null } ||
                (degraded && rule.degradedFallback)
            return Detection(if (triggered) rule.id else null, rule.screenId, navBar, unknownScreen = false, homeTabSelected = homeTab)
        }

        val known = rules.knownScreens.firstOrNull { k -> k.matchers.all { it.firstMatch(nodes) != null } }
        return Detection(null, known?.screenId, navBar, unknownScreen = known == null, homeTabSelected = homeTab)
    }

    /**
     * Bounds de l'onglet [tab] de l'app du snapshot : premier nœud qui correspond au matcher
     * `PackageRules.redirectTabs[tab]`, à bounds non vides (largeur et hauteur > 0). `null` si le
     * package n'a pas cet onglet ou s'il n'est pas dans l'arbre (ex. barres masquées en défilant sur X).
     */
    fun redirectBounds(snapshot: Snapshot, tab: RedirectTab): Bounds? {
        val matcher = catalog.forPackage(snapshot.packageName)?.redirectTabs?.get(tab) ?: return null
        val nodes = snapshot.nodes
        return nodes.firstOrNull { n ->
            n.bounds.right > n.bounds.left && n.bounds.bottom > n.bounds.top && matcher.matches(n, nodes)
        }?.bounds
    }
}
