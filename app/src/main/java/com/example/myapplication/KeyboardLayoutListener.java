package com.example.myapplication;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

public class KeyboardLayoutListener {
    private final View rootLayout;
    private final OnKeyboardVisibilityListener listener;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener;
    private final int minKeyboardHeight;
    private boolean isKeyboardVisible = false;

    public interface OnKeyboardVisibilityListener {
        void onKeyboardVisible(int keyboardHeight);
        void onKeyboardHidden();
    }

    public KeyboardLayoutListener(View rootLayout, OnKeyboardVisibilityListener listener) {
        this.rootLayout = rootLayout;
        this.listener = listener;
        // Min keyboard height as 25% of screen height
        this.minKeyboardHeight = (int) (rootLayout.getResources().getDisplayMetrics().heightPixels * 0.25);

        layoutListener = () -> {
            View decorView = ((Activity) rootLayout.getContext()).getWindow().getDecorView();
            int screenHeight = decorView.getHeight();
            Rect r = new Rect();
            decorView.getWindowVisibleDisplayFrame(r);
            int keyboardHeight = screenHeight - r.bottom;

            boolean wasKeyboardVisible = isKeyboardVisible;
            isKeyboardVisible = keyboardHeight > minKeyboardHeight;

            if (wasKeyboardVisible != isKeyboardVisible) {
                if (isKeyboardVisible) {
                    listener.onKeyboardVisible(keyboardHeight);
                } else {
                    listener.onKeyboardHidden();
                }
            }
        };
    }

    public void attach() {
        rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    public void detach() {
        rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
    }
}
