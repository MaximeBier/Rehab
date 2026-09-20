package rehab.rules

import rehab.domain.model.TargetId

/** Une cible : [screenMatchers] doivent tous matcher un nœud ; si [triggerMatchers] est non vide, au moins un doit matcher pour activer la cible. */
data class TargetRule(
    val id: TargetId,
    val screenId: String,
    val screenMatchers: List<Matcher>,
    val triggerMatchers: List<Matcher> = emptyList(),
    val degradedFallback: Boolean = false,
    val priority: Int = 0,
)

/** Écran reconnu mais non ciblé (DM, profil…), pour savoir que les règles fonctionnent encore. */
data class KnownScreen(val screenId: String, val matchers: List<Matcher>)

data class PackageRules(
    val packageName: String,
    val testedVersions: VersionRange,
    val targets: List<TargetRule>,
    val knownScreens: List<KnownScreen> = emptyList(),
    val navBarMatcher: Matcher? = null,
    val homeTabMatcher: Matcher? = null,
)

data class Detection(
    val target: TargetId?,
    val screenId: String?,
    val navBarBounds: Bounds?,
    val unknownScreen: Boolean,
    val homeTabSelected: Boolean,
) {
    companion object {
        val NONE = Detection(null, null, null, unknownScreen = true, homeTabSelected = false)
    }
}
