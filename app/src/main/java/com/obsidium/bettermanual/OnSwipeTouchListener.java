package com.obsidium.bettermanual;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class OnSwipeTouchListener implements View.OnTouchListener
{
    private final GestureDetector m_gestureDetector;

    public OnSwipeTouchListener(Context context)
    {
        m_gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void onSwipeLeft()
    {
    }

    public void onSwipeRight()
    {
    }

    public boolean onClick()
    {
        return false;
    }

    public boolean onTouch(View v, MotionEvent event)
    {
        return m_gestureDetector.onTouchEvent(event);
    }

    public boolean onScrolled(float distanceX, float distanceY)
    {
        return false;
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener
    {
        private static final int SWIPE_DISTANCE_THRESHOLD = 50;
        private static final int SWIPE_VELOCITY_THRESHOLD = 50;

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
        {
            return onScrolled(distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e)
        {
            return onClick();
        }

        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            final float distanceX = e2.getX() - e1.getX();
            final float distanceY = e2.getY() - e1.getY();
            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD)
            {
                if (distanceX > 0)
                    onSwipeRight();
                else
                    onSwipeLeft();
                return true;
            }
            return false;
        }
    }
}