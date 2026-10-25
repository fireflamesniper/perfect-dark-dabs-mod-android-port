package com.dabdavis.perfectdark;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * Where the game keeps its files: PD-Dab in the phone's own storage
 * (/storage/emulated/0/PD-Dab), which any file manager or a computer over USB
 * can reach, so a ROM, a mod's zip or a texture pack is copied straight in.
 *
 * Reaching a folder there needs "All files access" from Android 11 on, and the
 * storage permission before it, so this asks first and starts the game once it
 * has it. The folder is handed to the game in PD_DATA_DIR (port/src/system.c).
 * Refused, the game keeps to the app's own folder under Android/data/, which
 * needs no permission. PDActivity sets PD_DATA_DIR itself, from whether
 * access is granted, so a game Android restores without this screen agrees.
 */
public class SetupActivity extends Activity {
    public static final String DIR_NAME = "PD-Dab";
    // PDActivity's extra: the player chose the app's own folder
    public static final String EXTRA_APP_FOLDER = "appFolder";
    private static final int REQUEST_STORAGE = 1;

    private boolean askedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (hasAccess()) {
            startGame(true);
            return;
        }
        showExplanation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // back from the settings page
        if (askedOnce && hasAccess()) {
            startGame(true);
        }
    }

    private static File gameDir() {
        return new File(Environment.getExternalStorageDirectory(), DIR_NAME);
    }

    private boolean hasAccess() {
        return hasAccess(this);
    }

    static boolean hasAccess(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * PD-Dab to the game, where it may be used: made (with data/, where the
     * ROM goes, so it is there to find) and put in PD_DATA_DIR, which
     * sysGetAndroidPath() reads.
     */
    static void exportGameDir(Activity activity) {
        if (!hasAccess(activity)) {
            return;
        }
        File dir = gameDir();
        new File(dir, "data").mkdirs();
        if (dir.isDirectory()) {
            try {
                Os.setenv("PD_DATA_DIR", dir.getAbsolutePath(), true);
            } catch (ErrnoException ignored) {
            }
        }
    }

    private void askAccess() {
        askedOnce = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_STORAGE && hasAccess()) {
            startGame(true);
        }
    }

    private void startGame(boolean shared) {
        Intent intent = new Intent(this, PDActivity.class);
        if (!shared) {
            intent.putExtra(EXTRA_APP_FOLDER, true);
        }
        startActivity(intent);
        finish();
    }

    private void showExplanation() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(32);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Perfect Dark Dabs Mod");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText("The game keeps its files in a folder called " + DIR_NAME
                + " in your phone's storage, so you can copy the ROM, mods and texture packs "
                + "into it with any file manager or from a computer.\n\n"
                + "Put your ROM at " + DIR_NAME + "/data/pd.ntsc-final.z64\n\n"
                + "Android asks you to allow file access for that.");
        body.setTextColor(0xFFCCCCCC);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(16), 0, dp(24));
        root.addView(body);

        Button allow = new Button(this);
        allow.setText("Allow file access");
        allow.setOnClickListener(v -> askAccess());
        root.addView(allow);

        Button skip = new Button(this);
        skip.setText("Use the app's own folder instead");
        skip.setOnClickListener(v -> startGame(false));
        root.addView(skip);

        setContentView(root);
        // a controller's D-pad and A work these buttons from the start
        allow.requestFocus();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
