# Messenger (Kotlin + Jetpack Compose) — MVP skeleton

This repository contains a minimal Android project skeleton for a messenger app (Kotlin + Jetpack Compose) configured to use Firebase services.

What is included
- `app/` — Android app module with a placeholder `MainActivity` using Compose.
- Gradle files with Compose and Firebase dependencies (you must add `google-services.json`).

Quick start
1. Install Android Studio (Electric Eel or newer recommended).
2. Open the folder `workspace/messenger` in Android Studio.
3. Add your Firebase `google-services.json` to `app/` if you plan to use Firebase.
4. Sync Gradle and run the app on an emulator or device.

Windows PowerShell build (optional, from project root):
```powershell
./gradlew assembleDebug
```

Next steps I can implement for you
- Firebase Authentication (email/phone/Google)
- Firestore data model for chats/messages
- Real-time messaging via Firestore or WebSocket
- Push notifications (FCM) setup
- UI: chat list, chat screen, attachments

Tell me which next step to implement and I will continue.
