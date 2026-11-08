package com.karar.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREFS = "karar_tv_prefs";
    private static final String FAVORITES = "favorites";
    private final Handler handler = new Handler();
    private final List<AppEntry> apps = new ArrayList<>();
    private final List<View> appCards = new ArrayList<>();
    private LinearLayout root;
    private GridLayout grid;
    private TextView clockView;
    private TextView hintView;
    private boolean editMode = false;
    private LinkedHashSet<String> favorites = new LinkedHashSet<>();

    private static class AppEntry {
        String label;
        String packageName;
        android.graphics.drawable.Drawable icon;
        AppEntry(String label, String packageName, android.graphics.drawable.Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clockView != null) {
                String value = new SimpleDateFormat("h:mm a  |  EEEE, d MMM", Locale.getDefault())
                        .format(new Date());
                clockView.setText(value);
            }
            handler.postDelayed(this, 30000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        loadFavorites();
        loadApps();
        buildHome();
        handler.post(clockTick);
    }

    @Override protected void onResume() {
        super.onResume();
        loadApps();
        if (root != null) buildHome();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    private void loadFavorites() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(FAVORITES, null);
        favorites.clear();
        if (saved != null) favorites.addAll(saved);
        else {
            Collections.addAll(favorites,
                    "com.google.android.youtube.tv",
                    "com.netflix.ninja",
                    "com.shahid.android",
                    "com.amazon.amazonvideo.livingroom",
                    "com.android.vending",
                    "com.android.tv.settings");
        }
    }

    private void saveFavorites() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(FAVORITES, new HashSet<>(favorites)).apply();
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory("android.intent.category.LEANBACK_LAUNCHER");
        List<ResolveInfo> results = pm.queryIntentActivities(leanback, 0);

        if (results == null || results.isEmpty()) {
            Intent launcher = new Intent(Intent.ACTION_MAIN);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            results = pm.queryIntentActivities(launcher, 0);
        }

        Set<String> seen = new HashSet<>();
        if (results != null) {
            for (ResolveInfo info : results) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                try {
                    CharSequence label = info.loadLabel(pm);
                    apps.add(new AppEntry(
                            label == null ? pkg : label.toString(),
                            pkg,
                            info.loadIcon(pm)));
                } catch (Exception ignored) { }
            }
        }

        apps.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
        // Keep favorites first and in the user's saved order.
        List<AppEntry> ordered = new ArrayList<>();
        for (String pkg : favorites) {
            for (AppEntry app : apps) {
                if (app.packageName.equals(pkg)) ordered.add(app);
            }
        }
        for (AppEntry app : apps) {
            if (!favorites.contains(app.packageName)) ordered.add(app);
        }
        apps.clear();
        apps.addAll(ordered);
    }

    private void buildHome() {
        appCards.clear();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(38), dp(24), dp(38), dp(18));
        GradientDrawable backdrop = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(24, 32, 48), Color.rgb(8, 12, 22), Color.rgb(3, 5, 10)});
        root.setBackground(backdrop);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView brand = new TextView(this);
        brand.setText("KARAR");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(30);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));

        clockView = new TextView(this);
        clockView.setTextColor(Color.rgb(220, 225, 235));
        clockView.setTextSize(15);
        clockView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(clockView);
        root.addView(header);

        TextView section = new TextView(this);
        section.setText(editMode ? "EDIT HOME SCREEN" : "YOUR APPS");
        section.setTextColor(Color.WHITE);
        section.setTextSize(19);
        section.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                -1, dp(44));
        sectionParams.topMargin = dp(24);
        root.addView(section, sectionParams);

        ScrollView scroll = new ScrollView(this);
        grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        int count = 0;
        for (AppEntry app : apps) {
            if (!editMode && !favorites.contains(app.packageName)) continue;
            grid.addView(createAppCard(app, count++));
        }
        scroll.setFillViewport(false);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        hintView = new TextView(this);
        hintView.setText(editMode
                ? "OK: add/remove app     ◀ ▶: reorder favorites     MENU: finish"
                : "Press MENU to customize your apps");
        hintView.setTextColor(Color.rgb(155, 165, 180));
        hintView.setTextSize(13);
        hintView.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(hintView, new LinearLayout.LayoutParams(-1, dp(34)));

        setContentView(root);
        if (!appCards.isEmpty()) appCards.get(0).requestFocus();
        else root.setFocusableInTouchMode(true);
    }

    private View createAppCard(AppEntry app, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setPadding(dp(8), dp(12), dp(8), dp(10));
        card.setBackground(cardBackground());
        card.setContentDescription(app.label + (favorites.contains(app.packageName) ? ", favorite" : ""));

        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(app.icon); } catch (Exception ignored) { }
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        iconParams.gravity = Gravity.CENTER;
        card.addView(icon, iconParams);

        TextView label = new TextView(this);
        label.setText(app.label + (editMode && favorites.contains(app.packageName) ? "  ★" : ""));
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(42));
        labelParams.topMargin = dp(8);
        card.addView(label, labelParams);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(112);
        params.height = dp(142);
        params.setMargins(dp(7), dp(6), dp(7), dp(6));
        card.setLayoutParams(params);
        appCards.add(card);

        card.setOnClickListener(v -> {
            if (editMode) {
                if (favorites.contains(app.packageName)) favorites.remove(app.packageName);
                else favorites.add(app.packageName);
                saveFavorites();
                loadApps();
                buildHome();
            } else {
                launchApp(app.packageName);
            }
        });

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || !editMode) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                moveFavorite(app.packageName, keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            }
            return false;
        });
        return card;
    }

    private StateListDrawable cardBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.argb(90, 255, 255, 255));
        focused.setCornerRadius(dp(17));
        focused.setStroke(dp(2), Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.argb(48, 255, 255, 255));
        normal.setCornerRadius(dp(17));
        normal.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void moveFavorite(String packageName, int direction) {
        if (!favorites.contains(packageName)) return;
        List<String> order = new ArrayList<>(favorites);
        int from = order.indexOf(packageName);
        int to = from + direction;
        if (to < 0 || to >= order.size()) return;
        Collections.swap(order, from, to);
        favorites.clear();
        favorites.addAll(order);
        saveFavorites();
        loadApps();
        buildHome();
        for (int i = 0; i < appCards.size(); i++) {
            if (i < apps.size() && apps.get(i).packageName.equals(packageName)) {
                appCards.get(i).requestFocus();
                break;
            }
        }
    }

    private void launchApp(String packageName) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory("android.intent.category.LEANBACK_LAUNCHER");
                launch.setPackage(packageName);
            }
            if (launch != null) startActivity(launch);
        } catch (Exception ignored) { }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            editMode = !editMode;
            loadApps();
            buildHome();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && editMode) {
            editMode = false;
            buildHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
