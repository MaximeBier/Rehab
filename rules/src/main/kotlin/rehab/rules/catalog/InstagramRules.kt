package rehab.rules.catalog

import rehab.domain.model.TargetId
import rehab.rules.KnownScreen
import rehab.rules.Matcher
import rehab.rules.PackageRules
import rehab.rules.TargetRule
import rehab.rules.VersionRange

object InstagramRules {
    const val PACKAGE = "com.instagram.android"

    /** v0.7.0 (demande de Maxime, 2026-10-14) : tout Instagram compte, sauf les DM — comme X depuis 0.6.0. */
    val APP = TargetId("InstagramApp")

    /** Anciennes cibles (≤ 0.6.2) : ne servent plus qu'à nommer l'historique d'usage déjà enregistré. */
    val REELS = TargetId("InstagramReels")
    val SUGGESTED = TargetId("InstagramSuggested")

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

    // DM relevés sur Instagram 447 : onglet `direct_tab` sélectionné (liste des conversations). La conversation
    // ouverte est ajoutée à la tâche 8 du plan v0.7 après relevé sur le Pixel.
    private val dmScreen = Matcher.AnyOf(listOf(
        tabActive("direct_tab"),
        Matcher.Selected(Matcher.ContentDesc(Regex("^(Envoyer un message|Messages|Direct)$"))),
    ))

    val PACKAGE_RULES = PackageRules(
        packageName = PACKAGE,
        testedVersions = VersionRange("447.0", "470.0"),
        targets = listOf(
            TargetRule(id = APP, screenId = "instagram.app", screenMatchers = listOf(Matcher.Any), excludeMatchers = listOf(dmScreen)),
        ),
        knownScreens = listOf(KnownScreen("instagram.dm", listOf(dmScreen))),
        // La barre d'onglets borne l'overlay : elle reste cliquable pour rejoindre les messages pendant un blocage.
        navBarMatcher = Matcher.ViewId("tab_bar"),
        homeTabMatcher = homeTab,
    )
}
