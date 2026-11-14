package app.karala.ink

/**
 * Bir sayfa üzerindeki düzenlemeleri geri al / yinele desteğiyle yönetir.
 * Android'e bağımlı değildir; birim testleriyle doğrulanır.
 */
class InkEditor(page: InkPage = InkPage()) {
    var page: InkPage = page
        private set

    private sealed interface Action
    private class Add(val stroke: Stroke) : Action
    /** Silme/parçalama: önceki ve sonraki tam çizgi listesi. */
    private class Replace(val before: List<Stroke>, val after: List<Stroke>) : Action

    private val undo = ArrayDeque<Action>()
    private val redo = ArrayDeque<Action>()
    private var eraseSnapshot: List<Stroke>? = null

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun load(p: InkPage) { page = p; undo.clear(); redo.clear(); eraseSnapshot = null }

    fun add(s: Stroke) {
        page.strokes.add(s); push(Add(s))
    }

    /** Son eklenen çizgiyi (ör. şekil tanıma sonrası) başka biriyle değiştirir; tek geri alma adımı kalır. */
    fun replaceLast(s: Stroke) {
        val last = undo.lastOrNull()
        if (last is Add && page.strokes.lastOrNull() === last.stroke) {
            page.strokes[page.strokes.size - 1] = s
            undo[undo.size - 1] = Add(s)
        } else add(s)
    }

    /** Bir silgi hareketinin başlangıcı. Hareket boyunca yapılan silmeler tek adımda geri alınır. */
    fun beginErase() { eraseSnapshot = page.strokes.toList() }

    /** @return bir şey silindiyse true */
    fun eraseAt(x: Float, y: Float, radius: Float, mode: EraserMode): Boolean {
        var changed = false
        val out = ArrayList<Stroke>(page.strokes.size)
        for (s in page.strokes) {
            if (mode == EraserMode.STROKE) {
                if (Geometry.hits(s, x, y, radius)) changed = true else out.add(s)
            } else {
                val parts = Geometry.erasePartial(s, x, y, radius)
                if (parts == null) out.add(s) else { changed = true; out.addAll(parts) }
            }
        }
        if (changed) { page.strokes.clear(); page.strokes.addAll(out) }
        return changed
    }

    fun endErase() {
        val before = eraseSnapshot ?: return
        eraseSnapshot = null
        val after = page.strokes.toList()
        if (before.size != after.size || before.indices.any { before[it] !== after[it] }) push(Replace(before, after))
    }

    fun clear() {
        if (page.strokes.isEmpty()) return
        val before = page.strokes.toList()
        page.strokes.clear()
        push(Replace(before, emptyList()))
    }

    fun undo(): Boolean {
        val a = undo.removeLastOrNull() ?: return false
        when (a) {
            is Add -> page.strokes.remove(a.stroke)
            is Replace -> { page.strokes.clear(); page.strokes.addAll(a.before) }
        }
        redo.addLast(a); return true
    }

    fun redo(): Boolean {
        val a = redo.removeLastOrNull() ?: return false
        when (a) {
            is Add -> page.strokes.add(a.stroke)
            is Replace -> { page.strokes.clear(); page.strokes.addAll(a.after) }
        }
        undo.addLast(a); return true
    }

    private fun push(a: Action) {
        undo.addLast(a); redo.clear()
        while (undo.size > MAX_HISTORY) undo.removeFirst()
    }

    companion object { const val MAX_HISTORY = 200 }
}
