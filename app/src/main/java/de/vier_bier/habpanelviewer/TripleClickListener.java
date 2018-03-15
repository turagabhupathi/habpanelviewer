package de.vier_bier.habpanelviewer;

import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v4.widget.DrawerLayout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

/**
 * Triple click listener that unlocks a drawer layout on successful pin entry.
 */
class TripleClickListener implements View.OnTouchListener {
    private final DrawerLayout mDrawerLayout;

    private boolean mDraggingPrevented;
    private boolean mMenuLocked;

    private Handler handler = new Handler();

    private int numberOfTaps = 0;
    private long lastTapTimeMs = 0;
    private long touchDownMs = 0;

    private SharedPreferences mPrefs;

    TripleClickListener(DrawerLayout l) {
        mDrawerLayout = l;
    }

    void updateFromPreferences(SharedPreferences prefs) {
        mPrefs = prefs;
        mDraggingPrevented = prefs.getBoolean("pref_prevent_dragging", false);
        mMenuLocked = prefs.getBoolean("pref_lock_menu_enabled", false);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (mMenuLocked) {
            // handle triple click
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownMs = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacksAndMessages(null);

                    if ((System.currentTimeMillis() - touchDownMs) > ViewConfiguration.getTapTimeout()) {
                        //it was not a tap
                        numberOfTaps = 0;
                        lastTapTimeMs = 0;
                        break;
                    }

                    if (numberOfTaps > 0
                            && (System.currentTimeMillis() - lastTapTimeMs) < ViewConfiguration.getDoubleTapTimeout()) {
                        numberOfTaps += 1;
                    } else {
                        numberOfTaps = 1;
                    }

                    lastTapTimeMs = System.currentTimeMillis();

                    if (numberOfTaps == 3) {
                        mMenuLocked = false;

                        SharedPreferences.Editor editor1 = mPrefs.edit();
                        editor1.putBoolean("pref_lock_menu_enabled", false);
                        editor1.apply();

                        //TODO.vb. PIN dialog

                        Toast.makeText(view.getContext(), R.string.menuLockRemoved, Toast.LENGTH_LONG).show();
                        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                        return true;
                    }
            }
        }

        return event.getAction() == MotionEvent.ACTION_MOVE && mDraggingPrevented;
    }
}
