package app.karala

import app.karala.study.Reference
import app.karala.study.Units
import app.karala.ui.streak
import org.junit.Assert.*
import org.junit.Test

class StudyTest {
    private fun cat(name: String) = Units.categories.first { it.name == name }
    private fun conv(c: String, v: Double, from: String, to: String): Double {
        val k = cat(c)
        return Units.convert(k, v, k.units.first { it.symbol == from }, k.units.first { it.symbol == to })
    }

    @Test fun unitConversions() {
        assertEquals(1000.0, conv("Uzunluk", 1.0, "km", "m"), 1e-9)
        assertEquals(2.54, conv("Uzunluk", 1.0, "in", "cm"), 1e-9)
        assertEquals(72.0, conv("Hız", 20.0, "m/s", "km/sa"), 1e-9)
        assertEquals(212.0, conv("Sıcaklık", 100.0, "°C", "°F"), 1e-9)
        assertEquals(-40.0, conv("Sıcaklık", -40.0, "°F", "°C"), 1e-9)
        assertEquals(273.15, conv("Sıcaklık", 0.0, "°C", "K"), 1e-9)
        assertEquals(180.0, conv("Açı", Math.PI, "rad", "°"), 1e-9)
        assertEquals(1.0, conv("Basınç", 101325.0, "Pa", "atm"), 1e-9)
        assertEquals(8.0, conv("Veri", 1.0, "B", "bit"), 1e-9)
        assertEquals(1.0, conv("Enerji", 4184.0, "J", "kcal"), 1e-9)
    }

    @Test fun periodicTableLayout() {
        val e = Reference.elements
        assertEquals(118, e.size)
        assertEquals("Og", e.last().symbol)
        // Her hücrede en fazla bir element olmalı.
        assertEquals(118, e.map { it.row to it.col }.toSet().size)
        assertTrue(e.all { it.col in 0..17 && it.row in 0..8 })
        fun el(s: String) = e.first { it.symbol == s }
        assertEquals(1 to 18, el("Ne").let { it.period - 1 to it.group }.let { 1 to it.second })
        assertEquals(8, el("Fe").group); assertEquals(4, el("Fe").period)
        assertEquals(11, el("Au").group); assertEquals(6, el("Au").period)
        assertEquals(17, el("Cl").group); assertEquals(3, el("Cl").period)
        assertEquals(14, el("Pb").group); assertEquals(6, el("Pb").period)
        assertEquals(4, el("Rf").group); assertEquals(7, el("Rf").period)
        assertEquals(18, el("Og").group)
        assertEquals(0, el("U").group); assertEquals(8, el("U").row)
        assertEquals(Reference.Category.NOBLE, el("He").category)
        assertEquals(Reference.Category.ALKALI, el("Na").category)
        assertEquals(26, el("Fe").z); assertEquals(79, el("Au").z); assertEquals(92, el("U").z); assertEquals(47, el("Ag").z)
    }

    @Test fun formulaBookHasContent() {
        assertEquals(4, Reference.courses.size)
        assertTrue(Reference.courses.all { c -> c.topics.isNotEmpty() && c.topics.all { it.formulas.isNotEmpty() } })
    }

    @Test fun streakCounting() {
        assertEquals(0, streak(emptySet(), 100))
        assertEquals(3, streak(setOf(98L, 99L, 100L), 100))
        assertEquals(2, streak(setOf(98L, 99L), 100))      // bugün henüz çalışılmadı: seri bozulmaz
        assertEquals(0, streak(setOf(97L, 98L), 100))      // dün boş: seri bitti
        assertEquals(1, streak(setOf(100L, 98L), 100))
    }
}
