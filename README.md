# Project Code & Setup

## Prerequisites
*   Git installed
*   Android Studio installed
*   An Android device with Developer Options and USB Debugging enabled (Android 14/ API level 34 or higher)

## Setup Instructions

1.  **Clone the Repository:**
    Open a terminal or command prompt and run:
    ```bash
    git clone https://github.com/raphael1-w/Natural-Language-Idea-Inbox.git
    ```

2.  **Open Project in Android Studio:**
    *   Launch Android Studio.
    *   Select 'Open' or 'Import Project'.
    *   Navigate to and select the cloned `Natural-Language-Idea-Inbox` directory. Allow Android Studio to sync the project dependencies.

3.  **Obtain and Place the Summarization Model (Gemma-2 2b):**
    *   Due to file size limitations on GitHub, the Gemma-2 2b model used for summarization must be downloaded separately.
    *   Download the model file (`gemma2-2b-it-cpu-int8.tflite`) from: [https://www.kaggle.com/models/google/gemma-2/tfLite/gemma2-2b-it-cpu-int8](https://www.kaggle.com/models/google/gemma-2/tfLite/gemma2-2b-it-cpu-int8)
    * Create a directory named `llm_model` within `/data/local/tmp/` on your target device. Place the downloaded model file (`gemma2-2b-it-cpu-int8.tflite`) inside this directory. The application expects the file at `/data/local/tmp/llm_model/<model_filename>`.
      *   **Placement method:** You can typically place the file using Android Studio's Device File Explorer (View > Tool Windows > Device Explorer). Permissions might vary by device. The application code currently requires the model at this specific path.
    * *The Whisper Tiny model used for transcription is included within the project's assets and does not require separate download or placement.*

5.  **Build and Run:**
    *   Connect your Android device (with USB Debugging enabled). 
    *   Ensure your device is selected as the deployment target in Android Studio.
    *   Click the 'Run' button in Android Studio to build, install, and launch the application.
