// Belirli anlardan PNG kareler alır: node shorts/onizle.js <html> 1.0,5.5,9 <klasor>
const { chromium } = require("playwright"); const fs = require("fs");
(async () => {
  const b = await chromium.launch(), p = await b.newPage(); const hata = [];
  p.on("pageerror", e => hata.push(e.message));
  await p.goto("file://" + require("path").resolve(process.argv[2]));
  await p.evaluate(() => { window.KAYIT = true; return window.hazir; });
  for (const t of process.argv[3].split(",").map(Number)) {
    const u = await p.evaluate(t => { window.ciz(t); return document.getElementById("c").toDataURL("image/png"); }, t);
    fs.writeFileSync(`${process.argv[4]}/kare_${t.toFixed(2).padStart(6, "0")}.png`, Buffer.from(u.split(",")[1], "base64"));
  }
  console.log("hatalar:", hata); await b.close();
})();
