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

class SnapshotBuilder(private val maxNodes: Int = 400, private val maxDepth: Int = 12) {

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
