# Dhun — local music player (Android)

Phone ke apne gaane chalane ke liye. Isme na ads hain, na account, aur internet ki zaroorat bhi nahi.

## Features
- **Smart shuffle:** har playlist, album, artist, folder aur "All songs" ka apna cycle hota hai. Shuffle mein pehle wo gaane bajte hain jo is cycle mein abhi tak nahi sune. Sune hue gaane baad mein aate hain, sabse purane sune hue pehle. Saare gaane sun lene pe naya cycle apne aap shuru ho jaata hai. Koi gaana 30 sec (ya chhote gaane ka aadha) chalne ke baad "heard" mana jaata hai.
- **Background mein bina ruke:** foreground media service, notification aur lock-screen controls, headphone nikalne pe pause, call aane pe pause aur call ke baad resume.
- **Crossfade (0–12 sec):** gaana khatam hone se pehle dheere fade out hota hai aur agla gaana fade in hoke saath mein shuru hota hai, Spotify jaisa. Settings mein slider hai.
- App recents se swipe karne pe bhi music chalta rehta hai.
- App dobara kholne pe pichla gaana usi jagah se continue hota hai.
- Library mein Songs, Playlists, Albums, Artists aur Folders, saath mein search.
- **Duplicate finder:** Settings → "Find & delete duplicate songs". Har gaane ki best-quality copy rakhta hai, baaki ek saath delete karta hai; playlists aur history bachi hui copy pe shift ho jaati hai.
- Playlists bana sakte ho, rename/delete kar sakte ho, gaane add/remove kar sakte ho. "Play next" aur "Add to queue" bhi hai.
- Dark UI, album art, aur Now Playing screen pe blurred background.

## Build
GitHub Actions har push pe release APK banata hai. Latest APK yahan milega:
`https://github.com/<user>/Dhun/releases/download/latest/Dhun.apk`

Local build ke liye: Android Studio mein project kholo, phir **Build → Build APK(s)**.

Tech: Kotlin, Jetpack Compose, Media3 (ExoPlayer + MediaSessionService), Coil.
