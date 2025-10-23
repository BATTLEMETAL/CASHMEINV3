# $karbonetka (2024) - Budget Management App

`$karbonetka` is a sophisticated Android application, developed as an engineering thesis project, designed to offer a robust and intuitive solution for personal budget management. It features secure multi-provider authentication, an intelligent daily budget engine, OCR-powered receipt scanning, a visual calendar for financial history, customizable themes, and is supported by Google AdMob.

## ✨ Key Features

This application is built with a focus on security, automation, user experience, and personalization.

### 🔐 Secure & Flexible Authentication
The application is secured by a robust authentication system powered by **Firebase Authentication**, providing users with multiple ways to sign in.
*   **Multiple Sign-In Options:** Users can register and log in using their email and password, or use their existing **Google** or **Facebook** accounts for quick and easy access.
*   **Account Management:** Includes essential features like new user registration and a **password reset** function for email-based accounts.
*   **Seamless Transition:** Upon successful authentication, the user is immediately taken to the main `HomeActivity`, and the login screen is removed from the back stack for a smooth user experience.

### 🏡 Main Dashboard (Home Screen)
The Home screen is the app's central control panel where you set your financial goals.
*   **Set Your Budget:** Define your **total monthly income** and your **monthly expense target**. These two values are the foundation for all of the app's automated calculations.
*   **Smart Updates:** When you save a new budget, the app intelligently flags that a change has been made, prompting an automatic recalculation of your daily spending allowance.

### 💰 Intelligent Budgeting Engine
The core of the app is a dynamic daily budgeting system that helps you stay on track.
*   **Dynamic Daily Allowance:** Based on the goals you set, the app calculates a unique spending allowance for each day.
*   **Rollover System:** Unspent funds from one day are automatically carried over to the next. Overspending reduces the next day's budget, promoting financial discipline.
*   **End-of-Day Processing:** At the start of each new day, the app automatically calculates and saves the previous day's net financial result (surplus or deficit).

### 🗓️ Financial History Calendar
Visualize your financial performance over time with an interactive calendar.
*   **Visual Interface:** Uses the `Material Calendar View` library to provide a clean way to browse past dates.
*   **Daily Performance Review:** Tap on any past day to see your final net financial result—either the amount you saved or overspent.

### 📸 OCR-Powered Receipt Scanning
Effortlessly digitize your paper receipts.
*   **Flexible Input:** Scan receipts in real-time using the device **camera** or import an existing picture from your phone's **gallery**.
*   **Google ML Kit Integration:** Utilizes Google's powerful on-device Machine Learning for fast and accurate text recognition.
*   **Intelligent Parsing:** Implements custom regular expressions (`RegEx`) tailored to parse common Polish receipt formats, automatically itemizing products and their final prices.

### 🎨 Customizable User Interface
Personalize the application's appearance to your preference.
*   **Theme Support:** Choose between a **Light Theme**, a **Dark Theme** (default), or have the app automatically follow your device's **System Settings**.
*   **Consistent Experience:** A centralized `BaseActivity` ensures that your chosen theme is applied consistently across all screens.

### Monetization
*   **Google AdMob:** The application is integrated with Google AdMob to display ads, supporting the project's continued development.

## 🛠️ Technology Stack

*   **Languages:** A hybrid project utilizing **Java** and **Kotlin**.
*   **Platform:** **Android SDK** (Minimum Version: SDK 24 / Android 7.0 Nougat).
*   **Architecture:** A `BaseActivity` approach for centralized theme management.
*   **UI Components:**
    *   **Material Components for Android**
    *   **Material Calendar View (by Applandeo)**
*   **Machine Learning:**
    *   **Google ML Kit Text Recognition**
*   **Data Handling:**
    *   **Gson**
*   **Authentication & Backend:**
    *   **Firebase Authentication**
    *   **Google Sign-In for Android**
    *   **Facebook SDK for Android**
    *   **Firebase Analytics**
*   **Monetization:**
    *   **Google AdMob**

## 🚀 Getting Started

To get a local copy up and running, follow these simple steps.

### Prerequisites

*   **Android Studio** (latest version recommended).
*   An **Android Emulator** or a physical **Android device** (API 24+).

### Installation

1.  **Clone the repository:**
    ```sh
    git clone https://github.com/your-username/your-repository-name.git
    ```
2.  **Open the project in Android Studio.**
3.  **Configure Firebase:**
    *   Go to the [Firebase Console](https://console.firebase.google.com/).
    *   Create a new project.
    *   Add a new Android application to your project. Ensure the package name is `com.example.cashmeinv3`.
    *   Download the generated `google-services.json` file and place it in the project's `app/` directory.
    *   In the Firebase console, navigate to the **Authentication -> Sign-in method** tab and enable the **Email/Password**, **Google**, and **Facebook** providers.
4.  **Sync Gradle Files.**
    *   Android Studio should automatically prompt you to sync. If not, go to `File > Sync Project with Gradle Files`.
5.  **Build and Run the application.**
    *   Select your target device and click the 'Run' button (▶).
