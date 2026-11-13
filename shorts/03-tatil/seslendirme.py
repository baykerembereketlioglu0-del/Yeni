"""ElevenLabs seslendirmesini cümle cümle animasyondaki yerlerine yerleştirir ve kayıt ayarlarını yazar.
   python3 shorts/03-tatil/seslendirme.py  ->  ses/anlatim_yerlesik.wav + kayit_ayar.json (müziksiz)
Parçalar (kaynak başlangıç, kaynak bitiş, videodaki başlangıç) ffmpeg silencedetect ile bulunan duraklardan alındı."""
import json, subprocess, pathlib
K = pathlib.Path(__file__).parent
KAYNAK, HEDEF, SURE = K / "ses/anlatim.mp3", K / "ses/anlatim_yerlesik.wav", 50.0
PARCALAR = [
    (0.00, 2.01, 0.15), (2.26, 3.58, 2.25), (3.98, 5.95, 3.40), (6.33, 7.27, 5.40),     # kanca + soru
    (7.73, 8.14, 6.42), (8.56, 8.99, 7.02), (9.38, 9.77, 7.62),                         # üç, iki, bir
    (10.29, 10.78, 8.80), (11.24, 13.60, 9.30), (13.97, 15.63, 11.75), (16.01, 17.73, 13.50),   # Tokyo
    (18.26, 18.84, 17.95), (19.21, 20.68, 18.70), (21.02, 24.36, 20.80),               # Norveç
    (24.81, 25.41, 27.15), (25.83, 27.65, 27.90), (28.00, 31.45, 30.00),               # Dubai
    (31.95, 32.49, 36.35), (32.90, 34.40, 37.05), (34.73, 36.49, 39.00), (36.75, 39.58, 40.85),   # Paris
    (40.11, 41.62, 45.50), (42.01, 43.14, 47.15), (43.46, 44.16, 48.40),               # final
]
ALTYAZI = [
    [0.15, 2.2, "Kapıda dev bir {hediye paketi} var!"], [2.25, 3.35, "İçinden {tatil} çıktı..."], [3.4, 5.35, "ama sadece {birini} seçebilirsin!"], [5.4, 6.38, "{Hangisini} seçerdin?"],
    [6.42, 7.0, "{Üç...}"], [7.02, 7.6, "{İki...}"], [7.62, 8.5, "{Bir!}"],
    [8.8, 9.28, "{Tokyo} mu?"], [9.3, 11.7, "Işıklar, robotlar... {harika!}"], [11.75, 13.45, "Ama {kapsül otelde} kalırsın..."], [13.5, 15.5, "Odan sadece {bire iki metre!}"],
    [17.95, 18.65, "{Norveç} mi?"], [18.7, 20.75, "Kuzey ışıkları {büyüleyici!}"], [20.8, 24.4, "Ama kışın orada güneş {neredeyse iki ay} hiç doğmaz!"],
    [27.15, 27.85, "{Dubai} mi?"], [27.9, 29.95, "Gökdelenler, {çöl safarisi!}"], [30.0, 33.7, "Ama yazın sıcaklık {gölgede bile 45 dereceyi} geçebilir!", 560],
    [36.35, 37.0, "{Paris} mi?"], [37.05, 38.95, "{Eyfel Kulesi} seni bekliyor!"], [39.0, 40.8, "Ama asansör kuyruğu uzunsa,"], [40.85, 43.9, "ikinci kata {674 basamak} tırmanırsın!"],
    [45.5, 47.1, "Peki {en iyisi} hangisi?"], [47.15, 48.35, "Sen hangisini {seçerdin?}"], [48.4, 49.95, "{Yorumlara yaz!}"],
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
