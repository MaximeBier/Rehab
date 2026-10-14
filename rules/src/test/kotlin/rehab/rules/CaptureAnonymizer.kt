package rehab.rules

/**
 * Anonymise une capture avant commit (dépôt public, v0.7.0) : tout `text`/`contentDesc` devient un jeton neutre
 * (`t<i>`/`d<i>`, i = index du nœud), sauf les libellés d'interface dont dépendent les règles ([KEEP]) et les
 * chaînes vides. Identifiants, classes, bounds, sélection et profondeur sont conservés : c'est ce que lisent les règles.
 */
object CaptureAnonymizer {
    val KEEP = Regex(
        "^(Accueil|Home|Messages|Direct|Envoyer un message|Reels|Rechercher|Search|Recherche|Explorer|Explore|Profil|Profile|Notifications|Suivre|Follow|Suggestions?|Pour vous|For you|Abonnements|Following)$",
        RegexOption.IGNORE_CASE,
    )

    fun anonymize(snapshot: Snapshot): Snapshot = snapshot.copy(
        nodes = snapshot.nodes.mapIndexed { i, n ->
            n.copy(text = n.text?.let { mask(it, "t$i") }, contentDesc = n.contentDesc?.let { mask(it, "d$i") })
        },
    )

    private fun mask(value: String, token: String) = if (value.isBlank() || KEEP.matches(value.trim())) value else token
}
