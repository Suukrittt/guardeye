"""GuardEye Windows companion (v0.2). Captures your screen ONLY after you type YES, OCRs it locally, checks scam wording.
Needs: Python 3.10+, `pip install mss pillow pytesseract plyer`, and Tesseract-OCR installed (https://github.com/UB-Mannheim/tesseract/wiki).
Stop: press Ctrl+C. Results (no screenshots, no screen text) go to guardeye_history.jsonl."""
import json, re, sys, time, mss
from PIL import Image
import pytesseract
from plyer import notification
RULES = [(r"guarantee[d]?|risk[- ]free|assured returns?|\b\d+\s?x returns?|double your money", 30, "Promises guaranteed or unusually high returns"),
 (r"act now|limited (slots|time|seats)|hurry|last chance|expires (today|soon)|urgent", 15, "Uses urgency to pressure you"),
 (r"\botp\b|password|\bpin\b|cvv|card number|aadhaar|bank details", 35, "Asks for private codes or financial details"),
 (r"https?://\S+|\b(bit\.ly|tinyurl|wa\.me)\S*", 10, "Contains a link - check where it really goes"),
 (r"invest|crypto|bitcoin|trading|forex|lottery|prize|you (have )?won", 15, "Mentions investment, crypto or prizes"),
 (r"(prime minister|minister|celebrity|ceo).{0,40}(endors|recommend|launch|scheme)|government[- ]backed", 20, "Claims a public figure or government endorses it")]
def analyze(t):
    hit = [(w, m) for p, w, m in RULES if re.search(p, t, re.I)]; s = min(100, sum(w for w, _ in hit))
    return ("Possible Scam" if s >= 50 else "Suspicious Content" if s >= 25 else "No Strong Warning Signals Detected"), s, [m for _, m in hit]
def main(every=5):
    if input("GuardEye will read text on your screen every %ds until you press Ctrl+C. Nothing is saved except warnings. Type YES to allow: " % every).strip() != "YES": return
    prev = None; last = ("", 0)
    with mss.mss() as sct:
        try:
            while True:
                shot = sct.grab(sct.monitors[1]); img = Image.frombytes("RGB", shot.size, shot.rgb)
                small = list(img.convert("L").resize((32, 32)).getdata())
                if prev and sum(abs(a - b) for a, b in zip(small, prev)) / 1024 < 1.0: time.sleep(every); continue
                prev = small; cat, s, why = analyze(pytesseract.image_to_string(img))  # text kept in memory only
                if s >= 25 and (("|".join(why), 0)[0] != last[0] or time.time() - last[1] > 60):
                    last = ("|".join(why), time.time())
                    open("guardeye_history.jsonl", "a", encoding="utf-8").write(json.dumps({"ts": time.time(), "category": cat, "score": s, "reasons": why, "method": "mss capture + Tesseract OCR + rule-based wording check"}) + "\n")
                    notification.notify(title="CAUTION: " + cat, message="; ".join(why) + ". Verify before sending money or sharing.", timeout=10)
                time.sleep(every)
        except KeyboardInterrupt: print("Stopped.")
if __name__ == "__main__": main()
