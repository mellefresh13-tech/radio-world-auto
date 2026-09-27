from pathlib import Path

TARGET = Path("android/app/src/main/java/com/mellefresh13/radio/MainActivity.kt")
s = TARGET.read_text(encoding="utf-8")
s = s.replace("import androidx.emoji2.bundled.BundledEmojiCompatConfig\n", "")
s = s.replace("import androidx.emoji2.text.EmojiCompat\n", "")
s = s.replace("        EmojiCompat.init(BundledEmojiCompatConfig(this))\n", "")
s = s.replace("EmojiCompat.get().process(text)", "text")\nTARGET.write_text(s, encoding="utf-8")
print("EmojiCompat compile dependency removed from MainActivity build source.")
