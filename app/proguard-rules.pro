# SalonLink R8 / ProGuard rules (release builds only).
# Firestore data is mapped by hand (see data/firestore/*Mappers.kt), not by reflection,
# so no keep rules are needed for our model classes.

# Keep line numbers so crash reports point at real source lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
