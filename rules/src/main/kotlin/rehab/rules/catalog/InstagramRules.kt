package rehab.rules.catalog

import rehab.domain.model.TargetId
import rehab.rules.KnownScreen
import rehab.rules.Matcher
import rehab.rules.PackageRules
import rehab.rules.RedirectTab
import rehab.rules.TargetRule
import rehab.rules.VersionRange

object InstagramRules {
    const val PACKAGE = "com.instagram.android"
    val REELS = TargetId("InstagramReels")
    val SUGGESTED = TargetId("InstagramSuggested")

    // Observé sur Instagram 447 (fr-FR) : « Suggestions » dans secondary_label, « Suggestion Reel de … » en content-desc.
    private val suggestedLabel = Regex(
        "^Suggestions?\\b|Sugg[ée]r[ée]e?s? pour vous|Publications? sugg[ée]r[ée]es?|Suggested for you|Suggested posts?",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Onglet actif : sur Instagram 447, l'onglet (feed_tab…) est parfois selected lui-même (DM, recherche, profil),
     * parfois seulement son icône enfant tab_icon (accueil). On accepte les deux formes.
     */
    private fun tabActive(tabId: String) = Matcher.AnyOf(listOf(
        Matcher.Selected(Matcher.ViewId(tabId)),
        Matcher.Within(Matcher.ViewId(tabId), Matcher.Selected(Matcher.Any)),
    ))

    private val homeTab = Matcher.AnyOf(listOf(
        tabActive("feed_tab"),
        Matcher.Selected(Matcher.ContentDesc(Regex("^(Accueil|Home)$"))),
    ))

    val PACKAGE_RULES = PackageRules(
        packageName = PACKAGE,
        testedVersions = VersionRange("447.0", "470.0"),
        targets = listOf(
            TargetRule(
                id = REELS,
                screenId = "instagram.reels",
                priority = 10,
                screenMatchers = listOf(Matcher.AnyOf(listOf(
                    Matcher.ViewId("clips_viewer_view_pager"),
                    Matcher.ViewId("clips_viewer_container"),
                    Matcher.ViewId("root_clips_layout"),
                    tabActive("clips_tab"),
                    Matcher.Selected(Matcher.ContentDesc(Regex("^Reels$"))),
                ))),
            ),
            TargetRule(
                id = SUGGESTED,
                screenId = "instagram.home",
                priority = 5,
                screenMatchers = listOf(homeTab),
                triggerMatchers = listOf(
                    Matcher.Text(suggestedLabel),
                    Matcher.ContentDesc(suggestedLabel),
                    // Bouton « Suivre » dans l'en-tête d'un post : on ne suit pas l'auteur, donc post suggéré.
                    Matcher.ViewId("inline_follow_button"),
                ),
                degradedFallback = true,
            ),
        ),
        knownScreens = listOf(
            KnownScreen("instagram.search", listOf(Matcher.AnyOf(listOf(
                tabActive("search_tab"), Matcher.Selected(Matcher.ContentDesc(Regex("^(Rechercher|Search)"))))))),
            KnownScreen("instagram.profile", listOf(Matcher.AnyOf(listOf(
                tabActive("profile_tab"), Matcher.Selected(Matcher.ContentDesc(Regex("^Profil"))))))),
            KnownScreen("instagram.dm", listOf(Matcher.AnyOf(listOf(
                tabActive("direct_tab"), Matcher.Selected(Matcher.ContentDesc(Regex("^(Envoyer un message|Messages|Direct)$"))))))),
        ),
        navBarMatcher = Matcher.ViewId("tab_bar"),
        homeTabMatcher = homeTab,
        // Onglets de la barre du bas (Instagram 447, fr-FR) : direct_tab [432,2148][648,2274], search_tab à sa droite.
        redirectTabs = mapOf(
            RedirectTab.Messages to Matcher.ViewId("direct_tab"),
            RedirectTab.Search to Matcher.ViewId("search_tab"),
        ),
    )
}
