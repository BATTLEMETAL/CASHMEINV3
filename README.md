# SmartBudget-OCR (Engineering Thesis)

**SmartBudget-OCR** (formerly known as *$karbonetka*) is a sophisticated Android application developed as an engineering thesis project. It offers a robust and intuitive solution for personal budget management, featuring secure multi-provider authentication, an intelligent daily budget engine, OCR-powered receipt scanning, and detailed financial analytics.

## ✨ Key Features

This application is built with a focus on security, automation, user experience, and personalization.

### 🔐 Secure & Flexible Authentication
The application is secured by a robust authentication system powered by **Firebase Authentication**.
*   **Multiple Sign-In Options:** Support for Email/Password, **Google**, and **Facebook** login.
*   **Account Management:** Includes registration and a **password reset** function.
*   **Seamless Transition:** Successful login creates a seamless user experience by managing the back stack properly via `HomeActivity`.

### 🏡 Main Dashboard & Budgeting
The core of the app is a dynamic daily budgeting system.
*   **Smart Updates:** Define your total monthly income and expense targets. The app intelligently flags changes and prompts automatic recalculation.
*   **Dynamic Daily Allowance:** Based on goals, the app calculates a unique spending allowance for each day.
*   **Rollover System:** Unspent funds are automatically carried over to the next day, while overspending reduces the future budget to promote financial discipline.

### 📸 OCR-Powered Receipt Scanning
Effortlessly digitize paper receipts using on-device Machine Learning.
*   **Google ML Kit Integration:** Utilizes Google's powerful on-device ML for fast and accurate text recognition.
*   **Intelligent Parsing:** Implements custom `RegEx` algorithms tailored to parse common receipt formats, automatically itemizing products and prices.

### 🗓️ Financial History & Analytics
*   **Visual Calendar:** Uses `Material Calendar View` to browse past financial performance.
*   **Daily Review:** Tap on any date to view the net financial result (surplus or deficit).

### 🎨 Customizable UI
*   **Theme Support:** Light Theme, Dark Theme (default), or System Default.
*   **Consistent Design:** A centralized `BaseActivity` architecture ensures consistent theming across the entire application.

## 🛠️ Technology Stack

*   **Languages:** Java (Core Logic), Kotlin (Extensions).
*   **Platform:** Android SDK (Min SDK: 24 / Android 7.0).
*   **Architecture:** MVC / BaseActivity Pattern (Classic Android Architecture).
*   **Machine Learning:** Google ML Kit Text Recognition.
*   **Backend & Auth:** Firebase (Authentication, Firestore, Analytics).
*   **Libraries:**
    *   Material Components for Android
    *   Material Calendar View (Applandeo)
    *   Gson
    *   Retrofit / OkHttp
    *   MPAndroidChart (Data Visualization)

## 🚀 Getting Started

To get a local copy up and running, follow these steps.

### Prerequisites

*   **Android Studio** (Latest version recommended).
*   **Android Device/Emulator** (API 24+).

### Installation

1.  **Clone the repository:**
    ```sh
    git clone https://github.com/BATTLEMETAL/SmartBudget-OCR.git
    ```
2.  **Open the project in Android Studio.**
    Let the Gradle sync finish downloading dependencies.

3.  **Configure Firebase:**
    > **Note:** The internal package name is `com.example.cashmeinv3`. You must use this exact package name in the Firebase Console.
    *   Go to the [Firebase Console](https://console.firebase.google.com/).
    *   Create a new project.
    *   Add an Android app with package name: `com.example.cashmeinv3`.
    *   Download `google-services.json` and place it in the project's `app/` directory.
    *   Enable **Email/Password**, **Google**, and **Facebook** in the *Authentication* tab.

4.  **Build and Run:**
    *   Select your target device and click **Run** (▶).

---
*Created by Michał Zalewski*
