#include <PR/ultratypes.h>
#include <PR/os_thread.h>
#include <PR/os_cont.h>
#include "platform.h"

#ifdef PLATFORM_ANDROID

#include <jni.h>
#include "input.h"

/**
 * The on-screen controls' way in: TouchControlsView (android/app/src/main/
 * java/com/dabdavis/perfectdark) draws them over SDL's surface and calls this
 * from the UI thread whenever what is held changes. inputSetTouchPad() keeps
 * it for inputReadController() to read on the game's thread.
 */
JNIEXPORT void JNICALL Java_com_dabdavis_perfectdark_TouchControlsView_nativeSetPad(JNIEnv *env, jclass cls,
		jint buttons, jfloat lx, jfloat ly, jfloat rx, jfloat ry)
{
	const f32 axes[4] = { lx, ly, rx, ry };
	inputSetTouchPad((u32)buttons, axes);
}

#endif
