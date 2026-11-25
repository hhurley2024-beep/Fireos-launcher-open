package com.example.firehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Fire-TV-style home screen: top tabs + clock, a hero area that follows focus,
 * a row of your apps, a row of quick settings, and a full app grid tab.
 */
public class MainActivity extends Activity {

    /** One tile: an app, or a settings shortcut (when image == null). */
    static class AppEntry {
        String label;
        String pkg;          // null for settings shortcuts
        Drawable image;      // banner or icon; null -> text card
        boolean banner;
        Intent intent;
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();
    private final List<AppEntry> quick = new ArrayList<>();

    private TileAdapter rowAdapter;
    private TileAdapter gridAdapter;
    private TileAdapter quickAdapter;

    private View homePage;
    private View appsPage;
    private TextView tabHome;
    private TextView tabApps;
    private TextView heroTitle;
    private TextView heroSub;
    private ImageView heroIcon;
    private RecyclerView rowApps;

    private boolean onHomeTab = true;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        homePage = findViewById(R.id.homePage);
        appsPage = findViewById(R.id.appsPage);
        tabHome = findViewById(R.id.tabHome);
        tabApps = findViewById(R.id.tabApps);
        heroTitle = findViewById(R.id.heroTitle);
        heroSub = findViewById(R.id.heroSub);
        heroIcon = findViewById(R.id.heroIcon);
        rowApps = findViewById(R.id.rowApps);
        RecyclerView rowQuick = findViewById(R.id.rowQuick);
        RecyclerView grid = (RecyclerView) appsPage;

        rowAdapter = new TileAdapter(apps, true);
        quickAdapter = new TileAdapter(quick, true);
        gridAdapter = new TileAdapter(apps, false);

        rowApps.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowApps.setAdapter(rowAdapter);
        rowQuick.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowQuick.setAdapter(quickAdapter);
        grid.setLayoutManager(new GridLayoutManager(this, 5));
        grid.setAdapter(gridAdapter);

        // Focusing a tab switches to it, like the real thing.
        tabHome.setOnFocusChangeListener((v, focused) -> {
            if (focused) showPage(true);
        });
        tabApps.setOnFocusChangeListener((v, focused) -> {
            if (focused) showPage(false);
        });
        tabHome.setOnClickListener(v -> showPage(true));
        tabApps.setOnClickListener(v -> showPage(false));

        buildQuickSettings();
        showDefaultHero();
        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(packageReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** Pressing Home while we're already the home screen lands here (singleTask). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showPage(true);
        rowApps.scrollToPosition(0);
        focusFirstTile();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // A launcher should never exit. Back from Apps returns to Home.
        if (!onHomeTab) {
            tabHome.requestFocus();
            showPage(true);
            focusFirstTile();
        }
    }

    // ---------------------------------------------------------------- pages

    private void showPage(boolean home) {
        onHomeTab = home;
        homePage.setVisibility(home ? View.VISIBLE : View.GONE);
        appsPage.setVisibility(home ? View.GONE : View.VISIBLE);
        tabHome.setTextColor(home ? 0xFFFFFFFF : 0x99FFFFFF);
        tabApps.setTextColor(home ? 0x99FFFFFF : 0xFFFFFFFF);
        if (home) showDefaultHero();
    }

    private void focusFirstTile() {
        ui.postDelayed(() -> {
            RecyclerView target = onHomeTab ? rowApps : (RecyclerView) appsPage;
            RecyclerView.ViewHolder vh = target.findViewHolderForAdapterPosition(0);
            if (vh != null) {
                vh.itemView.findViewById(R.id.card).requestFocus();
            }
        }, 120);
    }

    // ----------------------------------------------------------------- hero

    private void showDefaultHero() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        heroTitle.setText(greeting);
        heroSub.setText("Pick an app to get started");
        heroIcon.setImageDrawable(null);
    }

    private void showHero(AppEntry e) {
        heroTitle.setText(e.label);
        heroSub.setText(e.pkg != null ? "Press OK to open  •  Hold OK for app info" : "Press OK to open");
        heroIcon.setImageDrawable(e.image);
    }

    // ----------------------------------------------------------- data loading

    private void buildQuickSettings() {
        quick.clear();
        addQuick("Settings", Settings.ACTION_SETTINGS);
        addQuick("Wi-Fi", Settings.ACTION_WIFI_SETTINGS);
        addQuick("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS);
        addQuick("Display", Settings.ACTION_DISPLAY_SETTINGS);
        addQuick("Sound", Settings.ACTION_SOUND_SETTINGS);
        addQuick("All apps", Settings.ACTION_APPLICATION_SETTINGS);
        quickAdapter.notifyDataSetChanged();
    }

    private void addQuick(String label, String action) {
        AppEntry e = new AppEntry();
        e.label = label;
        e.intent = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        quick.add(e);
    }

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final String self = getPackageName();
        io.execute(() -> {
            Map<String, AppEntry> map = new LinkedHashMap<>();
            String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
            for (String cat : categories) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(self) || map.containsKey(pkg)) continue;

                    AppEntry e = new AppEntry();
                    e.pkg = pkg;
                    e.label = String.valueOf(ri.loadLabel(pm));
                    Drawable b = ri.activityInfo.loadBanner(pm);
                    if (b != null) {
                        e.image = b;
                        e.banner = true;
                    } else {
                        e.image = ri.loadIcon(pm);
                    }
                    e.intent = new Intent(Intent.ACTION_MAIN)
                            .addCategory(cat)
                            .setClassName(pkg, ri.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    map.put(pkg, e);
                }
            }
            final List<AppEntry> sorted = new ArrayList<>(map.values());
            Collections.sort(sorted, (a, b) -> a.label.compareToIgnoreCase(b.label));

            ui.post(() -> {
                apps.clear();
                apps.addAll(sorted);
                rowAdapter.notifyDataSetChanged();
                gridAdapter.notifyDataSetChanged();
                if (getCurrentFocus() == null) focusFirstTile();
            });
        });
    }

    // ---------------------------------------------------------------- actions

    private void launch(AppEntry e) {
        try {
            startActivity(e.intent);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppInfo(AppEntry e) {
        if (e.pkg == null) return;
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", e.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "App info isn't available", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- adapter

    private class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {
        private final List<AppEntry> data;
        private final boolean driveHero;

        TileAdapter(List<AppEntry> data, boolean driveHero) {
            this.data = data;
            this.driveHero = driveHero;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tile, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            h.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final View root;
            final View card;
            final ImageView image;
            final TextView cardText;
            final TextView label;

            VH(View v) {
                super(v);
                root = v.findViewById(R.id.root);
                card = v.findViewById(R.id.card);
                image = v.findViewById(R.id.image);
                cardText = v.findViewById(R.id.cardText);
                label = v.findViewById(R.id.label);
            }

            void bind(final AppEntry e) {
                label.setText(e.label);
                if (e.image != null) {
                    image.setVisibility(View.VISIBLE);
                    cardText.setVisibility(View.GONE);
                    image.setImageDrawable(e.image);
                    image.setScaleType(e.banner ? ImageView.ScaleType.CENTER_CROP
                                                : ImageView.ScaleType.FIT_CENTER);
                    int pad = e.banner ? 0 : dp(18);
                    image.setPadding(pad, pad, pad, pad);
                } else {
                    image.setVisibility(View.GONE);
                    cardText.setVisibility(View.VISIBLE);
                    cardText.setText(e.label);
                }

                card.setOnClickListener(v -> launch(e));
                card.setOnLongClickListener(v -> {
                    showAppInfo(e);
                    return true;
                });
                card.setOnFocusChangeListener((v, focused) -> {
                    float s = focused ? 1.12f : 1f;
                    root.animate().scaleX(s).scaleY(s).setDuration(120).start();
                    root.setTranslationZ(focused ? dp(8) : 0);
                    label.animate().alpha(focused ? 1f : 0f).setDuration(120).start();
                    if (focused && driveHero) showHero(e);
                });
            }
        }
    }
}
