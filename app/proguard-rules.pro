# AccessibilityService subclasses are instantiated by Android from the manifest.
# Keep the service class and callbacks intact in minified release builds.
-keep class com.focustube.app.FocusService { *; }
