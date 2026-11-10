#!/usr/bin/env bash
set -euo pipefail

# Download pinned official sherpa-onnx Android AAR and the GigaSpeech KWS model.
# Called by GitHub Actions before Gradle builds the APK. The binary/model are
# not committed to the source tree.
AAR_VERSION=2.0.1
AAR_SHA256=a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7
MODEL=sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/${MODEL}.tar.bz2"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${AAR_VERSION}/sherpa-onnx-${AAR_VERSION}.aar"

mkdir -p app/libs app/src/main/assets
curl --fail --location --retry 4 --retry-delay 4 "$AAR_URL" --output app/libs/sherpa-onnx.aar
echo "$AAR_SHA256  app/libs/sherpa-onnx.aar" | sha256sum --check

curl --fail --location --retry 4 --retry-delay 4 "$MODEL_URL" --output /tmp/jarvis-sherpa-kws.tar.bz2
echo "Downloaded model archive: $(sha256sum /tmp/jarvis-sherpa-kws.tar.bz2)"
tar -xjf /tmp/jarvis-sherpa-kws.tar.bz2 --directory app/src/main/assets --no-same-owner
MODEL_DIR="app/src/main/assets/$MODEL"
test -s "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.onnx"
test -s "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
test -s "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
test -s "$MODEL_DIR/tokens.txt"
test -s "$MODEL_DIR/bpe.model"

# Generate the tokenized keyword using the model's own SentencePiece vocab.
# Raw 'JARVIS' cannot be used directly as a KWS keywords.txt entry.
python3 -m venv /tmp/jarvis-sherpa-env
/tmp/jarvis-sherpa-env/bin/pip install --disable-pip-version-check --quiet sentencepiece==0.2.1
/tmp/jarvis-sherpa-env/bin/python - "$MODEL_DIR" <<'PY'
import pathlib
import sentencepiece as spm
import sys
directory = pathlib.Path(sys.argv[1])
sp = spm.SentencePieceProcessor(model_file=str(directory / "bpe.model"))
pieces = sp.encode("JARVIS", out_type=str)
vocabulary = {
    line.rsplit(maxsplit=1)[0]
    for line in (directory / "tokens.txt").read_text(encoding="utf-8").splitlines()
    if line.strip()
}
assert pieces and all(piece in vocabulary for piece in pieces), (
    "Keyword BPE tokens missing from model: " + repr(pieces)
)
text = " ".join(pieces) + " @JARVIS\n"
(directory / "keywords.txt").write_text(text, encoding="utf-8")
print("Encoded offline wake keyword:", text.strip())
PY

# Keep only the files required by the Android inference pipeline.
rm -rf "$MODEL_DIR/test_wavs"
rm -f "$MODEL_DIR"/*.int8.onnx "$MODEL_DIR/configuration.json" \
  "$MODEL_DIR/keywords_raw.txt" "$MODEL_DIR/README.md" "$MODEL_DIR/bpe.model"
du -sh app/libs/sherpa-onnx.aar "$MODEL_DIR"
