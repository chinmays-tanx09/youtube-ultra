package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.exceptions.*;
import org.schabi.newpipe.extractor.services.youtube.ItagItem; import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import java.util.*; import java.util.concurrent.Callable;
/** Finds the streams for a video. The public extractor goes first (unchanged behaviour for public videos);
 *  when it can't (members-only, "sign in to confirm", age check, ...) and the app is signed in, the video is
 *  fetched with the account (see Account). The result is a normal StreamInfo, so the quality sheet, Formats,
 *  the engine and URL refresh all work the same for both. */
public final class Resolve {
  public static final String MEMBERS="This video needs a channel membership on your account";
  public static final String MEMBERS_SIGNED_OUT=MEMBERS+". Sign in to YouTube Ultra with that account to download it.";
  /** An error whose message is meant to be shown as is. */
  public static final class Plain extends java.io.IOException { public Plain(String m){ super(m); } }

  public static StreamInfo info(final String url) throws Exception {
    Net.init();
    return resolve(url, new Callable<StreamInfo>(){ public StreamInfo call() throws Exception { return StreamInfo.getInfo(ServiceList.YouTube, url); } }); }

  static StreamInfo resolve(String url, Callable<StreamInfo> publicPath) throws Exception {
    Exception first;
    try { StreamInfo i=publicPath.call(); if (usable(i)) return i; first=new ExtractionException("no downloadable formats"); }
    catch (Exception e){ first=e; }
    Map<String,String> h=Account.headers();
    if (h!=null) { String id=videoId(url); if (id!=null) { StreamInfo s=signed(id, url, Account.fetch(id, h)); if (s!=null) return s; } }
    if (membersOnly(first)) throw new Plain(h!=null? MEMBERS: MEMBERS_SIGNED_OUT);
    throw first; }

  static boolean membersOnly(Throwable e){ for (Throwable t=e; t!=null; t=t.getCause()) { if (t instanceof PaidContentException) return true;
      String m=String.valueOf(t.getMessage()).toLowerCase(Locale.US); if (m.contains("members-only") || m.contains("members of the channel") || m.contains("join this channel")) return true; } return false; }
  static boolean usable(StreamInfo i){ return i!=null && (!Formats.videoChoices(i).isEmpty() || !Formats.audioChoices(i).isEmpty()); }
  static String videoId(String url){ try { return YoutubeStreamLinkHandlerFactory.getInstance().getId(url); } catch (Throwable t){ return null; } }

  /** StreamInfo from an account fetch, or null when it is missing / not playable / has no direct streams. */
  static StreamInfo signed(String id, String url, Account.Result r){
    if (r==null) return null; PlayerProto.Player p=PlayerProto.parse(r.player); if (p==null || !p.playable()) return null;
    StreamInfo s=toInfo(p, id, url); if (s==null || !usable(s)) return null;
    for (VideoStream v: s.getVideoStreams()) Net.uaFor(v.getContent(), r.userAgent);
    for (VideoStream v: s.getVideoOnlyStreams()) Net.uaFor(v.getContent(), r.userAgent);
    for (AudioStream a: s.getAudioStreams()) Net.uaFor(a.getContent(), r.userAgent);
    return s; }

  /** Same shape as the public extractor's output: progressive / video-only / audio lists of direct HTTP streams. */
  public static StreamInfo toInfo(PlayerProto.Player p, String id, String url){
    try {
      StreamInfo s=new StreamInfo(ServiceList.YouTube.getServiceId(), url, url, StreamType.VIDEO_STREAM, p.videoId.isEmpty()? id: p.videoId, p.title.isEmpty()? "YouTube video": p.title, 0);
      s.setUploaderName(p.author); if (!p.channelId.isEmpty()) s.setUploaderUrl("https://www.youtube.com/channel/"+p.channelId); s.setDuration(p.lengthSeconds);
      List<Image> th=new ArrayList<>(); for (PlayerProto.Thumb t: p.thumbs) th.add(new Image(t.url, t.height>0? t.height: Image.HEIGHT_UNKNOWN, t.width>0? t.width: Image.WIDTH_UNKNOWN, Image.ResolutionLevel.fromHeight(t.height)));
      if (th.isEmpty()) th.add(new Image("https://i.ytimg.com/vi/"+s.getId()+"/hqdefault.jpg", 360, 480, Image.ResolutionLevel.MEDIUM));
      s.setThumbnails(th);
      List<VideoStream> muxed=new ArrayList<>(), vonly=new ArrayList<>(); List<AudioStream> audio=new ArrayList<>();
      for (PlayerProto.Fmt f: p.formats) { if (!f.hasUrl() || !ItagItem.isSupported(f.itag)) continue;
        ItagItem it; try { it=new ItagItem(ItagItem.getItag(f.itag)); } catch (Exception e){ continue; }
        if (f.bitrate>0) it.setBitrate(f.bitrate); if (f.width>0) it.setWidth(f.width); if (f.height>0) it.setHeight(f.height);
        String codec=f.codec(); if (codec!=null) it.setCodec(codec); if (f.contentLength>0) it.setContentLength(f.contentLength); if (f.approxDurationMs>0) it.setApproxDurationMs(f.approxDurationMs);
        if (!f.qualityLabel.isEmpty()) it.setQuality(f.qualityLabel);
        if (it.itagType==ItagItem.ItagType.AUDIO) {
          if (f.sampleRate>0) it.setSampleRate((int)f.sampleRate); if (f.channels>0) it.setAudioChannels(f.channels);
          audio.add(new AudioStream.Builder().setId(String.valueOf(f.itag)).setContent(f.url, true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(f.averageBitrate>0? f.averageBitrate/1000: it.getAverageBitrate()).setItagItem(it).build());
        } else {
          if (f.fps>0) it.setFps(f.fps);
          String res=!f.qualityLabel.isEmpty()? f.qualityLabel: (f.height>0? f.height+"p": it.getResolutionString());
          VideoStream v=new VideoStream.Builder().setId(String.valueOf(f.itag)).setContent(f.url, true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(it.itagType==ItagItem.ItagType.VIDEO_ONLY).setResolution(res).setItagItem(it).build();
          if (it.itagType==ItagItem.ItagType.VIDEO_ONLY) vonly.add(v); else muxed.add(v);
        } }
      s.setVideoStreams(muxed); s.setVideoOnlyStreams(vonly); s.setAudioStreams(audio);
      return s;
    } catch (Exception e){ Account.log("account: could not read streams: "+e.getClass().getSimpleName()); return null; } }
}
