package rehab.rules.catalog

import rehab.domain.model.TargetId
import rehab.rules.KnownScreen
import rehab.rules.Matcher
import rehab.rules.PackageRules
import rehab.rules.TargetRule
import rehab.rules.VersionRange

/**
 * v0.6.0 (demande de Maxime, 2026-09-25) : **tout X compte, sauf les DM**. Reconnaître le fil à chaque
 * version de X échouait (barres masquées en défilant, structure Compose changeante) et l'usage cessait alors
 * d'être compté — un échec « ouvert ». On inverse : n'importe quel écran de X est la cible [APP], sauf la liste
 * des messages et une conversation ouverte. Si X change, Rehab compte trop plutôt que rien.
 */
object TwitterRules {
    const val PACKAGE = "com.twitter.android"
    val APP = TargetId("TwitterApp")

    /** Ancienne cible (≤ 0.5.1, fil d'accueil seul) : ne sert plus qu'à nommer l'historique d'usage déjà enregistré. */
    val HOME = TargetId("TwitterHome")

    // X est en Compose. La barre du bas est une suite de conteneurs android.view.View ; le conteneur de l'onglet
    // actif porte selected=true et contient un nœud dont le content-desc est le nom de l'onglet. On borne le
    // conteneur sélectionné à la fraction basse de l'écran pour écarter les sélecteurs internes du haut.
    private fun bottomTabActive(label: Regex) = Matcher.Within(
        outer = Matcher.AllOf(listOf(Matcher.Selected(Matcher.Any), Matcher.NearBottom())),
        inner = Matcher.ContentDesc(label),
    )

    private val homeTab = bottomTabActive(Regex("^(Accueil|Home)$"))

    // DM, relevés sur X 12.28 (captures du 2026-09-25) : liste des conversations (`xchat_conversation_list`,
    // onglet Messages sélectionné quand la barre est visible) et conversation ouverte (`RootDm`,
    // `message_list_v2` — jamais de barre d'onglets dans une conversation).
    private val dmScreen = Matcher.AnyOf(
        listOf(
            bottomTabActive(Regex("^Messages$")),
            Matcher.ViewId("xchat_conversation_list"),
            Matcher.ViewId("RootDm"),
            Matcher.ViewId("message_list_v2"),
        ),
    )

    val PACKAGE_RULES = PackageRules(
        packageName = PACKAGE,
        testedVersions = VersionRange("12.27", "13.0"),
        targets = listOf(
            TargetRule(id = APP, screenId = "twitter.app", screenMatchers = listOf(Matcher.Any), excludeMatchers = listOf(dmScreen)),
        ),
        knownScreens = listOf(KnownScreen("twitter.dm", listOf(dmScreen))),
        // Pas d'id pour la barre : on prend l'icône « Accueil ». L'overlay s'arrête au-dessus : la barre reste
        // visible et cliquable, ce qui permet d'aller dans Messages pendant un blocage.
        navBarMatcher = Matcher.ContentDesc(Regex("^(Accueil|Home)$")),
        homeTabMatcher = homeTab,
    )
}
