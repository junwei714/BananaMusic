# Google Sign-In Setup for BananaMusic

This guide will help you set up Google Sign-In for your BananaMusic Android app.

## Prerequisites

1. A Google Cloud Platform (GCP) project
2. Firebase project connected to your Android app
3. SHA-1 certificate fingerprint of your app added to Firebase

## Step 1: Configure Google Cloud Project

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project that's linked to your Firebase project
3. Navigate to "APIs & Services" > "Credentials"
4. Click on "Create Credentials" > "OAuth client ID"
5. Select "Android" as the application type
6. Enter your app's package name (`my.edu.utar.bananamusic`)
7. Enter the SHA-1 certificate fingerprint you used for Firebase
8. Click "Create"
9. Now create a Web OAuth client ID (needed for Firebase Auth):
   - Click on "Create Credentials" > "OAuth client ID" again
   - Select "Web application" as the application type
   - Name it "BananaMusic Web Client"
   - Add authorized JavaScript origins (e.g., `https://bananamusic-xxxx.firebaseapp.com`)
   - Click "Create"
10. Copy the Web client ID - you'll need this in Step 3

## Step 2: Configure Firebase

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to "Authentication" > "Sign-in method"
4. Enable "Google" as a sign-in provider
5. Make sure you've added your SHA-1 fingerprint:
   - Go to "Project settings" (the gear icon)
   - Scroll down to "Your apps" section
   - Select your Android app
   - Add your SHA-1 fingerprint if not already added

## Step 3: Update Your App

1. Open `app/src/main/res/values/strings.xml`
2. Replace the placeholder value in the `web_client_id` string with your actual Web client ID:
   ```xml
   <string name="web_client_id">YOUR_WEB_CLIENT_ID_HERE</string>
   ```

## Step 4: Getting SHA-1 Fingerprint

If you don't know your SHA-1 fingerprint:

### For Debug APK (development)
```
cd android
./gradlew signingReport
```
Look for the SHA-1 under "Task :app:signingReport"

### For Release APK (production)
If you're using a keystore file:
```
keytool -list -v -keystore your_keystore_file.keystore
```

## Step 5: Testing

1. Build and run your app
2. Go to the login screen
3. Tap the "Sign in with Google" button
4. Select a Google account to sign in
5. You should be redirected to the main app screen after successful authentication

## Troubleshooting

### Common Issues:

1. **"Google Sign-In failed" error**:
   - Check that you've added the correct SHA-1 fingerprint to your Firebase project
   - Verify that your `web_client_id` is correct
   - Make sure Google Play Services is up to date on your device

2. **"ERROR_INVALID_CUSTOM_TOKEN" or similar**:
   - Make sure the Google sign-in provider is enabled in Firebase Authentication

3. **"Developer Error" when clicking the Sign-In button**:
   - This typically means your OAuth client ID is incorrect or not properly configured
   - Double-check your web client ID in strings.xml
   - Verify that the SHA-1 fingerprint in Google Cloud Console matches your app

4. **"DEVELOPER_ERROR: Unknown"**:
   - This often occurs when using an emulator with an old or missing Google Play Services
   - Try updating Google Play Services in the emulator or test on a physical device

## Additional Resources

- [Firebase Authentication Documentation](https://firebase.google.com/docs/auth)
- [Google Sign-In for Android Guide](https://developers.google.com/identity/sign-in/android/start-integrating)
- [Firebase Google Auth Documentation](https://firebase.google.com/docs/auth/android/google-signin) 