package com.crosspro.noactivity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class TriangleColorPickerView extends View {

    private Paint huePaint;
    private Paint trianglePaint;
    private Paint selectorPaint;
    
    private float hue = 0; // 0-360
    private float saturation = 0; // 0-1
    private float value = 1; // 0-1
    
    private float centerX, centerY;
    private float outerRadius, innerRadius;
    private RectF hueRect = new RectF();
    
    private Path trianglePath = new Path();
    private PointF p0 = new PointF(); 
    private PointF p1 = new PointF(); 
    private PointF p2 = new PointF(); 
    
    private OnColorChangedListener listener;

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    public TriangleColorPickerView(Context context) {
        super(context);
        init();
    }

    public TriangleColorPickerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        huePaint.setStyle(Paint.Style.STROKE);
        
        trianglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectorPaint.setStyle(Paint.Style.STROKE);
        selectorPaint.setStrokeWidth(4f);
        selectorPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        float size = Math.min(w, h);
        outerRadius = size * 0.45f;
        innerRadius = size * 0.35f;
        
        float r = (outerRadius + innerRadius) / 2;
        hueRect.set(centerX - r, centerY - r, centerX + r, centerY + r);
        huePaint.setStrokeWidth(outerRadius - innerRadius);
        
        updateTriangleVertices();
    }

    private void updateTriangleVertices() {
        double angle = Math.toRadians(hue - 90);
        float r = innerRadius - 10;
        
        p0.set((float)(centerX + r * Math.cos(angle)), (float)(centerY + r * Math.sin(angle)));
        p1.set((float)(centerX + r * Math.cos(angle + Math.toRadians(120))), (float)(centerY + r * Math.sin(angle + Math.toRadians(120))));
        p2.set((float)(centerX + r * Math.cos(angle + Math.toRadians(240))), (float)(centerY + r * Math.sin(angle + Math.toRadians(240))));
        
        trianglePath.reset();
        trianglePath.moveTo(p0.x, p0.y);
        trianglePath.lineTo(p1.x, p1.y);
        trianglePath.lineTo(p2.x, p2.y);
        trianglePath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw Hue Circle manually to avoid SweepShader issues in some SDKs
        for (int i = 0; i < 360; i += 2) {
            huePaint.setColor(Color.HSVToColor(new float[]{i, 1f, 1f}));
            canvas.drawArc(hueRect, i - 90, 3, false, huePaint);
        }
        
        // Draw Triangle
        int baseColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
        Shader s1 = new LinearGradient(p1.x, p1.y, p0.x, p0.y, Color.WHITE, baseColor, Shader.TileMode.CLAMP);
        Shader s2 = new LinearGradient((p0.x + p1.x) / 2, (p0.y + p1.y) / 2, p2.x, p2.y, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        
        // Try to use ComposeShader, if it also fails we'll know
        try {
            ComposeShader compose = new ComposeShader(s1, s2, PorterDuff.Mode.DARKEN);
            trianglePaint.setShader(compose);
        } catch (Exception e) {
            trianglePaint.setShader(s1); // Fallback
        }
        
        canvas.drawPath(trianglePath, trianglePaint);
        
        // Draw Selectors
        drawHueSelector(canvas);
        drawTriangleSelector(canvas);
    }

    private void drawHueSelector(Canvas canvas) {
        double angle = Math.toRadians(hue - 90);
        float r = (outerRadius + innerRadius) / 2;
        float x = (float)(centerX + r * Math.cos(angle));
        float y = (float)(centerY + r * Math.sin(angle));
        canvas.drawCircle(x, y, 10f, selectorPaint);
    }

    private void drawTriangleSelector(Canvas canvas) {
        float x = p1.x + saturation * (p0.x - p1.x) + (1 - value) * (p2.x - (p1.x + saturation * (p0.x - p1.x)));
        float y = p1.y + saturation * (p0.y - p1.y) + (1 - value) * (p2.y - (p1.y + saturation * (p0.y - p1.y)));
        
        selectorPaint.setColor(value > 0.5 ? Color.BLACK : Color.WHITE);
        canvas.drawCircle(x, y, 12f, selectorPaint);
        selectorPaint.setColor(Color.WHITE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float dx = x - centerX;
        float dy = y - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > innerRadius - 10 && dist < outerRadius + 20) {
            hue = (float) Math.toDegrees(Math.atan2(dy, dx)) + 90;
            if (hue < 0) hue += 360;
            updateTriangleVertices();
            notifyListener();
            invalidate();
            return true;
        } else if (isPointInTriangle(x, y)) {
            updateSaturationValue(x, y);
            notifyListener();
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean isPointInTriangle(float x, float y) {
        float denominator = ((p1.y - p2.y) * (p0.x - p2.x) + (p2.x - p1.x) * (p0.y - p2.y));
        float a = ((p1.y - p2.y) * (x - p2.x) + (p2.x - p1.x) * (y - p2.y)) / denominator;
        float b = ((p2.y - p0.y) * (x - p2.x) + (p0.x - p2.x) * (y - p2.y)) / denominator;
        float c = 1 - a - b;
        return a >= 0 && a <= 1 && b >= 0 && b <= 1 && c >= 0 && c <= 1;
    }

    private void updateSaturationValue(float x, float y) {
        float denominator = ((p1.y - p2.y) * (p0.x - p2.x) + (p2.x - p1.x) * (p0.y - p2.y));
        float a = ((p1.y - p2.y) * (x - p2.x) + (p2.x - p1.x) * (y - p2.y)) / denominator;
        float b = ((p2.y - p0.y) * (x - p2.x) + (p0.x - p2.x) * (y - p2.y)) / denominator;
        
        saturation = a / (a + b);
        value = 1 - (1 - a - b);
        if (Float.isNaN(saturation)) saturation = 0;
        if (saturation > 1) saturation = 1;
        if (value > 1) value = 1;
        if (value < 0) value = 0;
    }

    public void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        updateTriangleVertices();
        invalidate();
    }

    public int getColor() {
        return Color.HSVToColor(new float[]{hue, saturation, value});
    }

    public void setOnColorChangedListener(OnColorChangedListener l) {
        this.listener = l;
    }

    private void notifyListener() {
        if (listener != null) listener.onColorChanged(getColor());
    }
}
