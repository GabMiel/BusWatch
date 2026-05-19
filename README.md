# BusWatch 🚌

**BusWatch** is a school project developed at **Quezon City University** during our 3rd year of college for the subject **Application Development and Emerging Technologies**. It is a comprehensive real-time school bus tracking system designed to provide peace of mind to parents and ensure the safety of students during their daily commute.

The project consists of a multi-module Android application catering to parents, drivers, conductors and administrator, along with an official landing page.

## 🚀 Features

- **Real-time Tracking:** Live GPS monitoring of school buses on an interactive map.
- **Boarding Status:** Instant notifications when students board or deboard the bus.
- **ETA Notifications:** Estimated arrival times to help parents never miss a stop.
- **SOS Alerts:** Emergency signaling from drivers to school administrators.
- **Verified Network:** Secure access limited to authorized parents, drivers, and conductors.
- **Trip Monitoring:** Access to student medical profiles and emergency contacts for administrators during active trips.

## 📁 Project Structure

This project follows a modular architecture to separate concerns between different user roles:

```text
BusWatch/
├── app/          # Parent Application (Main Entry Point)
├── driver/       # Driver & Conductor Module
├── admin/        # School Administrator Module
├── common/       # Shared resources (Models, Utils, UI Components, Styles)
├── web/          # Official Website & Landing Page
└── gradle/       # Build configuration and Version Catalog
```

## 🛠️ Tech Stack & Tools

### Languages & Frameworks
- **Kotlin:** Primary language for Android development.
- **Jetpack Compose:** Modern toolkit for building native UI.
- **XML Layouts:** Used for traditional UI components and fragments.
- **HTML/CSS/JS:** Used for the official website.

### Backend & APIs
- **Firebase:**
  - *Authentication:* Secure user login and registration.
  - *Firestore:* Real-time database for bus locations and student data.
  - *Cloud Messaging (FCM):* Push notifications for alerts and boarding status.
  - *Storage:* Hosting for student and staff profile images.
- **Maps & Location:**
  - *Google Maps SDK:* Core mapping functionality.
  - *OSMDroid & OSM Bonus Pack:* Open-source map alternatives and routing enhancements.
- **Third-Party Services:**
  - *OneSignal:* Advanced notification management.
  - *Cloudinary:* Image management and optimization.
  - *Glide:* Efficient image loading for Android.
  - *OkHttp:* Robust HTTP networking.

## ⚙️ Access & Installation

The project is hosted and available for easy access:

1. **Official Website:** Visit [https://buswatch-434b2.web.app/](https://buswatch-434b2.web.app/) to learn more about the project.
2. **Download & Install:**
   - Go to the website and click the **Download for Android** button.
   - This will lead you to a Google Drive folder containing the APK files.
   - Download the APK and install it on your Android device (ensure "Install from Unknown Sources" is enabled).

## 👥 Development Team

- **Roland Carl A. Verdan** - Project Manager
- **Gabriel Roncake O. Miel** - Lead Back End Developer
- **Jessie James F. Cantones** - Back End Developer
- **Joyce Ann J. Baluyot** - Front End Developer
- **Emilita D. Cristobal** - UI/UX Designer
- **Yancee Exequiel G. Petre** - Documentation

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
© 2026 BusWatch Systems. All rights reserved.
