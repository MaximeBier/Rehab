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

    /** Conteneur DM qui occupe l'écran (même garde que X) : un aperçu ou un petit bloc ne fait pas un DM. */
    private fun dmContainer(id: String) = Matcher.AllOf(listOf(Matcher.ViewId(id), Matcher.CoversScreen()))

    // DM : liste des conversations = onglet `direct_tab` sélectionné (relevé sur Instagram 447, inchangé en 456).
    // Conversation ouverte (ModalActivity, sans barre d'onglets) : `thread_view_root` et `message_list` couvrent
    // l'écran — relevé sur Instagram 456.1 le 2026-11-21 (captures v07_ig_dm_*).
    private val dmScreen = Matcher.AnyOf(listOf(
        tabActive("direct_tab"),
        Matcher.Selected(Matcher.ContentDesc(Regex("^(Envoyer un message|Messages|Direct)$"))),
        dmContainer("thread_view_root"),
        dmContainer("message_list"),
    ))

    val PACKAGE_RULES = PackageRules(
        packageName = PACKAGE,
        testedVersions = VersionRange("447.0", "481.0"),
        targets = listOf(
            TargetRule(id = APP, screenId = "instagram.app", screenMatchers = listOf(Matcher.Any), excludeMatchers = listOf(dmScreen)),
        ),
        knownScreens = listOf(KnownScreen("instagram.dm", listOf(dmScreen))),
        // La barre d'onglets borne l'overlay : elle reste cliquable pour rejoindre les messages pendant un blocage.
        // `feed_tab` en repli : même bord haut que la barre si `tab_bar` venait à disparaître (Instagram 451).
        navBarMatcher = Matcher.AnyOf(listOf(Matcher.ViewId("tab_bar"), Matcher.ViewId("feed_tab"))),
        homeTabMatcher = homeTab,
    )
}
