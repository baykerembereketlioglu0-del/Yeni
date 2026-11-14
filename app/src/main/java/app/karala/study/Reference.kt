package app.karala.study

/** Formül kitapçığı ve periyodik tablo verileri. */
object Reference {
    class Formula(val title: String, val body: String)
    class Topic(val name: String, val formulas: List<Formula>)
    class Course(val name: String, val topics: List<Topic>)

    private fun f(title: String, body: String) = Formula(title, body)

    val courses = listOf(
        Course("Matematik", listOf(
            Topic("Özdeşlikler", listOf(
                f("Tam kare", "(a ± b)² = a² ± 2ab + b²"),
                f("İki kare farkı", "a² − b² = (a − b)(a + b)"),
                f("Küp açılımı", "(a ± b)³ = a³ ± 3a²b + 3ab² ± b³"),
                f("Küp toplamı / farkı", "a³ + b³ = (a + b)(a² − ab + b²)\na³ − b³ = (a − b)(a² + ab + b²)"),
                f("Üç terimli kare", "(a + b + c)² = a² + b² + c² + 2(ab + ac + bc)"),
            )),
            Topic("Üslü ve köklü sayılar", listOf(
                f("Üs kuralları", "aᵐ · aⁿ = aᵐ⁺ⁿ\naᵐ / aⁿ = aᵐ⁻ⁿ\n(aᵐ)ⁿ = aᵐⁿ\na⁻ⁿ = 1 / aⁿ\na⁰ = 1 (a ≠ 0)"),
                f("Kök kuralları", "ⁿ√(aᵐ) = a^(m/n)\n√a · √b = √(ab)\n√a / √b = √(a/b)\n√(a²) = |a|"),
                f("Paydayı rasyonel yapma", "1 / (√a − √b) = (√a + √b) / (a − b)"),
            )),
            Topic("Denklemler", listOf(
                f("İkinci derece kökleri", "ax² + bx + c = 0\nΔ = b² − 4ac\nx₁,₂ = (−b ± √Δ) / 2a"),
                f("Kökler toplamı ve çarpımı", "x₁ + x₂ = −b / a\nx₁ · x₂ = c / a"),
                f("Parabolün tepe noktası", "r = −b / 2a,  k = f(r) = (4ac − b²) / 4a"),
                f("Mutlak değer", "|x| < a  ⇔  −a < x < a\n|x| > a  ⇔  x < −a veya x > a"),
            )),
            Topic("Logaritma", listOf(
                f("Tanım", "logₐ b = c  ⇔  aᶜ = b   (a > 0, a ≠ 1, b > 0)"),
                f("Kurallar", "logₐ(xy) = logₐ x + logₐ y\nlogₐ(x/y) = logₐ x − logₐ y\nlogₐ(xⁿ) = n · logₐ x"),
                f("Taban değiştirme", "logₐ b = log_c b / log_c a = 1 / log_b a"),
            )),
            Topic("Trigonometri", listOf(
                f("Temel özdeşlikler", "sin²x + cos²x = 1\ntan x = sin x / cos x\n1 + tan²x = 1 / cos²x"),
                f("Toplam – fark", "sin(a ± b) = sin a cos b ± cos a sin b\ncos(a ± b) = cos a cos b ∓ sin a sin b\ntan(a ± b) = (tan a ± tan b) / (1 ∓ tan a tan b)"),
                f("Yarım açı", "sin 2a = 2 sin a cos a\ncos 2a = cos²a − sin²a = 2cos²a − 1 = 1 − 2sin²a\ntan 2a = 2 tan a / (1 − tan²a)"),
                f("Özel açılar", "sin 30° = 1/2   cos 30° = √3/2   tan 30° = √3/3\nsin 45° = √2/2  cos 45° = √2/2   tan 45° = 1\nsin 60° = √3/2  cos 60° = 1/2    tan 60° = √3"),
                f("Sinüs ve kosinüs teoremi", "a / sin A = b / sin B = c / sin C = 2R\na² = b² + c² − 2bc · cos A"),
            )),
            Topic("Diziler ve toplamlar", listOf(
                f("Aritmetik dizi", "aₙ = a₁ + (n − 1)d\nSₙ = n(a₁ + aₙ) / 2"),
                f("Geometrik dizi", "aₙ = a₁ · rⁿ⁻¹\nSₙ = a₁(1 − rⁿ) / (1 − r)   (r ≠ 1)"),
                f("Ardışık toplamlar", "1 + 2 + … + n = n(n + 1) / 2\n1² + 2² + … + n² = n(n + 1)(2n + 1) / 6\n1 + 3 + … + (2n − 1) = n²"),
            )),
            Topic("Sayma ve olasılık", listOf(
                f("Permütasyon", "P(n, r) = n! / (n − r)!"),
                f("Kombinasyon", "C(n, r) = n! / (r! (n − r)!)"),
                f("Binom açılımı", "(a + b)ⁿ = Σ C(n, k) aⁿ⁻ᵏ bᵏ   (k = 0 … n)"),
                f("Olasılık", "P(A) = istenen durum / tüm durum\nP(A ∪ B) = P(A) + P(B) − P(A ∩ B)\nBağımsız olaylar: P(A ∩ B) = P(A) · P(B)"),
            )),
            Topic("Limit, türev, integral", listOf(
                f("Türev tanımı", "f′(x) = lim (h→0) [f(x + h) − f(x)] / h"),
                f("Türev kuralları", "(xⁿ)′ = n xⁿ⁻¹\n(f · g)′ = f′g + fg′\n(f / g)′ = (f′g − fg′) / g²\n(f(g(x)))′ = f′(g(x)) · g′(x)"),
                f("Özel türevler", "(sin x)′ = cos x\n(cos x)′ = −sin x\n(tan x)′ = 1 + tan²x\n(eˣ)′ = eˣ\n(ln x)′ = 1 / x"),
                f("İntegral", "∫ xⁿ dx = xⁿ⁺¹ / (n + 1) + C   (n ≠ −1)\n∫ 1/x dx = ln|x| + C\n∫ eˣ dx = eˣ + C\n∫ sin x dx = −cos x + C\n∫ cos x dx = sin x + C"),
                f("Belirli integral", "∫ₐᵇ f(x) dx = F(b) − F(a)"),
            )),
        )),
        Course("Geometri", listOf(
            Topic("Üçgenler", listOf(
                f("Alan", "A = (taban · yükseklik) / 2\nA = (1/2) · a · b · sin C\nA = √(u(u−a)(u−b)(u−c)),  u = (a + b + c) / 2"),
                f("Pisagor", "Dik üçgende: a² + b² = c²\nÖzel üçgenler: 3-4-5, 5-12-13, 8-15-17, 7-24-25"),
                f("Öklid", "h² = p · k\nb² = k · a,  c² = p · a"),
                f("Özel dik üçgenler", "30°-60°-90°: kenarlar a, a√3, 2a\n45°-45°-90°: kenarlar a, a, a√2"),
                f("Açıortay ve kenarortay", "İç açıortay: |AB| / |AC| = |BN| / |NC|\nKenarortay: 2Vₐ² = b² + c² − a²/2"),
            )),
            Topic("Dörtgenler ve çokgenler", listOf(
                f("Alanlar", "Kare: a²\nDikdörtgen: a · b\nParalelkenar: taban · yükseklik\nYamuk: (a + c) · h / 2\nEşkenar dörtgen / deltoid: (e · f) / 2"),
                f("Çokgen", "İç açılar toplamı: (n − 2) · 180°\nKöşegen sayısı: n(n − 3) / 2\nDüzgün çokgenin bir dış açısı: 360° / n"),
            )),
            Topic("Çember ve daire", listOf(
                f("Çevre ve alan", "Çevre = 2πr\nAlan = πr²"),
                f("Yay ve dilim", "Yay uzunluğu = 2πr · (α / 360°)\nDilim alanı = πr² · (α / 360°)"),
                f("Açılar", "Merkez açı = gördüğü yay\nÇevre açı = gördüğü yayın yarısı\nÇapı gören çevre açı = 90°"),
                f("Kuvvet", "Aynı noktadan çizilen teğetler eşittir.\n|PA| · |PB| = |PC| · |PD| = |PT|²"),
            )),
            Topic("Katı cisimler", listOf(
                f("Prizma ve silindir", "Hacim = taban alanı · yükseklik\nSilindir: V = πr²h,  yanal alan = 2πrh"),
                f("Piramit ve koni", "Hacim = (taban alanı · yükseklik) / 3\nKoni: V = πr²h / 3,  yanal alan = πr·l"),
                f("Küre", "Hacim = (4/3)πr³\nYüzey alanı = 4πr²"),
                f("Küp ve dikdörtgenler prizması", "Küp: V = a³, yüzey = 6a², cisim köşegeni = a√3\nPrizma: V = abc, köşegen = √(a² + b² + c²)"),
            )),
            Topic("Analitik geometri", listOf(
                f("İki nokta arası uzaklık", "d = √((x₂ − x₁)² + (y₂ − y₁)²)"),
                f("Orta nokta", "M = ((x₁ + x₂)/2, (y₁ + y₂)/2)"),
                f("Eğim ve doğru denklemi", "m = (y₂ − y₁) / (x₂ − x₁) = tan α\ny − y₁ = m(x − x₁)\nParalel: m₁ = m₂,  dik: m₁ · m₂ = −1"),
                f("Noktanın doğruya uzaklığı", "d = |ax₀ + by₀ + c| / √(a² + b²)"),
                f("Çember denklemi", "(x − a)² + (y − b)² = r²"),
            )),
        )),
        Course("Fizik", listOf(
            Topic("Hareket", listOf(
                f("Sabit hızlı hareket", "x = v · t"),
                f("Sabit ivmeli hareket", "v = v₀ + a·t\nx = v₀·t + (1/2)·a·t²\nv² = v₀² + 2·a·x"),
                f("Serbest düşme", "h = (1/2)·g·t²\nv = g·t\nv² = 2·g·h"),
                f("Yatay atış", "x = v₀ · t\nh = (1/2)·g·t²"),
            )),
            Topic("Kuvvet ve enerji", listOf(
                f("Newton'un 2. yasası", "F = m · a"),
                f("Ağırlık ve sürtünme", "G = m · g\nFₛ = k · N"),
                f("İş, güç", "W = F · x · cos α\nP = W / t = F · v"),
                f("Enerji", "Eₖ = (1/2)·m·v²\nEₚ = m·g·h\nYay: E = (1/2)·k·x²"),
                f("Momentum ve itme", "p = m · v\nİtme = F · Δt = Δp"),
                f("Tork", "τ = F · d · sin α"),
            )),
            Topic("Madde ve ısı", listOf(
                f("Özkütle", "d = m / V"),
                f("Basınç", "Katı: P = F / A\nSıvı: P = h · d · g"),
                f("Kaldırma kuvveti", "F = V_batan · d_sıvı · g"),
                f("Isı", "Q = m · c · ΔT\nHal değişimi: Q = m · L"),
                f("Genleşme", "ΔL = L₀ · α · ΔT"),
            )),
            Topic("Elektrik ve manyetizma", listOf(
                f("Coulomb yasası", "F = k · q₁ · q₂ / d²"),
                f("Ohm yasası", "V = I · R\nR = ρ · L / A"),
                f("Direnç bağlama", "Seri: R = R₁ + R₂ + …\nParalel: 1/R = 1/R₁ + 1/R₂ + …"),
                f("Elektriksel güç ve enerji", "P = V · I = I²·R = V² / R\nW = P · t"),
                f("Sığa", "C = q / V\nE = (1/2)·C·V²"),
                f("Manyetik kuvvet", "F = B · I · L · sin α\nF = q · v · B · sin α"),
            )),
            Topic("Dalgalar ve optik", listOf(
                f("Dalga", "v = λ · f\nT = 1 / f"),
                f("Kırılma (Snell)", "n₁ · sin θ₁ = n₂ · sin θ₂\nn = c / v"),
                f("Ayna ve mercek", "1/f = 1/Dc + 1/Dg\nBüyütme = Hg / Hc = Dg / Dc"),
                f("Foton enerjisi", "E = h · f = h · c / λ"),
            )),
            Topic("Çembersel hareket ve kütle çekimi", listOf(
                f("Çembersel hareket", "v = 2πr / T = ω · r\na = v² / r\nF = m·v² / r"),
                f("Kütle çekimi", "F = G · m₁ · m₂ / d²"),
                f("Basit sarkaç ve yay", "T = 2π√(L / g)\nT = 2π√(m / k)"),
            )),
        )),
        Course("Kimya", listOf(
            Topic("Mol kavramı", listOf(
                f("Mol", "n = m / M_A\nn = N / N_A   (N_A = 6,02 × 10²³)\nNŞA'da gaz: n = V / 22,4 L"),
                f("Yüzde bileşim", "Kütlece % = (elementin kütlesi / bileşiğin kütlesi) · 100"),
            )),
            Topic("Gazlar", listOf(
                f("İdeal gaz denklemi", "P · V = n · R · T\nR = 0,082 L·atm/(mol·K) = 8,314 J/(mol·K)"),
                f("Birleşik gaz yasası", "P₁V₁ / T₁ = P₂V₂ / T₂"),
                f("Kısmi basınç (Dalton)", "P_toplam = P₁ + P₂ + …\nPᵢ = Xᵢ · P_toplam"),
                f("Graham difüzyon yasası", "v₁ / v₂ = √(M₂ / M₁)"),
                f("Sıcaklık", "T(K) = t(°C) + 273"),
            )),
            Topic("Çözeltiler", listOf(
                f("Molarite", "M = n / V (L)"),
                f("Molalite", "m = n_çözünen / kg çözücü"),
                f("Seyreltme", "M₁ · V₁ = M₂ · V₂"),
                f("Kütlece yüzde", "% = (m_çözünen / m_çözelti) · 100\nM = (d · % · 10) / M_A"),
                f("Koligatif özellikler", "ΔTk = Kk · m · i\nΔTd = Kd · m · i"),
            )),
            Topic("Kimyasal tepkimeler ve enerji", listOf(
                f("Tepkime entalpisi", "ΔH = ΣH(ürünler) − ΣH(girenler)\nΔH = Σ(kırılan bağlar) − Σ(oluşan bağlar)"),
                f("Tepkime hızı", "Hız = k · [A]ᵐ · [B]ⁿ"),
                f("Denge sabiti", "aA + bB ⇌ cC + dD\nKc = [C]ᶜ[D]ᵈ / [A]ᵃ[B]ᵇ\nKp = Kc · (RT)^Δn"),
            )),
            Topic("Asitler ve bazlar", listOf(
                f("pH ve pOH", "pH = −log[H⁺]\npOH = −log[OH⁻]\npH + pOH = 14   (25 °C)"),
                f("Suyun iyonlaşması", "Ksu = [H⁺][OH⁻] = 10⁻¹⁴   (25 °C)"),
                f("Nötrleşme", "n(H⁺) = n(OH⁻)\nMₐ · Vₐ · tesir değeri = M_b · V_b · tesir değeri"),
                f("Zayıf asit", "Ka = [H⁺][A⁻] / [HA]\n[H⁺] ≈ √(Ka · C)"),
            )),
            Topic("Elektrokimya", listOf(
                f("Pil gerilimi", "E°pil = E°katot − E°anot"),
                f("Faraday", "Q = I · t\n1 mol e⁻ = 96 500 C\nm = (Q · M_A) / (96 500 · z)"),
            )),
        )),
    )

    // ---- Periyodik tablo ----

    class Element(val z: Int, val symbol: String, val name: String, val mass: String) {
        val period: Int get() = when {
            z <= 2 -> 1; z <= 10 -> 2; z <= 18 -> 3; z <= 36 -> 4; z <= 54 -> 5; z <= 86 -> 6; else -> 7
        }
        /** 1–18; lantanit ve aktinitler için 0. */
        val group: Int get() = when {
            z == 1 -> 1; z == 2 -> 18
            z in 3..4 -> z - 2; z in 5..10 -> z + 8
            z in 11..12 -> z - 10; z in 13..18 -> z
            z in 19..36 -> z - 18; z in 37..54 -> z - 36
            z in 55..56 -> z - 54; z in 57..71 -> 0; z in 72..86 -> z - 68
            z in 87..88 -> z - 86; z in 89..103 -> 0; else -> z - 100
        }
        /** Tabloda satır (0 tabanlı): lantanitler 7, aktinitler 8. */
        val row: Int get() = when (z) { in 57..71 -> 7; in 89..103 -> 8; else -> period - 1 }
        /** Tabloda sütun (0 tabanlı). */
        val col: Int get() = when (z) { in 57..71 -> z - 57 + 2; in 89..103 -> z - 89 + 2; else -> group - 1 }
        val category: Category get() = when (z) {
            3, 11, 19, 37, 55, 87 -> Category.ALKALI
            4, 12, 20, 38, 56, 88 -> Category.ALKALINE
            2, 10, 18, 36, 54, 86, 118 -> Category.NOBLE
            9, 17, 35, 53, 85, 117 -> Category.HALOGEN
            1, 6, 7, 8, 15, 16, 34 -> Category.NONMETAL
            5, 14, 32, 33, 51, 52 -> Category.METALLOID
            in 57..71 -> Category.LANTHANIDE
            in 89..103 -> Category.ACTINIDE
            13, 31, 49, 50, 81, 82, 83, 84, 113, 114, 115, 116 -> Category.POST_TRANSITION
            else -> Category.TRANSITION
        }
    }

    enum class Category(val label: String, val color: Long) {
        ALKALI("Alkali metal", 0xFFE57373), ALKALINE("Toprak alkali metal", 0xFFFFB74D), TRANSITION("Geçiş metali", 0xFF64B5F6),
        POST_TRANSITION("Zayıf metal", 0xFF4DB6AC), METALLOID("Yarı metal", 0xFFAED581), NONMETAL("Ametal", 0xFFFFF176),
        HALOGEN("Halojen", 0xFFF06292), NOBLE("Soy gaz", 0xFFBA68C8), LANTHANIDE("Lantanit", 0xFF90A4AE), ACTINIDE("Aktinit", 0xFFA1887F),
    }

    private const val DATA = "H,Hidrojen,1.008;He,Helyum,4.003;Li,Lityum,6.94;Be,Berilyum,9.012;B,Bor,10.81;C,Karbon,12.011;N,Azot,14.007;O,Oksijen,15.999;" +
        "F,Flor,18.998;Ne,Neon,20.180;Na,Sodyum,22.990;Mg,Magnezyum,24.305;Al,Alüminyum,26.982;Si,Silisyum,28.085;P,Fosfor,30.974;S,Kükürt,32.06;" +
        "Cl,Klor,35.45;Ar,Argon,39.948;K,Potasyum,39.098;Ca,Kalsiyum,40.078;Sc,Skandiyum,44.956;Ti,Titanyum,47.867;V,Vanadyum,50.942;Cr,Krom,51.996;" +
        "Mn,Mangan,54.938;Fe,Demir,55.845;Co,Kobalt,58.933;Ni,Nikel,58.693;Cu,Bakır,63.546;Zn,Çinko,65.38;Ga,Galyum,69.723;Ge,Germanyum,72.630;" +
        "As,Arsenik,74.922;Se,Selenyum,78.971;Br,Brom,79.904;Kr,Kripton,83.798;Rb,Rubidyum,85.468;Sr,Stronsiyum,87.62;Y,İtriyum,88.906;Zr,Zirkonyum,91.224;" +
        "Nb,Niyobyum,92.906;Mo,Molibden,95.95;Tc,Teknesyum,(98);Ru,Rutenyum,101.07;Rh,Rodyum,102.906;Pd,Paladyum,106.42;Ag,Gümüş,107.868;Cd,Kadmiyum,112.414;" +
        "In,İndiyum,114.818;Sn,Kalay,118.710;Sb,Antimon,121.760;Te,Tellür,127.60;I,İyot,126.904;Xe,Ksenon,131.293;Cs,Sezyum,132.905;Ba,Baryum,137.327;" +
        "La,Lantan,138.905;Ce,Seryum,140.116;Pr,Praseodim,140.908;Nd,Neodim,144.242;Pm,Prometyum,(145);Sm,Samaryum,150.36;Eu,Evropiyum,151.964;Gd,Gadolinyum,157.25;" +
        "Tb,Terbiyum,158.925;Dy,Disprosyum,162.500;Ho,Holmiyum,164.930;Er,Erbiyum,167.259;Tm,Tulyum,168.934;Yb,İterbiyum,173.045;Lu,Lutesyum,174.967;Hf,Hafniyum,178.49;" +
        "Ta,Tantal,180.948;W,Tungsten,183.84;Re,Renyum,186.207;Os,Osmiyum,190.23;Ir,İridyum,192.217;Pt,Platin,195.084;Au,Altın,196.967;Hg,Cıva,200.592;" +
        "Tl,Talyum,204.38;Pb,Kurşun,207.2;Bi,Bizmut,208.980;Po,Polonyum,(209);At,Astatin,(210);Rn,Radon,(222);Fr,Fransiyum,(223);Ra,Radyum,(226);" +
        "Ac,Aktinyum,(227);Th,Toryum,232.038;Pa,Protaktinyum,231.036;U,Uranyum,238.029;Np,Neptünyum,(237);Pu,Plütonyum,(244);Am,Amerikyum,(243);Cm,Küriyum,(247);" +
        "Bk,Berkelyum,(247);Cf,Kaliforniyum,(251);Es,Aynştaynyum,(252);Fm,Fermiyum,(257);Md,Mendelevyum,(258);No,Nobelyum,(259);Lr,Lavrensiyum,(266);Rf,Rutherfordiyum,(267);" +
        "Db,Dubniyum,(268);Sg,Seaborgiyum,(269);Bh,Bohriyum,(270);Hs,Hassiyum,(277);Mt,Meitneriyum,(278);Ds,Darmstadtiyum,(281);Rg,Röntgenyum,(282);Cn,Kopernikyum,(285);" +
        "Nh,Nihonyum,(286);Fl,Flerovyum,(289);Mc,Moskovyum,(290);Lv,Livermoryum,(293);Ts,Tennesin,(294);Og,Oganesson,(294)"

    val elements: List<Element> = DATA.split(';').mapIndexed { i, s -> val p = s.split(','); Element(i + 1, p[0], p[1], p[2]) }
}
