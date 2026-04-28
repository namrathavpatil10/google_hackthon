"""
Converts dima806/deepfake_vs_real_image_detection to TFLite.
No ONNX needed — uses HuggingFace's built-in TF export.

Setup (run once):
    pip install transformers tensorflow pillow numpy

Run:
    python3 convert_model.py

Output:
    deepfake_detector.tflite  → copy to app/src/main/assets/
"""

import numpy as np
import tensorflow as tf
import os

MODEL_ID = "dima806/deepfake_vs_real_image_detection"
OUTPUT   = "deepfake_detector.tflite"

def convert():
    print("Step 1 — Loading model directly as TensorFlow (no ONNX)…")
    from transformers import AutoFeatureExtractor, TFAutoModelForImageClassification

    extractor = AutoFeatureExtractor.from_pretrained(MODEL_ID)
    tf_model  = TFAutoModelForImageClassification.from_pretrained(MODEL_ID, from_pt=True)
    tf_model.trainable = False

    labels = tf_model.config.id2label
    print(f"  Label map: {labels}")
    # Find which index = REAL
    real_idx = next((k for k, v in labels.items() if "real" in v.lower()), 1)
    print(f"  REAL index → {real_idx}  (trustScore = output[0][{real_idx}])")

    print("\nStep 2 — Saving as TF SavedModel…")
    # Wrap in a concrete function with fixed input shape
    @tf.function(input_signature=[tf.TensorSpec(shape=[1, 224, 224, 3], dtype=tf.float32, name="pixel_values")])
    def serving(pixel_values):
        # HuggingFace ViT expects channel-first; permute
        x = tf.transpose(pixel_values, perm=[0, 3, 1, 2])
        out = tf_model(pixel_values=x, training=False)
        return {"logits": out.logits}

    tf.saved_model.save(tf_model, "deepfake_saved_model",
                        signatures={"serving_default": serving})

    print("\nStep 3 — Converting SavedModel → TFLite (float32, no quantisation for accuracy)…")
    converter = tf.lite.TFLiteConverter.from_saved_model("deepfake_saved_model")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]

    tflite_bytes = converter.convert()
    with open(OUTPUT, "wb") as f:
        f.write(tflite_bytes)

    mb = os.path.getsize(OUTPUT) / 1_000_000
    print(f"\n✅  Done!  {OUTPUT}  ({mb:.1f} MB)")
    print(f"\n   Label map: {labels}")
    print(f"   REAL probability → output[0][{real_idx}]")
    print(f"\n   Next step:")
    print(f"   cp {OUTPUT} app/src/main/assets/deepfake_detector.tflite")
    print(f"\n   Then open DeepfakeDetector.kt and set:")
    print(f"   val realProb = output[0][{real_idx}]")

if __name__ == "__main__":
    convert()
