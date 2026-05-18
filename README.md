# KinMaster - Offline Gemma 4 Tutor

**Saving Ethiopian students from failing - teaches until mastery.**

Built for the Gemma 4 Good Hackathon

---

## What Makes KinMaster Different

KinMaster is NOT just another chatbot. It implements a **persistent teaching algorithm** that:

- Detects when a student says "I don't know"
- Immediately switches from testing to teaching
- Teaches from first principles (assumes zero prior knowledge)
- Uses analogies from the student's own life
- Continues until the student explains in their own words
- Declares mastery only when true understanding is proven

No textbook, no YouTube video, no online course does this.

---

## Technical Implementation

### Core Components

| Component  | Source                  | What I built                                        |
|------------|-------------------------|-----------------------------------------------------|
| LLM Engine | Google Gemma 4 2B E2B   | The AI teacher                                      |
| Inference  | llama.cpp (open source) | Runs model on device                                |
| Vision     | Google ML Kit           | Extracts text from textbook photos                  |
| UI & Logic | Kotlin code             | Teaching algorithm, mastery tracking, user profiles |

### My Original Contributions

1. **Persistent Teaching Protocol** - Custom system prompt that implements my 3-state teaching algorithm (Engagement → Gap Diagnosis → Mastery Declaration)

2. **Mastery Detection System** - Parses `[MASTERY_ACHIEVED]` tokens and saves progress locally

3. **Offline Multimodal Pipeline** - Uses ML Kit preprocessing to enable image understanding without running vision on Gemma 4 (saves RAM)

4. **Context Shift Algorithm** - Custom memory management for long conversations on 2GB RAM phones


###  Dependencies

- **llama.cpp** - Used for efficient inference on resource-constrained devices
- **ML Kit** - Used for offline image labeling and text recognition
- **Gemma 4** - The foundation model

The inference wrapper (`ai_chat.cpp`) is adapted from existing llama.cpp 
---

## The Problem This Solves

Ethiopia's Grade 12 exam pass rate: 3-8% (92% fail)

- 70% have no internet
- 80% live in rural areas
- No libraries, no extra tutors
- Schools closed in conflict zones

KinMaster gives every student a persistent teacher that works offline on $30 phones.

---## Download & Installation

### Step 1: Download the Model

Download Gemma 4 2B E2B quantized (Q3_K_M) from:
**https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/**


### Step 2: Download the APK

The APK is attached to the Kaggle submission. Download it from there.

### Step 3: Load and Learn

1. Install the APK on your Android phone (Android 8+)
2. Open KinMaster
3. Tap Menu → Load Model
4. Select your .gguf model file
5. Start learning!

---

## Video Demo

https://www.youtube.com/watch?v=p9LSSp5dZEA

## License

MIT License

## Acknowledgments

- Google Gemma 4 team
- llama.cpp contributors
- ML Kit team
