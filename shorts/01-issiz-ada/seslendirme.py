"""ElevenLabs seslendirmesini cümle cümle animasyondaki yerlerine yerleştirir ve kayıt ayarını yazar.
   python3 shorts/01-issiz-ada/seslendirme.py   ->  ses/anlatim_yerlesik.wav + kayit_ayar.json
Parçalar (kaynak başlangıç, kaynak bitiş, videodaki başlangıç) ffmpeg silencedetect ile bulunan duraklardan alındı."""
import json, subprocess, pathlib
K = pathlib.Path(__file__).parent
KAYNAK, HEDEF, SURE = K / "ses/anlatim.mp3", K / "ses/anlatim_yerlesik.wav", 50.0
PARCALAR = [  # (kaynak a, kaynak b, video başlangıcı)
    (0.00, 2.48, 0.10), (2.96, 4.99, 3.25), (5.39, 6.36, 5.35),                      # kanca + soru
    (6.79, 7.20, 6.42), (7.62, 8.06, 7.02), (8.47, 8.78, 7.62),                      # üç, iki, bir
    (9.36, 11.38, 8.80), (11.76, 15.86, 11.00),                                       # ayna
    (16.32, 18.13, 17.90), (18.55, 21.54, 19.80), (21.92, 26.04, 22.85),              # çikolata
    (26.56, 28.06, 27.15), (28.47, 30.11, 28.75), (30.44, 34.94, 30.60),              # gözlük
    (35.35, 36.70, 36.30), (37.04, 38.79, 38.30), (39.05, 42.08, 40.80),              # şişe
    (42.57, 44.13, 45.50), (44.47, 46.57, 47.20),                                     # final
]
ALTYAZI = [
    [0.10, 2.65, "Gemin battı ve {ıssız bir adaya} düştün!"], [3.25, 5.30, "Yanına sadece {1 eşya} alabilirsin."],
    [5.35, 6.38, "Hangisini {seçerdin?}"], [6.42, 7.00, "{Üç...}"], [7.02, 7.60, "{İki...}"], [7.62, 8.60, "{Bir!}"],
    [8.80, 9.70, "{Ayna} mı?"], [9.70, 10.95, "Çok {basit} görünüyor..."],
    [11.00, 12.45, "Ama güneşi {yansıtınca,}"], [12.45, 15.40, "uçaklar seni {kilometrelerce} öteden görür!"],
    [17.90, 18.80, "{Çikolata} mı?"], [18.80, 19.78, "{Mmm...}"],
    [19.80, 21.90, "Ama asıl tehlike {açlık değil...}"], [21.90, 22.83, "{susuzluk!}"],
    [22.85, 25.10, "İnsan yemeksiz {haftalarca} dayanır,"], [25.10, 27.00, "ama susuz sadece {3 gün!}"],
    [27.15, 28.72, "{Gözlükle} ateş mi yakacaksın?"], [28.75, 30.55, "{Miyop} gözlük ışığı dağıtır..."],
    [30.60, 32.50, "Sadece {artı numaralı} gözlük,"], [32.50, 35.40, "ışığı tek noktada toplar ve {ateşi yakar!}"],
    [36.30, 37.95, "{Deniz suyu} içilmez!"], [38.30, 40.35, "Ama yağmur suyunu {şişede} topla,"],
    [40.80, 42.45, "güneşte {6 saat} bırak..."], [42.45, 44.20, "{mikropların} çoğu ölür!"],
    [45.50, 47.15, "Peki {en iyisi} hangisi?"], [47.20, 48.45, "Sen hangisini {seçtin?}"], [48.45, 49.95, "{Yorumlara yaz!}"],
]
ON, SON = .06, .08
filtre, etiket = [], []
for i, (a, b, v) in enumerate(PARCALAR):
    a0 = max(0, a - ON); d = round((v - (a - a0)) * 1000)
    filtre.append(f"[0:a]atrim={a0}:{b + SON},asetpts=PTS-STARTPTS,afade=t=in:d=0.02,afade=t=out:st={b + SON - a0 - .03:.3f}:d=0.03,adelay={d}|{d}[p{i}]")
    etiket.append(f"[p{i}]")
filtre.append("".join(etiket) + f"amix=inputs={len(etiket)}:normalize=0,apad,atrim=0:{SURE}[s]")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(KAYNAK), "-filter_complex", ";".join(filtre), "-map", "[s]", "-ac", "2", "-ar", "48000", str(HEDEF)], check=True)
json.dump({"anlatim": str(HEDEF.relative_to(K.parent.parent)), "efektSes": 0.45, "altyazi": ALTYAZI},
          open(K / "kayit_ayar.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("hazır:", HEDEF)
