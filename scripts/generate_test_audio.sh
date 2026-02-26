#!/bin/bash
# Generate test audio files for speech-to-text benchmarking using edge-tts.
#
# Requirements:
#   pip install edge-tts
#   brew install ffmpeg
#
# Usage:
#   ./scripts/generate_test_audio.sh [output_dir]
#
# Output: WAV files (16kHz mono PCM16) ready for emulator injection or unit tests.

set -euo pipefail

OUTPUT_DIR="${1:-app/src/test/resources}"
EDGE_TTS="${EDGE_TTS:-edge-tts}"

# Check dependencies
if ! command -v "$EDGE_TTS" &>/dev/null; then
    EDGE_TTS="$HOME/Library/Python/3.9/bin/edge-tts"
    if ! command -v "$EDGE_TTS" &>/dev/null; then
        echo "ERROR: edge-tts not found. Install with: pip install edge-tts"
        exit 1
    fi
fi

if ! command -v ffmpeg &>/dev/null; then
    echo "ERROR: ffmpeg not found. Install with: brew install ffmpeg"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

generate() {
    local name="$1"
    local voice="$2"
    local text="$3"
    local rate="${4:---rate=+0%}"

    local mp3="$OUTPUT_DIR/${name}.mp3"
    local wav="$OUTPUT_DIR/${name}.wav"

    echo "Generating: $name ($voice)"
    "$EDGE_TTS" --text "$text" --voice "$voice" "$rate" --write-media "$mp3" 2>/dev/null
    ffmpeg -y -i "$mp3" -ar 16000 -ac 1 -sample_fmt s16 "$wav" 2>/dev/null
    rm "$mp3"

    local duration
    duration=$(ffprobe -v quiet -show_entries format=duration -of csv=p=0 "$wav" | cut -d. -f1)
    local size
    size=$(du -h "$wav" | cut -f1 | xargs)
    echo "  → $wav (${duration}s, ${size})"
}

echo "=== Generating English test audio ==="

generate "tts_short_en" "en-US-AriaNeural" \
    "Hello, this is a test for speech recognition."

generate "tts_medium_en" "en-US-GuyNeural" \
    "The quick brown fox jumps over the lazy dog. This sentence contains every letter of the English alphabet and is commonly used for testing purposes."

generate "tts_long_en" "en-US-AriaNeural" \
    "Today we're going to discuss the importance of accurate speech recognition in modern applications. Voice interfaces have become increasingly common in smartphones, smart speakers, and accessibility tools. The ability to convert spoken language into text reliably is crucial for users who depend on these technologies in their daily lives. Testing these systems requires diverse audio samples that cover different speaking styles, accents, and background conditions."

generate "tts_numbers_en" "en-US-JennyNeural" \
    "My phone number is five five five, one two three four. The meeting is at 3:30 PM on January 15th, 2026. The total comes to forty-seven dollars and ninety-nine cents."

generate "tts_fast_en" "en-US-AriaNeural" \
    "This is a fast speech test to verify that the model can handle rapid speaking rates without losing accuracy." \
    "--rate=+30%"

generate "tts_slow_en" "en-US-AriaNeural" \
    "This is a slow speech test to check handling of deliberate, measured speaking." \
    "--rate=-30%"

echo ""
echo "=== Generating German test audio ==="

generate "tts_short_de" "de-DE-KatjaNeural" \
    "Hallo, dies ist ein Test für die Spracherkennung."

generate "tts_medium_de" "de-DE-ConradNeural" \
    "Der schnelle braune Fuchs springt über den faulen Hund. Dieser Satz wird häufig verwendet, um die Qualität von Spracherkennungssystemen zu testen."

generate "tts_long_de" "de-DE-KatjaNeural" \
    "Die Spracherkennung hat in den letzten Jahren enorme Fortschritte gemacht. Moderne Systeme können natürliche Sprache in Echtzeit verarbeiten und erreichen dabei eine beeindruckende Genauigkeit. Für die Entwicklung solcher Systeme ist es wichtig, mit verschiedenen Testdaten zu arbeiten, die unterschiedliche Sprechgeschwindigkeiten und Akzente abdecken."

echo ""
echo "=== Done ==="
ls -lh "$OUTPUT_DIR"/tts_*.wav 2>/dev/null | awk '{print $5, $9}'
echo ""
echo "Total files: $(ls "$OUTPUT_DIR"/tts_*.wav 2>/dev/null | wc -l | xargs)"
