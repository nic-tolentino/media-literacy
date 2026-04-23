# Run Guide: Android Studio

To run the **Gemma4ML** project on an Android device or emulator, follow these steps to configure your IDE.

## 1. Import the Project
*   Open **Android Studio**.
*   Select **Open** and choose the `media-literacy` root directory.
*   Wait for the Gradle sync to complete (this may take a minute as it downloads the KMP dependencies).

## 2. Define the Run Configuration
If the run configuration wasn't automatically created by the KMP Wizard:

1.  Click the **Run Configuration** dropdown in the top toolbar (usually says "Add Configuration..." or shows a stop icon).
2.  Select **Edit Configurations...**
3.  Click the **+ (Add New Configuration)** button and select **Android App**.
4.  Set the following fields:
    *   **Name**: `composeApp`
    *   **Module**: `media-literacy.composeApp.main` (or just `composeApp`)
    *   **Deploy**: `Default APK`
    *   **Launch Options**: `Default Activity` (it should automatically detect `org.medialiteracy.MainActivity`).
5.  Click **Apply** and **OK**.

## 3. Run the App
*   Select your target **Emulator** or **Physical Device** from the dropdown.
*   Click the green **Run** button (or press `Shift + F10`).

## 4. Troubeshooting Sync Errors
If you see "Module not found" or red code in the IDE:
1.  Go to **File > Invalidate Caches...**
2.  Select **Clear file system cache and Local History** and click **Invalidate and Restart**.
3.  Once restarted, click the **Elephant Icon (Sync Project with Gradle Files)** in the top right.
