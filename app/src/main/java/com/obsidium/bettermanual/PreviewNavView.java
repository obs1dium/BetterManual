package com.obsidium.bettermanual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

public class PreviewNavView extends View
{
    private static final float STROKE_WIDTH = 2.0f;

    private Pair<Float, Float>      m_posFract;
    private float                   m_zoomFactor;
    private final Paint             m_paint = new Paint();

    public PreviewNavView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        m_paint.setAntiAlias(false);
        m_paint.setStrokeWidth(STROKE_WIDTH);
        m_paint.setStyle(Paint.Style.STROKE);
    }

    public void update(Pair<Integer, Integer> position, float zoomFactor)
    {
        m_zoomFactor = zoomFactor;
        if (position != null)
            m_posFract = new Pair<Float, Float>((float)position.first / 1000.0f, (float)position.second / 1000.0f);
        else
            m_posFract = null;
        invalidate();
    }

    public void onDraw(Canvas canvas)
    {
        // solid black background
        canvas.drawARGB(255, 0, 0, 0);
        if (m_posFract != null && m_zoomFactor != 0.0f)
        {
            final float w = getWidth();
            final float h = getHeight();
            // white outer frame
            m_paint.setARGB(255, 255, 255, 255);
            canvas.drawRect(0, 0, w - STROKE_WIDTH, h - STROKE_WIDTH, m_paint);

            // 0, 0 is the center
            final float centerX = w / 2.0f;
            final float centerY = h / 2.0f;
            final float curCenterX = centerX + m_posFract.first * centerX;
            final float curCenterY = centerY + m_posFract.second * centerY;

            final float w2 = w / (m_zoomFactor * 2.0f);
            final float h2 = h / (m_zoomFactor * 2.0f);

            // red position rectangle
            m_paint.setARGB(255, 255, 0, 0);
            canvas.drawRect(curCenterX - w2, curCenterY - h2, curCenterX + w2, curCenterY + h2, m_paint);
        }
    }
}
