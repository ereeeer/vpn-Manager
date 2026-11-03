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
    private static final String PREFS = "VpnManagerPrefs", KEY_FAV = "favorites", KEY_CUSTOM = "custom_vpns", KEY_MARKS = "app_marks", KEY_VIRTUAL = "virtual_airports", KEY_THEME = "theme_index", KEY_SHOW_ALL = "show_all", VPN_SERVICE_ACTION = "android.net.VpnService";
    private static final String[] KEYWORDS = {"vpn", "代理", "proxy", "加速", "梯子", "翻墙", "clash", "v2ray", "xray", "ssr", "shadowsocks", "surfboard", "trojan", "wireguard", "openvpn", "hysteria", "sing-box", "singbox", "nekobox", "sagernet", "outline", "hiddify", "roxi", "tuic"};
    private static final String[] THEME_NAMES = {"渐变蓝", "深色夜间", "护眼绿", "薰衣草紫", "暖橙米色", "薄荷青"};

    private LinearLayout rootLayout;
    private TextView titleText, subtitleText, emptyView, tvHelp, btnClearSearch;
    private ListView listView;
    private Button btnSettings;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private AppAdapter adapter;
    private final LruCache<String, Drawable> iconCache = new LruCache<>(150);
    private ThemeColors currentTheme = ThemeColors.THEMES[0];
    private boolean isShowAll = false, receiverRegistered = false;
    private int scanGeneration = 0;
    private final List<AppInfo> allApps = new ArrayList<>(), apps = new ArrayList<>();
    private SharedPreferences prefs;
    private Set<String> favoritePackages = new HashSet<>(), customVpnPackages = new HashSet<>(), virtualAirports = new HashSet<>();
    private Map<String, String> appMarks = new HashMap<>();

    private final BroadcastReceiver uninstallReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                String pkg = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
                if (pkg != null) { favoritePackages.remove(pkg); customVpnPackages.remove(pkg); appMarks.remove(pkg); saveSets(); }
                scanApps();
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) scanApps();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        favoritePackages = new HashSet<>(prefs.getStringSet(KEY_FAV, Collections.emptySet()));
        customVpnPackages = new HashSet<>(prefs.getStringSet(KEY_CUSTOM, Collections.emptySet()));
        virtualAirports = new HashSet<>(prefs.getStringSet(KEY_VIRTUAL, Collections.emptySet()));
        migrateVirtualFavoriteKeys();
        loadMarks();
        isShowAll = prefs.getBoolean(KEY_SHOW_ALL, false);
        rootLayout = findViewById(R.id.root_layout);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        emptyView = findViewById(R.id.empty_view);
        listView = findViewById(R.id.list);
        btnSettings = findViewById(R.id.btn_settings);
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
        showAllCheckbox.setOnCheckedChangeListener((v, c) -> { isShowAll = c; prefs.edit().putBoolean(KEY_SHOW_ALL, c).apply(); scanApps(); });
        tvHelp.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("使用说明").setMessage("【设置中心】点击右上角「设置」，可以添加自建机场、切换界面主题。\n\n【自动刷新】安装、卸载应用以及修改标签、增删机场后，列表会自动更新。\n\n【点击应用】弹出菜单，可打开、打标签、卸载等。\n\n【长按收藏】长按任意条目可直接置顶收藏。").setPositiveButton("我知道了", null).show());
        btnClearSearch.setOnClickListener(v -> searchBox.setText(""));
        btnSettings.setOnClickListener(v -> {
            String[] ops = {"➕ 添加自建机场", "🎨 切换界面主题"};
            new AlertDialog.Builder(this).setTitle("设置").setItems(ops, (d, w) -> { if (w == 0) showAddAirportDialog(); else showThemeDialog(); }).show();
        });
        listView.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < apps.size()) showAppOptionsDialog(apps.get(pos)); });
        listView.setOnItemLongClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < apps.size()) toggleFavorite(apps.get(pos).packageName); return true; });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) { btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE); }
        });
        IntentFilter f = new IntentFilter(); f.addAction(Intent.ACTION_PACKAGE_REMOVED); f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(uninstallReceiver, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(uninstallReceiver, f);
        receiverRegistered = true;
        scanApps();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (receiverRegistered) { try { unregisterReceiver(uninstallReceiver); } catch (Exception e) {} receiverRegistered = false; }
    }

    private static String virtualPackageKey(String name) { return "virtual_" + Base64.encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING); }
    private void migrateVirtualFavoriteKeys() {
        boolean changed = false;
        for (String name : virtualAirports) {
            String oldKey = "virtual_" + name.hashCode(); String newKey = virtualPackageKey(name);
            if (favoritePackages.remove(oldKey)) { favoritePackages.add(newKey); changed = true; }
        }
        if (changed) saveSets();
    }
    private void loadMarks() {
        appMarks.clear();
        try {
            JSONObject jsonObject = new JSONObject(prefs.getString(KEY_MARKS, "{}")); Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) { String key = keys.next(); appMarks.put(key, jsonObject.getString(key)); }
        } catch (Exception ignored) {}
    }
    private void saveSets() { prefs.edit().putStringSet(KEY_FAV, new HashSet<>(favoritePackages)).putStringSet(KEY_CUSTOM, new HashSet<>(customVpnPackages)).putStringSet(KEY_VIRTUAL, new HashSet<>(virtualAirports)).putString(KEY_MARKS, new JSONObject(appMarks).toString()).apply(); }
    
    private void showAddAirportDialog() {
        final EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setHint("例如：一元机场、XX专线");
        new AlertDialog.Builder(this).setTitle("添加自建机场").setView(input).setPositiveButton("添加", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) { if (virtualAirports.contains(name)) Toast.makeText(this, "该机场已存在", Toast.LENGTH_SHORT).show(); else { virtualAirports.add(name); saveSets(); scanApps(); } }
        }).setNegativeButton("取消", null).show();
    }
    private void showRenameAirportDialog(AppInfo app) {
        final EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setText(app.label);
        new AlertDialog.Builder(this).setTitle("修改机场名称").setView(input).setPositiveButton("保存", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(app.label)) {
                if (virtualAirports.contains(newName)) { Toast.makeText(this, "该机场名称已存在", Toast.LENGTH_SHORT).show(); return; }
                String oldKey = app.packageName, newKey = virtualPackageKey(newName);
                if (favoritePackages.remove(oldKey)) favoritePackages.add(newKey);
                virtualAirports.remove(app.label); virtualAirports.add(newName); saveSets(); scanApps();
            }
        }).setNegativeButton("取消", null).show();
    }
    private void deleteAirport(AppInfo app) {
        new AlertDialog.Builder(this).setTitle("删除机场").setMessage("确定删除自建机场「" + app.label + "」？").setPositiveButton("删除", (dialog, which) -> {
            virtualAirports.remove(app.label); favoritePackages.remove(app.packageName); saveSets(); scanApps();
        }).setNegativeButton("取消", null).show();
    }
    private void showAppOptionsDialog(AppInfo app) {
        List<String> options = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if (app.isVirtual) {
            options.add("📝 修改名称"); actions.add(() -> showRenameAirportDialog(app));
            options.add("🗑️ 删除机场"); actions.add(() -> deleteAirport(app));
        } else {
            options.add("🚀 打开应用"); actions.add(() -> { if (app.launchIntent != null) { try { startActivity(app.launchIntent); } catch (Exception e) { Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show(); } } });
            options.add("🏷️ 添加/修改标签"); actions.add(() -> showMarkDialog(app));
            options.add("📋 复制包名"); actions.add(() -> copyPackage(app.packageName));
            options.add("⚙️ 应用信息"); actions.add(() -> jumpToSettings(app.packageName));
            if (app.isCustom) { options.add("❌ 取消收编"); actions.add(() -> unmarkVpn(app.packageName)); }
            options.add("🗑️ 卸载应用"); actions.add(() -> confirmUninstall(app));
        }
        new AlertDialog.Builder(this).setTitle(app.label).setItems(options.toArray(new String[0]), (dialog, which) -> actions.get(which).run()).show();
    }
    private void showMarkDialog(AppInfo app) {
        String[] marks = {"最低收费", "最高收费", "稳定", "下载速度快", "免费好用", "清除标签"};
        new AlertDialog.Builder(this).setTitle("选择标签").setItems(marks, (dialog, which) -> {
            if (which == 5) { appMarks.remove(app.packageName); saveSets(); scanApps(); } else applyMark(app.packageName, marks[which]);
        }).show();
    }
    private void applyMark(String p, String m) { appMarks.put(p, m); customVpnPackages.add(p); saveSets(); scanApps(); }
    private void unmarkVpn(String p) { customVpnPackages.remove(p); appMarks.remove(p); saveSets(); scanApps(); }
    private void toggleFavorite(String p) {
        if (favoritePackages.contains(p)) favoritePackages.remove(p); else favoritePackages.add(p); saveSets();
        for (AppInfo a : allApps) a.isFavorite = favoritePackages.contains(a.packageName);
        int f = listView.getFirstVisiblePosition(); View v = listView.getChildAt(0); int t = v == null ? 0 : v.getTop();
        sortAndAssignIndex(); filterApps(searchBox.getText().toString()); listView.setSelectionFromTop(f, t);
    }
    private void copyPackage(String p) { ClipboardManager c = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (c != null) { c.setPrimaryClip(ClipData.newPlainText("Package Name", p)); Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show(); } }
    private void confirmUninstall(AppInfo a) {
        new AlertDialog.Builder(this).setTitle("卸载").setMessage("确定卸载「" + a.label + "」？").setNegativeButton("取消", null).setPositiveButton("卸载", (d, w) -> { try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.packageName))); } catch (Exception e) { jumpToSettings(a.packageName); } }).show();
    }
    private void showThemeDialog() {
        int s = prefs.getInt(KEY_THEME, 0); if (s < 0 || s >= THEME_NAMES.length) s = 0;
        new AlertDialog.Builder(this).setTitle("选择主题").setSingleChoiceItems(THEME_NAMES, s, (d, w) -> { applyTheme(w, true); d.dismiss(); }).setNegativeButton("取消", null).show();
    }
    private void applyTheme(int w, boolean p) {
        if (w < 0 || w >= ThemeColors.THEMES.length) w = 0;
        currentTheme = ThemeColors.THEMES[w];
        rootLayout.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{currentTheme.backgroundStart, currentTheme.backgroundEnd}));
        titleText.setTextColor(currentTheme.text); if (subtitleText != null) subtitleText.setTextColor(currentTheme.secondary);
        showAllCheckbox.setTextColor(currentTheme.text); tvHelp.setTextColor(Color.WHITE); tvHelp.setBackground(circleDrawable(currentTheme.accent)); emptyView.setTextColor(currentTheme.secondary);
        searchBox.setTextColor(currentTheme.text); searchBox.setHintTextColor(currentTheme.secondary); searchBox.setBackground(roundDrawable(currentTheme.searchBackground, currentTheme.cardStroke, dp(18)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) showAllCheckbox.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{currentTheme.accent, currentTheme.secondary}));
        btnSettings.setTextColor(currentTheme.text); btnSettings.setBackground(roundDrawable(currentTheme.card, currentTheme.cardStroke, dp(16))); btnSettings.setAllCaps(false);
        btnClearSearch.setTextColor(currentTheme.secondary);
        if (p) prefs.edit().putInt(KEY_THEME, w).apply(); if (adapter != null) adapter.notifyDataSetChanged();
    } 
