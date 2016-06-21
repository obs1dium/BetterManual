package com.obsidium.bettermanual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

import com.sony.scalar.hardware.avio.DisplayManager;

public class GridView extends View
{
    private final Paint                 m_paint = new Paint();
    private DisplayManager.VideoRect    m_videoRect;

    public GridView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        m_paint.setAntiAlias(false);
        m_paint.setARGB(100, 100, 100, 100);
        m_paint.setStrokeWidth(2);
        m_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
    }

    public void setVideoRect(DisplayManager.VideoRect videoRect)
    {
        m_videoRect = videoRect;
    }

    public void onDraw(Canvas canvas)
    {
        canvas.drawARGB(0, 0, 0, 0);

        if (m_videoRect != null)
        {
            final float w = getWidth();
            final float h = getHeight();
            final float w3 = (float) (m_videoRect.pxRight - m_videoRect.pxLeft) / 3.0f;
            final float h3 = h / 3.0f;

            // Vertical lines
            canvas.drawLine(m_videoRect.pxLeft + w3, 0, m_videoRect.pxLeft + w3, h, m_paint);
            canvas.drawLine(m_videoRect.pxLeft + w3 * 2, 0, m_videoRect.pxLeft + w3 * 2, h, m_paint);

            // Horizontal lines
            canvas.drawLine(m_videoRect.pxLeft, h3, w - m_videoRect.pxLeft, h3, m_paint);
            canvas.drawLine(m_videoRect.pxLeft, h3 * 2, w - m_videoRect.pxLeft, h3 * 2, m_paint);
        }
    }
}
