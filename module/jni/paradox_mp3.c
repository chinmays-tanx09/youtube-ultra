/* JNI bridge between paradox.dl.Mp3 and LAME 3.100 (LGPL-2.0-or-later, https://lame.sourceforge.io).
 * Part of YouTube Ultra (GPL-3.0). LAME's source is fetched unmodified by scripts/build-native.sh. */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "lame.h"

#define J(name) Java_paradox_dl_Mp3_##name

JNIEXPORT jstring JNICALL J(nVersion)(JNIEnv *env, jclass c) { return (*env)->NewStringUTF(env, get_lame_version()); }

JNIEXPORT jlong JNICALL J(nOpen)(JNIEnv *env, jclass c, jint rate, jint channels, jint kbps, jint quality) {
  lame_t g = lame_init(); if (!g) return 0;
  int ch = channels >= 2 ? 2 : 1;
  lame_set_in_samplerate(g, rate);
  lame_set_num_channels(g, ch);
  lame_set_mode(g, ch == 2 ? JOINT_STEREO : MONO);
  lame_set_VBR(g, vbr_off);
  lame_set_brate(g, kbps);
  lame_set_quality(g, quality);
  lame_set_write_id3tag_automatic(g, 0);   /* the Java side writes the ID3v2 tag */
  lame_set_bWriteVbrTag(g, 1);              /* reserve the first frame for the Info tag */
  if (lame_init_params(g) < 0) { lame_close(g); return 0; }
  return (jlong)(intptr_t)g;
}

/* Down-mixes >2 channels to stereo (Android order FL FR FC LFE BL BR ...): L = FL + 0.707 FC (+ 0.707 BL), normalised; LFE dropped.
 * Returns a malloc'd interleaved stereo buffer (same sample type) or NULL. */
static void *downmix(const uint8_t *src, int samples, int inCh, int isFloat) {
  int surround = inCh >= 6; float norm = 1.0f / (1.0f + 0.7071f + (surround ? 0.7071f : 0.0f));
  if (isFloat) {
    const float *s = (const float *)src; float *d = (float *)malloc((size_t)samples * 2 * sizeof(float)); if (!d) return NULL;
    for (int i = 0; i < samples; i++) { const float *f = s + (size_t)i * inCh; float c = 0.7071f * f[2];
      float l = f[0] + c + (surround ? 0.7071f * f[4] : 0.0f), r = f[1] + c + (surround ? 0.7071f * f[5] : 0.0f);
      d[2 * i] = l * norm; d[2 * i + 1] = r * norm; }
    return d;
  } else {
    const short *s = (const short *)src; short *d = (short *)malloc((size_t)samples * 2 * sizeof(short)); if (!d) return NULL;
    for (int i = 0; i < samples; i++) { const short *f = s + (size_t)i * inCh; float c = 0.7071f * f[2];
      float l = (f[0] + c + (surround ? 0.7071f * f[4] : 0.0f)) * norm, r = (f[1] + c + (surround ? 0.7071f * f[5] : 0.0f)) * norm;
      d[2 * i] = (short)(l > 32767.f ? 32767 : l < -32768.f ? -32768 : l); d[2 * i + 1] = (short)(r > 32767.f ? 32767 : r < -32768.f ? -32768 : r); }
    return d;
  }
}

JNIEXPORT jint JNICALL J(nEncode)(JNIEnv *env, jclass c, jlong h, jobject buf, jint off, jint bytes, jint channels, jboolean isFloat, jbyteArray out) {
  lame_t g = (lame_t)(intptr_t)h; if (!g) return -100;
  uint8_t *base = (uint8_t *)(*env)->GetDirectBufferAddress(env, buf); if (!base) return -101;
  jlong cap = (*env)->GetDirectBufferCapacity(env, buf); if (off < 0 || bytes < 0 || (jlong)off + bytes > cap) return -102;
  int bps = isFloat ? 4 : 2; int inCh = channels < 1 ? 1 : channels;
  int samples = bytes / (bps * inCh); if (samples <= 0) return 0;
  const uint8_t *src = base + off; void *tmp = NULL;
  if (inCh > 2) { tmp = downmix(src, samples, inCh, isFloat); if (!tmp) return -103; src = (const uint8_t *)tmp; inCh = 2; }
  jsize outLen = (*env)->GetArrayLength(env, out);
  jbyte *o = (jbyte *)(*env)->GetPrimitiveArrayCritical(env, out, NULL); if (!o) { free(tmp); return -104; }
  int n;
  int encCh = lame_get_num_channels(g);
  if (isFloat) {
    if (inCh == 2 && encCh == 2) n = lame_encode_buffer_interleaved_ieee_float(g, (const float *)src, samples, (unsigned char *)o, outLen);
    else if (inCh == 1) n = lame_encode_buffer_ieee_float(g, (const float *)src, (const float *)src, samples, (unsigned char *)o, outLen);
    else { /* stereo input, mono encoder: mix */
      float *m = (float *)malloc((size_t)samples * sizeof(float)); if (!m) { (*env)->ReleasePrimitiveArrayCritical(env, out, o, 0); free(tmp); return -103; }
      const float *s = (const float *)src; for (int i = 0; i < samples; i++) m[i] = 0.5f * (s[2 * i] + s[2 * i + 1]);
      n = lame_encode_buffer_ieee_float(g, m, m, samples, (unsigned char *)o, outLen); free(m); }
  } else {
    if (inCh == 2 && encCh == 2) n = lame_encode_buffer_interleaved(g, (short *)src, samples, (unsigned char *)o, outLen);
    else if (inCh == 1) n = lame_encode_buffer(g, (const short *)src, (const short *)src, samples, (unsigned char *)o, outLen);
    else {
      short *m = (short *)malloc((size_t)samples * sizeof(short)); if (!m) { (*env)->ReleasePrimitiveArrayCritical(env, out, o, 0); free(tmp); return -103; }
      const short *s = (const short *)src; for (int i = 0; i < samples; i++) m[i] = (short)(((int)s[2 * i] + s[2 * i + 1]) / 2);
      n = lame_encode_buffer(g, m, m, samples, (unsigned char *)o, outLen); free(m); }
  }
  (*env)->ReleasePrimitiveArrayCritical(env, out, o, 0);
  free(tmp);
  return n;
}

JNIEXPORT jint JNICALL J(nFlush)(JNIEnv *env, jclass c, jlong h, jbyteArray out) {
  lame_t g = (lame_t)(intptr_t)h; if (!g) return -100;
  jsize outLen = (*env)->GetArrayLength(env, out);
  jbyte *o = (jbyte *)(*env)->GetPrimitiveArrayCritical(env, out, NULL); if (!o) return -104;
  int n = lame_encode_flush(g, (unsigned char *)o, outLen);
  (*env)->ReleasePrimitiveArrayCritical(env, out, o, 0);
  return n;
}

JNIEXPORT jbyteArray JNICALL J(nInfoFrame)(JNIEnv *env, jclass c, jlong h) {
  lame_t g = (lame_t)(intptr_t)h; if (!g) return NULL;
  size_t need = lame_get_lametag_frame(g, NULL, 0); if (need == 0) return NULL;
  unsigned char *b = (unsigned char *)malloc(need); if (!b) return NULL;
  size_t got = lame_get_lametag_frame(g, b, need);
  jbyteArray r = NULL;
  if (got > 0 && got <= need) { r = (*env)->NewByteArray(env, (jsize)got); if (r) (*env)->SetByteArrayRegion(env, r, 0, (jsize)got, (const jbyte *)b); }
  free(b); return r;
}

JNIEXPORT void JNICALL J(nClose)(JNIEnv *env, jclass c, jlong h) { lame_t g = (lame_t)(intptr_t)h; if (g) lame_close(g); }
