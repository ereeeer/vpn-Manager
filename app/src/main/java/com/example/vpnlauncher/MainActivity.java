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

        static final ThemeColors[] THEMES = {
            new ThemeColors(0xFFBFD9FF, 0xFF91B9F2, 0xFFFFFFFF, 0xFF82AEE8, 0xFF172B4D, 0xFF5C6D87, 0xFF2878E8, 0xFFFFFFFF, 0xFFE5EEFF, false),
            new ThemeColors(0xFF141A2E, 0xFF0A0E17, 0xFF1E263D, 0xFF405477, 0xFFF1F5FF, 0xFF8A9BB8, 0xFF72A7FF, 0xFF24314B, 0xFF2D3C56, true),
            new ThemeColors(0xFFE1F2E4, 0xFFCBE7CE, 0xFFFFFFFF, 0xFF91C99B, 0xFF2D4532, 0xFF5D7562, 0xFF548B5B, 0xFFFFFFFF, 0xFFDCECD8, false),
            new ThemeColors(0xFFE6D8FF, 0xFFD1BDFF, 0xFFFFFFFF, 0xFFB39AE8, 0xFF3A2D52, 0xFF736789, 0xFF8062C9, 0xFFFFFFFF, 0xFFEADDFF, false),
            new ThemeColors(0xFFFFEEDD, 0xFFFFD9B0, 0xFFFFFFFF, 0xFFE2B77F, 0xFF513D29, 0xFF806B53, 0xFFC17A32, 0xFFFFFFFF, 0xFFFFE8CC, false),
            new ThemeColors(0xFFDEF5F0, 0xFFC0E8E0, 0xFFFFFFFF, 0xFF82C9BA, 0xFF234B41, 0xFF5E857C, 0xFF278F82, 0xFFFFFFFF, 0xFFD4ECE6, false)
        };
    }
}
