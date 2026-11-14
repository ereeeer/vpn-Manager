    private static class ThemeBackgroundDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeColors theme;
        private int drawableAlpha = 255;
        private ColorFilter colorFilter;

        ThemeBackgroundDrawable(ThemeColors theme) {
            this.theme = theme;
        }

        private int withAlpha(int color) {
            int alpha = Color.alpha(color) * drawableAlpha / 255;
            return (color & 0x00FFFFFF) | (alpha << 24);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float width = bounds.width();
            float height = bounds.height();
            if (width <= 0 || height <= 0) return;

            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);

            // 主背景渐变。
            paint.setShader(new LinearGradient(
                    0, 0, width, height,
                    theme.backgroundStart,
                    theme.backgroundEnd,
                    Shader.TileMode.CLAMP
            ));
            paint.setColor(Color.WHITE);
            paint.setAlpha(drawableAlpha);
            paint.setColorFilter(colorFilter);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);

            // 顶部柔和斜向光带。
            paint.setColor(withAlpha(
                    theme.dark ? 0x1C91B8FF : 0x35FFFFFF));
            paint.setColorFilter(colorFilter);

            Path topRibbon = new Path();
            topRibbon.moveTo(width * 0.34f, 0);
            topRibbon.cubicTo(
                    width * 0.50f, height * 0.02f,
                    width * 0.57f, height * 0.09f,
                    width * 0.49f, height * 0.17f
            );
            topRibbon.cubicTo(
                    width * 0.43f, height * 0.24f,
                    width * 0.48f, height * 0.30f,
                    width * 0.66f, height * 0.36f
            );
            topRibbon.cubicTo(
                    width * 0.50f, height * 0.31f,
                    width * 0.34f, height * 0.24f,
                    width * 0.37f, height * 0.15f
            );
            topRibbon.cubicTo(
                    width * 0.40f, height * 0.08f,
                    width * 0.28f, height * 0.04f,
                    width * 0.34f, 0
            );
            topRibbon.close();
            canvas.drawPath(topRibbon, paint);

            // 右上方弧形光影。
            paint.setColor(withAlpha(
                    theme.dark ? 0x174A74C7 : 0x24FFFFFF));

            Path upperRight = new Path();
            upperRight.moveTo(width * 0.67f, 0);
            upperRight.cubicTo(
                    width * 0.91f, height * 0.02f,
                    width * 0.98f, height * 0.11f,
                    width * 0.82f, height * 0.20f
            );
            upperRight.cubicTo(
                    width * 0.72f, height * 0.26f,
                    width * 0.70f, height * 0.31f,
                    width * 0.82f, height * 0.39f
            );
            upperRight.cubicTo(
                    width * 0.63f, height * 0.31f,
                    width * 0.56f, height * 0.22f,
                    width * 0.67f, height * 0.13f
            );
            upperRight.cubicTo(
                    width * 0.76f, height * 0.07f,
                    width * 0.72f, height * 0.03f,
                    width * 0.67f, 0
            );
            upperRight.close();
            canvas.drawPath(upperRight, paint);

            // 底部斜向装饰。
            paint.setColor(withAlpha(
                    theme.dark ? 0x203C68B7 : 0x25FFFFFF));

            Path bottomRibbon = new Path();
            bottomRibbon.moveTo(width * 0.58f, height);
            bottomRibbon.cubicTo(
                    width * 0.72f, height * 0.91f,
                    width * 0.85f, height * 0.91f,
                    width, height * 0.80f
            );
            bottomRibbon.lineTo(width, height);
            bottomRibbon.close();
            canvas.drawPath(bottomRibbon, paint);

            // 左下角淡淡的弧形装饰。
            paint.setColor(withAlpha(
                    theme.dark ? 0x164C77D2 : 0x20FFFFFF));

            Path lowerLeft = new Path();
            lowerLeft.moveTo(0, height * 0.80f);
            lowerLeft.cubicTo(
                    width * 0.12f, height * 0.73f,
                    width * 0.19f, height * 0.78f,
                    width * 0.23f, height
            );
            lowerLeft.lineTo(0, height);
            lowerLeft.close();
            canvas.drawPath(lowerLeft, paint);

            canvas.restoreToCount(save);
            paint.setShader(null);
        }

        @Override
        public void setAlpha(int alpha) {
            drawableAlpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            colorFilter = filter;
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class ThemeColors {
        final int backgroundStart, backgroundEnd, card, cardStroke, text, secondary, accent, searchBackground, indexBackground;
        final boolean dark;

        ThemeColors(int s, int e, int c, int st, int t, int sc, int a, int se, int i, boolean d) {
            backgroundStart = s; backgroundEnd = e; card = c; cardStroke = st;
            text = t; secondary = sc; accent = a; searchBackground = se;
            indexBackground = i; dark = d;
        }

        // 颜色已调整：背景稍微加深，卡片不再纯白（更柔和）
        static final ThemeColors[] THEMES = {
            // 蓝（默认）
            new ThemeColors(0xFFA8C8F0, 0xFF7BA8E0, 0xF5FFFFFF, 0xFF7AA3D9, 0xFF172B4D, 0xFF5C6D87, 0xFF2878E8, 0xF8FFFFFF, 0xFFE0ECFF, false),
            // 深色
            new ThemeColors(0xFF141A2E, 0xFF0A0E17, 0xFF1E263D, 0xFF405477, 0xFFF1F5FF, 0xFF8A9BB8, 0xFF72A7FF, 0xFF24314B, 0xFF2D3C56, true),
            // 绿
            new ThemeColors(0xFFC8E6CC, 0xFFB0D6B5, 0xF5FFFFFF, 0xFF85B98F, 0xFF2D4532, 0xFF5D7562, 0xFF548B5B, 0xF8FFFFFF, 0xFFD4E8D0, false),
            // 紫
            new ThemeColors(0xFFD4C4F0, 0xFFBCA8E8, 0xF5FFFFFF, 0xFFA48DD4, 0xFF3A2D52, 0xFF736789, 0xFF8062C9, 0xF8FFFFFF, 0xFFE4D6FF, false),
            // 橙
            new ThemeColors(0xFFF5DFC4, 0xFFE8C89A, 0xF5FFFFFF, 0xFFD4A86E, 0xFF513D29, 0xFF806B53, 0xFFC17A32, 0xF8FFFFFF, 0xFFFFE4C4, false),
            // 青
            new ThemeColors(0xFFC8EBE4, 0xFFA8D9CF, 0xF5FFFFFF, 0xFF75B8A9, 0xFF234B41, 0xFF5E857C, 0xFF278F82, 0xF8FFFFFF, 0xFFCAE6DF, false)
        };
    }
