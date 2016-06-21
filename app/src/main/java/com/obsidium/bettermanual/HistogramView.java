/*
 * Modified code, original license:
 *
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.obsidium.bettermanual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

public class HistogramView extends View
{
    private final Paint     m_paint = new Paint();
    private final Path      m_path = new Path();
    private short[]         m_histogram;

    public HistogramView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public void setHistogram(short[] histogram)
    {
        m_histogram = histogram;
        invalidate();
    }

    private void drawHistogram(Canvas canvas, short[] histogram)
    {
        short max = 0;
        for (short value : histogram)
        {
            if (value > max)
                max = value;
        }
        final float w = getWidth();
        final float h = getHeight();
        final float dx = 0;
        final float wl = w / histogram.length;
        final float wh = h / max;
        m_paint.reset();
        m_paint.setAntiAlias(false);
        m_paint.setARGB(100, 255, 255, 255);
        m_paint.setStrokeWidth((int) Math.ceil(wl));
        m_paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(dx, 0, dx + w - wl, h - wl, m_paint);
        canvas.drawLine(dx + w / 3, 1, dx + w / 3, h - 1, m_paint);
        canvas.drawLine(dx + 2 * w / 3, 1, dx + 2 * w / 3, h - 1, m_paint);
        m_paint.setStyle(Paint.Style.FILL);
        m_paint.setColor(Color.WHITE);
        m_paint.setStrokeWidth(6);
        m_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        m_path.reset();
        m_path.moveTo(dx, h);
        boolean firstPointEncountered = false;
        float prev = 0;
        float last = 0;
        for (int i = 0; i < histogram.length; i++)
        {
            final float x = i * wl + dx;
            final float l = histogram[i] * wh;
            if (l != 0)
            {
                final float v = h - (l + prev) / 2.0f;
                if (!firstPointEncountered)
                {
                    m_path.lineTo(x, h);
                    firstPointEncountered = true;
                }
                m_path.lineTo(x, v);
                prev = l;
                last = x;
            }
        }
        m_path.lineTo(last, h);
        m_path.lineTo(w, h);
        m_path.close();
        m_paint.setARGB(255, 255, 255, 255);
        canvas.drawPath(m_path, m_paint);
        /*
        m_paint.setStrokeWidth(2);
        m_paint.setStyle(Paint.Style.STROKE);
        m_paint.setARGB(255, 200, 200, 200);
        canvas.drawPath(m_path, m_paint);
        */
    }

    public void onDraw(Canvas canvas)
    {
        canvas.drawARGB(50, 0, 0, 0);
        if (m_histogram != null)
            drawHistogram(canvas, m_histogram);
    }
}