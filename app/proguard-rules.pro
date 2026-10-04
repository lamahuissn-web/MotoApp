# Keep the app's own names for anything a stack trace will be read against in the office, and nothing
# else: the office reads crashes, not internals.
-keepnames class com.lamahuissn.motoapp.data.** { *; }

# OkHttp ships its own consumer rules, and the panel's JSON is read key by key with org.json (which is
# part of the platform), so no reflection is used anywhere in this app: no further keep rules are
# needed. Anything added here without a reason is a rule that hides a real shrinking bug.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
