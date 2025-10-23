$karbonetka/ CASHMEINv3 (2024) - Budget Management App
$karbonetka is a sophisticated Android application, developed as an engineering thesis project, designed to offer a robust and intuitive solution for personal budget management. The app features secure authentication, an intelligent daily budget engine, OCR-powered receipt scanning, a visual calendar for reviewing financial history, and customizable themes.
✨ Key Features
This application is built with a focus on security, automation, user experience, and modern technologies.
🔐 Secure & Flexible Authentication
Access to the application is secured by a robust authentication system powered by Firebase Authentication, offering users multiple ways to sign in.
Multiple Sign-In Options: Users can register and log in using their email and password or use their existing Google and Facebook accounts for quick and easy access.
Account Management: The app includes essential features like new user registration and a password reset function for email-based accounts.
Seamless Transition: Upon successful authentication, the user is immediately taken to the main HomeActivity, and the login screen is removed from the back stack for a smooth user experience.
🏡 Main Dashboard (Home Screen)
The Home screen is the app's central control panel where you set your financial goals.
Set Your Budget: Define your total monthly income and your monthly expense target. These two values are the foundation for all of the app's automated calculations.
Smart Updates: When you save a new budget, the app intelligently flags that a change has been made. This prompts the Expenses screen to automatically recalculate your daily spending allowance for the current day according to your new goals.
💰 Intelligent Budgeting Engine
The core of the app is a dynamic daily budgeting system that helps keep your finances on track.
Dynamic Daily Allowance: Based on the goals set on the Home screen, the app calculates a unique spending allowance for each day.
Rollover System: Unspent funds from one day are automatically carried over to the next, increasing its budget. Overspending reduces the next day's allowance, promoting financial discipline.
Automatic End-of-Day Processing: At the start of each new day, the app automatically calculates and saves the financial result from the previous day (surplus or deficit).
🗓️ Financial History Calendar
Visualize your financial performance over time with an interactive calendar.
Visual Interface: Utilizes the Material Calendar View library to provide a clean and intuitive way to browse past days.
Daily Performance Review: Simply tap on any past day to see the final financial result—the amount you saved or the amount by which you overspent your budget.
📸 OCR-Powered Receipt Scanning
Effortlessly digitize your paper receipts.
Flexible Input: Scan receipts in real-time using the camera or import an existing picture from your phone's gallery.
Google ML Kit Integration: Leverages Google's powerful, on-device machine learning for fast and accurate text recognition.
Intelligent Parsing: Implements custom regular expressions (RegEx) tailored to analyze common Polish receipt formats, automatically extracting item names and their prices.
🎨 Customizable User Interface
Personalize the application's appearance to your own preference.
Theme Support: Choose between a Light Theme, a Dark Theme (default), or allow the app to automatically follow your device's System Settings.
Consistent Experience: A centralized BaseActivity ensures that your chosen theme is applied consistently across all application screens.
🛠️ Technology Stack
Programming Languages: A hybrid project utilizing Java and Kotlin.
Platform: Android SDK (Minimum Version: SDK 24 / Android 7.0 Nougat).
Architecture:
A BaseActivity approach for centralized theme management.
Firebase as a Backend-as-a-Service (BaaS).
User Interface (UI):
A combination of traditional XML-based views with Material Design components.
The project is also configured to support Jetpack Compose, Android's modern UI toolkit.
Authentication:
Firebase Authentication
Google Sign-In for Android
Facebook SDK for Android
Backend Services:
Firebase Authentication
Firebase Analytics (for collecting anonymous usage data).
Monetization:
Google AdMob
Machine Learning:
Google ML Kit Text Recognition
Data Handling:
Gson (for object serialization to JSON format).
SharedPreferences (for local data storage).
External Libraries:
Material Calendar View (by Applandeo)
🚀 Getting Started
To get a local copy up and running, follow these simple steps.
Prerequisites
Android Studio (latest version recommended).
An Android Emulator or a physical Android device (API 24+).
Installation
Clone the repository:
code
Sh
git clone https://github.com/your-username/your-repository-name.git
Open the project in Android Studio.
Configure Firebase:
Go to the Firebase Console.
Create a new project.
Add a new Android application to your project. Ensure the package name is com.example.cashmeinv3.
Download the generated google-services.json file and place it in the app/ directory of your project.
In the Firebase console, navigate to the Authentication -> Sign-in method tab and enable the following providers: Email/Password, Google, and Facebook.
Ensure that you have also enabled Firebase Analytics in your project settings.
Sync Gradle Files.
Android Studio should automatically prompt you to sync. If not, go to File > Sync Project with Gradle Files.
Build and Run the application.
Select your target device and click the 'Run' button (▶).
