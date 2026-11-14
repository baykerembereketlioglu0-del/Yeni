package app.karala.study

/** Birim çevirici. Her birim, kategorisinin temel birimine çarpanla bağlanır; sıcaklık özel hesaplanır. */
object Units {
    class Unit(val name: String, val symbol: String, val factor: Double)
    class Category(val name: String, val units: List<Unit>)

    val TEMPERATURE = Category("Sıcaklık", listOf(Unit("Santigrat", "°C", 1.0), Unit("Fahrenhayt", "°F", 1.0), Unit("Kelvin", "K", 1.0)))

    val categories = listOf(
        Category("Uzunluk", listOf(
            Unit("Milimetre", "mm", 0.001), Unit("Santimetre", "cm", 0.01), Unit("Metre", "m", 1.0), Unit("Kilometre", "km", 1000.0),
            Unit("İnç", "in", 0.0254), Unit("Fit", "ft", 0.3048), Unit("Mil", "mi", 1609.344), Unit("Nanometre", "nm", 1e-9), Unit("Işık yılı", "ly", 9.4607304725808e15))),
        Category("Kütle", listOf(
            Unit("Miligram", "mg", 1e-6), Unit("Gram", "g", 0.001), Unit("Kilogram", "kg", 1.0), Unit("Ton", "t", 1000.0),
            Unit("Ons", "oz", 0.028349523125), Unit("Libre", "lb", 0.45359237))),
        Category("Zaman", listOf(
            Unit("Saniye", "sn", 1.0), Unit("Dakika", "dk", 60.0), Unit("Saat", "sa", 3600.0), Unit("Gün", "gün", 86400.0),
            Unit("Hafta", "hf", 604800.0), Unit("Yıl (365 gün)", "yıl", 31536000.0))),
        TEMPERATURE,
        Category("Hız", listOf(
            Unit("Metre/saniye", "m/s", 1.0), Unit("Kilometre/saat", "km/sa", 1 / 3.6), Unit("Mil/saat", "mph", 0.44704), Unit("Knot", "kn", 0.514444444444))),
        Category("Alan", listOf(
            Unit("Santimetrekare", "cm²", 1e-4), Unit("Metrekare", "m²", 1.0), Unit("Dönüm", "dönüm", 1000.0), Unit("Hektar", "ha", 10000.0), Unit("Kilometrekare", "km²", 1e6))),
        Category("Hacim", listOf(
            Unit("Mililitre", "mL", 1e-6), Unit("Santimetreküp", "cm³", 1e-6), Unit("Litre", "L", 0.001), Unit("Metreküp", "m³", 1.0))),
        Category("Enerji", listOf(
            Unit("Joule", "J", 1.0), Unit("Kilojoule", "kJ", 1000.0), Unit("Kalori", "cal", 4.184), Unit("Kilokalori", "kcal", 4184.0),
            Unit("Kilovatsaat", "kWh", 3.6e6), Unit("Elektronvolt", "eV", 1.602176634e-19))),
        Category("Basınç", listOf(
            Unit("Pascal", "Pa", 1.0), Unit("Kilopascal", "kPa", 1000.0), Unit("Bar", "bar", 1e5), Unit("Atmosfer", "atm", 101325.0), Unit("mmHg", "mmHg", 133.322387415))),
        Category("Açı", listOf(Unit("Derece", "°", 1.0), Unit("Radyan", "rad", 180.0 / Math.PI), Unit("Grad", "grad", 0.9))),
        Category("Veri", listOf(
            Unit("Bit", "bit", 0.125), Unit("Bayt", "B", 1.0), Unit("Kilobayt", "KB", 1024.0), Unit("Megabayt", "MB", 1048576.0),
            Unit("Gigabayt", "GB", 1073741824.0), Unit("Terabayt", "TB", 1099511627776.0))),
    )

    fun convert(c: Category, value: Double, from: Unit, to: Unit): Double {
        if (c === TEMPERATURE) {
            val celsius = when (from.symbol) { "°F" -> (value - 32) * 5 / 9; "K" -> value - 273.15; else -> value }
            return when (to.symbol) { "°F" -> celsius * 9 / 5 + 32; "K" -> celsius + 273.15; else -> celsius }
        }
        return value * from.factor / to.factor
    }
}
