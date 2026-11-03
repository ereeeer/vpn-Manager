package com.example.vpnlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS = "VpnManagerPrefs";
    private static final String KEY_FAV = "favorites";
    private static final String KEY_CUSTOM = "custom_vpns";
    private static final String KEY_MARKS = "app_marks";
    private static final String KEY_VIRTUAL = "virtual_airports";
    private static final String KEY_THEME = "theme_index";
    private static final String KEY_SHOW_ALL = "show_all";
    private static final String VPN_SERVICE_ACTION = "android.net.VpnService";

    private static final String[] KEYWORDS = {
            "vpn", "代理", "proxy", "加速", "梯子", "翻墙",
            "clash", "v2ray", "xray", "ssr", "shadowsocks",
            "surfboard", "trojan", "wireguard", "openvpn",
            "hysteria", "sing-box", "singbox", "nekobox",
            "sagernet", "outline", "hiddify", "roxi", "tuic"
    };

    private static final String[] THEME_NAMES = {
            "渐变蓝", "深色夜间", "护眼绿",
            "薰衣草紫", "暖橙米色", "薄荷青"
    };

    private LinearLayout rootLayout;
    private LinearLayout searchContainer;

    private TextView titleText;
    private TextView subtitleText;
    private TextView emptyView;
    private TextView tvHelp;
    private TextView btnClearSearch;

    private ListView listView;
    private Button btnSettings;
    private EditText searchBox;
    private CheckBox showAllCheckbox;

    private AppAdapter adapter;

    private final LruCache<String, Drawable> iconCache =
            new LruCache<>(150);

    private ThemeColors currentTheme = ThemeColors.THEMES[0];

    private boolean isShowAll = false;
    private boolean receiverRegistered = false;

    private int scanGeneration = 0;

    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> apps = new ArrayList<>();

    private SharedPreferences prefs;

    private Set<String> favoritePackages = new HashSet<>();
    private Set<String> customVpnPackages = new HashSet<>();
    private Set<String> virtualAirports = new HashSet<>();

    private Map<String, String> appMarks = new HashMap<>();

    private final BroadcastReceiver uninstallReceiver =
            new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action == null) return;

            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {

                if (intent.getBooleanExtra(
                        Intent.EXTRA_REPLACING, false)) {
                    return;
                }

                String pkg = intent.getData() != null
                        ? intent.getData().getSchemeSpecificPart()
                        : null;

                if (pkg != null) {
                    favoritePackages.remove(pkg);
                    customVpnPackages.remove(pkg);
                    appMarks.remove(pkg);
                    saveSets();
                }

                scanApps();

            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                scanApps();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        favoritePackages = new HashSet<>(
                prefs.getStringSet(KEY_FAV, Collections.emptySet()));

        customVpnPackages = new HashSet<>(
                prefs.getStringSet(KEY_CUSTOM, Collections.emptySet()));

        virtualAirports = new HashSet<>(
                prefs.getStringSet(KEY_VIRTUAL, Collections.emptySet()));

        migrateVirtualFavoriteKeys();
        loadMarks();

        isShowAll = prefs.getBoolean(KEY_SHOW_ALL, false);

        rootLayout = findViewById(R.id.root_layout);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        emptyView = findViewById(R.id.empty_view);
        listView = findViewById(R.id.list);
        btnSettings = findViewById(R.id.btn_settings);
        searchContainer = findViewById(R.id.search_container);
        searchBox = findViewById(R.id.search_box);
        showAllCheckbox = findViewById(R.id.show_all_checkbox);
        tvHelp = findViewById(R.id.tv_help);
        btnClearSearch = findViewById(R.id.btn_clear_search);

        adapter = new AppAdapter();

        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter);

        applyTheme(prefs.getInt(KEY_THEME, 0), false);

        showAllCheckbox.setOnCheckedChangeListener(null);
        showAllCheckbox.setChecked(isShowAll);

        showAllCheckbox.setOnCheckedChangeListener(
                (buttonView, checked) -> {

            isShowAll = checked;

            prefs.edit()
                    .putBoolean(KEY_SHOW_ALL, checked)
                    .apply();

            scanApps();
        });

        tvHelp.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("使用说明")
                        .setMessage(
                                "【设置中心】点击右上角「设置」，可以添加自建机场、切换界面主题。\n\n"
                                + "【自动刷新】安装、卸载应用以及修改标签、增删机场后，列表会自动更新。\n\n"
                                + "【点击应用】弹出菜单，可打开、打标签、卸载等。\n\n"
                                + "【长按收藏】长按任意条目可直接置顶收藏。")
                        .setPositiveButton("我知道了", null)
                        .show());

        btnClearSearch.setOnClickListener(
                v -> searchBox.setText(""));

        btnSettings.setOnClickListener(v -> {

            String[] ops = {
                    "➕ 添加自建机场",
                    "🎨 切换界面主题"
            };

            new AlertDialog.Builder(this)
                    .setTitle("设置")
                    .setItems(ops, (dialog, which) -> {

                        if (which == 0) {
                            showAddAirportDialog();
                        } else {
                            showThemeDialog();
                        }
                    })
                    .show();
        });

        listView.setOnItemClickListener(
                (parent, view, position, id) -> {

            if (position >= 0 && position < apps.size()) {
                showAppOptionsDialog(apps.get(position));
            }
        });

        listView.setOnItemLongClickListener(
                (parent, view, position, id) -> {

            if (position >= 0 && position < apps.size()) {
                toggleFavorite(apps.get(position).packageName);
            }

            return true;
        });

        searchBox.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {

                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

                btnClearSearch.setVisibility(
                        s.length() > 0
                                ? View.VISIBLE
                                : View.GONE);
            }
        });

        IntentFilter filter = new IntentFilter();

        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            registerReceiver(
                    uninstallReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED);

        } else {
            registerReceiver(uninstallReceiver, filter);
        }

        receiverRegistered = true;

        scanApps();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (receiverRegistered) {

            try {
                unregisterReceiver(uninstallReceiver);
            } catch (Exception ignored) {
            }

            receiverRegistered = false;
        }
    }

    private static String virtualPackageKey(String name) {

        return "virtual_" + Base64.encodeToString(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Base64.URL_SAFE
                        | Base64.NO_WRAP
                        | Base64.NO_PADDING);
    }

    private void migrateVirtualFavoriteKeys() {

        boolean changed = false;

        for (String name : virtualAirports) {

            String oldKey = "virtual_" + name.hashCode();
            String newKey = virtualPackageKey(name);

            if (favoritePackages.remove(oldKey)) {
                favoritePackages.add(newKey);
                changed = true;
            }
        }

        if (changed) saveSets();
    }

    private void loadMarks() {

        appMarks.clear();

        try {

            JSONObject jsonObject = new JSONObject(
                    prefs.getString(KEY_MARKS, "{}"));

            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {

                String key = keys.next();

                appMarks.put(key, jsonObject.getString(key));
            }

        } catch (Exception ignored) {
        }
    }

    private void saveSets() {

        prefs.edit()
                .putStringSet(
                        KEY_FAV,
                        new HashSet<>(favoritePackages))
                .putStringSet(
                        KEY_CUSTOM,
                        new HashSet<>(customVpnPackages))
                .putStringSet(
                        KEY_VIRTUAL,
                        new HashSet<>(virtualAirports))
                .putString(
                        KEY_MARKS,
                        new JSONObject(appMarks).toString())
                .apply();
    }

    private void showAddAirportDialog() {

        final EditText input = new EditText(this);

        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("例如：一元机场、XX专线");

        new AlertDialog.Builder(this)
                .setTitle("添加自建机场")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {

                    String name = input.getText()
                            .toString()
                            .trim();

                    if (!name.isEmpty()) {

                        if (virtualAirports.contains(name)) {

                            Toast.makeText(
                                    this,
                                    "该机场已存在",
                                    Toast.LENGTH_SHORT).show();

                        } else {

                            virtualAirports.add(name);
                            saveSets();
                            scanApps();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameAirportDialog(AppInfo app) {

        final EditText input = new EditText(this);

        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(app.label);

        new AlertDialog.Builder(this)
                .setTitle("修改机场名称")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {

                    String newName = input.getText()
                            .toString()
                            .trim();

                    if (!newName.isEmpty()
                            && !newName.equals(app.label)) {

                        if (virtualAirports.contains(newName)) {

                            Toast.makeText(
                                    this,
                                    "该机场名称已存在",
                                    Toast.LENGTH_SHORT).show();

                            return;
                        }

                        String oldKey = app.packageName;
                        String newKey = virtualPackageKey(newName);

                        if (favoritePackages.remove(oldKey)) {
                            favoritePackages.add(newKey);
                        }

                        virtualAirports.remove(app.label);
                        virtualAirports.add(newName);

                        saveSets();
                        scanApps();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteAirport(AppInfo app) {

        new AlertDialog.Builder(this)
                .setTitle("删除机场")
                .setMessage(
                        "确定删除自建机场「" + app.label + "」？")
                .setPositiveButton("删除", (dialog, which) -> {

                    virtualAirports.remove(app.label);
                    favoritePackages.remove(app.packageName);

                    saveSets();
                    scanApps();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAppOptionsDialog(AppInfo app) {

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (app.isVirtual) {

            options.add("📝 修改名称");
            actions.add(() -> showRenameAirportDialog(app));

            options.add("🗑️ 删除机场");
            actions.add(() -> deleteAirport(app));

        } else {

            options.add("🚀 打开应用");

            actions.add(() -> {

                if (app.launchIntent != null) {

                    try {
                        startActivity(app.launchIntent);
                    } catch (Exception e) {

                        Toast.makeText(
                                this,
                                "无法启动",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });

            options.add("🏷️ 添加/修改标签");
            actions.add(() -> showMarkDialog(app));

            options.add("📋 复制包名");
            actions.add(() -> copyPackage(app.packageName));

            options.add("⚙️ 应用信息");
            actions.add(() -> jumpToSettings(app.packageName));

            if (app.isCustom) {

                options.add("❌ 取消收编");
                actions.add(() -> unmarkVpn(app.packageName));
            }

            options.add("🗑️ 卸载应用");
            actions.add(() -> confirmUninstall(app));
        }

        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(
                        options.toArray(new String[0]),
                        (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void showMarkDialog(AppInfo app) {

        String[] marks = {
                "最低收费",
                "最高收费",
                "稳定",
                "下载速度快",
                "免费好用",
                "清除标签"
        };

        new AlertDialog.Builder(this)
                .setTitle("选择标签")
                .setItems(marks, (dialog, which) -> {

                    if (which == 5) {

                        appMarks.remove(app.packageName);
                        saveSets();
                        scanApps();

                    } else {

                        applyMark(
                                app.packageName,
                                marks[which]);
                    }
                })
                .show();
    }

    private void applyMark(String packageName, String mark) {

        appMarks.put(packageName, mark);
        customVpnPackages.add(packageName);

        saveSets();
        scanApps();
    }

    private void unmarkVpn(String packageName) {

        customVpnPackages.remove(packageName);
        appMarks.remove(packageName);

        saveSets();
        scanApps();
    }

    private void toggleFavorite(String packageName) {

        if (favoritePackages.contains(packageName)) {
            favoritePackages.remove(packageName);
        } else {
            favoritePackages.add(packageName);
        }

        saveSets();

        for (AppInfo app : allApps) {
            app.isFavorite =
                    favoritePackages.contains(app.packageName);
        }

        int first = listView.getFirstVisiblePosition();

        View view = listView.getChildAt(0);

        int top = view == null ? 0 : view.getTop();

        sortAndAssignIndex();

        filterApps(searchBox.getText().toString());

        listView.setSelectionFromTop(first, top);
    }

    private void copyPackage(String packageName) {

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        if (clipboard != null) {

            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            "Package Name",
                            packageName));

            Toast.makeText(
                    this,
                    "已复制",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmUninstall(AppInfo app) {

        new AlertDialog.Builder(this)
                .setTitle("卸载")
                .setMessage("确定卸载「" + app.label + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("卸载", (dialog, which) -> {

                    try {

                        startActivity(new Intent(
                                Intent.ACTION_DELETE,
                                Uri.parse("package:" + app.packageName)));

                    } catch (Exception e) {

                        jumpToSettings(app.packageName);
                    }
                })
                .show();
    }

    private void showThemeDialog() {

        int selected = prefs.getInt(KEY_THEME, 0);

        if (selected < 0 || selected >= THEME_NAMES.length) {
            selected = 0;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择主题")
                .setSingleChoiceItems(
                        THEME_NAMES,
                        selected,
                        (dialog, which) -> {

                            applyTheme(which, true);
                            dialog.dismiss();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyTheme(int themeIndex, boolean save) {

        if (themeIndex < 0
                || themeIndex >= ThemeColors.THEMES.length) {
            themeIndex = 0;
        }

        currentTheme = ThemeColors.THEMES[themeIndex];

        rootLayout.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        currentTheme.backgroundStart,
                        currentTheme.backgroundEnd
                }));

        titleText.setTextColor(currentTheme.text);

        if (subtitleText != null) {
            subtitleText.setTextColor(currentTheme.secondary);
        }

        showAllCheckbox.setTextColor(currentTheme.text);

        tvHelp.setTextColor(Color.WHITE);
        tvHelp.setBackground(
                circleDrawable(currentTheme.accent));

        emptyView.setTextColor(currentTheme.secondary);

        searchBox.setTextColor(currentTheme.text);
        searchBox.setHintTextColor(currentTheme.secondary);

        // 搜索框外层负责圆角背景，输入框本身保持透明。
        if (searchContainer != null) {

            searchContainer.setBackground(roundDrawable(
                    currentTheme.searchBackground,
                    currentTheme.cardStroke,
                    dp(18)));
        }

        searchBox.setBackgroundColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            showAllCheckbox.setButtonTintList(
                    new ColorStateList(
                            new int[][]{
                                    new int[]{
                                            android.R.attr.state_checked
                                    },
                                    new int[]{}
                            },
                            new int[]{
                                    currentTheme.accent,
                                    currentTheme.secondary
                            }));
        }

        btnSettings.setTextColor(currentTheme.text);

        btnSettings.setBackground(roundDrawable(
                currentTheme.card,
                currentTheme.cardStroke,
                dp(16)));

        btnSettings.setAllCaps(false);

        btnClearSearch.setTextColor(currentTheme.secondary);

        if (save) {
            prefs.edit()
                    .putInt(KEY_THEME, themeIndex)
                    .apply();
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private GradientDrawable roundDrawable(
            int color,
            int stroke,
            int radius) {

        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(radius);

        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }

        return drawable;
    }

    private GradientDrawable circleDrawable(int color) {

        GradientDrawable drawable = new GradientDrawable();

        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);

        return drawable;
    }

    private int dp(float value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f);
    }

    private void jumpToSettings(String packageName) {

        try {

            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS);

            intent.setData(
                    Uri.parse("package:" + packageName));

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "无法打开设置页",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void scanApps() {

        final int generation = ++scanGeneration;

        final Set<String> favSnap =
                new HashSet<>(favoritePackages);

        final Set<String> customSnap =
                new HashSet<>(customVpnPackages);

        final Map<String, String> marksSnap =
                new HashMap<>(appMarks);

        final Set<String> virtualSnap =
                new HashSet<>(virtualAirports);

        final boolean showAll = isShowAll;

        final String self = getPackageName();

        emptyView.setText("正在扫描…");

        int first = listView.getFirstVisiblePosition();

        View topView = listView.getChildAt(0);

        int top = topView == null ? 0 : topView.getTop();

        Thread scanThread = new Thread(() -> {

            List<AppInfo> found = new ArrayList<>();

            String scanError = null;

            try {

                PackageManager pm = getPackageManager();

                Set<String> vpnPackages = new HashSet<>();

                List<ResolveInfo> vpnServices =
                        pm.queryIntentServices(
                                new Intent(VPN_SERVICE_ACTION),
                                0);

                if (vpnServices != null) {

                    for (ResolveInfo info : vpnServices) {

                        if (info.serviceInfo != null) {

                            vpnPackages.add(
                                    info.serviceInfo.packageName);
                        }
                    }
                }

                Intent launcher = new Intent(Intent.ACTION_MAIN);

                launcher.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> activities =
                        pm.queryIntentActivities(launcher, 0);

                Set<String> seen = new HashSet<>();

                if (activities != null) {

                    for (ResolveInfo info : activities) {

                        if (info.activityInfo == null) continue;

                        String packageName =
                                info.activityInfo.packageName;

                        if (!seen.add(packageName)
                                || self.equals(packageName)) {
                            continue;
                        }

                        ApplicationInfo appInfo;

                        try {

                            appInfo = pm.getApplicationInfo(
                                    packageName, 0);

                        } catch (
                                PackageManager.NameNotFoundException e) {
                            continue;
                        }

                        if ((appInfo.flags
                                & ApplicationInfo.FLAG_SYSTEM) != 0) {
                            continue;
                        }

                        Intent launchIntent =
                                pm.getLaunchIntentForPackage(packageName);

                        if (launchIntent == null) continue;

                        CharSequence labelCs =
                                pm.getApplicationLabel(appInfo);

                        String label = labelCs == null
                                ? packageName
                                : labelCs.toString();

                        String mark = marksSnap.get(packageName);

                        boolean isAuto =
                                vpnPackages.contains(packageName)
                                || matchesKeyword(label, packageName);

                        boolean isCustom =
                                customSnap.contains(packageName);

                        boolean isFavorite =
                                favSnap.contains(packageName);

                        if (!showAll
                                && !isAuto
                                && !isCustom
                                && !isFavorite
                                && mark == null) {
                            continue;
                        }

                        Drawable icon = iconCache.get(packageName);

                        if (icon == null) {

                            try {

                                icon = pm.getApplicationIcon(appInfo);

                            } catch (Exception e) {

                                icon = pm.getDefaultActivityIcon();
                            }

                            if (icon != null) {
                                iconCache.put(packageName, icon);
                            }
                        }

                        found.add(new AppInfo(
                                label,
                                packageName,
                                icon,
                                launchIntent,
                                isAuto,
                                isCustom,
                                isFavorite,
                                mark,
                                formatSize(appInfo),
                                false));
                    }
                }

                for (String airportName : virtualSnap) {

                    String fakePackage =
                            virtualPackageKey(airportName);

                    Drawable icon = pm.getDefaultActivityIcon();

                    boolean isFavorite =
                            favSnap.contains(fakePackage);

                    found.add(new AppInfo(
                            airportName,
                            fakePackage,
                            icon,
                            null,
                            false,
                            true,
                            isFavorite,
                            "机场",
                            "自建",
                            true));
                }
} catch (Exception e) {

                String errorMessage = e.getClass().getSimpleName()
                        + (e.getMessage() == null
                        ? ""
                        : ": " + e.getMessage());

                runOnUiThread(() -> {
                    Toast.makeText(
                            MainActivity.this,
                            "扫描失败：" + errorMessage,
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {

                runOnUiThread(() -> {
                    if (scanButton != null) {
                        scanButton.setEnabled(true);
                        scanButton.setText("开始扫描");
                    }
                });

            }

        }).start();
    }

    // ==================== 主题颜色配置 ====================
    private static class ThemeColors {

        final int backgroundStart;
        final int backgroundEnd;
        final int card;
        final int cardStroke;
        final int text;
        final int secondary;
        final int accent;
        final int searchBackground;
        final int indexBackground;
        final boolean dark;

        ThemeColors(
                int backgroundStart,
                int backgroundEnd,
                int card,
                int cardStroke,
                int text,
                int secondary,
                int accent,
                int searchBackground,
                int indexBackground,
                boolean dark
        ) {
            this.backgroundStart = backgroundStart;
            this.backgroundEnd = backgroundEnd;
            this.card = card;
            this.cardStroke = cardStroke;
            this.text = text;
            this.secondary = secondary;
            this.accent = accent;
            this.searchBackground = searchBackground;
            this.indexBackground = indexBackground;
            this.dark = dark;
        }

        static final ThemeColors[] THEMES = {

                // 1. 渐变蓝
                new ThemeColors(
                        0xFFEAF3FF, 0xFFD8E8FF,
                        0xF7FFFFFF, 0xFFDCE8FA,
                        0xFF172B4D, 0xFF7889A8,
                        0xFF2878E8, 0xF9FFFFFF,
                        0xFFE5EEFF, false
                ),

                // 2. 深色夜间
                new ThemeColors(
                        0xFF11182B, 0xFF202D4A,
                        0xFF27334F, 0xFF3A4969,
                        0xFFF1F5FF, 0xFFB1BDD4,
                        0xFF72A7FF, 0xFF303C58,
                        0xFF3A4969, true
                ),

                // 3. 护眼绿
                new ThemeColors(
                        0xFFEAF3E7, 0xFFDCEBDD,
                        0xF9FAFFF7, 0xFFD5E5D1,
                        0xFF304B37, 0xFF718875,
                        0xFF548B5B, 0xF9FFFFFF,
                        0xFFE5F0E1, false
                ),

                // 4. 薰衣草紫
                new ThemeColors(
                        0xFFF3ECFF, 0xFFE7DCFA,
                        0xFAFFFFFF, 0xFFE2D8F2,
                        0xFF3F315B, 0xFF82769B,
                        0xFF8062C9, 0xFAFFFFFF,
                        0xFFEDE5FF, false
                ),

                // 5. 暖橙米色
                new ThemeColors(
                        0xFFFFF5E6, 0xFFFFE7C8,
                        0xFAFFFCF6, 0xFFF0DFC8,
                        0xFF57432E, 0xFF927D65,
                        0xFFC17A32, 0xFAFFFFFF,
                        0xFFFFEED8, false
                ),

                // 6. 薄荷青
                new ThemeColors(
                        0xFFE8F7F3, 0xFFD4EEE8,
                        0xFAFFFFFF, 0xFFD6EAE4,
                        0xFF285248, 0xFF718F87,
                        0xFF278F82, 0xFAFFFFFF,
                        0xFFDFF2EC, false
                )
        };
    }
    }
