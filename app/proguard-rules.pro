-keep class ai.onnxruntime.** { *; }
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keep class com.fakeaway.app.core.storage.** { *; }
-keepclassmembers enum com.fakeaway.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn ai.onnxruntime.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**
-dontwarn com.google.mlkit.**
