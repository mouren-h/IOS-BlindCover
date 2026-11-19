package mr.x.iosx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class Jzui {

    private final Activity activity;
    private ImageView blurImageView;
    private boolean isInitialized = false;
    private float defaultBlurRadius = 20f;
    private Cqd globalCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable countdownTask;

    public Jzui(Activity activity) {
        this.activity = activity;
    }

    public Jzui(Activity activity, float defaultRadius) {
        this.activity = activity;
        this.defaultBlurRadius = clamp(defaultRadius);
    }

    // ======================== 主入口 ========================

    public Jzui show(Object... params) {
        if (activity == null) return this;

        boolean countdown = false;
        int seconds = 3;
        String radiusStr = null;
        Cqd callback = null;

        for (Object p : params) {
            if (p instanceof Boolean)      countdown = (Boolean) p;
            else if (p instanceof Number)  seconds = ((Number) p).intValue();
            else if (p instanceof String)  radiusStr = (String) p;
            else if (p instanceof Cqd)     callback = (Cqd) p;
        }

        final boolean doCountdown = countdown;
        final int sec = seconds;
        final float radius = parseFloat(radiusStr, defaultBlurRadius);
        this.globalCallback = callback;

        postWhenReady(new Runnable() {
            @Override
            public void run() {
                showBlurEffect(radius);
                isInitialized = true;

                if (doCountdown && sec > 0) {
                    cancelCountdown();
                    countdownTask = new Runnable() {
                        @Override
                        public void run() {
                            if (activity != null && !activity.isFinishing()) {
                                gb();
                                if (globalCallback != null) {
                                    globalCallback.onCheckComplete("true");
                                }
                            }
                        }
                    };
                    handler.postDelayed(countdownTask, sec * 1000L);
                }
            }
        });

        return this;
    }

    // ======================== 链式配置 ========================

    public Jzui setBlurRadius(String blurRadius) {
        if (activity == null) return this;
        final float radius = parseFloat(blurRadius, defaultBlurRadius);
        defaultBlurRadius = clamp(radius);

        // 不依赖 isInitialized / blurImageView，无条件重绘
        // 用 post 保证在 show() 之后执行，覆盖 show 里的半径
        postWhenReady(new Runnable() {
            @Override
            public void run() {
                showBlurEffect(radius);
                isInitialized = true;
            }
        });
        return this;
    }

    public Jzui setDefaultBlurRadius(float radius) {
        defaultBlurRadius = clamp(radius);
        return this;
    }

    public Jzui Async(Cqd callback) {
        this.globalCallback = callback;
        return this;
    }

    // ======================== 关闭 / 销毁 ========================

    public Jzui gb() {
        if (activity == null) return this;
        cancelCountdown();
        postWhenReady(new Runnable() {
            @Override
            public void run() {
                removeBlurEffect();
                isInitialized = false;
            }
        });
        return this;
    }

    public Jzui release() {
        cancelCountdown();
        handler.removeCallbacksAndMessages(null);
        globalCallback = null;
        if (activity != null && !activity.isFinishing()) {
            postWhenReady(new Runnable() {
                @Override
                public void run() {
                    removeBlurEffect();
                    isInitialized = false;
                }
            });
        }
        return this;
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            handler.removeCallbacks(countdownTask);
            countdownTask = null;
        }
    }

    /** 等 decorView 尺寸有效后再执行，避免 onCreate 阶段尺寸为 0 */
    private void postWhenReady(final Runnable action) {
        final View decor = activity.getWindow().getDecorView();
        decor.post(new Runnable() {
            @Override
            public void run() {
                if (decor.getWidth() <= 0 || decor.getHeight() <= 0) {
                    decor.postDelayed(this, 32);
                    return;
                }
                action.run();
            }
        });
    }

    // ======================== 内部实现 ========================

    private void showBlurEffect(float blurRadius) {
        try {
            View rootView = activity.getWindow().getDecorView().getRootView();

            // 先移除旧层，防止叠层
            if (blurImageView != null) {
                ((ViewGroup) rootView).removeView(blurImageView);
                blurImageView = null;
            }

            Bitmap screenshot = takeScreenshot(rootView);
            if (screenshot == null) {
                // 截图失败时降级为纯半透明遮罩，保证至少能挡住
                blurImageView = new ImageView(activity);
                blurImageView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                blurImageView.setBackgroundColor(0xCC000000);
            } else {
                Bitmap blurredBitmap = applyBlur(screenshot, blurRadius);

                blurImageView = new ImageView(activity);
                blurImageView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                blurImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                blurImageView.setImageBitmap(blurredBitmap);
                blurImageView.setAlpha(0.95f);

                if (!screenshot.isRecycled()) screenshot.recycle();
            }

            blurImageView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            blurImageView.setFocusable(true);
            blurImageView.setFocusableInTouchMode(true);
            blurImageView.setClickable(true);
            blurImageView.requestFocus();

            ((ViewGroup) rootView).addView(blurImageView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeBlurEffect() {
        if (activity == null) return;
        try {
            if (blurImageView != null) {
                ViewGroup rootLayout = (ViewGroup) activity.getWindow().getDecorView().getRootView();
                rootLayout.removeView(blurImageView);
                blurImageView = null;
            }
            View rootView = activity.getWindow().getDecorView().getRootView();
            rootView.setFocusable(true);
            rootView.setFocusableInTouchMode(true);
            rootView.requestFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 用 view.draw(canvas) 替代 drawingCache，硬件加速下也能拿到画面 */
    private Bitmap takeScreenshot(View view) {
        try {
            int w = view.getWidth();
            int h = view.getHeight();
            if (w <= 0 || h <= 0) return null;

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap applyBlur(Bitmap image, float blurRadius) {
        try {
            float radius = clamp(blurRadius);

            int width = Math.round(image.getWidth() / 8.0f);
            int height = Math.round(image.getHeight() / 8.0f);

            Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

            android.renderscript.RenderScript rs =
                    android.renderscript.RenderScript.create(activity);
            android.renderscript.Allocation tmpIn =
                    android.renderscript.Allocation.createFromBitmap(rs, inputBitmap);
            android.renderscript.Allocation tmpOut =
                    android.renderscript.Allocation.createFromBitmap(rs, outputBitmap);

            android.renderscript.ScriptIntrinsicBlur blurScript =
                    android.renderscript.ScriptIntrinsicBlur.create(
                            rs, android.renderscript.Element.U8_4(rs));

            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);

            tmpOut.copyTo(outputBitmap);

            Bitmap finalBitmap = Bitmap.createScaledBitmap(
                    outputBitmap, image.getWidth(), image.getHeight(), true);

            rs.destroy();
            if (!inputBitmap.isRecycled()) inputBitmap.recycle();
            if (!outputBitmap.isRecycled()) outputBitmap.recycle();

            return finalBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return image;
        }
    }

    private static float parseFloat(String str, float def) {
        if (str == null) return def;
        try { return Float.parseFloat(str); }
        catch (NumberFormatException e) { return def; }
    }

    private static float clamp(float r) {
        return Math.max(0.1f, Math.min(25f, r));
    }

    // ======================== 查询 ========================

    public boolean isShowing() {
        return isInitialized && blurImageView != null;
    }

    public Activity getActivity() {
        return activity;
    }

    public interface Cqd {
        void onCheckComplete(String result);
    }
}