# RecallShot release rules.
# AndroidX / Room / WorkManager / ML Kit ship their own consumer keep rules.
# Keep Room database implementation access and app workers explicit for release safety.
-keep class com.recallshot.app.data.RecallShotDatabase { *; }
-keep class com.recallshot.app.workers.** { *; }
-dontwarn org.conscrypt.**
