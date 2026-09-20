package rehab.rules.catalog

import rehab.domain.model.TargetId
import rehab.rules.KnownScreen
import rehab.rules.Matcher
import rehab.rules.PackageRules
import rehab.rules.TargetRule
import rehab.rules.VersionRange

object TwitterRules {
    const val PACKAGE = "com.twitter.android"
    val HOME = TargetId("TwitterHome")

    // X 12.27 est en Compose : aucun resource-id. La barre du bas est une suite de conteneurs android.view.View ;
    // le conteneur de l'onglet actif porte selected=true sans libellé, et contient un nœud dont le content-desc
    // est le nom de l'onglet (« Accueil », « Explorer », « Grok », « Onglet Notifications », « Messages »).
    private fun bottomTabActive(label: Regex) = Matcher.Within(Matcher.Selected(Matcher.Any), Matcher.ContentDesc(label))

    private val homeTab = bottomTabActive(Regex("^(Accueil|Home)$"))

    val PACKAGE_RULES = PackageRules(
        packageName = PACKAGE,
        testedVersions = VersionRange("12.27", "13.0"),
        targets = listOf(
            TargetRule(id = HOME, screenId = "twitter.home", screenMatchers = listOf(homeTab)),
        ),
        knownScreens = listOf(
            KnownScreen("twitter.search", listOf(bottomTabActive(Regex("^(Explorer|Explore|Rechercher|Search)$")))),
            KnownScreen("twitter.dm", listOf(bottomTabActive(Regex("^Messages$")))),
            KnownScreen("twitter.notifications", listOf(bottomTabActive(Regex("Notifications")))),
            KnownScreen("twitter.grok", listOf(bottomTabActive(Regex("^Grok$")))),
        ),
        // Pas d'id pour la barre : on prend l'icône « Accueil » (haut ≈ 2169 sur 2274 de barre). L'overlay laisse
        // donc visible la partie basse de la barre, qui reste cliquable (les cibles tactiles font 147 px de haut).
        navBarMatcher = Matcher.ContentDesc(Regex("^(Accueil|Home)$")),
        homeTabMatcher = homeTab,
    )
}
