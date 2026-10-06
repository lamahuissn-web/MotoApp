# Keep the app's own names for anything a stack trace will be read against in the office, and nothing
# else: the office reads crashes, not internals.
-keepnames class com.lamahuissn.motoapp.data.** { *; }

# OkHttp ships its own consumer rules, and the panel's JSON is read key by key with org.json (which is
# part of the platform), so the app itself uses no reflection — with ONE exception, and it needs a rule:
# WorkManager builds its workers by class NAME, so R8 renaming QueueSyncWorker would leave a job that
# cannot run when the phone is in the collector's pocket.
-keep class com.lamahuissn.motoapp.work.QueueSyncWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
