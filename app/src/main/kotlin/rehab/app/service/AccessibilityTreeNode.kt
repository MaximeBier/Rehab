package rehab.app.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import rehab.rules.Bounds
import rehab.rules.TreeNode

class AccessibilityTreeNode(private val info: AccessibilityNodeInfo) : TreeNode {
    override val id: String? get() = info.viewIdResourceName
    override val className: String? get() = info.className?.toString()
    override val text: String? get() = info.text?.toString()
    override val contentDesc: String? get() = info.contentDescription?.toString()
    override val bounds: Bounds get() {
        val r = Rect()
        info.getBoundsInScreen(r)
        return Bounds(r.left, r.top, r.right, r.bottom)
    }
    override val selected: Boolean get() = info.isSelected
    override val childCount: Int get() = info.childCount
    override fun child(index: Int): TreeNode? = info.getChild(index)?.let(::AccessibilityTreeNode)
    // recycle() : sans effet depuis API 34, on garde le défaut vide.
}
