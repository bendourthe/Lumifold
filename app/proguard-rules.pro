# Proguard rules for Ambient Fold Wallpaper

# Retain live wallpaper service and its inner Engine
-keep class com.ambientfold.wallpaper.service.FoldWallpaperService { *; }
-keep class com.ambientfold.wallpaper.service.FoldWallpaperService$FoldEngine { *; }
-keep class com.ambientfold.wallpaper.service.HingeSensorObserver { *; }

# Retain model classes and enums
-keep class com.ambientfold.wallpaper.model.** { *; }
-keepclassmembers enum com.ambientfold.wallpaper.model.** { *; }

# Retain activity for launcher and settings
-keep class com.ambientfold.wallpaper.ui.MainActivity { *; }
