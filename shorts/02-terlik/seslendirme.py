"""ElevenLabs seslendirmesini cümle cümle animasyondaki yerlerine yerleştirir ve kayıt ayarlarını yazar.
   python3 shorts/02-terlik/seslendirme.py  ->  ses/anlatim_yerlesik.wav + kayit_ayar.json (müziksiz)
Parçalar (kaynak başlangıç, kaynak bitiş, videodaki başlangıç) ffmpeg silencedetect ile bulunan duraklardan alındı."""
import json, subprocess, pathlib
K = pathlib.Path(__file__).parent
KAYNAK, HEDEF, SURE = K / "ses/anlatim.mp3", K / "ses/anlatim_yerlesik.wav", 50.0
PARCALAR = [
    (0.00, 1.27, 0.15), (1.68, 3.11, 1.75), (3.44, 4.31, 3.40),                        # kanca + soru
    (4.71, 5.15, 6.42), (5.47, 5.90, 7.02), (6.22, 6.57, 7.62),                        # üç, iki, bir
    (7.12, 7.93, 8.80), (8.33, 9.11, 9.70), (9.49, 10.97, 10.50), (11.30, 11.72, 12.02), (12.20, 14.82, 12.75),   # yatak altı
    (15.34, 15.87, 17.95), (16.27, 20.49, 18.60), (20.81, 23.71, 22.90),              # dolap
    (24.25, 25.40, 27.10), (25.76, 27.30, 28.40), (27.69, 31.96, 30.10), (32.33, 34.07, 34.45),   # babaanne
    (34.52, 35.37, 36.30), (35.71, 38.70, 37.30), (39.12, 42.41, 41.00),              # özür
    (42.88, 44.41, 45.50), (44.73, 45.76, 47.15), (46.06, 46.80, 48.35),              # final
]
ALTYAZI = [
    [0.15, 1.7, "Halıya {kola} döktün..."], [1.75, 3.2, "ve annen {terliği} kaptı!"], [3.4, 4.4, "{Nereye} kaçarsın?"],
    [6.42, 7.0, "{Üç...}"], [7.02, 7.6, "{İki...}"], [7.62, 8.5, "{Bir!}"],
    [8.8, 9.68, "{Yatağın altı} mı?"], [9.7, 10.48, "Çok {klasik...}"], [10.5, 12.0, "Ama yatağın altı {toz dolu.}"], [12.02, 12.7, "{Hapşu!}"],
    [12.75, 15.5, "Toz akarları seni {hapşırtır} ve yerini ele verir!"],
    [17.95, 18.58, "{Dolap} mı?"], [18.6, 20.65, "Karanlıkta {hiçbir şey} göremezsin,"], [20.65, 22.85, "her şeye çarparsın... {tangır tungur!}"],
    [22.9, 25.9, "Çünkü gözün karanlığa tam alışması {yarım saat} sürer!"],
    [27.1, 28.35, "{Babaannenin arkası} mı?"], [28.4, 30.0, "İşte {en güçlü kalkan!}"], [30.1, 32.2, "Araştırmalara göre babaannelerin beyni,"],
    [32.2, 34.4, "torunlarını görünce {sevgiyle parlıyor!}"], [34.45, 36.15, "Ama babaanne {evine dönünce...}"],
    [36.3, 37.2, "Ya da {hiç kaçma!}"], [37.3, 38.44, "{Özür dile} ve lekeyi"], [38.44, 40.4, "ovmadan, {tamponlayarak} temizle."],
    [41.0, 44.4, "Araştırmalar, içten bir özrün {öfkeyi azalttığını} gösteriyor!"],
    [45.5, 47.1, "Peki {en iyisi} hangisi?"], [47.15, 48.3, "Sen nereye {kaçardın?}"], [48.35, 49.95, "{Yorumlara yaz!}"],
]
ON, SON = .06, .08
# Netlik EQ: boğukluğu (200-350 Hz) azaltır, anlaşılırlığı (2.8-5 kHz) ve parlaklığı artırır, hafif kompresör
SES_FILTRE = ("highpass=f=90,equalizer=f=180:t=q:w=1:g=-4,equalizer=f=350:t=q:w=1.2:g=-3,equalizer=f=2800:t=q:w=1:g=4,"
              "equalizer=f=5000:t=q:w=1.4:g=3,treble=g=3:f=9000,acompressor=threshold=-22dB:ratio=3:attack=5:release=90:makeup=2")
filtre, etiket = [], []
for i, (a, b, v) in enumerate(PARCALAR):
    a0 = max(0, a - ON); d = round((v - (a - a0)) * 1000)
    filtre.append(f"[0:a]atrim={a0}:{b + SON},asetpts=PTS-STARTPTS,afade=t=in:d=0.02,afade=t=out:st={b + SON - a0 - .03:.3f}:d=0.03,adelay={d}|{d}[p{i}]")
    etiket.append(f"[p{i}]")
filtre.append("".join(etiket) + f"amix=inputs={len(etiket)}:normalize=0,{SES_FILTRE},apad,atrim=0:{SURE}[s]")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(KAYNAK), "-filter_complex", ";".join(filtre), "-map", "[s]", "-ac", "2", "-ar", "48000", str(HEDEF)], check=True)
taban = {"anlatim": str(HEDEF.relative_to(K.parent.parent)), "efektSes": 0.45, "altyazi": ALTYAZI}
json.dump(taban, open(K / "kayit_ayar.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("hazır:", HEDEF)
