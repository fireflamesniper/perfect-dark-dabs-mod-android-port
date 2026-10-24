package com.dabdavis.perfectdark;

import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

/**
 * The game is SDL's activity with the game's library: SDL creates the GLES
 * surface, reads controllers and starts SDL_main (port/src/main.c) on its own
 * thread.
 */
public class PDActivity extends SDLActivity {
    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // the picture runs under the camera cutout too, rather than stopping
        // short of it in landscape
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // the on-screen controls, over the game (not when the libraries failed
        // to load: SDL shows its error instead, and there is no game to drive)
        if (!mBrokenLibraries && mLayout != null) {
            mLayout.addView(new TouchControlsView(this),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
}
