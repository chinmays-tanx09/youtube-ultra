package app.morphe.extension.shared.spoof;
/** Test stub: the fields the module reads from the app's ClientType (values shaped like the app's; fake UAs). */
public enum ClientType {
  ANDROID_VR_SABR("Android VR", "ANDROID_VR", "fake-vr-ua", false, true, false, true, true),
  ANDROID_VR_DASH("Android VR Downgraded", "ANDROID_VR", "fake-vr-ua", false, true, false, false, true),
  ANDROID_CREATOR("Android Studio", "ANDROID_CREATOR", "com.google.android.apps.youtube.creator/26.10.000 (Linux; U; Android 16) gzip", true, true, false, false, true),
  TV_SIMPLY("TV Simply", "TVHTML5_SIMPLY", "Mozilla/5.0 (PS4; Leanback Shell) fake-tv-ua", true, false, true, false, true),
  TV_DASH("TV Downgraded", "TVHTML5", "fake-tizen-ua", true, false, true, false, true),
  VISIONOS_1_03("visionOS 1.03", "VISIONOS", "com.google.visionosyoutube/1.03 fake", false, false, false, false, true),
  VISIONOS_1_02("visionOS 1.02", "VISIONOS", "com.google.visionosyoutube/1.02 fake", false, false, false, false, true),
  ANDROID("Android", "ANDROID", "com.google.android.youtube/20.10.38 fake", true, true, false, false, true),
  GET_CHANNEL_FROM_ID("Get Channel From ID", "ANDROID", "x", false, false, true, false, true);
  public final String friendlyName, clientName, userAgent; public final boolean canLogin, requireLogin, requireJS, requireSABR, usePlayerEndpoint, supportsOAuth2;
  ClientType(String f, String cn, String ua, boolean canLogin, boolean requireLogin, boolean js, boolean sabr, boolean player){
    friendlyName=f; clientName=cn; userAgent=ua; this.canLogin=canLogin; this.requireLogin=requireLogin; requireJS=js; requireSABR=sabr; usePlayerEndpoint=player; supportsOAuth2=!canLogin && requireLogin; }
}
