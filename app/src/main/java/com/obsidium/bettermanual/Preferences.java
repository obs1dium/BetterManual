package com.obsidium.bettermanual;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.sony.scalar.hardware.CameraEx;

public class Preferences
{
    private final SharedPreferences m_prefs;

    private static final String KEY_SCENE_MODE = "sceneMode";
    private static final String KEY_DRIVE_MODE = "driveMode";
    private static final String KEY_BURST_DRIVE_SPEED = "burstDriveSpeed";
    private static final String KEY_MIN_SHUTTER_SPEED = "minShutterSpeed";
    private static final String KEY_VIEW_FLAGS = "viewFlags";

    public Preferences(Context context)
    {
        m_prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public String getSceneMode()
    {
        return m_prefs.getString(KEY_SCENE_MODE, CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE);
    }

    public void setSceneMode(String mode)
    {
        m_prefs.edit().putString(KEY_SCENE_MODE, mode).apply();
    }

    public String getDriveMode()
    {
        return m_prefs.getString(KEY_DRIVE_MODE, CameraEx.ParametersModifier.DRIVE_MODE_BURST);
    }

    public void setDriveMode(String mode)
    {
        m_prefs.edit().putString(KEY_DRIVE_MODE, mode).apply();
    }

    public String getBurstDriveSpeed()
    {
        return m_prefs.getString(KEY_BURST_DRIVE_SPEED, CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH);
    }

    public void setBurstDriveSpeed(String speed)
    {
        m_prefs.edit().putString(KEY_BURST_DRIVE_SPEED, speed).apply();
    }

    public int getMinShutterSpeed()
    {
        return m_prefs.getInt(KEY_MIN_SHUTTER_SPEED, -1);
    }

    public void setMinShutterSpeed(int speed)
    {
        m_prefs.edit().putInt(KEY_MIN_SHUTTER_SPEED, speed).apply();
    }

    public int getViewFlags(int defaultValue)
    {
        return m_prefs.getInt(KEY_VIEW_FLAGS, defaultValue);
    }

    public void setViewFlags(int flags)
    {
        m_prefs.edit().putInt(KEY_VIEW_FLAGS, flags).apply();
    }
}
