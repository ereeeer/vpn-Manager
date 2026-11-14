@Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View c, ViewGroup pr) {
            ViewHolder h; if (c == null) { c = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_app, pr, false); h = new ViewHolder(); h.root = c.findViewById(R.id.row_root); h.index = c.findViewById(R.id.tv_index); h.icon = c.findViewById(R.id.app_icon); h.name = c.findViewById(R.id.app_name); h.mark = c.findViewById(R.id.tv_custom_mark); h.reinstallMark = c.findViewById(R.id.tv_reinstalled); h.check = c.findViewById(R.id.check_mark); h.fav = c.findViewById(R.id.tv_favorite); h.size = c.findViewById(R.id.tv_size); h.plus = c.findViewById(R.id.tv_mark_vpn); h.arrow = c.findViewById(R.id.tv_arrow); c.setTag(h); } else h = (ViewHolder) c.getTag();
            AppInfo a = apps.get(p); ThemeColors t = currentTheme;
            h.index.setText(String.valueOf(a.absoluteIndex));
            h.index.setTextColor(t.secondary);
            h.index.setBackground(circleDrawable(t.indexBackground));
            if (a.isVirtual) h.icon.setImageResource(android.R.drawable.ic_menu_compass); else h.icon.setImageDrawable(a.icon);
            h.name.setText(a.label); h.name.setTextColor(t.text); h.size.setText(a.sizeStr); h.size.setTextColor(t.secondary); h.fav.setVisibility(a.isFavorite ? View.VISIBLE : View.GONE); h.arrow.setTextColor(t.secondary);
            
            if (a.mark != null && !a.mark.isEmpty()) { h.mark.setText(a.mark); h.mark.setVisibility(View.VISIBLE); } else h.mark.setVisibility(View.GONE);
            h.reinstallMark.setVisibility(a.isReinstalled ? View.VISIBLE : View.GONE);

            h.root.setBackground(roundDrawable(t.card, t.cardStroke, dp(17)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                h.root.setElevation(dp(t.dark ? 1.5f : 3f));
            }
            if (a.isDetected()) { h.check.setVisibility(View.VISIBLE); h.plus.setVisibility(View.GONE); h.plus.setOnClickListener(null); }
            else if (isShowAll) { h.check.setVisibility(View.GONE); h.plus.setTextColor(t.accent); h.plus.setVisibility(View.VISIBLE); h.plus.setOnClickListener(v -> applyMark(a.packageName, "手动收编")); }
            else { h.check.setVisibility(View.GONE); h.plus.setVisibility(View.GONE); h.plus.setOnClickListener(null); }
            h.fav.setOnClickListener(v -> toggleFavorite(a.packageName)); return c;
        }
    }

    private static class ViewHolder { LinearLayout root; TextView index, name, mark, reinstallMark, fav, size, plus, arrow; ImageView icon, check; }

    /** 绘制主题渐变和低对比度的弧形装饰，不需要额外图片资源。 */
    private static class ThemeBackgroundDrawable extends Drawable {
        private final ThemeColors theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int drawableAlpha = 255;
        private ColorFilter colorFilter;

        ThemeBackgroundDrawable(ThemeColors theme) {
            this.theme = theme;
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float width = bounds.width();
            float height = bounds.height();
            if (width <= 0 || height <= 0) return;

            paint.setShader(new LinearGradient(
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    theme.backgroundStart, theme.backgroundEnd, Shader.TileMode.CLAMP));
            paint.setAlpha(drawableAlpha);
            paint.setColorFilter(colorFilter);
            canvas.drawRect(bounds, paint);
            paint.setShader(null);

            // 上部斜向柔光带。
            paint.setColor(theme.dark ? 0x1C91B8FF : 0x42FFFFFF);
            paint.setAlpha(drawableAlpha);
            paint.setColorFilter(colorFilter);
            Path topRibbon = new Path();
            topRibbon.moveTo(width * 0.34f, 0);
            topRibbon.cubicTo(width * 0.50f, height * 0.02f, width * 0.57f, height * 0.09f, width * 0.49f, height * 0.17f);
            topRibbon.cubicTo(width * 0.43f, height * 0.24f, width * 0.48f, height * 0.30f, width * 0.66f, height * 0.36f);
            topRibbon.cubicTo(width * 0.50f, height * 0.31f, width * 0.34f, height * 0.24f, width * 0.37f, height * 0.15f);
            topRibbon.cubicTo(width * 0.40f, height * 0.08f, width * 0.28f, height * 0.04f, width * 0.34f, 0);
            topRibbon.close();
            canvas.drawPath(topRibbon, paint);

            // 右上角宽幅的半透明叶片形光影。
            paint.setColor(theme.dark ? 0x174A74C7 : 0x2AFFFFFF);
            Path upperRight = new Path();
            upperRight.moveTo(width * 0.67f, 0);
            upperRight.cubicTo(width * 0.91f, height * 0.02f, width * 0.98f, height * 0.11f, width * 0.82f, height * 0.20f);
            upperRight.cubicTo(width * 0.72f, height * 0.26f, width * 0.70f, height * 0.31f, width * 0.82f, height * 0.39f);
            upperRight.cubicTo(width * 0.63f, height * 0.31f, width * 0.56f, height * 0.22f, width * 0.67f, height * 0.13f);
            upperRight.cubicTo(width * 0.76f, height * 0.07f, width * 0.72f, height * 0.03f, width * 0.67f, 0);
            upperRight.close();
            canvas.drawPath(upperRight, paint);

            // 底部斜向装饰，避免遮挡文字和列表主体。
            paint.setColor(theme.dark ? 0x203C68B7 : 0x32FFFFFF);
            Path bottomRibbon = new Path();
            bottomRibbon.moveTo(width * 0.58f, height);
            bottomRibbon.cubicTo(width * 0.72f, height * 0.91f, width * 0.85f, height * 0.91f, width, height * 0.80f);
            bottomRibbon.lineTo(width, height);
            bottomRibbon.close();
            canvas.drawPath(bottomRibbon, paint);

            paint.setColor(theme.dark ? 0x164C77D2 : 0x25FFFFFF);
            Path lowerLeft = new Path();
            lowerLeft.moveTo(0, height * 0.80f);
            lowerLeft.cubicTo(width * 0.12f, height * 0.73f, width * 0.19f, height * 0.78f, width * 0.23f, height);
            lowerLeft.lineTo(0, height);
            lowerLeft.close();
            canvas.drawPath(lowerLeft, paint);
        }

        @Override public void setAlpha(int alpha) {
            drawableAlpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            colorFilter = filter;
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class ThemeColors {
        final int backgroundStart, backgroundEnd, card, cardStroke, text, secondary, accent, searchBackground, indexBackground; final boolean dark;
        ThemeColors(int s, int e, int c, int st, int t, int sc, int a, int se, int i, boolean d) { backgroundStart = s; backgroundEnd = e; card = c; cardStroke = st; text = t; secondary = sc; accent = a; searchBackground = se; indexBackground = i; dark = d; }
        static final ThemeColors[] THEMES = {
            new ThemeColors(0xFFD4E6FF, 0xFFB8D4FF, 0xFFFFFFFF, 0xFFB3CBE8, 0xFF172B4D, 0xFF5C6D87, 0xFF2878E8, 0xFFFFFFFF, 0xFFE5EEFF, false),
            new ThemeColors(0xFF141A2E, 0xFF0A0E17, 0xFF1E263D, 0xFF2D3C56, 0xFFF1F5FF, 0xFF8A9BB8, 0xFF72A7FF, 0xFF24314B, 0xFF2D3C56, true),
            new ThemeColors(0xFFE1F2E4, 0xFFCBE7CE, 0xFFFFFFFF, 0xFFB7DBBB, 0xFF2D4532, 0xFF5D7562, 0xFF548B5B, 0xFFFFFFFF, 0xFFDCECD8, false),
            new ThemeColors(0xFFE6D8FF, 0xFFD1BDFF, 0xFFFFFFFF, 0xFFCBB6EA, 0xFF3A2D52, 0xFF736789, 0xFF8062C9, 0xFFFFFFFF, 0xFFEADDFF, false),
            new ThemeColors(0xFFFFEEDD, 0xFFFFD9B0, 0xFFFFFFFF, 0xFFEECFAE, 0xFF513D29, 0xFF806B53, 0xFFC17A32, 0xFFFFFFFF, 0xFFFFE8CC, false),
            new ThemeColors(0xFFDEF5F0, 0xFFC0E8E0, 0xFFFFFFFF, 0xFFAED9D0, 0xFF234B41, 0xFF5E857C, 0xFF278F82, 0xFFFFFFFF, 0xFFD4ECE6, false)
        };
    }
}
