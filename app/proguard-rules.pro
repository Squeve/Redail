# App code is tiny; keep it intact so nothing reflective or framework-facing gets stripped.
-keep class com.squeve.redail.** { *; }
-dontwarn org.jetbrains.annotations.**
