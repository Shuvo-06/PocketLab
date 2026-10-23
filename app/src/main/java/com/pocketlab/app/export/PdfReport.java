package com.pocketlab.app.export;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.pocketlab.app.analysis.RunningStats;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.graph.NiceScale;
import com.pocketlab.app.graph.TimeFormat;
import com.pocketlab.app.sensor.SensorType;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One-recording laboratory report as an A4 PDF, drawn with Android's PdfDocument:
 * title, objective, results (inputs, calculated values, percentage error), summary table,
 * graph, markers and a disclaimer. Content flows onto extra pages when needed.
 */
final class PdfReport {

    private static final int PAGE_W = 595, PAGE_H = 842, MARGIN = 48;
    private static final float CONTENT_W = PAGE_W - 2f * MARGIN;
    private static final float BOTTOM = PAGE_H - MARGIN - 14f;
    private static final int[] AXIS_COLORS = {0xFFE53935, 0xFF43A047, 0xFF1E88E5};
    private static final int PRIMARY = 0xFF006A6A;

    private final PdfDocument doc = new PdfDocument();
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNo;
    private float y;

    private final TextPaint titleP = text(20, true, 0xFF111111);
    private final TextPaint headingP = text(13, true, PRIMARY);
    private final TextPaint bodyP = text(10.5f, false, 0xFF222222);
    private final TextPaint boldP = text(10.5f, true, 0xFF222222);
    private final TextPaint mutedP = text(10, false, 0xFF555555);
    private final TextPaint smallP = text(8.5f, false, 0xFF666666);
    private final TextPaint smallRight = new TextPaint(smallP);
    private final TextPaint smallCenter = new TextPaint(smallP);
    private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerP = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PdfReport() {
        smallRight.setTextAlign(Paint.Align.RIGHT);
        smallCenter.setTextAlign(Paint.Align.CENTER);
        gridP.setColor(0xFFD5D5D5);
        gridP.setStyle(Paint.Style.STROKE);
        gridP.setStrokeWidth(0.5f);
        lineP.setStyle(Paint.Style.STROKE);
        lineP.setStrokeWidth(1.2f);
        lineP.setStrokeJoin(Paint.Join.ROUND);
        markerP.setColor(0xFF777777);
        markerP.setStyle(Paint.Style.STROKE);
        markerP.setStrokeWidth(0.8f);
        markerP.setPathEffect(new DashPathEffect(new float[]{4f, 3f}, 0));
    }

    private static TextPaint text(float size, boolean bold, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    /** Writes the finished PDF to {@code out}. The caller closes the stream. */
    static void write(OutputStream out, ExportHeader h, List<Marker> markers, ExportSummary sum,
                      String objective, String appVersion) throws IOException {
        PdfReport r = new PdfReport();
        try {
            r.build(h, markers, sum, objective, appVersion);
            r.endPage();
            r.doc.writeTo(out);
        } finally {
            r.doc.close();
        }
    }

    // ------------------------------------------------------------------ content

    private void build(ExportHeader h, List<Marker> markers, ExportSummary sum,
                       String objective, String appVersion) {
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        SensorType t = h.sensor;
        startPage();

        paragraph(h.name, titleP, 4);
        StringBuilder meta = new StringBuilder(df.format(new Date(h.createdAtMillis)))
                .append(" · ").append(t.getDisplayName())
                .append(" · ").append(TimeFormat.format(h.durationSeconds))
                .append(" · ").append(sum.totalSamples).append(" samples");
        paragraph(meta.toString(), mutedP, 2);
        if (h.experimentTitle != null) paragraph("Experiment: " + h.experimentTitle, mutedP, 2);

        if (objective != null && !objective.isEmpty()) {
            heading("Objective");
            paragraph(objective, bodyP, 4);
        }

        if (h.resultText != null && !h.resultText.trim().isEmpty()) {
            heading("Results");
            for (String line : h.resultText.split("\n")) {
                if (line.trim().isEmpty()) {
                    y += 6;
                } else {
                    paragraph(line, bodyP, 2);
                }
            }
        }

        heading("Summary of measurements (" + t.getUnit() + ")");
        List<String[]> rows = new ArrayList<>();
        String fmt = "%." + (t.getDecimals() + 1) + "f";
        for (int a = 0; a < t.getAxes(); a++) {
            RunningStats s = sum.stats[a];
            rows.add(new String[]{t.getAxes() > 1 ? t.getAxisLabel(a) : t.getDisplayName(),
                    num(fmt, s.getMean()), num(fmt, s.getStdDev()), num(fmt, s.getMin()), num(fmt, s.getMax())});
        }
        table(new String[]{"Axis", "Mean", "Std dev", "Min", "Max"}, rows);

        heading("Graph");
        graph(h, sum, markers);

        if (!markers.isEmpty()) {
            heading("Markers");
            for (Marker m : markers) {
                paragraph(m.getLabel() + " at " + String.format(Locale.US, "%.2f s", m.getTimeSeconds()), bodyP, 1);
            }
        }

        y += 14;
        paragraph("Values are estimates from smartphone sensors, not laboratory-grade measurements. "
                + "Generated by PocketLab " + appVersion + " on " + df.format(new Date()) + ".", smallP, 0);
    }

    private static String num(String fmt, double v) {
        return Double.isNaN(v) ? "–" : String.format(Locale.US, fmt, v);
    }

    // ------------------------------------------------------------------ layout helpers

    private void startPage() {
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create());
        canvas = page.getCanvas();
        y = MARGIN;
    }

    private void endPage() {
        if (page == null) return;
        canvas.drawText("PocketLab · page " + pageNo, MARGIN, PAGE_H - MARGIN + 6, smallP);
        doc.finishPage(page);
        page = null;
    }

    private void ensure(float height) {
        if (y + height > BOTTOM) {
            endPage();
            startPage();
        }
    }

    private void heading(String s) {
        ensure(44);
        y += 12;
        paragraph(s, headingP, 5);
    }

    private void paragraph(String s, TextPaint p, float after) {
        StaticLayout l = StaticLayout.Builder.obtain(s, 0, s.length(), p, (int) CONTENT_W)
                .setLineSpacing(0f, 1.1f).build();
        ensure(l.getHeight());
        canvas.save();
        canvas.translate(MARGIN, y);
        l.draw(canvas);
        canvas.restore();
        y += l.getHeight() + after;
    }

    private void table(String[] head, List<String[]> rows) {
        float rowH = 18f;
        float colW = CONTENT_W / head.length;
        ensure(rowH * (rows.size() + 1) + 4);
        drawRow(head, boldP, rowH, colW);
        for (String[] r : rows) drawRow(r, bodyP, rowH, colW);
        y += 4;
    }

    private void drawRow(String[] cells, TextPaint p, float rowH, float colW) {
        for (int c = 0; c < cells.length; c++) {
            canvas.drawText(cells[c], MARGIN + c * colW + 4, y + 12.5f, p);
        }
        canvas.drawLine(MARGIN, y + rowH, MARGIN + CONTENT_W, y + rowH, gridP);
        y += rowH;
    }

    // ------------------------------------------------------------------ graph

    private void graph(ExportHeader h, ExportSummary sum, List<Marker> markers) {
        final float height = 230f;
        ensure(height + 10);
        int axes = h.sensor.getAxes();
        int n = sum.graphCount;
        if (n < 2) {
            paragraph("Not enough data to draw a graph.", bodyP, 0);
            return;
        }

        float left = MARGIN + 46, right = MARGIN + CONTENT_W - 6;
        float top = y + 18, bottom = y + height - 20;

        double t0 = sum.graphTimes[0], t1 = sum.graphTimes[n - 1];
        if (t1 <= t0) t1 = t0 + 1;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int a = 0; a < axes; a++) {
            for (int i = 0; i < n; i++) {
                lo = Math.min(lo, sum.graphValues[a][i]);
                hi = Math.max(hi, sum.graphValues[a][i]);
            }
        }
        double range = hi - lo;
        if (range < 1e-9) {
            lo -= 0.5;
            hi += 0.5;
        } else {
            lo -= range * 0.08;
            hi += range * 0.08;
        }
        range = hi - lo;

        // Legend and unit
        float lx = left;
        for (int a = 0; a < axes; a++) {
            lineP.setColor(color(axes, a));
            canvas.drawLine(lx, y + 9, lx + 14, y + 9, lineP);
            String label = axes > 1 ? h.sensor.getAxisLabel(a) : h.sensor.getDisplayName();
            canvas.drawText(label, lx + 18, y + 12, smallP);
            lx += 24 + smallP.measureText(label);
        }
        canvas.drawText(h.sensor.getUnit(), right, y + 12, smallRight);

        // Grid and labels
        double yStep = NiceScale.niceStep(range, 5);
        String yFmt = "%." + NiceScale.decimalsForStep(yStep) + "f";
        for (double v : NiceScale.ticks(lo, hi, yStep)) {
            float py = top + (float) ((hi - v) / range) * (bottom - top);
            canvas.drawLine(left, py, right, py, gridP);
            canvas.drawText(String.format(Locale.US, yFmt, v), left - 4, py + 3, smallRight);
        }
        double tStep = NiceScale.niceStep(t1 - t0, 6);
        String tFmt = "%." + NiceScale.decimalsForStep(tStep) + "fs";
        for (double tv : NiceScale.ticks(t0, t1, tStep)) {
            float px = left + (float) ((tv - t0) / (t1 - t0)) * (right - left);
            canvas.drawLine(px, top, px, bottom, gridP);
            canvas.drawText(String.format(Locale.US, tFmt, tv), px, bottom + 12, smallCenter);
        }
        canvas.drawLine(left, bottom, right, bottom, gridP);
        canvas.drawLine(left, top, left, bottom, gridP);

        // Markers
        for (Marker m : markers) {
            double mt = m.getTimeSeconds();
            if (mt < t0 || mt > t1) continue;
            float px = left + (float) ((mt - t0) / (t1 - t0)) * (right - left);
            canvas.drawLine(px, top, px, bottom, markerP);
            canvas.drawText(m.getLabel(), px + 2, top + 9, smallP);
        }

        // Series
        Path path = new Path();
        for (int a = 0; a < axes; a++) {
            path.rewind();
            for (int i = 0; i < n; i++) {
                float px = left + (float) ((sum.graphTimes[i] - t0) / (t1 - t0)) * (right - left);
                float py = top + (float) ((hi - sum.graphValues[a][i]) / range) * (bottom - top);
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            lineP.setColor(color(axes, a));
            canvas.drawPath(path, lineP);
        }
        y += height;
    }

    private static int color(int axes, int a) {
        return axes == 3 ? AXIS_COLORS[a] : PRIMARY;
    }
}
