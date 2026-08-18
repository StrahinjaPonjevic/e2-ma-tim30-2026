package com.example.slagalica.regions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionMapView extends View {

    public interface OnRegionClickListener {
        void onRegionClick(RegionDefinition region);
    }

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, RegionSummary> summaries = new HashMap<>();
    private final List<RegionPoint> playerPoints = new ArrayList<>();
    private RegionDefinition currentPlayerRegion;
    private OnRegionClickListener regionClickListener;
    private float contentLeft;
    private float contentTop;
    private float contentWidth;
    private float contentHeight;

    public RegionMapView(Context context) {
        super(context);
        initialize();
    }

    public RegionMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public RegionMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setClickable(true);
        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeJoin(Paint.Join.ROUND);
        borderPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setTypeface(Typeface.DEFAULT);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelPaint.setColor(Color.rgb(38, 50, 56));
        pointPaint.setStyle(Paint.Style.FILL);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setOnRegionClickListener(OnRegionClickListener listener) {
        regionClickListener = listener;
    }

    public void setData(List<RegionSummary> regionSummaries, List<RegionPoint> points,
                        RegionDefinition currentRegion) {
        summaries.clear();
        if (regionSummaries != null) {
            for (RegionSummary summary : regionSummaries) {
                summaries.put(summary.region.id, summary);
            }
        }
        playerPoints.clear();
        if (points != null) {
            playerPoints.addAll(points);
        }
        currentPlayerRegion = currentRegion;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float padding = dp(12);
        contentLeft = getPaddingLeft() + padding;
        contentTop = getPaddingTop() + padding;
        contentWidth = Math.max(1f, getWidth() - getPaddingLeft() - getPaddingRight() - padding * 2f);
        contentHeight = Math.max(1f, getHeight() - getPaddingTop() - getPaddingBottom() - padding * 2f);

        for (RegionDefinition region : RegionDefinition.ALL) {
            drawRegion(canvas, region);
        }
        drawPlayerPoints(canvas);
        for (RegionDefinition region : RegionDefinition.ALL) {
            drawRegionLabel(canvas, region);
        }
    }

    private void drawRegion(Canvas canvas, RegionDefinition region) {
        Path path = buildPath(region);
        fillPaint.setColor(region.color);
        canvas.drawPath(path, fillPaint);

        boolean current = currentPlayerRegion != null && currentPlayerRegion.id.equals(region.id);
        borderPaint.setColor(current ? Color.rgb(94, 53, 177) : Color.rgb(69, 90, 100));
        borderPaint.setStrokeWidth(dp(current ? 4f : 1.5f));
        if (current) {
            borderPaint.setShadowLayer(dp(5), 0f, 0f, Color.argb(120, 94, 53, 177));
        } else {
            borderPaint.clearShadowLayer();
        }
        canvas.drawPath(path, borderPaint);
    }

    private void drawRegionLabel(Canvas canvas, RegionDefinition region) {
        float[] center = centerOf(region.polygon);
        float centerX = toCanvasX(center[0]);
        float centerY = toCanvasY(center[1]);
        iconPaint.setTextSize(sp(region.id.equals("beograd") ? 16f : 19f));
        canvas.drawText(region.icon, centerX, centerY - dp(1), iconPaint);
        labelPaint.setTextSize(sp(region.shortLabel.length() > 8 ? 8f : 9.5f));
        canvas.drawText(region.shortLabel, centerX, centerY + dp(13), labelPaint);
    }

    private void drawPlayerPoints(Canvas canvas) {
        for (RegionPoint point : playerPoints) {
            float x = toCanvasX(point.x);
            float y = toCanvasY(point.y);
            if (point.currentPlayer) {
                pointPaint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dp(5.5f), pointPaint);
                pointPaint.setColor(Color.rgb(94, 53, 177));
                canvas.drawCircle(x, y, dp(3.5f), pointPaint);
            } else {
                pointPaint.setColor(Color.argb(210, 38, 50, 56));
                canvas.drawCircle(x, y, dp(2.3f), pointPaint);
            }
        }
    }

    private Path buildPath(RegionDefinition region) {
        Path path = new Path();
        for (int i = 0; i < region.polygon.length; i++) {
            float x = toCanvasX(region.polygon[i][0]);
            float y = toCanvasY(region.polygon[i][1]);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        return path;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }

        performClick();
        float normalizedX = (event.getX() - contentLeft) / contentWidth;
        float normalizedY = (event.getY() - contentTop) / contentHeight;
        for (RegionDefinition region : RegionDefinition.ALL) {
            if (region.contains(normalizedX, normalizedY)) {
                if (regionClickListener != null) {
                    regionClickListener.onRegionClick(region);
                }
                break;
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float[] centerOf(float[][] polygon) {
        float x = 0f;
        float y = 0f;
        for (float[] point : polygon) {
            x += point[0];
            y += point[1];
        }
        return new float[]{x / polygon.length, y / polygon.length};
    }

    private float toCanvasX(float normalizedX) {
        return contentLeft + normalizedX * contentWidth;
    }

    private float toCanvasY(float normalizedY) {
        return contentTop + normalizedY * contentHeight;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
