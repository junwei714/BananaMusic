# TensorFlow Lite Model Integration

This directory contains assets for the BananaMusic app, including placeholder files for TensorFlow Lite models that should be replaced with real trained models.

## Current Status

The file `mood_classifier.tflite` is a **placeholder text file** and not a real TensorFlow Lite model. You need to replace it with an actual trained model for the app to work properly.

## How to Add a Real TensorFlow Lite Model

1. Train or download a TensorFlow Lite model for mood classification
2. Name the model file `mood_classifier.tflite`
3. Replace the placeholder file in this directory with your actual model file
4. The model should expect text input and output emotion/mood probabilities

## Model Requirements

- Format: TensorFlow Lite (.tflite)
- Input: Text (string) representing user's mood or description
- Output: Probability scores for emotions (happy, sad, energetic, relaxed, etc.)
- Size: Typically 1MB-10MB for a reasonably complex model

## Fallback Behavior

If a valid model is not available, the app will automatically fall back to keyword-based mood detection, but with reduced accuracy. 