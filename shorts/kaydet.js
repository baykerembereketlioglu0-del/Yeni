// Tek dosya HTML animasyonunu kare kare (titremesiz) MP4'e kaydeder. Ses efektleri sayfadaki
// Web Audio motorundan OfflineAudioContext ile üretilir.
//   node shorts/kaydet.js <html> <cikti.mp4> [fps=60] [kayit_ayar.json]
// kayit_ayar.json (hepsi isteğe bağlı): eslesme + toplam (animasyonu seslendirmeye göre esnetir),
// altyazi (yeni altyazılar), anlatim (seslendirme dosyası), efektSes (efekt seviyesi, varsayılan 0.6),
// muzik + muzikLufs + muzikBaslangic + muzikOfset (fon müziği; seslendirme varken otomatik kısılır).
const { chromium } = require("playwright");
const { spawn } = require("child_process");
const fs = require("fs");
const path = require("path");
function ara(x, n) { for (let i = 0; i < n.length - 1; i++) { const [a0, b0] = n[i], [a1, b1] = n[i + 1]; if (x >= a0 && x <= a1) return b0 + (b1 - b0) * (x - a0) / (a1 - a0); } return x > n[n.length - 1][0] ? n[n.length - 1][1] : n[0][1]; }
(async () => {
  const html = path.resolve(process.argv[2]), cikti = path.resolve(process.argv[3]);
  const fps = Number(process.argv[4] || 60);
  const ayar = process.argv[5] ? JSON.parse(fs.readFileSync(process.argv[5], "utf8")) : null;
  const tar = await chromium.launch(), sayfa = await tar.newPage({ viewport: { width: 540, height: 960 } });
  sayfa.on("pageerror", e => console.error("SAYFA HATASI:", e.message));
  await sayfa.goto("file://" + html);
  await sayfa.evaluate((a) => { window.KAYIT = true; if (a && a.altyazi) window.AYAR = { altyazi: a.altyazi }; return window.hazir; }, ayar);
  const sure = ayar && ayar.toplam ? ayar.toplam : await sayfa.evaluate(() => window.SURE);
  const videoAnim = ayar && ayar.eslesme ? ayar.eslesme.map(([an, v]) => [v, an]) : null;
  const wav = cikti.replace(/\.mp4$/, ".efekt.wav");
  fs.writeFileSync(wav, Buffer.from(await sayfa.evaluate((a) => a && a.eslesme ? window.sesWav(a.eslesme, a.toplam) : window.sesWav(), ayar), "base64"));
  const girdi = ["-i", wav], parca = [`[1:a]volume=${(ayar && ayar.efektSes) || 0.6}[e]`], etiket = ["[e]"];
  const anlatimVar = ayar && ayar.anlatim;
  if (anlatimVar) { girdi.push("-i", path.resolve(ayar.anlatim)); const n = girdi.length / 2; parca.push(`[${n}:a]volume=1.0,asplit=2[n][nk]`); etiket.push("[n]"); }
  if (ayar && ayar.muzik) { // fon müziği: muzikBaslangic sn'de girer, parçanın muzikOfset sn'sinden başlar, konuşurken kısılır (ducking)
    girdi.push("-stream_loop", "-1", "-i", path.resolve(ayar.muzik)); const n = (girdi.length - 2) / 2;
    const bas = ayar.muzikBaslangic || 0, ofs = ayar.muzikOfset || 0, uz = sure - bas, ms = Math.round(bas * 1000);
    parca.push(`[${n}:a]atrim=${ofs}:${ofs + uz},asetpts=PTS-STARTPTS,loudnorm=I=${ayar.muzikLufs || -24}:TP=-3,aresample=48000,` +
      `afade=t=in:d=0.5,afade=t=out:st=${(uz - 1.2).toFixed(2)}:d=1.2,adelay=${ms}|${ms},apad=whole_dur=${sure}[m0]`);
    parca.push(anlatimVar ? "[m0][nk]sidechaincompress=threshold=0.03:ratio=4:attack=15:release=400[m]" : "[m0]anull[m]"); etiket.push("[m]"); }
  else if (anlatimVar) parca.push("[nk]anullsink");
  const filtre = ["-filter_complex", parca.join(";") + ";" + etiket.join("") + `amix=inputs=${etiket.length}:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11[s]`, "-map", "0:v", "-map", "[s]"];
  const ff = spawn("ffmpeg", ["-v", "error", "-y", "-f", "image2pipe", "-framerate", String(fps), "-c:v", "mjpeg", "-i", "-", ...girdi, ...filtre,
    "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p", "-r", String(fps), "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
    "-movflags", "+faststart", "-shortest", cikti], { stdio: ["pipe", "inherit", "inherit"] });
  const toplam = Math.round(sure * fps), t0 = Date.now();
  for (let i = 0; i < toplam; i++) {
    const v = i / fps, t = videoAnim ? ara(v, videoAnim) : v;
    const url = await sayfa.evaluate((t) => { window.ciz(t); return document.getElementById("c").toDataURL("image/jpeg", 0.95); }, t);
    if (!ff.stdin.write(Buffer.from(url.split(",")[1], "base64"))) await new Promise(r => ff.stdin.once("drain", r));
    if (i % (fps * 5) === 0) process.stdout.write(`\r  kare ${i}/${toplam} (${((Date.now() - t0) / 1000).toFixed(0)} sn)`);
  }
  ff.stdin.end(); await new Promise(r => ff.on("close", r)); fs.unlinkSync(wav); await tar.close();
  console.log(`\nBİTTİ: ${cikti}`);
})();
