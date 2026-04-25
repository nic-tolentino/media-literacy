# Gemma 4: Media Literacy Engine 🛰️🧠

> [!IMPORTANT]
> **Documentation Hygiene**: It is a core requirement of this project to keep documentation (README, `./docs`) and in-code comments (KDoc) perfectly aligned with the implementation. Any architectural change or feature update MUST be reflected in the relevant documentation to ensure total clarity for both human developers and AI agents.

**Gemma 4: Media Literacy Engine** is a local-first mobile application designed to empower digital equity and safety. Built with Kotlin Multiplatform and powered by on-device AI (Gemma 4), it analyzes news sources for logical fallacies, biases, and structural integrity—all 100% offline.

This project is a submission for the [Gemma 4 Kaggle competition: "Gemma 4 Good"](https://www.kaggle.com/competitions/gemma-4-good-hackathon).

## 🎯 Objective
To provide a privacy-first, secure environment where users can evaluate the news critically. By leveraging local inference and structured analytical prompts, the engine exposes the internal "logic" behind media narratives.

Instead of acting as an infallible arbiter of truth, the app acts as a local logic tutor. It does not judge the *facts*; it evaluates the *structural logic* and *evidence quality* of the arguments presented.

## 🚀 Key Features

* **Structured Structural Analysis**: Migrated from brittle text to a high-reliability **JSON Data Paradigm**, ensuring 100% precision in metric extraction. 🧬
* **The Truth Radar**: Dynamic 4-axis visualization (Logic, Evidence, Credibility, Objectivity) calibrated on an intuitive "High = Good" 0-100 scale. 📊
* **Sticky-Session Logic Master**: A persistent conversation thread that maintains up to 4096 tokens of context across analysis and chat turns. 🧠
* **Native Memory Safety**: Optimized for Android with a **Mutex-locked Actor pattern** that prevents SIGSEGV crashes during back-to-back inference. 🛡️
* **Local-First Privacy**: 100% on-device inference using LiteRT-LM. No data ever leaves the device. 🔒

## 🏗️ Technology Stack

* **Platform**: Kotlin Multiplatform (Android & iOS) utilizing Compose Multiplatform.
* **Model Engine**: LiteRT-LM Android SDK (v0.10.0) for high-performance on-device inference.
* **Architecture**: MVI/ScreenModel (Voyager) with a centralized `GemmaOrchestrator` managing the KMP inference lifecycle.
* **Data Layer**: `kotlinx-serialization` for formal schema-based analytical extraction.

## 📂 Documentation

* [Project Architecture & Design Decisions](./docs/ARCHITECTURE.md)
* [High-Level Hackathon Roadmap](./docs/PLAN.md)
* [Sample Articles & Test Cases](./docs/SAMPLE_ARTICLES.md)
