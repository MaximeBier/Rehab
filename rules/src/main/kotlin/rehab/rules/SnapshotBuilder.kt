package rehab.rules

interface TreeNode {
    val id: String?
    val className: String?
    val text: String?
    val contentDesc: String?
    val bounds: Bounds
    val selected: Boolean
    val childCount: Int
    fun child(index: Int): TreeNode?
    fun recycle() {}
}

/**
 * [maxDepth] par défaut (64) : mesuré sur les 10 captures de `rules/src/test/resources/captures/raw/` (Instagram
 * 447, X 12.27) — la plus profonde (`ig_reels.xml`) atteint la profondeur 32, exactement la valeur qu'utilisait
 * cette borne jusqu'ici (zéro marge : une version d'app légèrement plus profonde retombait dans le bug qu'elle
 * corrige). Portée à 64 pour donner de la marge sans coût réel : les captures font 79 à 202 nœuds pour un
 * [maxNodes] de 400, donc c'est [maxNodes], pas [maxDepth], qui borne le travail effectif. Une ancienne valeur
 * de 12 amputait silencieusement l'arbre bien avant les nœuds ciblés par les règles (ex.
 * `clips_viewer_view_pager` à 19-21, le libellé « Suggestions… » à 21-22, le conteneur `selected` de la barre
 * du bas de X à 15-16), rendant la détection non fonctionnelle en production sans qu'aucun test ne le voie (les
 * tests de `rules` construisaient leur snapshot sans passer par `SnapshotBuilder`, donc sans cette borne — voir
 * `Fixtures`/`UiAutomatorXml`). Ne pas rabaisser cette valeur sans revérifier `SnapshotBuilderCapturesTest`
 * (aucune capture tronquée).
 */
class SnapshotBuilder(private val maxNodes: Int = 400, private val maxDepth: Int = 64) {

    fun build(root: TreeNode, packageName: String, appVersion: String, capturedAt: Long): Snapshot {
        val nodes = ArrayList<Node>(minOf(maxNodes, 128))
        var truncated = false

        fun visit(n: TreeNode, depth: Int) {
            if (nodes.size >= maxNodes) { truncated = true; return }
            nodes += Node(n.id, n.className, n.text, n.contentDesc, n.bounds, n.selected, depth)
            if (depth >= maxDepth) { if (n.childCount > 0) truncated = true; return }
            for (i in 0 until n.childCount) {
                val c = n.child(i) ?: continue
                visit(c, depth + 1)
                c.recycle()
            }
        }

        visit(root, 0)
        return Snapshot(packageName, appVersion, capturedAt, nodes, truncated)
    }
}
