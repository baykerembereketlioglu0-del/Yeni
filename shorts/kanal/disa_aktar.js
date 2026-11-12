// node shorts/kanal/disa_aktar.js  ->  shorts/kanal/logo.png (1024x1024) + banner.png (2560x1440)
// Karakter, videodaki çizim kodundan (01-issiz-ada/ada.html) birebir alınır.
const { chromium } = require("playwright"); const fs = require("fs"); const path = require("path");
(async () => {
  const d = __dirname, tar = await chromium.launch(), s = await tar.newPage();
  s.on("pageerror", e => console.error("HATA:", e.message));
  await s.goto("file://" + path.join(d, "../01-issiz-ada/ada.html"));
  await s.evaluate(() => { window.KAYIT = true; return window.hazir; });
  const bust = await s.evaluate(() => {   // baş + omuzlar, şeffaf zemin, 1024x1024
    cv.width = 1024; cv.height = 1024; g.clearRect(0, 0, 1024, 1024);
    const sk = 2.35; sen(512, 600 + 342 * sk, sk, { ifade: "gulumse", t: 1, bak: [.7, -.35], kolL: [.15, .1], kolR: [.15, .1], etiket: false });
    return cv.toDataURL("image/png");
  });
  await s.goto("file://" + path.join(d, "kanal.html"));
  await s.evaluate(b => window.ciz(b), bust);
  for (const ad of ["logo", "banner"]) {
    const url = await s.evaluate(id => document.getElementById(id).toDataURL("image/png"), ad);
    fs.writeFileSync(path.join(d, ad + ".png"), Buffer.from(url.split(",")[1], "base64")); console.log("yazıldı:", ad + ".png");
  }
  await tar.close();
})();
