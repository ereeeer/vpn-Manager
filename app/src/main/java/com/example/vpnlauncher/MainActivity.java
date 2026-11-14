
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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
    private static final String KEY_UNINSTALLED = "uninstalled_history";
    private static final String VPN_SERVICE_ACTION = "android.net.VpnService";

    private static final String[] KEYWORDS = {
            "vpn", "代理", "proxy", "加速", "梯子", "翻墙",
            "clash", "v2ray", "xray", "ssr", "shadowsocks",
            "surfboard", "trojan", "wireguard", "openvpn",
            "hysteria", "sing-box", "singbox", "nekobox",
            "sagernet", "outline", "hiddify", "roxi", "tuic"
    };

    private static final String[] EXCLUDE_KEYWORDS = {
            "清理", "杀毒", "病毒", "安全", "卫士",
            "内存", "优化", "防骚扰", "拦截"
    };

    private static final String[] THEME_NAMES = {
            "渐变蓝", "深色夜间", "护眼绿",
            "薰衣草紫", "暖橙米色", "薄荷青"
    };

    private LinearLayout rootLayout;
    private TextView titleText, subtitleText;
    private TextView emptyView, tvHelp, btnClearSearch, btnSettings;
    private ListView listView;
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
    private Set<String> uninstalledHistory = new HashSet<>();

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
                    uninstalledHistory.add(pkg);
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

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        favoritePackages = new HashSet<>(
                prefs.getStringSet(KEY_FAV, Collections.emptySet()));

        customVpnPackages = new HashSet<>(
                prefs.getStringSet(KEY_CUSTOM, Collections.emptySet()));

        virtualAirports = new HashSet<>(
                prefs.getStringSet(KEY_VIRTUAL, Collections.emptySet()));

        uninstalledHistory = new HashSet<>(
                prefs.getStringSet(KEY_UNINSTALLED, Collections.emptySet()));

        migrateVirtualFavoriteKeys();
        loadMarks();

        isShowAll = prefs.getBoolean(KEY_SHOW_ALL, false);

        createMainLayout();

        adapter = new AppAdapter();

        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter);

        applyTheme(prefs.getInt(KEY_THEME, 0), false);

        showAllCheckbox.setChecked(isShowAll);

        showAllCheckbox.setOnCheckedChangeListener((v, checked) -> {
            isShowAll = checked;
            prefs.edit().putBoolean(KEY_SHOW_ALL, checked).apply();
            scanApps();
        });

        tvHelp.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("使用说明")
                        .setMessage(
                                "【设置中心】点击右上角设置，可以添加自有机场、切换界面主题。\n\n" +
                                "【卸载追踪】如果卸载某个应用，以后重新安装时，会带上「曾卸载」标签。\n\n" +
                                "【收费标签】点击标签选择最低收费或最高收费，可以输入具体金额。"
                        )
                        .setPositiveButton("我知道了", null)
                        .show()
        );

        btnClearSearch.setOnClickListener(v ->
                searchBox.setText(""));

        btnSettings.setOnClickListener(v -> {
            String[] ops = {
                    "➕ 自有机场",
                    "🎨 切换界面主题"
            };

            new AlertDialog.Builder(this)
                    .setTitle("设置")
                    .setItems(ops, (d, w) -> {
                        if (w == 0) {
                            showAddAirportDialog();
                        } else {
                            showThemeDialog();
                        }
                    })
                    .show();
        });

        listView.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos >= 0 && pos < apps.size()) {
                showAppOptionsDialog(apps.get(pos));
            }
        });

        listView.setOnItemLongClickListener((parent, view, pos, id) -> {
            if (pos >= 0 && pos < apps.size()) {
                toggleFavorite(apps.get(pos).packageName);
            }
            return true;
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int st, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClearSearch.setVisibility(
                        s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    uninstallReceiver,
                    f,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(uninstallReceiver, f);
        }

        receiverRegistered = true;

        scanApps();
    }

    // =========================================================
    // Java 动态创建主界面，不依赖 XML
    // =========================================================

    private void createMainLayout() {

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(12), dp(12), dp(12), dp(8));

        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 顶部标题栏
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams titleGroupParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                );

        header.addView(titleGroup, titleGroupParams);

        titleText = new TextView(this);
        titleText.setText("VPN 管理器");
        titleText.setTextSize(25);
        titleText.setTypeface(null, Typeface.BOLD);

        titleGroup.addView(titleText);

        subtitleText = new TextView(this);
        subtitleText.setText("管理你的网络连接工具");
        subtitleText.setTextSize(12);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin = dp(3);
        titleGroup.addView(subtitleText, subtitleParams);

        btnSettings = new TextView(this);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(23);
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setPadding(dp(13), dp(8), dp(13), dp(8));

        header.addView(btnSettings);

        rootLayout.addView(header);

        // 搜索栏
        LinearLayout searchLayout = new LinearLayout(this);
        searchLayout.setOrientation(LinearLayout.HORIZONTAL);
        searchLayout.setGravity(Gravity.CENTER_VERTICAL);
        searchLayout.setPadding(dp(12), 0, dp(8), 0);

        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48)
                );

        searchParams.topMargin = dp(17);

        rootLayout.addView(searchLayout, searchParams);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(14);
        searchBox.setPadding(0, 0, 0, 0);
        searchBox.setBackgroundColor(Color.TRANSPARENT);
        searchBox.setHint("搜索应用名称或包名");
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);

        LinearLayout.LayoutParams searchInputParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                );

        searchLayout.addView(searchBox, searchInputParams);

        btnClearSearch = new TextView(this);
        btnClearSearch.setText("✕");
        btnClearSearch.setTextSize(17);
        btnClearSearch.setGravity(Gravity.CENTER);
        btnClearSearch.setVisibility(View.GONE);
        btnClearSearch.setPadding(dp(10), 0, dp(5), 0);

        searchLayout.addView(btnClearSearch);

        // 显示全部与帮助
        LinearLayout toolsLayout = new LinearLayout(this);
        toolsLayout.setOrientation(LinearLayout.HORIZONTAL);
        toolsLayout.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams toolsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        toolsParams.topMargin = dp(10);
        toolsParams.bottomMargin = dp(8);

        rootLayout.addView(toolsLayout, toolsParams);

        showAllCheckbox = new CheckBox(this);
        showAllCheckbox.setText("显示全部应用");
        showAllCheckbox.setTextSize(13);

        toolsLayout.addView(showAllCheckbox,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                ));

        tvHelp = new TextView(this);
        tvHelp.setText("使用说明");
        tvHelp.setTextSize(11);
        tvHelp.setGravity(Gravity.CENTER);
        tvHelp.setPadding(dp(12), dp(6), dp(12), dp(6));

        toolsLayout.addView(tvHelp);

        // 应用列表
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(2), 0, dp(8));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            listView.setBackgroundColor(Color.TRANSPARENT);
            listView.setNestedScrollingEnabled(true);
        }

        rootLayout.addView(listView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                ));

        // 空列表提示
        emptyView = new TextView(this);
        emptyView.setText("正在扫描…");
        emptyView.setTextSize(14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(20), dp(30), dp(20), dp(30));

        rootLayout.addView(emptyView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

        setContentView(rootLayout);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (receiverRegistered) {
            try {
                unregisterReceiver(uninstallReceiver);
            } catch (Exception ignored) {}

            receiverRegistered = false;
        }
    }

    // =========================================================
    // 自建机场与数据保存
    // =========================================================

    private static String virtualPackageKey(String name) {
        return "virtual_" + Base64.encodeToString(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
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

        } catch (Exception ignored) {}
    }

    private void saveSets() {
        prefs.edit()
                .putStringSet(KEY_FAV, new HashSet<>(favoritePackages))
                .putStringSet(KEY_CUSTOM, new HashSet<>(customVpnPackages))
                .putStringSet(KEY_VIRTUAL, new HashSet<>(virtualAirports))
                .putStringSet(KEY_UNINSTALLED, new HashSet<>(uninstalledHistory))
                .putString(KEY_MARKS, new JSONObject(appMarks).toString())
                .apply();
    }

    private void showAddAirportDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("例如：一元机场");

        new AlertDialog.Builder(this)
                .setTitle("添加自有机场")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = input.getText().toString().trim();

                    if (name.isEmpty()) return;

                    if (virtualAirports.contains(name)) {
                        Toast.makeText(
                                this, "已存在", Toast.LENGTH_SHORT).show();
                    } else {
                        virtualAirports.add(name);
                        saveSets();
                        scanApps();
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
                .setTitle("修改名称")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = input.getText().toString().trim();

                    if (newName.isEmpty() || newName.equals(app.label)) {
                        return;
                    }

                    if (virtualAirports.contains(newName)) {
                        Toast.makeText(
                                this, "已存在", Toast.LENGTH_SHORT).show();
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
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteAirport(AppInfo app) {
        new AlertDialog.Builder(this)
                .setTitle("删除")
                .setMessage("确定删除？")
                .setPositiveButton("删除", (dialog, which) -> {
                    virtualAirports.remove(app.label);
                    favoritePackages.remove(app.packageName);
                    saveSets();
                    scanApps();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // =========================================================
    // 应用操作
    // =========================================================

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
                    } catch (Exception ignored) {}
                }
            });

            options.add("🏷️ 标签");
            actions.add(() -> showMarkDialog(app));

            options.add("📋 复制包名");
            actions.add(() -> copyPackage(app.packageName));

            options.add("⚙️ 应用信息");
            actions.add(() -> jumpToSettings(app.packageName));

            if (app.isCustom) {
                options.add("❌ 取消收编");
                actions.add(() -> unmarkVpn(app.packageName));
            }

            if (app.isReinstalled) {
                options.add("✨ 清除曾卸载标记");
                actions.add(() -> {
                    uninstalledHistory.remove(app.packageName);
                    saveSets();
                    scanApps();
                });
            }

            options.add("🗑️ 卸载");
            actions.add(() -> confirmUninstall(app));
        }

        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(options.toArray(new String[0]),
                        (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void showMarkDialog(AppInfo app) {

        String[] marks = {
                "最低收费",
                "最高收费",
                "支持支/微",
                "稳定",
                "下载快",
                "免费好用",
                "清除标签"
        };

        new AlertDialog.Builder(this)
                .setTitle("标签")
                .setItems(marks, (dialog, which) -> {

                    if (which == 6) {
                        appMarks.remove(app.packageName);
                        saveSets();
                        scanApps();

                    } else if (which == 0 || which == 1) {

                        final EditText input = new EditText(this);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setHint("如: 15元/月");

                        new AlertDialog.Builder(this)
                                .setTitle(marks[which])
                                .setView(input)
                                .setPositiveButton("确定", (d, w) -> {
                                    String amt = input.getText()
                                            .toString().trim();

                                    String res = marks[which]
                                            + (amt.isEmpty()
                                            ? ""
                                            : ": " + amt);

                                    applyMark(app.packageName, res);
                                })
                                .setNegativeButton("取消", null)
                                .show();

                    } else {
                        applyMark(app.packageName, marks[which]);
                    }
                })
                .show();
    }

    private void applyMark(String p, String m) {
        appMarks.put(p, m);
        customVpnPackages.add(p);
        saveSets();
        scanApps();
    }

    private void unmarkVpn(String p) {
        customVpnPackages.remove(p);
        appMarks.remove(p);
        saveSets();
        scanApps();
    }

    private void toggleFavorite(String p) {

        if (favoritePackages.contains(p)) {
            favoritePackages.remove(p);
        } else {
            favoritePackages.add(p);
        }

        saveSets();

        for (AppInfo a : allApps) {
            a.isFavorite = favoritePackages.contains(a.packageName);
        }

        int first = listView.getFirstVisiblePosition();
        View v = listView.getChildAt(0);
        int top = v == null ? 0 : v.getTop();

        sortAndAssignIndex();
        filterApps(searchBox.getText().toString());

        listView.setSelectionFromTop(first, top);
    }

    private void copyPackage(String p) {
        ClipboardManager c = (ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);

        if (c != null) {
            c.setPrimaryClip(ClipData.newPlainText("pkg", p));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmUninstall(AppInfo a) {
        try {
            startActivity(new Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:" + a.packageName)
            ));
        } catch (Exception e) {
            jumpToSettings(a.packageName);
        }
    }

    // =========================================================
    // 主题
    // =========================================================

    private void showThemeDialog() {

        int selected = prefs.getInt(KEY_THEME, 0);

        if (selected < 0 || selected >= THEME_NAMES.length) {
            selected = 0;
        }

        new AlertDialog.Builder(this)
                .setTitle("主题")
                .setSingleChoiceItems(THEME_NAMES, selected,
                        (dialog, which) -> {
                            applyTheme(which, true);
                            dialog.dismiss();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyTheme(int w, boolean save) {

        if (w < 0 || w >= ThemeColors.THEMES.length) {
            w = 0;
        }

        currentTheme = ThemeColors.THEMES[w];

        rootLayout.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        currentTheme.backgroundStart,
                        currentTheme.backgroundEnd
                }
        ));

        titleText.setTextColor(currentTheme.text);
        subtitleText.setTextColor(currentTheme.secondary);

        showAllCheckbox.setTextColor(currentTheme.text);

        tvHelp.setTextColor(Color.WHITE);
        tvHelp.setBackground(
                circleDrawable(currentTheme.accent));

        emptyView.setTextColor(currentTheme.secondary);

        searchBox.setTextColor(currentTheme.text);
        searchBox.setHintTextColor(currentTheme.secondary);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            showAllCheckbox.setButtonTintList(
                    new ColorStateList(
                            new int[][]{
                                    new int[]{android.R.attr.state_checked},
                                    new int[]{}
                            },
                            new int[]{
                                    currentTheme.accent,
                                    currentTheme.secondary
                            }
                    )
            );
        }

        btnSettings.setTextColor(currentTheme.text);

        btnSettings.setBackground(
                roundDrawable(
                        currentTheme.card,
                        currentTheme.cardStroke,
                        dp(16)
                )
        );

        btnClearSearch.setTextColor(currentTheme.secondary);

        if (save) {
            prefs.edit().putInt(KEY_THEME, w).apply();
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private GradientDrawable roundDrawable(
            int color, int stroke, int radius) {

        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);

        if (stroke != Color.TRANSPARENT) {
            d.setStroke(dp(1), stroke);
        }

        return d;
    }

    private GradientDrawable circleDrawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int dp(float value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }

    private void jumpToSettings(String p) {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + p)
            ));
        } catch (Exception ignored) {}
    }

    // =========================================================
    // 应用扫描
    // =========================================================

    private void scanApps() {

        final int gen = ++scanGeneration;

        final Set<String> fs = new HashSet<>(favoritePackages);
        final Set<String> cs = new HashSet<>(customVpnPackages);
        final Set<String> vs = new HashSet<>(virtualAirports);
        final Set<String> uh = new HashSet<>(uninstalledHistory);

        final Map<String, String> ms = new HashMap<>(appMarks);

        final boolean sa = isShowAll;
        final String self = getPackageName();

        emptyView.setText("正在扫描…");

        int first = listView.getFirstVisiblePosition();
        View v = listView.getChildAt(0);
        int top = v == null ? 0 : v.getTop();

        new Thread(() -> {

            List<AppInfo> found = new ArrayList<>();
            String error = null;

            try {

                PackageManager pm = getPackageManager();

                Set<String> vpnPackages = new HashSet<>();

                List<ResolveInfo> services = pm.queryIntentServices(
                        new Intent(VPN_SERVICE_ACTION), 0);

                if (services != null) {
                    for (ResolveInfo ri : services) {
                        if (ri.serviceInfo != null) {
                            vpnPackages.add(
                                    ri.serviceInfo.packageName);
                        }
                    }
                }

                Intent launcherIntent =
                        new Intent(Intent.ACTION_MAIN);

                launcherIntent.addCategory(
                        Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> activities =
                        pm.queryIntentActivities(launcherIntent, 0);

                Set<String> scanned = new HashSet<>();

                if (activities != null) {

                    for (ResolveInfo ri : activities) {

                        if (ri.activityInfo == null) continue;

                        String p = ri.activityInfo.packageName;

                        if (!scanned.add(p) || self.equals(p)) {
                            continue;
                        }

                        ApplicationInfo ai;

                        try {
                            ai = pm.getApplicationInfo(p, 0);
                        } catch (Exception e) {
                            continue;
                        }

                        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                            continue;
                        }

                        Intent launchIntent =
                                pm.getLaunchIntentForPackage(p);

                        if (launchIntent == null) continue;

                        String label =
                                pm.getApplicationLabel(ai).toString();

                        boolean excluded = false;

                        for (String ex : EXCLUDE_KEYWORDS) {
                            if (label.contains(ex)) {
                                excluded = true;
                                break;
                            }
                        }

                        if (excluded) continue;

                        String mark = ms.get(p);

                        boolean auto =
                                vpnPackages.contains(p)
                                        || matchesKeyword(label, p);

                        boolean custom = cs.contains(p);
                        boolean favorite = fs.contains(p);
                        boolean reinstalled = uh.contains(p);

                        if (!sa && !auto && !custom
                                && !favorite && mark == null) {
                            continue;
                        }

                        Drawable icon = iconCache.get(p);

                        if (icon == null) {
                            try {
                                icon = pm.getApplicationIcon(ai);
                            } catch (Exception e) {
                                icon = pm.getDefaultActivityIcon();
                            }

                            iconCache.put(p, icon);
                        }

                        found.add(new AppInfo(
                                label,
                                p,
                                icon,
                                launchIntent,
                                auto,
                                custom,
                                favorite,
                                mark,
                                formatSize(ai),
                                false,
                                reinstalled
                        ));
                    }
                }

                for (String name : vs) {

                    String key = virtualPackageKey(name);

                    found.add(new AppInfo(
                            name,
                            key,
                            pm.getDefaultActivityIcon(),
                            null,
                            false,
                            true,
                            fs.contains(key),
                            "自建",
                            "自有机场",
                            true,
                            false
                    ));
                }

            } catch (Exception e) {
                error = e.getClass().getSimpleName();
            }

            final String finalError = error;
            final List<AppInfo> result = found;

            runOnUiThread(() -> {

                if (gen != scanGeneration
                        || isFinishing()
                        || isDestroyed()
                        || listView.getAdapter() == null) {
                    return;
                }

                allApps.clear();
                allApps.addAll(result);

                sortAndAssignIndex();
                filterApps(searchBox.getText().toString());

                listView.post(() ->
                        listView.setSelectionFromTop(first, top));

                if (finalError != null) {
                    emptyView.setText("扫描失败");
                } else if (result.isEmpty()) {
                    emptyView.setText(
                            sa
                                    ? "没有应用"
                                    : "没有识别到 VPN\n勾选「显示全部」可收编"
                    );
                } else if (apps.isEmpty()) {
                    emptyView.setText("无匹配");
                }
            });

        }).start();
    }

    private static boolean matchesKeyword(String label, String pkg) {

        String l = label.toLowerCase(Locale.ROOT);
        String p = pkg.toLowerCase(Locale.ROOT);

        for (String keyword : KEYWORDS) {
            if (l.contains(keyword) || p.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private static String formatSize(ApplicationInfo ai) {

        long size = 0;

        try {
            if (ai.publicSourceDir != null) {
                size += new File(ai.publicSourceDir).length();
            }
        } catch (Exception ignored) {}

        return String.format(
                Locale.getDefault(),
                "%.1f MB",
                size / (1024.0 * 1024.0)
        );
    }

    private void sortAndAssignIndex() {

        Collections.sort(allApps, (a, b) -> {

            if (a.isFavorite != b.isFavorite) {
                return a.isFavorite ? -1 : 1;
            }

            if (a.isDetected() != b.isDetected()) {
                return a.isDetected() ? -1 : 1;
            }

            return Collator.getInstance().compare(
                    a.label, b.label);
        });

        for (int i = 0; i < allApps.size(); i++) {
            allApps.get(i).absoluteIndex = i + 1;
        }
    }

    private void filterApps(String query) {

        apps.clear();

        if (query == null || query.isEmpty()) {
            apps.addAll(allApps);

        } else {

            String q = query.toLowerCase(Locale.ROOT);

            for (AppInfo a : allApps) {

                if (a.label.toLowerCase(Locale.ROOT).contains(q)
                        || a.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    apps.add(a);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (apps.isEmpty() && !allApps.isEmpty()) {
            emptyView.setText("无匹配");
        }
    }

    // =========================================================
    // 应用数据类
    // =========================================================

    private static class AppInfo {

        final String label;
        final String packageName;
        final String sizeStr;

        final Drawable icon;
        final Intent launchIntent;

        final boolean isAutoDetected;
        final boolean isVirtual;

        boolean isCustom;
        boolean isFavorite;
        String mark;
        int absoluteIndex;
        boolean isReinstalled;

        AppInfo(
                String label,
                String packageName,
                Drawable icon,
                Intent launchIntent,
                boolean auto,
                boolean custom,
                boolean favorite,
                String mark,
                String size,
                boolean virtual,
                boolean reinstalled) {

            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.launchIntent = launchIntent;
            this.isAutoDetected = auto;
            this.isCustom = custom;
            this.isFavorite = favorite;
            this.mark = mark;
            this.sizeStr = size;
            this.isVirtual = virtual;
            this.isReinstalled = reinstalled;
        }

        boolean isDetected() {
            return isAutoDetected
                    || isCustom
                    || mark != null
                    || isVirtual;
        }
    }

    // =========================================================
    // Java 动态创建应用列表
    // =========================================================

    private class AppAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int p) {
            return apps.get(p);
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public View getView(int p, View convertView, ViewGroup parent) {

            ViewHolder h;

            if (convertView == null) {

                convertView = createRow();

                h = (ViewHolder) convertView.getTag();

            } else {
                h = (ViewHolder) convertView.getTag();
            }

            AppInfo a = apps.get(p);
            ThemeColors t = currentTheme;

            h.index.setText(String.valueOf(a.absoluteIndex));
            h.index.setTextColor(t.secondary);
            h.index.setBackground(
                    circleDrawable(t.indexBackground));

            if (a.isVirtual) {
                h.icon.setImageResource(
                        android.R.drawable.ic_menu_compass);
            } else {
                h.icon.setImageDrawable(a.icon);
            }

            h.name.setText(a.label);
            h.name.setTextColor(t.text);

            h.size.setText(a.sizeStr);
            h.size.setTextColor(t.secondary);

            h.fav.setVisibility(
                    a.isFavorite ? View.VISIBLE : View.GONE);

            h.arrow.setTextColor(t.secondary);

            if (a.mark != null && !a.mark.isEmpty()) {
                h.mark.setText(a.mark);
                h.mark.setVisibility(View.VISIBLE);
            } else {
                h.mark.setVisibility(View.GONE);
            }

            h.reinstallMark.setVisibility(
                    a.isReinstalled ? View.VISIBLE : View.GONE);

            h.root.setBackground(
                    roundDrawable(t.card, t.cardStroke, dp(17)));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                h.root.setElevation(dp(t.dark ? 1.5f : 3f));
            }

            if (a.isDetected()) {

                h.check.setVisibility(View.VISIBLE);
                h.plus.setVisibility(View.GONE);
                h.plus.setOnClickListener(null);

            } else if (isShowAll) {

                h.check.setVisibility(View.GONE);
                h.plus.setTextColor(t.accent);
                h.plus.setVisibility(View.VISIBLE);

                h.plus.setOnClickListener(v ->
                        applyMark(a.packageName, "手动收编"));

            } else {

                h.check.setVisibility(View.GONE);
                h.plus.setVisibility(View.GONE);
                h.plus.setOnClickListener(null);
            }

            h.fav.setOnClickListener(v ->
                    toggleFavorite(a.packageName));

            return convertView;
        }

        private View createRow() {

            LinearLayout root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(
                    dp(12), dp(11), dp(12), dp(11));

            LinearLayout.LayoutParams rootParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            rootParams.setMargins(
                    dp(3), dp(5), dp(3), dp(5));

            root.setLayoutParams(rootParams);

            // 序号
            TextView index = new TextView(MainActivity.this);
            index.setGravity(Gravity.CENTER);
            index.setTextSize(12);
            index.setTypeface(null, Typeface.BOLD);

            root.addView(index, new LinearLayout.LayoutParams(
                    dp(29), dp(29)));

            // 图标
            ImageView icon = new ImageView(MainActivity.this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(dp(43), dp(43));

            iconParams.leftMargin = dp(10);
            iconParams.rightMargin = dp(11);

            root.addView(icon, iconParams);

            // 应用信息
            LinearLayout info = new LinearLayout(MainActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams infoParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    );

            root.addView(info, infoParams);

            TextView name = new TextView(MainActivity.this);
            name.setTextSize(15);
            name.setTypeface(null, Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);

            info.addView(name);

            // 标签行
            LinearLayout tagLine = new LinearLayout(MainActivity.this);
            tagLine.setOrientation(LinearLayout.HORIZONTAL);
            tagLine.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams tagLineParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            tagLineParams.topMargin = dp(4);

            info.addView(tagLine, tagLineParams);

            TextView mark = new TextView(MainActivity.this);
            mark.setTextSize(10);
            mark.setPadding(
                    dp(5), dp(2), dp(5), dp(2));
            mark.setBackground(roundDrawable(
                    0x223B82F6, Color.TRANSPARENT, dp(5)));

            tagLine.addView(mark);

            TextView reinstallMark = new TextView(MainActivity.this);
            reinstallMark.setText("曾卸载");
            reinstallMark.setTextSize(10);
            reinstallMark.setTextColor(0xFF548B5B);
            reinstallMark.setPadding(
                    dp(5), dp(2), dp(5), dp(2));

            LinearLayout.LayoutParams reinstallParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            reinstallParams.leftMargin = dp(5);

            tagLine.addView(reinstallMark, reinstallParams);

            TextView size = new TextView(MainActivity.this);
            size.setTextSize(11);

            LinearLayout.LayoutParams sizeParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            sizeParams.topMargin = dp(3);

            info.addView(size, sizeParams);

            // 右侧操作区域
            LinearLayout right = new LinearLayout(MainActivity.this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams rightParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            rightParams.leftMargin = dp(8);

            root.addView(right, rightParams);

            TextView fav = new TextView(MainActivity.this);
            fav.setText("★");
            fav.setTextSize(19);
            fav.setTextColor(0xFFFFB52E);
            fav.setGravity(Gravity.CENTER);

            right.addView(fav, new LinearLayout.LayoutParams(
                    dp(30), dp(29)));

            ImageView check = new ImageView(MainActivity.this);
            check.setImageResource(
                    android.R.drawable.checkbox_on_background);

            LinearLayout.LayoutParams checkParams =
                    new LinearLayout.LayoutParams(dp(22), dp(22));

            checkParams.topMargin = dp(4);

            right.addView(check, checkParams);

            TextView plus = new TextView(MainActivity.this);
            plus.setText("＋");
            plus.setTextSize(22);
            plus.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams plusParams =
                    new LinearLayout.LayoutParams(dp(30), dp(28));

            right.addView(plus, plusParams);

            TextView arrow = new TextView(MainActivity.this);
            arrow.setText("›");
            arrow.setTextSize(23);
            arrow.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams arrowParams =
                    new LinearLayout.LayoutParams(dp(30), dp(24));

            right.addView(arrow, arrowParams);

            ViewHolder holder = new ViewHolder();

            holder.root = root;
            holder.index = index;
            holder.icon = icon;
            holder.name = name;
            holder.mark = mark;
            holder.reinstallMark = reinstallMark;
            holder.check = check;
            holder.fav = fav;
            holder.size = size;
            holder.plus = plus;
            holder.arrow = arrow;

            root.setTag(holder);

            return root;
        }
    }

    private static class ViewHolder {
        LinearLayout root;
        TextView index;
        TextView name;
        TextView mark;
        TextView reinstallMark;
        TextView fav;
        TextView size;
        TextView plus;
        TextView arrow;
        ImageView icon;
        ImageView check;
    }

    // =========================================================
    // 六套主题
    // =========================================================

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
                int s, int e, int c, int st,
                int t, int sc, int a, int se, int i,
                boolean d) {

            backgroundStart = s;
            backgroundEnd = e;
            card = c;
            cardStroke = st;
            text = t;
            secondary = sc;
            accent = a;
            searchBackground = se;
            indexBackground = i;
            dark = d;
        }

        static final ThemeColors[] THEMES = {

                new ThemeColors(
                        0xFFD4E6FF, 0xFFB8D4FF,
                        0xFFFFFFFF, 0xFFB3CBE8,
                        0xFF172B4D, 0xFF5C6D87,
                        0xFF2878E8, 0xFFFFFFFF,
                        0xFFE5EEFF, false
                ),

                new ThemeColors(
                        0xFF141A2E, 0xFF0A0E17,
                        0xFF1E263D, 0xFF2D3C56,
                        0xFFF1F5FF, 0xFF8A9BB8,
                        0xFF72A7FF, 0xFF24314B,
                        0xFF2D3C56, true
                ),

                new ThemeColors(
                        0xFFE1F2E4, 0xFFCBE7CE,
                        0xFFFFFFFF, 0xFFB7DBBB,
                        0xFF2D4532, 0xFF5D7562,
                        0xFF548B5B, 0xFFFFFFFF,
                        0xFFDCECD8, false
                ),

                new ThemeColors(
                        0xFFE6D8FF, 0xFFD1BDFF,
                        0xFFFFFFFF, 0xFFCBB6EA,
                        0xFF3A2D52, 0xFF736789,
                        0xFF8062C9, 0xFFFFFFFF,
                        0xFFEADDFF, false
                ),

                new ThemeColors(
                        0xFFFFEEDD, 0xFFFFD9B0,
                        0xFFFFFFFF, 0xFFEECFAE,
                        0xFF513D29, 0xFF806B53,
                        0xFFC17A32, 0xFFFFFFFF,
                        0xFFFFE8CC, false
                ),

                new ThemeColors(
                        0xFFDEF5F0, 0xFFC0E8E0,
                        0xFFFFFFFF, 0xFFAED9D0,
                        0xFF234B41, 0xFF5E857C,
                        0xFF278F82, 0xFFFFFFFF,
                        0xFFD4ECE6, false
                )
        };
    }
}
