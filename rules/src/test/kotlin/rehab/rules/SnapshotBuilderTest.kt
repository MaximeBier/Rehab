package rehab.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeTreeNode(
    override val id: String? = null,
    override val className: String? = null,
    override val text: String? = null,
    override val contentDesc: String? = null,
    override val bounds: Bounds = Bounds(0, 0, 100, 100),
    override val selected: Boolean = false,
    override val visible: Boolean = true,
    private val children: List<FakeTreeNode> = emptyList(),
) : TreeNode {
    override val childCount get() = children.size
    override fun child(index: Int) = children[index]
}

class SnapshotBuilderTest {
    private fun chain(depth: Int): FakeTreeNode =
        if (depth == 0) FakeTreeNode(id = "leaf") else FakeTreeNode(id = "d$depth", children = listOf(chain(depth - 1)))

    @Test fun `parcours pre-ordre avec profondeur`() {
        val root = FakeTreeNode(id = "root", children = listOf(FakeTreeNode(id = "a", children = listOf(FakeTreeNode(id = "a1"))), FakeTreeNode(id = "b")))
        val s = SnapshotBuilder().build(root, "pkg", "1", 0L)
        assertEquals(listOf("root", "a", "a1", "b"), s.nodes.map { it.id })
        assertEquals(listOf(0, 1, 2, 1), s.nodes.map { it.depth })
        assertFalse(s.truncated)
    }

    @Test fun `borne de profondeur`() {
        val s = SnapshotBuilder(maxDepth = 3).build(chain(10), "pkg", "1", 0L)
        assertEquals(4, s.nodes.size) // profondeurs 0..3
        assertTrue(s.truncated)
    }

    @Test fun `borne de nombre de noeuds`() {
        val root = FakeTreeNode(id = "root", children = (1..50).map { FakeTreeNode(id = "c$it") })
        val s = SnapshotBuilder(maxNodes = 10).build(root, "pkg", "1", 0L)
        assertEquals(10, s.nodes.size)
        assertTrue(s.truncated)
    }

    @Test fun `le noeud racine (profondeur 0) survit toujours a la troncature`() {
        // Invariant dont dépend Matcher.NearBottom pour estimer la hauteur d'écran : la racine est toujours
        // ajoutée en premier (nodes.size vaut 0 au moment de sa visite), donc jamais elle-même tronquée,
        // même quand des descendants le sont.
        val root = FakeTreeNode(id = "root", bounds = Bounds(0, 0, 1080, 2400), children = (1..50).map { FakeTreeNode(id = "c$it") })
        val s = SnapshotBuilder(maxNodes = 10).build(root, "pkg", "1", 0L)
        assertTrue(s.truncated)
        assertEquals(0, s.nodes.first().depth)
        assertEquals(Bounds(0, 0, 1080, 2400), s.nodes.first().bounds)
    }

    @Test fun `noeud invisible et son sous-arbre ignores`() {
        val root = FakeTreeNode(id = "root", children = listOf(
            FakeTreeNode(id = "cache", visible = false, children = listOf(FakeTreeNode(id = "cache-enfant"))),
            FakeTreeNode(id = "vu"),
        ))
        val s = SnapshotBuilder().build(root, "pkg", "1", 0L)
        assertEquals(listOf("root", "vu"), s.nodes.map { it.id })
        assertFalse(s.truncated)
    }

    @Test fun `racine gardee meme invisible`() {
        val s = SnapshotBuilder().build(FakeTreeNode(id = "root", visible = false), "pkg", "1", 0L)
        assertEquals(listOf("root"), s.nodes.map { it.id })
    }

    @Test fun `les noeuds invisibles ne consomment pas le budget`() {
        val root = FakeTreeNode(id = "root", children = (1..50).map { FakeTreeNode(id = "h$it", visible = false) } + FakeTreeNode(id = "vu"))
        val s = SnapshotBuilder(maxNodes = 5).build(root, "pkg", "1", 0L)
        assertEquals(listOf("root", "vu"), s.nodes.map { it.id })
        assertFalse(s.truncated)
    }

    @Test fun `snapshot illisible = racine seule`() {
        val s = SnapshotBuilder.unreadable("pkg", "1", 42L)
        assertEquals(1, s.nodes.size)
        assertEquals(0, s.nodes.single().depth)
        assertEquals(42L, s.capturedAt)
    }
}
