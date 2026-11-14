package app.karala

import app.karala.ink.*
import app.karala.study.Expr
import app.karala.study.Srs
import app.karala.study.net
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private fun pen(vararg p: Float) = Stroke(ToolType.PEN, -1, 4f, p)

    @Test fun undoRedoAddAndClear() {
        val e = InkEditor()
        e.add(pen(0f, 0f, 10f, 10f)); e.add(pen(20f, 20f, 30f, 30f))
        assertEquals(2, e.page.strokes.size)
        e.clear(); assertTrue(e.page.isEmpty)
        assertTrue(e.undo()); assertEquals(2, e.page.strokes.size)
        assertTrue(e.undo()); assertEquals(1, e.page.strokes.size)
        assertTrue(e.redo()); assertEquals(2, e.page.strokes.size)
        assertTrue(e.redo()); assertTrue(e.page.isEmpty)
        assertFalse(e.redo())
    }

    @Test fun newActionClearsRedo() {
        val e = InkEditor()
        e.add(pen(0f, 0f, 1f, 1f)); e.undo(); e.add(pen(5f, 5f, 6f, 6f))
        assertFalse(e.canRedo)
    }

    @Test fun strokeEraserRemovesWholeStrokeAsOneUndoStep() {
        val e = InkEditor()
        e.add(pen(0f, 0f, 100f, 0f)); e.add(pen(0f, 50f, 100f, 50f))
        e.beginErase()
        assertTrue(e.eraseAt(50f, 1f, 5f, EraserMode.STROKE))
        assertFalse(e.eraseAt(50f, 25f, 5f, EraserMode.STROKE))
        assertTrue(e.eraseAt(50f, 49f, 5f, EraserMode.STROKE))
        e.endErase()
        assertTrue(e.page.isEmpty)
        e.undo(); assertEquals(2, e.page.strokes.size)
    }

    @Test fun partialEraserSplitsStroke() {
        val e = InkEditor()
        e.add(pen(0f, 0f, 100f, 0f))
        e.beginErase(); e.eraseAt(50f, 0f, 6f, EraserMode.PARTIAL); e.endErase()
        assertEquals(2, e.page.strokes.size)
        val (a, b) = e.page.strokes
        assertTrue(a.x(a.size - 1) < 45f); assertTrue(b.x(0) > 55f)
        e.undo(); assertEquals(1, e.page.strokes.size); assertEquals(100f, e.page.strokes[0].x(1), 0f)
    }

    @Test fun eraserWithoutHitAddsNoUndoStep() {
        val e = InkEditor(); e.add(pen(0f, 0f, 10f, 0f))
        e.beginErase(); e.eraseAt(500f, 500f, 5f, EraserMode.STROKE); e.endErase()
        e.undo(); assertTrue(e.page.isEmpty)
    }

    @Test fun shapeHitTestUsesOutlineNotInterior() {
        val rect = Stroke(ToolType.RECT, -1, 4f, floatArrayOf(0f, 0f, 100f, 100f))
        assertFalse(Geometry.hits(rect, 50f, 50f, 5f))
        assertTrue(Geometry.hits(rect, 50f, 0f, 5f))
        val ell = Stroke(ToolType.ELLIPSE, -1, 4f, floatArrayOf(0f, 0f, 100f, 100f))
        assertFalse(Geometry.hits(ell, 50f, 50f, 5f))
        assertTrue(Geometry.hits(ell, 100f, 50f, 5f))
    }

    @Test fun recognizeLineRectEllipse() {
        val wobbly = pen(0f, 0f, 50f, 2f, 100f, -1f, 150f, 3f, 200f, 1f)
        assertEquals(ToolType.LINE, Geometry.recognize(wobbly)!!.tool)
        assertEquals(0f, Geometry.recognize(wobbly)!!.y(1), 0f) // yataya hizalandı

        val rect = pen(0f, 0f, 100f, 2f, 198f, 0f, 200f, 50f, 199f, 100f, 100f, 101f, 2f, 99f, 0f, 50f, 1f, 3f)
        assertEquals(ToolType.RECT, Geometry.recognize(rect)!!.tool)

        val n = 40
        val circle = FloatArray(n * 2) { i ->
            val a = (i / 2) * (2 * Math.PI / n)
            if (i % 2 == 0) (100 + 80 * Math.cos(a)).toFloat() else (100 + 80 * Math.sin(a)).toFloat()
        }
        assertEquals(ToolType.ELLIPSE, Geometry.recognize(pen(*circle))!!.tool)

        val zigzag = pen(0f, 0f, 50f, 80f, 100f, 0f, 150f, 80f, 200f, 0f)
        assertNull(Geometry.recognize(zigzag))
    }

    @Test fun jsonRoundTrip() {
        val p = InkPage(1080, 1920, background = PageBackground.GRID)
        p.strokes.add(Stroke(ToolType.PEN, 0xFFFF0000.toInt(), 6.5f, floatArrayOf(1.26f, 2f, 3f, 4f), floatArrayOf(0.5f, 1f)))
        p.strokes.add(Stroke(ToolType.ARROW, -16711936, 3f, floatArrayOf(0f, 0f, 9f, 9f)))
        p.strokes.add(Stroke(ToolType.LASER, -1, 3f, floatArrayOf(0f, 0f, 9f, 9f)))
        val q = InkJson.decode(InkJson.encode(p))
        assertEquals(1080, q.width); assertEquals(PageBackground.GRID, q.background)
        assertEquals(2, q.strokes.size) // lazer kaydedilmez
        assertEquals(1.3f, q.strokes[0].x(0), 0.001f)
        assertEquals(0.5f, q.strokes[0].pressures!![0], 0.001f)
        assertEquals(ToolType.ARROW, q.strokes[1].tool)
        val (pages, cur) = InkJson.decodePages(InkJson.encodePages(listOf(p, InkPage()), 1))
        assertEquals(2, pages.size); assertEquals(1, cur)
    }

    @Test fun srsSchedule() {
        var s = Srs.State()
        var r = Srs.review(s, Srs.Grade.GOOD, 0); assertEquals(1, r.state.intervalDays); s = r.state
        r = Srs.review(s, Srs.Grade.GOOD, 0); assertEquals(3, r.state.intervalDays); s = r.state
        r = Srs.review(s, Srs.Grade.GOOD, 0); assertEquals(8, r.state.intervalDays); s = r.state
        assertEquals(8 * Srs.DAY_MS, r.dueAt)
        r = Srs.review(s, Srs.Grade.AGAIN, 1000)
        assertEquals(0, r.state.reps); assertEquals(1, r.state.lapses); assertEquals(1000 + 600_000L, r.dueAt)
        assertTrue(r.state.ease < 2.5f)
        repeat(20) { s = Srs.review(s, Srs.Grade.AGAIN, 0).state }
        assertEquals(1.3f, s.ease, 0.0001f)
    }

    @Test fun netCalculation() {
        assertEquals(27.5f, net(30, 10, 4), 0.0001f)
        assertEquals(26.667f, net(30, 10, 3), 0.001f)
        assertEquals(30f, net(30, 10, 0), 0f)
    }

    private fun ev(s: String, deg: Boolean = true) = Expr.evaluate(s, deg)

    @Test fun calculator() {
        assertEquals(14.0, ev("2+3*4"), 1e-9)
        assertEquals(20.0, ev("(2+3)×4"), 1e-9)
        assertEquals(512.0, ev("2^3^2"), 1e-9)
        assertEquals(-4.0, ev("-2^2"), 1e-9)
        assertEquals(0.5, ev("sin(30)"), 1e-9)
        assertEquals(1.0, ev("sin(π/2)", deg = false), 1e-9)
        assertEquals(120.0, ev("5!"), 1e-9)
        assertEquals(0.5, ev("50%"), 1e-9)
        assertEquals(2 * Math.PI, ev("2π"), 1e-9)
        assertEquals(15.0, ev("3(4+1)"), 1e-9)
        assertEquals(3.0, ev("√9"), 1e-9)
        assertEquals(2.5, ev("2,5"), 1e-9)
        assertEquals(3.0, ev("log(1000)"), 1e-9)
        assertEquals(7.0, ev("(3+4"), 1e-9)
        assertEquals(1500.0, ev("1.5E3"), 1e-9)
        assertEquals(Math.E, ev("e"), 1e-9)
    }

    @Test fun calculatorErrors() {
        for (bad in listOf("1/0", "2+", "tan(90)", "sqrt(-1)", "foo(2)", "2 2 +", "(-1)!")) {
            try { ev(bad); fail("should throw: $bad") } catch (_: Expr.Error) {}
        }
    }

    @Test fun calculatorFormat() {
        assertEquals("0.3", Expr.format(0.1 + 0.2))
        assertEquals("14", Expr.format(14.0))
        assertEquals("-2.5", Expr.format(-2.5))
        assertEquals("1E15", Expr.format(1e15))
    }
}
