package rehab.rules

/**
 * Anonymise une capture avant commit (dépôt public) : tout `text`/`contentDesc` devient un jeton neutre
 * (`t<i>`/`d<i>`, i = index du nœud), sauf les libellés d'interface dont dépendent les règles ([KEEP]) et les
 * chaînes vides. Identifiants, classes, bounds, sélection et profondeur sont conservés : c'est ce que lisent les règles.
 *
 * [KEEP] contient aussi les libellés lus par des règles aujourd'hui retirées (« Rechercher et explorer »,
 * « Onglet Notifications », « Grok ») : les captures anonymisées doivent faire passer les tests de chaque version.
 */
object CaptureAnonymizer {
    val KEEP = Regex(
        "^(Accueil|Home|Messages|Direct|Envoyer un message|Reels|Rechercher|Search|Recherche|Explorer|Explore|Profil|Profile|Notifications|Suivre|Follow|Suggestions?|Pour vous|For you|Abonnements|Following|Rechercher et explorer|Onglet Notifications|Grok)$",
        RegexOption.IGNORE_CASE,
    )

    fun anonymize(snapshot: Snapshot): Snapshot = snapshot.copy(
        nodes = snapshot.nodes.mapIndexed { i, n ->
            n.copy(text = n.text?.let { mask(it, "t$i") }, contentDesc = n.contentDesc?.let { mask(it, "d$i") })
        },
    )

    /**
     * Même traitement pour un dump uiautomator, réécrit sur place : seules les valeurs de `text`, `content-desc` et
     * `hint` changent (`t<i>`/`d<i>`/`h<i>`, i = rang du `<node>` dans le document, l'ordre de [SnapshotBuilder]).
     * Le reste du fichier (attributs, ordre, `resource-id`, `bounds`, états) est conservé octet pour octet.
     */
    fun anonymizeXml(xml: String): String {
        var i = -1
        return NODE_TAG.replace(xml) { tag ->
            i++
            ATTR.replace(tag.value) { a ->
                val (space, name, raw) = a.destructured
                val prefix = XML_TOKENS[name] ?: return@replace a.value
                val value = if (keep(unescape(raw))) raw else "$prefix$i"
                "$space$name=\"$value\""
            }
        }
    }

    private val XML_TOKENS = mapOf("text" to "t", "content-desc" to "d", "hint" to "h")
    private val NODE_TAG = Regex("""<node\b(?:[^>"]|"[^"]*")*>""")
    private val ATTR = Regex("""(\s)([\w:.-]+)="([^"]*)"""")
    private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);""")

    private fun unescape(s: String) = ENTITY.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> String(Character.toChars(if (e[1] == 'x') e.substring(2).toInt(16) else e.substring(1).toInt()))
        }
    }

    private fun keep(value: String) = value.isBlank() || KEEP.matches(value.trim())

    private fun mask(value: String, token: String) = if (keep(value)) value else token
}
