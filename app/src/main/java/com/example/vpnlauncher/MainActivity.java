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
    private static final String[] KEYWORDS = {"vpn", "代理", "proxy", "加速", "梯子", "翻墙", "clash", "v2ray", "xray", "ssr", "shadowsocks", "surfboard", "trojan", "wireguard", "openvpn", "hysteria", "sing-box", "singbox", "nekobox", "sagernet", "outline", "hiddify", "roxi", "tuic"};
    private static final String[] THEME_NAMES = {"渐变蓝", "深色夜间", "护眼绿", "薰衣草紫", "暖橙米色", "薄荷青"};

    private LinearLayout rootLayout;
    private TextView titleText, subtitleText, emptyView, tvHelp;
    private ListView listView;
    private Button btnSettings;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private AppAdapter adapter;
    private final LruCache<String, Drawable> iconCache = new LruCache<>(150);
    private ThemeColors currentTheme = ThemeColors.THEMES[0];
    private boolean isShowAll = false;
    private int scanGeneration = 0;
    private boolean receiverRegistered = false;
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
        adapter = new AppAdapter();
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter);
        applyTheme(prefs.getInt(KEY_THEME, 0), false);
        showAllCheckbox.setOnCheckedChangeListener(null);
        showAllCheckbox.setChecked(isShowAll);
        showAllCheckbox.setOnCheckedChangeListener((v, c) -> { isShowAll = c; prefs.edit().putBoolean(KEY_SHOW_ALL, c).apply(); scanApps(); });
        tvHelp.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("使用说明").setMessage("【设置中心】点击右上角「设置」，可以添加自建机场、切换界面主题。\n\n【自动刷新】安装、卸载应用以及修改标签、增删机场后，列表会自动更新。\n\n【点击应用】弹出菜单，可打开、打标签、卸载等。\n\n【长按收藏】长按任意条目可直接置顶收藏。").setPositiveButton("我知道了", null).show());
        btnSettings.setOnClickListener(v -> {
            String[] ops = {"➕ 添加自建机场", "🎨 切换界面主题"};
            new AlertDialog.Builder(this).setTitle("设置").setItems(ops, (d, w) -> { if (w == 0) showAddAirportDialog(); else showThemeDialog(); }).show();
        });
        listView.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < apps.size()) showAppOptionsDialog(apps.get(pos)); });
        listView.setOnItemLongClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < apps.size()) toggleFavorite(apps.get(pos).packageName); return true; });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
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

    private void loadMarks() {
        appMarks.clear();
        try {
            JSONObject o = new JSONObject(prefs.getString(KEY_MARKS, "{}"));
            Iterator<String> k = o.keys();
            while (k.hasNext()) { String key = k.next(); appMarks.put(key, o.getString(key)); }
        } catch (Exception e) {}
    }

    private void saveSets() {
        prefs.edit().putStringSet(KEY_FAV, new HashSet<>(favoritePackages)).putStringSet(KEY_CUSTOM, new HashSet<>(customVpnPackages)).putStringSet(KEY_VIRTUAL, new HashSet<>(virtualAirports)).putString(KEY_MARKS, new JSONObject(appMarks).toString()).apply();
    }

    private void showAddAirportDialog() {
        final EditText i = new EditText(this); i.setInputType(InputType.TYPE_CLASS_TEXT); i.setHint("例如：一元机场");
        new AlertDialog.Builder(this).setTitle("添加自建机场").setView(i).setPositiveButton("添加", (d, w) -> {
            String n = i.getText().toString().trim();
            if (!n.isEmpty()) { if (virtualAirports.contains(n)) Toast.makeText(this, "已存在", Toast.LENGTH_SHORT).show(); else { virtualAirports.add(n); saveSets(); scanApps(); } }
        }).setNegativeButton("取消", null).show();
    }

    private void showRenameAirportDialog(AppInfo a) {
        final EditText i = new EditText(this); i.setInputType(InputType.TYPE_CLASS_TEXT); i.setText(a.label);
        new AlertDialog.Builder(this).setTitle("修改名称").setView(i).setPositiveButton("保存", (d, w) -> {
            String n = i.getText().toString().trim();
            if (!n.isEmpty() && !n.equals(a.label)) { virtualAirports.remove(a.label); virtualAirports.add(n); saveSets(); scanApps(); }
        }).setNegativeButton("取消", null).show();
    }

    private void deleteAirport(AppInfo a) {
        new AlertDialog.Builder(this).setTitle("删除机场").setMessage("确定删除「" + a.label + "」？").setPositiveButton("删除", (d, w) -> { virtualAirports.remove(a.label); saveSets(); scanApps(); }).setNegativeButton("取消", null).show();
    }

    private void showAppOptionsDialog(AppInfo a) {
        List<String> ops = new ArrayList<>(); List<Runnable> ac = new ArrayList<>();
        if (a.isVirtual) { ops.add("📝 修改名称"); ac.add(() -> showRenameAirportDialog(a)); ops.add("🗑️ 删除机场"); ac.add(() -> deleteAirport(a)); }
        else {
            ops.add("🚀 打开应用"); ac.add(() -> { if (a.launchIntent != null) { try { startActivity(a.launchIntent); } catch (Exception e) { Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show(); } } });
            ops.add("🏷️ 标签"); ac.add(() -> showMarkDialog(a)); ops.add("📋 复制包名"); ac.add(() -> copyPackage(a.packageName)); ops.add("⚙️ 应用信息"); ac.add(() -> jumpToSettings(a.packageName));
            if (a.isCustom) { ops.add("❌ 取消收编"); ac.add(() -> unmarkVpn(a.packageName)); }
            ops.add("🗑️ 卸载应用"); ac.add(() -> confirmUninstall(a));
        }
        new AlertDialog.Builder(this).setTitle(a.label).setItems(ops.toArray(new String[0]), (d, w) -> ac.get(w).run()).show();
    }

    private void showMarkDialog(AppInfo a) {
        String[] m = {"最低收费", "最高收费", "稳定", "下载速度快", "免费好用", "清除标签"};
        new AlertDialog.Builder(this).setTitle("选择标签").setItems(m, (d, w) -> { if (w == 5) { appMarks.remove(a.packageName); saveSets(); scanApps(); } else applyMark(a.packageName, m[w]); }).show();
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
        new AlertDialog.Builder(this).setTitle("选择主题").setSingleChoiceItems(THEME_NAMES, prefs.getInt(KEY_THEME, 0), (d, w) -> { applyTheme(w, true); d.dismiss(); }).setNegativeButton("取消", null).show();
    }

    private void applyTheme(int w, boolean p) {
        if (w < 0 || w >= ThemeColors.THEMES.length) w = 0;
        currentTheme = ThemeColors.THEMES[w];
        rootLayout.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{currentTheme.backgroundStart, currentTheme.backgroundEnd}));
        titleText.setTextColor(currentTheme.text); if (subtitleText != null) subtitleText.setTextColor(currentTheme.secondary);
        showAllCheckbox.setTextColor(currentTheme.text); tvHelp.setTextColor(Color.WHITE); tvHelp.setBackground(circleDrawable(currentTheme.accent)); emptyView.setTextColor(currentTheme.secondary);
        searchBox.setTextColor(currentTheme.text); searchBox.setHintTextColor(currentTheme.secondary); searchBox.setBackground(roundDrawable(currentTheme.searchBackground, currentTheme.stroke, dp(18)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) showAllCheckbox.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{currentTheme.accent, currentTheme.secondary}));
        btnSettings.setTextColor(currentTheme.text); btnSettings.setBackground(roundDrawable(currentTheme.card, currentTheme.stroke, dp(16))); btnSettings.setAllCaps(false);
        if (p) prefs.edit().putInt(KEY_THEME, w).apply(); if (adapter != null) adapter.notifyDataSetChanged();
    }

    private GradientDrawable roundDrawable(int c, int s, int r) { GradientDrawable d = new GradientDrawable(); d.setColor(c); d.setCornerRadius(r); if (s != Color.TRANSPARENT) d.setStroke(dp(1), s); return d; }
    private GradientDrawable circleDrawable(int c) { GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(c); return d; }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void jumpToSettings(String p) { try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p))); } catch (Exception e) { Toast.makeText(this, "无法打开设置页", Toast.LENGTH_SHORT).show(); } }

    private void scanApps() {
        final int gen = ++scanGeneration; final Set<String> fs = new HashSet<>(favoritePackages), cs = new HashSet<>(customVpnPackages), vs = new HashSet<>(virtualAirports); final Map<String, String> ms = new HashMap<>(appMarks); final boolean sa = isShowAll; final String self = getPackageName();
        emptyView.setText("正在扫描…"); int f = listView.getFirstVisiblePosition(); View v = listView.getChildAt(0); int t = v == null ? 0 : v.getTop();
        new Thread(() -> {
            PackageManager pm = getPackageManager(); Set<String> vp = new HashSet<>(); List<ResolveInfo> s = pm.queryIntentServices(new Intent(VPN_SERVICE_ACTION), 0);
            if (s != null) for (ResolveInfo ri : s) if (ri.serviceInfo != null) vp.add(ri.serviceInfo.packageName);
            Intent li = new Intent(Intent.ACTION_MAIN); li.addCategory(Intent.CATEGORY_LAUNCHER); List<ResolveInfo> as = pm.queryIntentActivities(li, 0);
            Set<String> sn = new HashSet<>(); List<AppInfo> fd = new ArrayList<>();
            if (as != null) for (ResolveInfo ri : as) {
                if (ri.activityInfo == null) continue; String p = ri.activityInfo.packageName; if (!sn.add(p) || self.equals(p)) continue;
                ApplicationInfo ai; try { ai = pm.getApplicationInfo(p, 0); } catch (Exception e) { continue; }
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue; Intent l = pm.getLaunchIntentForPackage(p); if (l == null) continue;
                String lb = pm.getApplicationLabel(ai).toString(); String mk = ms.get(p); boolean ia = vp.contains(p) || matchesKeyword(lb, p), ic = cs.contains(p), ifv = fs.contains(p);
                if (!sa && !ia && !ic && !ifv && mk == null) continue;
                Drawable i = iconCache.get(p); if (i == null) { try { i = pm.getApplicationIcon(ai); } catch (Exception e) { i = pm.getDefaultActivityIcon(); } iconCache.put(p, i); }
                fd.add(new AppInfo(lb, p, i, l, ia, ic, ifv, mk, formatSize(ai), false));
            }
            for (String an : vs) fd.add(new AppInfo(an, "v_" + an.hashCode(), pm.getDefaultActivityIcon(), null, false, true, fs.contains("v_" + an.hashCode()), "机场", "自建", true));
            runOnUiThread(() -> {
                if (gen != scanGeneration || isFinishing() || isDestroyed() || listView.getAdapter() == null) return;
                allApps.clear(); allApps.addAll(fd); sortAndAssignIndex(); filterApps(searchBox.getText().toString()); listView.post(() -> listView.setSelectionFromTop(f, t));
                if (fd.isEmpty()) emptyView.setText(sa ? "无可启动应用" : "未识别到 VPN\n可勾选「显示全部应用」");
            });
        }).start();
    }

    private static boolean matchesKeyword(String l, String p) { String ll = l.toLowerCase(Locale.ROOT), lp = p.toLowerCase(Locale.ROOT); for (String k : KEYWORDS) if (ll.contains(k) || lp.contains(k)) return true; return false; }
    private static String formatSize(ApplicationInfo ai) { long s = 0; try { if (ai.publicSourceDir != null) s += new File(ai.publicSourceDir).length(); } catch (Exception e) {} return String.format(Locale.getDefault(), "%.1f MB", s / (1024.0 * 1024.0)); }
    
    private void sortAndAssignIndex() { 
        Collections.sort(allApps, (a, b) -> { if (a.isFavorite != b.isFavorite) return a.isFavorite ? -1 : 1; if (a.isDetected() != b.isDetected()) return a.isDetected() ? -1 : 1; return Collator.getInstance().compare(a.label, b.label); }); 
        for (int i = 0; i < allApps.size(); i++) allApps.get(i).absoluteIndex = i + 1; 
    }
    
    private void filterApps(String q) {
        apps.clear(); if (q == null || q.isEmpty()) apps.addAll(allApps);
        else { String lq = q.toLowerCase(Locale.ROOT); for (AppInfo a : allApps) if (a.label.toLowerCase(Locale.ROOT).contains(lq) || a.packageName.toLowerCase(Locale.ROOT).contains(lq)) apps.add(a); }
        adapter.notifyDataSetChanged();
    }private static class AppInfo {
        final String label, packageName, sizeStr; final Drawable icon; final Intent launchIntent; final boolean isAutoDetected, isVirtual;
        boolean isCustom, isFavorite; String mark; int absoluteIndex;
        AppInfo(String l, String p, Drawable i, Intent in, boolean a, boolean c, boolean f, String m, String s, boolean v) { label = l; packageName = p; icon = i; launchIntent = in; isAutoDetected = a; isCustom = c; isFavorite = f; mark = m; sizeStr = s; isVirtual = v; }
        boolean isDetected() { return isAutoDetected || isCustom || mark != null || isVirtual; }
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int p) { return apps.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View c, ViewGroup pr) {
            ViewHolder h; 
            if (c == null) { 
                c = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_app, pr, false); 
                h = new ViewHolder(); 
                h.root = c.findViewById(R.id.row_root); h.index = c.findViewById(R.id.tv_index); h.icon = c.findViewById(R.id.app_icon); h.name = c.findViewById(R.id.app_name); h.mark = c.findViewById(R.id.tv_custom_mark); h.check = c.findViewById(R.id.check_mark); h.fav = c.findViewById(R.id.tv_favorite); h.size = c.findViewById(R.id.tv_size); h.plus = c.findViewById(R.id.tv_mark_vpn); h.arrow = c.findViewById(R.id.tv_arrow); 
                c.setTag(h); 
            } else h = (ViewHolder) c.getTag();
            
            AppInfo a = apps.get(p); ThemeColors t = currentTheme;
            h.index.setText(String.valueOf(a.absoluteIndex)); h.index.setTextColor(t.secondary); h.index.setBackground(circleDrawable(t.indexBackground));
            if (a.isVirtual) h.icon.setImageResource(android.R.drawable.ic_menu_compass); else h.icon.setImageDrawable(a.icon);
            h.name.setText(a.label); h.name.setTextColor(t.text); h.size.setText(a.sizeStr); h.size.setTextColor(t.secondary); h.fav.setVisibility(a.isFavorite ? View.VISIBLE : View.GONE); h.arrow.setTextColor(t.secondary);
            
            if (a.mark != null && !a.mark.isEmpty()) { h.mark.setText(a.mark); h.mark.setVisibility(View.VISIBLE); } else h.mark.setVisibility(View.GONE);
            
            h.root.setBackground(roundDrawable(t.card, t.cardStroke, dp(20)));
            
            if (a.isDetected()) { 
                h.check.setVisibility(View.VISIBLE); h.check.setText("✓"); h.check.setTextColor(Color.WHITE); h.check.setBackground(circleDrawable(Color.rgb(48, 190, 98))); h.plus.setVisibility(View.GONE); h.plus.setOnClickListener(null); 
            } else if (isShowAll) { 
                h.check.setVisibility(View.GONE); h.plus.setTextColor(t.accent); h.plus.setVisibility(View.VISIBLE); h.plus.setOnClickListener(v -> applyMark(a.packageName, "手动收编")); 
            } else { 
                h.check.setVisibility(View.GONE); h.plus.setVisibility(View.GONE); h.plus.setOnClickListener(null); 
            }
            
            h.fav.setOnClickListener(v -> toggleFavorite(a.packageName));
            return c;
        }
    }

    private static class ViewHolder { LinearLayout root; TextView index, name, mark, check, fav, size, plus, arrow; ImageView icon; }

    private static class ThemeColors {
        final int backgroundStart, backgroundEnd, card, cardStroke, text, secondary, accent, searchBackground, indexBackground; final boolean dark;
        ThemeColors(int s, int e, int c, int st, int t, int sc, int a, int se, int i, boolean d) { backgroundStart = s; backgroundEnd = e; card = c; cardStroke = st; text = t; secondary = sc; accent = a; searchBackground = se; indexBackground = i; dark = d; }
        static final ThemeColors[] THEMES = {
            new ThemeColors(0xFFEAF3FF, 0xFFD8E8FF, 0xF7FFFFFF, 0xFFDCE8FA, 0xFF172B4D, 0xFF7889A8, 0xFF2878E8, 0xF9FFFFFF, 0xFFE5EEFF, false),
            new ThemeColors(0xFF11182B, 0xFF202D4A, 0xFF27334F, 0xFF3A4969, 0xFFF1F5FF, 0xFFB1BDD4, 0xFF72A7FF, 0xFF303C58, 0xFF3A4969, true),
            new ThemeColors(0xFFEAF3E7, 0xFFDCEBDD, 0xF9FAFFF7, 0xFFD5E5D1, 0xFF304B37, 0xFF718875, 0xFF548B5B, 0xF9FFFFFF, 0xFFE5F0E1, false),
            new ThemeColors(0xFFF3ECFF, 0xFFE7DCFA, 0xFAFFFFFF, 0xFFE2D8F2, 0xFF3F315B, 0xFF82769B, 0xFF8062C9, 0xFAFFFFFF, 0xFFEDE5FF, false),
            new ThemeColors(0xFFFFF5E6, 0xFFFFE7C8, 0xFAFFFCF6, 0xFFF0DFC8, 0xFF57432E, 0xFF927D65, 0xFFC17A32, 0xFAFFFFFF, 0xFFFFEED8, false),
            new ThemeColors(0xFFE8F7F3, 0xFFD4EEE8, 0xFAFFFFFF, 0xFFD6EAE4, 0xFF285248, 0xFF718F87, 0xFF278F82, 0xFAFFFFFF, 0xFFDFF2EC, false)
        };
    }
}
