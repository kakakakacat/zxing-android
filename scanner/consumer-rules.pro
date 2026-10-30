# Enum valueOf/values are used to map barcode format names from Intent extras.
-keepclassmembers enum com.google.zxing.BarcodeFormat {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
