"""ElevenLabs seslendirmesini cümle cümle animasyondaki yerlerine yerleştirir ve kayıt ayarlarını yazar.
   python3 shorts/04-seytan-meyvesi/seslendirme.py  ->  ses/anlatim_yerlesik.wav + kayit_ayar.json (müziksiz)
Parçalar (kaynak başlangıç, kaynak bitiş, videodaki başlangıç) ffmpeg silencedetect ile bulunan duraklardan alındı."""
import json, subprocess, pathlib
K = pathlib.Path(__file__).parent
KAYNAK, HEDEF, SURE = K / "ses/anlatim.mp3", K / "ses/anlatim_yerlesik.wav", 50.0
PARCALAR = [  # (kaynak a, kaynak b, video başlangıcı[, hız])
    (0.00, 2.23, 0.08, 1.08), (2.64, 4.34, 2.18, 1.08), (4.80, 6.69, 3.78, 1.08), (7.12, 8.06, 5.55, 1.08),   # kanca + soru (hafif hızlı)
    (8.48, 8.86, 6.42), (9.32, 9.73, 7.02), (10.15, 10.48, 7.62),                                        # üç, iki, bir
    (11.07, 12.04, 8.75), (12.43, 13.99, 10.40), (14.28, 15.51, 12.00), (16.00, 19.06, 13.60),          # lastik
    (19.68, 20.55, 17.95), (20.94, 22.85, 18.85), (23.20, 25.41, 20.80), (25.68, 27.54, 23.05),         # ateş
    (28.20, 29.01, 27.10), (29.35, 30.69, 28.80), (31.03, 32.19, 30.20), (32.44, 33.62, 31.40),         # buz
    (34.15, 35.32, 36.30), (35.68, 36.94, 37.90), (37.25, 39.06, 39.20), (39.26, 39.85, 41.05), (40.18, 42.29, 41.70),   # görünmezlik
    (42.83, 44.36, 45.45), (44.82, 47.09, 47.00, 1.08), (47.52, 48.23, 49.15, 1.08),                     # final
]
ALTYAZI = [
    [0.08, 2.15, "Issız adada bir {hazine sandığı} buldun!"], [2.18, 3.75, "İçinde {dört şeytan meyvesi} var..."], [3.78, 5.52, "ama sadece {birini} yiyebilirsin!"], [5.55, 6.4, "{Hangisini} seçerdin?"],
    [6.42, 7.0, "{Üç...}"], [7.02, 7.6, "{İki...}"], [7.62, 8.5, "{Bir!}"],
    [8.75, 9.75, "{Lastik meyvesi} mi?"], [10.4, 11.97, "Kolların {lastik gibi} uzar!"], [12.0, 13.4, "Ama hep {geri fırlar...}"], [13.6, 16.7, "Doğal kauçuk, boyunun {yedi katına} kadar uzayabilir!"],
    [17.95, 18.82, "{Ateş meyvesi} mi?"], [18.85, 20.77, "Alev alev yanarsın, {çok havalı!}"], [20.8, 23.02, "Ama dondurman bile {anında buharlaşır,}"], [23.05, 24.95, "kuma bastığın yer bile {cama dönüşür!}"],
    [27.1, 27.95, "{Buz meyvesi} mi?"], [28.8, 30.17, "{Denizi bile} dondurursun!"], [30.2, 31.37, "Ama bir {hapşırırsın...}"], [31.4, 32.9, "ve {kendini dondurursun!}"],
    [36.3, 37.6, "{Görünmezlik meyvesi} mi?"], [37.9, 39.18, "Kimse seni {göremez!}"], [39.2, 41.02, "Ama gerçekten görünmez olsaydın,"], [41.05, 41.68, "{kör olurdun!}"], [41.7, 44.0, "Çünkü göz, ışığı {yakalayarak} görür."],
    [45.45, 46.98, "Peki {en iyisi} hangisi?"], [47.0, 49.12, "Ama unutma: {hiçbiriyle yüzemezsin!}"], [49.15, 49.98, "{Yorumlara yaz!}"],
]
ON, SON = .06, .08
# Netlik EQ: boğukluğu (200-350 Hz) azaltır, anlaşılırlığı (2.8-5 kHz) ve parlaklığı artırır, hafif kompresör
SES_FILTRE = ("highpass=f=90,equalizer=f=180:t=q:w=1:g=-4,equalizer=f=350:t=q:w=1.2:g=-3,equalizer=f=2800:t=q:w=1:g=4,"
              "equalizer=f=5000:t=q:w=1.4:g=3,treble=g=3:f=9000,acompressor=threshold=-22dB:ratio=3:attack=5:release=90:makeup=2")
filtre, etiket = [], []
for i, (a, b, v, *hiz) in enumerate(PARCALAR):
    h = hiz[0] if hiz else 1.0
    a0 = max(0, a - ON); d = round((v - (a - a0) / h) * 1000)
    filtre.append(f"[0:a]atrim={a0}:{b + SON},asetpts=PTS-STARTPTS,afade=t=in:d=0.02,afade=t=out:st={b + SON - a0 - .03:.3f}:d=0.03,atempo={h},adelay={d}|{d}[p{i}]")
    etiket.append(f"[p{i}]")
filtre.append("".join(etiket) + f"amix=inputs={len(etiket)}:normalize=0,{SES_FILTRE},apad,atrim=0:{SURE}[s]")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(KAYNAK), "-filter_complex", ";".join(filtre), "-map", "[s]", "-ac", "2", "-ar", "48000", str(HEDEF)], check=True)
taban = {"anlatim": str(HEDEF.relative_to(K.parent.parent)), "efektSes": 0.45, "altyazi": ALTYAZI}
json.dump(taban, open(K / "kayit_ayar.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("hazır:", HEDEF)
