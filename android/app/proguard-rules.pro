# SlandBar — ProGuard/R8 rules

# Keep Gson model classes (serialized to DataStore)
-keep class com.slandbar.app.core.model.** { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Accessibility service API (referenced by name in the manifest)
-keep class com.slandbar.app.accessibility.SlandAccessibilityService { *; }
