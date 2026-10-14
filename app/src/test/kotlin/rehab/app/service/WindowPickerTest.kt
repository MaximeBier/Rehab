package rehab.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowPickerTest {
    private val tracked = setOf("ig", "x")
    private fun w(id: Int, pkg: String?, app: Boolean = true, active: Boolean = false, focused: Boolean = false, area: Long = 100) =
        WindowCandidate(id, app, pkg, active, focused, area)

    @Test fun `fenetre active suivie`() =
        assertEquals(PickedWindow(1, "ig"), WindowPicker.pick(listOf(w(1, "ig", active = true), w(2, "x", area = 999)), tracked, emptyMap()))

    @Test fun `active non suivie, on prend la focalisee suivie`() =
        assertEquals(PickedWindow(2, "x"), WindowPicker.pick(listOf(w(1, "autre", active = true), w(2, "x", focused = true)), tracked, emptyMap()))

    @Test fun `ni active ni focalisee, on prend la plus grande (PiP, ecran partage)`() =
        assertEquals(PickedWindow(3, "x"), WindowPicker.pick(listOf(w(1, "autre", active = true), w(2, "ig", area = 50), w(3, "x", area = 500)), tracked, emptyMap()))

    @Test fun `overlay et fenetres systeme ignores`() =
        assertNull(WindowPicker.pick(listOf(w(1, "ig", app = false, active = true)), tracked, emptyMap()))

    @Test fun `racine illisible d une fenetre deja vue reste l app`() =
        assertEquals(PickedWindow(7, "x"), WindowPicker.pick(listOf(w(7, null, active = true)), tracked, mapOf(7 to "x")))

    @Test fun `racine illisible inconnue ignoree`() =
        assertNull(WindowPicker.pick(listOf(w(7, null, active = true)), tracked, emptyMap()))

    @Test fun `aucune fenetre`() = assertNull(WindowPicker.pick(emptyList(), tracked, emptyMap()))
}
