package com.pocketlab.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.pocketlab.app.R;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.graph.NiceScale;
import com.pocketlab.app.graph.SeriesBuffer;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Custom Canvas line graph for live sensor data.
 *
 * <ul>
 *   <li>Several series, drawn from {@link SeriesBuffer}s that the owner keeps updating.</li>
 *   <li>Y axis auto-scales to the visible window; "nice" grid ticks on both axes.</li>
 *   <li>Follows the newest data by default. Pinch = zoom the time window, drag = pan
 *       back in time, double-tap = back to live view.</li>
 *   <li>Event markers drawn as dashed vertical lines.</li>
 *   <li>Dense data is reduced to a min/max envelope per pixel column, so spikes are kept
 *       and drawing stays cheap.</li>
 * </ul>
 * The owner calls {@link #invalidate()} when data changes. Main thread only.
 */
public class GraphView extends View {

    private static final double MIN_WINDOW = 1.0;
    private static final double MAX_WINDOW = 600.0;
    private static final double DEFAULT_WINDOW = 10.0;

    private List<SeriesBuffer> series = Collections.emptyList();
    private List<String> labels = Collections.emptyList();
    private int[] colors = new int[]{Color.BLUE};
    private List<Marker> markers = Collections.emptyList();
    private String unit = "";

    private double windowSeconds = DEFAULT_WINDOW;
    private boolean follow = true;
    private double viewEnd;
    private double currentEnd, currentOldest, currentLatest;
    private float plotLeft, plotTop, plotRight, plotBottom;

    private final float density;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    public GraphView(Context context) {
        this(context, null);
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        int textColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        int outline = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutline, Color.GRAY);

        textPaint.setColor(textColor);
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));

        gridPaint.setColor(ColorUtils.setAlphaComponent(outline, 90));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(Math.max(1f, density * 0.7f));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        markerPaint.setColor(textColor);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(Math.max(1f, density));
        markerPaint.setPathEffect(new DashPathEffect(new float[]{6f * density, 4f * density}, 0));

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                windowSeconds = clamp(windowSeconds / d.getScaleFactor(), MIN_WINDOW, MAX_WINDOW);
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scaleDetector.isInProgress()) return false;
                if (currentLatest - currentOldest <= windowSeconds) return false; // nothing to pan
                float pw = plotRight - plotLeft;
                if (pw <= 0) return false;
                if (follow) {
                    follow = false;
                    viewEnd = currentEnd;
                }
                // Finger moving right => distanceX < 0 => look further back in time.
                viewEnd += dx / pw * windowSeconds;
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                resetView();
                return true;
            }
        });

        setContentDescription(context.getString(R.string.graph_description));
    }

    // ---------------- public API ----------------

    /** Binds live data. The lists/buffers are read on every draw, not copied. */
    public void setData(@NonNull List<SeriesBuffer> series, @NonNull List<String> labels,
                        @NonNull int[] colors, @NonNull List<Marker> markers, @NonNull String unit) {
        this.series = series;
        this.labels = labels;
        this.colors = colors.length == 0 ? new int[]{Color.BLUE} : colors;
        this.markers = markers;
        this.unit = unit;
        invalidate();
    }

    public void clearData() {
        series = Collections.emptyList();
        labels = Collections.emptyList();
        markers = Collections.emptyList();
        invalidate();
    }

    /** Back to live view with the default time window. */
    public void resetView() {
        follow = true;
        windowSeconds = DEFAULT_WINDOW;
        invalidate();
    }

    // ---------------- touch ----------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        ViewParent parent = getParent();
        int action = e.getActionMasked();
        if (parent != null) {
            if (action == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true);
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                parent.requestDisallowInterceptTouchEvent(false);
        }
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        return true;
    }

    // ---------------- drawing ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float pad = 8f * density;
        float textH = textPaint.getTextSize();

        // --- data extent ---
        boolean hasData = false;
        double oldest = Double.MAX_VALUE, latest = -Double.MAX_VALUE;
        for (SeriesBuffer s : series) {
            if (s.size() == 0) continue;
            hasData = true;
            oldest = Math.min(oldest, s.getTime(0));
            latest = Math.max(latest, s.getTime(s.size() - 1));
        }
        if (!hasData) {
            drawCentered(canvas, getContext().getString(R.string.graph_empty), w, h);
            return;
        }

        // --- visible time window ---
        double win = windowSeconds;
        double start, end;
        if (latest - oldest <= win) {
            start = oldest;
            end = oldest + win;
        } else if (follow) {
            end = latest;
            start = end - win;
        } else {
            viewEnd = clamp(viewEnd, oldest + win, latest);
            end = viewEnd;
            start = end - win;
        }
        currentEnd = end;
        currentOldest = oldest;
        currentLatest = latest;

        // --- y range from visible points ---
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (SeriesBuffer s : series) {
            for (int i = s.lowerBound(start); i < s.size(); i++) {
                if (s.getTime(i) > end) break;
                float v = s.getValue(i);
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (min > max) { min = 0; max = 1; }
        double range = max - min;
        if (range < 1e-6) {
            min -= 0.5;
            max += 0.5;
        } else {
            min -= range * 0.1;
            max += range * 0.1;
        }
        range = max - min;

        double yStep = NiceScale.niceStep(range, 5);
        int yDec = NiceScale.decimalsForStep(yStep);
        double[] yTicks = NiceScale.ticks(min, max, yStep);
        String yFmt = "%." + yDec + "f";
        float maxLabelW = 0;
        for (double v : yTicks) {
            maxLabelW = Math.max(maxLabelW, textPaint.measureText(String.format(Locale.US, yFmt, v)));
        }

        plotLeft = pad + maxLabelW + 4f * density;
        plotRight = w - pad * 1.5f;
        plotTop = textH + pad * 2f;
        plotBottom = h - (textH + pad * 1.5f);
        float pw = plotRight - plotLeft, ph = plotBottom - plotTop;
        if (pw < 10 || ph < 10) return;

        // --- grid + y labels ---
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (double v : yTicks) {
            float y = plotTop + (float) ((max - v) / range) * ph;
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
            canvas.drawText(String.format(Locale.US, yFmt, v), plotLeft - 4f * density, y + textH / 3f, textPaint);
        }

        // --- grid + x labels ---
        double tStep = NiceScale.niceStep(win, 5);
        String tFmt = "%." + NiceScale.decimalsForStep(tStep) + "fs";
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (double t : NiceScale.ticks(start, end, tStep)) {
            float x = plotLeft + (float) ((t - start) / win) * pw;
            canvas.drawLine(x, plotTop, x, plotBottom, gridPaint);
            canvas.drawText(String.format(Locale.US, tFmt, t), x, plotBottom + textH + 2f * density, textPaint);
        }
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, gridPaint);
        canvas.drawLine(plotLeft, plotTop, plotLeft, plotBottom, gridPaint);

        // --- clipped content: markers + series ---
        canvas.save();
        canvas.clipRect(plotLeft, plotTop, plotRight, plotBottom);

        textPaint.setTextAlign(Paint.Align.LEFT);
        for (Marker m : markers) {
            double t = m.getTimeSeconds();
            if (t < start || t > end) continue;
            float x = plotLeft + (float) ((t - start) / win) * pw;
            canvas.drawLine(x, plotTop, x, plotBottom, markerPaint);
            canvas.drawText(m.getLabel(), x + 3f * density, plotTop + textH, textPaint);
        }

        for (int k = 0; k < series.size(); k++) {
            SeriesBuffer s = series.get(k);
            if (s.size() == 0) continue;
            path.rewind();
            buildPath(s, start, end, win, min, range, pw, ph);
            linePaint.setColor(colors[k % colors.length]);
            canvas.drawPath(path, linePaint);
        }
        canvas.restore();

        drawLegend(canvas, pad, textH, !follow && (latest - oldest > win));
    }

    private void buildPath(SeriesBuffer s, double start, double end, double win,
                           double min, double range, float pw, float ph) {
        int n = s.size();
        int i0 = Math.max(0, s.lowerBound(start) - 1);
        int i1 = Math.min(n - 1, s.lowerBound(end));
        int count = i1 - i0 + 1;
        if (count <= 0) return;
        int pxCount = Math.max(1, (int) pw);

        if (count <= pxCount * 2) {
            for (int i = i0; i <= i1; i++) addPoint(s, i, start, win, min, range, pw, ph);
            return;
        }
        // Dense: one min and one max point per pixel column, emitted in time order.
        int curBucket = -1, minI = -1, maxI = -1;
        for (int i = i0; i <= i1; i++) {
            int b = (int) (((s.getTime(i) - start) / win) * pxCount);
            b = Math.max(0, Math.min(pxCount, b));
            float v = s.getValue(i);
            if (b != curBucket) {
                if (curBucket != -1) flushBucket(s, minI, maxI, start, win, min, range, pw, ph);
                curBucket = b;
                minI = i;
                maxI = i;
            } else {
                if (v < s.getValue(minI)) minI = i;
                if (v > s.getValue(maxI)) maxI = i;
            }
        }
        flushBucket(s, minI, maxI, start, win, min, range, pw, ph);
    }

    private void flushBucket(SeriesBuffer s, int a, int b, double start, double win,
                             double min, double range, float pw, float ph) {
        int first = Math.min(a, b), second = Math.max(a, b);
        addPoint(s, first, start, win, min, range, pw, ph);
        if (second != first) addPoint(s, second, start, win, min, range, pw, ph);
    }

    private void addPoint(SeriesBuffer s, int i, double start, double win,
                          double min, double range, float pw, float ph) {
        float x = plotLeft + (float) ((s.getTime(i) - start) / win) * pw;
        float y = plotTop + (float) ((min + range - s.getValue(i)) / range) * ph;
        if (path.isEmpty()) path.moveTo(x, y); else path.lineTo(x, y);
    }

    private void drawLegend(Canvas canvas, float pad, float textH, boolean showLiveHint) {
        float baseY = pad + textH * 0.9f;
        float x = plotLeft;
        textPaint.setTextAlign(Paint.Align.LEFT);
        for (int k = 0; k < series.size() && k < labels.size(); k++) {
            linePaint.setColor(colors[k % colors.length]);
            canvas.drawLine(x, baseY - textH * 0.3f, x + 12f * density, baseY - textH * 0.3f, linePaint);
            x += 16f * density;
            canvas.drawText(labels.get(k), x, baseY, textPaint);
            x += textPaint.measureText(labels.get(k)) + 12f * density;
        }
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(unit, plotRight, baseY, textPaint);
        if (showLiveHint) {
            float unitW = textPaint.measureText(unit) + 12f * density;
            canvas.drawText(getContext().getString(R.string.graph_live_hint), plotRight - unitW, baseY, textPaint);
        }
    }

    private void drawCentered(Canvas canvas, String text, float w, float h) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, w / 2f, h / 2f, textPaint);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
