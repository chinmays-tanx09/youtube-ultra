package app.morphe.extension.shared.spoof;
public enum ClientType {
  ANDROID_CREATOR("Android Studio", "com.google.android.apps.youtube.creator/26.10.000 (Linux; U; Android 16) gzip"),
  TV_SIMPLY("TV Simply", "Mozilla/5.0 (PS4; Leanback Shell) fake-tv-ua");
  public final String friendlyName, userAgent;
  ClientType(String f, String ua){ friendlyName=f; userAgent=ua; }
}
