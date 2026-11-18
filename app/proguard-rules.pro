# SalonLink R8 / ProGuard rules (release builds only).
# Firestore maps documents to our data classes by reflection, so keep their names.
# DTO classes will live in this package from Phase 2.
-keep class com.group12.salonlink.data.firestore.dto.** { *; }

# Keep line numbers so crash reports point at real source lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
