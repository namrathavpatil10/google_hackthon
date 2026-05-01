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

MODEL_ID_V1 = "dima806/deepfake_vs_real_image_detection"
MODEL_ID_V2 = "prithivMLmods/Deep-fake-detector-v2-model"
OUTPUT_V1   = "deepfake_detector_v1.tflite"
OUTPUT_V2   = "deepfake_detector_v2.tflite"

def convert_v1():
    print(f"--- Converting V1: {MODEL_ID_V1} ---")
    from transformers import AutoFeatureExtractor, TFAutoModelForImageClassification

    extractor = AutoFeatureExtractor.from_pretrained(MODEL_ID_V1)
    tf_model  = TFAutoModelForImageClassification.from_pretrained(MODEL_ID_V1, from_pt=True)
    tf_model.trainable = False

    @tf.function(input_signature=[tf.TensorSpec(shape=[1, 224, 224, 3], dtype=tf.float32, name="pixel_values")])
    def serving(pixel_values):
        x = tf.transpose(pixel_values, perm=[0, 3, 1, 2])
        out = tf_model(pixel_values=x, training=False)
        return {"logits": out.logits}

    tf.saved_model.save(tf_model, "v1_saved_model", signatures={"serving_default": serving})

    converter = tf.lite.TFLiteConverter.from_saved_model("v1_saved_model")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]
    tflite_bytes = converter.convert()
    with open(OUTPUT_V1, "wb") as f:
        f.write(tflite_bytes)
    print(f"✅ V1 Done: {OUTPUT_V1}")

def convert_v2():
    print(f"--- Converting V2: {MODEL_ID_V2} ---")
    from transformers import AutoImageProcessor, TFAutoModelForImageClassification

    # V2 is often also a ViT or similar high-accuracy model
    processor = AutoImageProcessor.from_pretrained(MODEL_ID_V2)
    tf_model  = TFAutoModelForImageClassification.from_pretrained(MODEL_ID_V2, from_pt=True)
    tf_model.trainable = False

    @tf.function(input_signature=[tf.TensorSpec(shape=[1, 224, 224, 3], dtype=tf.float32, name="pixel_values")])
    def serving(pixel_values):
        # Normalize/Transpose logic specific to V2 might be needed,
        # but usually HuggingFace TF models follow this pattern.
        x = tf.transpose(pixel_values, perm=[0, 3, 1, 2])
        out = tf_model(pixel_values=x, training=False)
        return {"logits": out.logits}

    tf.saved_model.save(tf_model, "v2_saved_model", signatures={"serving_default": serving})

    converter = tf.lite.TFLiteConverter.from_saved_model("v2_saved_model")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]
    tflite_bytes = converter.convert()
    with open(OUTPUT_V2, "wb") as f:
        f.write(tflite_bytes)
    print(f"✅ V2 Done: {OUTPUT_V2}")

if __name__ == "__main__":
    convert_v1()
    convert_v2()
