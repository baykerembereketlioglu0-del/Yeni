"""Kaynak HTML'deki /*@FONT:Aile:dosya-oneki:kalinlik@*/ yer tutucularına fontu gömer -> tek dosya HTML.

    python3 shorts/derle.py shorts/01-issiz-ada/ada.kaynak.html   # -> ada.html
"""
import base64
import re
import sys
from pathlib import Path

FONT_DIZIN = Path(__file__).parent / "ortak" / "fontlar"
ARALIK = {
    "latin": "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD",
    "latin-ext": "U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF",
}


def font_css(aile, dosya, kalinlik):
    parcalar = []
    for alt, aralik in ARALIK.items():
        veri = base64.b64encode((FONT_DIZIN / f"{dosya}-{alt}-{kalinlik}-normal.woff2").read_bytes()).decode()
        parcalar.append(f"@font-face {{ font-family: '{aile}'; font-style: normal; font-weight: {kalinlik}; font-display: block; "
                        f"src: url(data:font/woff2;base64,{veri}) format('woff2'); unicode-range: {aralik}; }}")
    return "\n".join(parcalar)


def main(kaynak):
    kaynak = Path(kaynak)
    hedef = kaynak.with_name(kaynak.name.replace(".kaynak", ""))
    metin = kaynak.read_text(encoding="utf-8")
    metin = re.sub(r"/\*@FONT:([^:]+):([^:]+):(\d+)@\*/", lambda m: font_css(m.group(1), m.group(2), m.group(3)), metin)
    hedef.write_text(metin, encoding="utf-8")
    print(hedef)


if __name__ == "__main__":
    main(sys.argv[1])
