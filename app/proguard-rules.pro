# ProGuard rules for Vault T

# Keep Hilt and Dagger classes
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.** { *; }

# Keep Room classes
-keep class androidx.room.** { *; }

# Tink and Google Crypto
-keep class com.google.crypto.tink.** { *; }

# Jetpack Compose
-keep class androidx.compose.** { *; }
