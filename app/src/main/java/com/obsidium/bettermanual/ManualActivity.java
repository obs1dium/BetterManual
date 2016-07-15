package com.obsidium.bettermanual;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.sysutil.ScalarInput;
import com.sony.scalar.sysutil.didep.Settings;

import java.io.IOException;
import java.util.List;

public class ManualActivity extends BaseActivity implements SurfaceHolder.Callback, View.OnClickListener, CameraEx.ShutterListener, CameraEx.ShutterSpeedChangeListener
{
    private static final boolean LOGGING_ENABLED = false;
    private static final int MESSAGE_TIMEOUT = 1000;

    private SurfaceHolder   m_surfaceHolder;
    private CameraEx        m_camera;
    private CameraEx.AutoPictureReviewControl m_autoReviewControl;
    private int             m_pictureReviewTime;

    private Preferences     m_prefs;

    private TextView        m_tvShutter;
    private TextView        m_tvAperture;
    private TextView        m_tvISO;
    private TextView        m_tvExposureCompensation;
    private LinearLayout    m_lExposure;
    private TextView        m_tvExposure;
    private TextView        m_tvLog;
    private TextView        m_tvMagnification;
    private TextView        m_tvMsg;
    private HistogramView   m_vHist;
    private TableLayout     m_lInfoBottom;
    private ImageView       m_ivDriveMode;
    private ImageView       m_ivMode;
    private ImageView       m_ivTimelapse;
    private ImageView       m_ivBracket;
    private GridView        m_vGrid;
    private TextView        m_tvHint;
    private FocusScaleView  m_focusScaleView;
    private View            m_lFocusScale;

    // Bracketing
    private int             m_bracketStep;  // in 1/3 stops
    private int             m_bracketMaxPicCount;
    private int             m_bracketPicCount;
    private int             m_bracketShutterDelta;
    private boolean         m_bracketActive;
    private Pair<Integer, Integer> m_bracketNextShutterSpeed;
    private int             m_bracketNeutralShutterIndex;

    // Timelapse
    private int             m_autoPowerOffTimeBackup;
    private boolean         m_timelapseActive;
    private int             m_timelapseInterval;    // ms
    private int             m_timelapsePicCount;
    private int             m_timelapsePicsTaken;
    private int             m_countdown;
    private static final int COUNTDOWN_SECONDS = 5;
    private final Runnable  m_timelapseRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            m_camera.burstableTakePicture();
        }
    };
    private final Runnable  m_countDownRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (--m_countdown > 0)
            {
                m_tvMsg.setText(String.format("Starting in %d...", m_countdown));
                m_handler.postDelayed(this, 1000);
            }
            else
            {
                m_tvMsg.setVisibility(View.GONE);
                if (m_timelapseActive)
                    startShootingTimelapse();
                else if (m_bracketActive)
                    startShootingBracket();
            }
        }
    };

    private final Runnable m_hideFocusScaleRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            m_lFocusScale.setVisibility(View.GONE);
        }
    };

    // ISO
    private int             m_curIso;
    private List<Integer>   m_supportedIsos;

    // Shutter speed
    private boolean         m_notifyOnNextShutterSpeedChange;

    // Aperture
    private boolean         m_notifyOnNextApertureChange;
    private boolean         m_haveApertureControl;

    // Exposure compensation
    private int             m_maxExposureCompensation;
    private int             m_minExposureCompensation;
    private int             m_curExposureCompensation;
    private float           m_exposureCompensationStep;

    // Preview magnification
    private List<Integer>   m_supportedPreviewMagnifications;
    private boolean         m_zoomLeverPressed;
    private int             m_curPreviewMagnification;
    private float           m_curPreviewMagnificationFactor;
    private Pair<Integer, Integer>  m_curPreviewMagnificationPos = new Pair<Integer, Integer>(0, 0);
    private int             m_curPreviewMagnificationMaxPos;
    private PreviewNavView  m_previewNavView;

    enum DialMode { shutter, aperture, iso, exposure, mode, drive,
        timelapse, bracket,
        timelapseSetInterval, timelapseSetPicCount,
        bracketSetStep, bracketSetPicCount
    }
    private DialMode        m_dialMode;

    enum SceneMode { manual, aperture, shutter, other }
    private SceneMode       m_sceneMode;

    private final Handler   m_handler = new Handler();
    private final Runnable  m_hideMessageRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            m_tvMsg.setVisibility(View.GONE);
        }
    };

    private boolean         m_takingPicture;
    private boolean         m_shutterKeyDown;

    private boolean         m_haveTouchscreen;

    private static final int VIEW_FLAG_GRID         = 0x01;
    private static final int VIEW_FLAG_HISTOGRAM    = 0x02;
    private static final int VIEW_FLAG_EXPOSURE     = 0x04;
    private static final int VIEW_FLAG_MASK         = 0x07; // all flags combined
    private int             m_viewFlags;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_manual);

        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler))
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler());

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceView.setOnTouchListener(new SurfaceSwipeTouchListener(this));
        m_surfaceHolder = surfaceView.getHolder();
        m_surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // not needed - appears to be the default font
        //final Typeface sonyFont = Typeface.createFromFile("system/fonts/Sony_DI_Icons.ttf");

        m_tvMsg = (TextView)findViewById(R.id.tvMsg);

        m_tvAperture = (TextView)findViewById(R.id.tvAperture);
        m_tvAperture.setOnTouchListener(new ApertureSwipeTouchListener(this));

        m_tvShutter = (TextView)findViewById(R.id.tvShutter);
        m_tvShutter.setOnTouchListener(new ShutterSwipeTouchListener(this));

        m_tvISO = (TextView)findViewById(R.id.tvISO);
        m_tvISO.setOnTouchListener(new IsoSwipeTouchListener(this));

        m_tvExposureCompensation = (TextView)findViewById(R.id.tvExposureCompensation);
        m_tvExposureCompensation.setOnTouchListener(new ExposureSwipeTouchListener(this));
        m_lExposure = (LinearLayout)findViewById(R.id.lExposure);

        m_tvExposure = (TextView)findViewById(R.id.tvExposure);
        //noinspection ResourceType
        m_tvExposure.setCompoundDrawablesWithIntrinsicBounds(SonyDrawables.p_meteredmanualicon, 0, 0, 0);

        m_tvLog = (TextView)findViewById(R.id.tvLog);
        m_tvLog.setVisibility(LOGGING_ENABLED ? View.VISIBLE : View.GONE);

        m_vHist = (HistogramView)findViewById(R.id.vHist);

        m_tvMagnification = (TextView)findViewById(R.id.tvMagnification);

        m_lInfoBottom = (TableLayout)findViewById(R.id.lInfoBottom);

        m_previewNavView = (PreviewNavView)findViewById(R.id.vPreviewNav);
        m_previewNavView.setVisibility(View.GONE);

        m_ivDriveMode = (ImageView)findViewById(R.id.ivDriveMode);
        m_ivDriveMode.setOnClickListener(this);

        m_ivMode = (ImageView)findViewById(R.id.ivMode);
        m_ivMode.setOnClickListener(this);

        m_ivTimelapse = (ImageView)findViewById(R.id.ivTimelapse);
        //noinspection ResourceType
        m_ivTimelapse.setImageResource(SonyDrawables.p_16_dd_parts_43_shoot_icon_setting_drivemode_invalid);
        m_ivTimelapse.setOnClickListener(this);

        m_ivBracket = (ImageView)findViewById(R.id.ivBracket);
        //noinspection ResourceType
        m_ivBracket.setImageResource(SonyDrawables.p_16_dd_parts_contshot);
        m_ivBracket.setOnClickListener(this);

        m_vGrid = (GridView)findViewById(R.id.vGrid);

        m_tvHint = (TextView)findViewById(R.id.tvHint);
        m_tvHint.setVisibility(View.GONE);

        m_focusScaleView = (FocusScaleView)findViewById(R.id.vFocusScale);
        m_lFocusScale = findViewById(R.id.lFocusScale);
        m_lFocusScale.setVisibility(View.GONE);

        //noinspection ResourceType
        ((ImageView)findViewById(R.id.ivFocusRight)).setImageResource(SonyDrawables.p_16_dd_parts_rec_focuscontrol_far);
        //noinspection ResourceType
        ((ImageView)findViewById(R.id.ivFocusLeft)).setImageResource(SonyDrawables.p_16_dd_parts_rec_focuscontrol_near);

        setDialMode(DialMode.shutter);

        m_prefs = new Preferences(this);

        m_haveTouchscreen = getDeviceInfo().getModel().compareTo("ILCE-5100") == 0;
    }

    private class SurfaceSwipeTouchListener extends OnSwipeTouchListener
    {
        public SurfaceSwipeTouchListener(Context context)
        {
            super(context);
        }

        @Override
        public boolean onScrolled(float distanceX, float distanceY)
        {
            if (m_curPreviewMagnification != 0)
            {
                m_curPreviewMagnificationPos = new Pair<Integer, Integer>(Math.max(Math.min(m_curPreviewMagnificationMaxPos, m_curPreviewMagnificationPos.first + (int)distanceX), -m_curPreviewMagnificationMaxPos),
                        Math.max(Math.min(m_curPreviewMagnificationMaxPos, m_curPreviewMagnificationPos.second + (int)distanceY), -m_curPreviewMagnificationMaxPos));
                m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
                return true;
            }
            return false;
        }
    }

    private class ApertureSwipeTouchListener extends OnSwipeTouchListener
    {
        private int m_lastDistance;
        private int m_accumulatedDistance;

        public ApertureSwipeTouchListener(Context context)
        {
            super(context);
        }

        @Override
        public boolean onScrolled(float distanceX, float distanceY)
        {
            if (m_curIso != 0)
            {
                final int distance = (int)(Math.abs(distanceX) > Math.abs(distanceY) ? distanceX : -distanceY);
                if ((m_lastDistance > 0) != (distance > 0))
                    m_accumulatedDistance = distance;
                else
                    m_accumulatedDistance += distance;
                m_lastDistance = distance;
                if (Math.abs(m_accumulatedDistance) > 10)
                {
                    for (int i = Math.abs(m_accumulatedDistance); i > 10; i -= 10)
                    {
                        m_notifyOnNextApertureChange = true;
                        if (distance > 0)
                            m_camera.decrementAperture();
                        else
                            m_camera.incrementAperture();
                    }
                    m_accumulatedDistance = 0;
                    return true;
                }
            }
            return false;
        }
    }

    private class ShutterSwipeTouchListener extends OnSwipeTouchListener
    {
        private int m_lastDistance;
        private int m_accumulatedDistance;

        public ShutterSwipeTouchListener(Context context)
        {
            super(context);
        }

        @Override
        public boolean onScrolled(float distanceX, float distanceY)
        {
            if (m_curIso != 0)
            {
                final int distance = (int)(Math.abs(distanceX) > Math.abs(distanceY) ? distanceX : -distanceY);
                if ((m_lastDistance > 0) != (distance > 0))
                    m_accumulatedDistance = distance;
                else
                    m_accumulatedDistance += distance;
                m_lastDistance = distance;
                if (Math.abs(m_accumulatedDistance) > 10)
                {
                    for (int i = Math.abs(m_accumulatedDistance); i > 10; i -= 10)
                    {
                        m_notifyOnNextShutterSpeedChange = true;
                        if (distance > 0)
                            m_camera.decrementShutterSpeed();
                        else
                            m_camera.incrementShutterSpeed();
                    }
                    m_accumulatedDistance = 0;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onClick()
        {
            if (m_sceneMode == SceneMode.aperture)
            {
                // Set minimum shutter speed
                startActivity(new Intent(getApplicationContext(), MinShutterActivity.class));
                return true;
            }
            return false;
        }
    }

    private class ExposureSwipeTouchListener extends OnSwipeTouchListener
    {
        private int m_lastDistance;
        private int m_accumulatedDistance;

        public ExposureSwipeTouchListener(Context context)
        {
            super(context);
        }

        @Override
        public boolean onScrolled(float distanceX, float distanceY)
        {
            if (m_curIso != 0)
            {
                final int distance = (int)(Math.abs(distanceX) > Math.abs(distanceY) ? distanceX : -distanceY);
                if ((m_lastDistance > 0) != (distance > 0))
                    m_accumulatedDistance = distance;
                else
                    m_accumulatedDistance += distance;
                m_lastDistance = distance;
                if (Math.abs(m_accumulatedDistance) > 10)
                {
                    for (int i = Math.abs(m_accumulatedDistance); i > 10; i -= 10)
                    {
                        if (distance > 0)
                            decrementExposureCompensation(true);
                        else
                            incrementExposureCompensation(true);
                    }
                    m_accumulatedDistance = 0;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onClick()
        {
            // Reset exposure compensation
            setExposureCompensation(0);
            return true;
        }
    }

    private class IsoSwipeTouchListener extends OnSwipeTouchListener
    {
        private int m_lastDistance;
        private int m_accumulatedDistance;

        public IsoSwipeTouchListener(Context context)
        {
            super(context);
        }

        @Override
        public boolean onScrolled(float distanceX, float distanceY)
        {
            if (m_curIso != 0)
            {
                final int distance = (int)(Math.abs(distanceX) > Math.abs(distanceY) ? distanceX : -distanceY);
                if ((m_lastDistance > 0) != (distance > 0))
                    m_accumulatedDistance = distance;
                else
                    m_accumulatedDistance += distance;
                m_lastDistance = distance;
                if (Math.abs(m_accumulatedDistance) > 10)
                {
                    int iso = m_curIso;
                    for (int i = Math.abs(m_accumulatedDistance); i > 10; i -= 10)
                        iso = distance > 0 ? getPreviousIso(iso) : getNextIso(iso);
                    m_accumulatedDistance = 0;
                    if (iso != 0)
                    {
                        setIso(iso);
                        showMessage(String.format("\uE488 %d", iso));
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onClick()
        {
            // Toggle manual / automatic ISO
            setIso(m_curIso == 0 ? getFirstManualIso() : 0);
            showMessage(m_curIso == 0 ? "Auto \uE488" : "Manual \uE488");
            return true;
        }
    }

    private void showMessage(String msg)
    {
        m_tvMsg.setText(msg);
        m_tvMsg.setVisibility(View.VISIBLE);
        m_handler.removeCallbacks(m_hideMessageRunnable);
        m_handler.postDelayed(m_hideMessageRunnable, MESSAGE_TIMEOUT);
    }

    private void log(final String str)
    {
        if (LOGGING_ENABLED)
            m_tvLog.append(str);
    }
    
    private void setIso(int iso)
    {
        //log("setIso: " + String.valueOf(iso) + "\n");
        m_curIso = iso;
        m_tvISO.setText(String.format("\uE488 %s", (iso == 0 ? "AUTO" : String.valueOf(iso))));
        Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setISOSensitivity(iso);
        m_camera.getNormalCamera().setParameters(params);
    }

    private int getPreviousIso(int current)
    {
        int previous = 0;
        for (Integer iso : m_supportedIsos)
        {
            if (iso == current)
                return previous;
            else
                previous = iso;
        }
        return 0;
    }

    private int getNextIso(int current)
    {
        boolean next = false;
        for (Integer iso : m_supportedIsos)
        {
            if (next)
                return iso;
            else if (iso == current)
                next = true;
        }
        return current;
    }

    private int getFirstManualIso()
    {
        for (Integer iso : m_supportedIsos)
        {
            if (iso != 0)
                return iso;
        }
        return 0;
    }

    private void updateShutterSpeed(int n, int d)
    {
        final String text = CameraUtil.formatShutterSpeed(n, d);
        m_tvShutter.setText(text);
        if (m_notifyOnNextShutterSpeedChange)
        {
            showMessage(text);
            m_notifyOnNextShutterSpeedChange = false;
        }
    }

    private void setExposureCompensation(int value)
    {
        m_curExposureCompensation = value;
        Camera.Parameters params = m_camera.createEmptyParameters();
        params.setExposureCompensation(value);
        m_camera.getNormalCamera().setParameters(params);
        updateExposureCompensation(false);
    }

    private void decrementExposureCompensation(boolean notify)
    {
        if (m_curExposureCompensation > m_minExposureCompensation)
        {
            --m_curExposureCompensation;

            Camera.Parameters params = m_camera.createEmptyParameters();
            params.setExposureCompensation(m_curExposureCompensation);
            m_camera.getNormalCamera().setParameters(params);

            updateExposureCompensation(notify);
        }
    }

    private void incrementExposureCompensation(boolean notify)
    {
        if (m_curExposureCompensation < m_maxExposureCompensation)
        {
            ++m_curExposureCompensation;

            Camera.Parameters params = m_camera.createEmptyParameters();
            params.setExposureCompensation(m_curExposureCompensation);
            m_camera.getNormalCamera().setParameters(params);

            updateExposureCompensation(notify);
        }
    }

    private void updateExposureCompensation(boolean notify)
    {
        final String text;
        if (m_curExposureCompensation == 0)
            text = "\uEB18\u00B10.0";
        else if (m_curExposureCompensation > 0)
            text = String.format("\uEB18+%.1f", m_curExposureCompensation * m_exposureCompensationStep);
        else
            text = String.format("\uEB18%.1f", m_curExposureCompensation * m_exposureCompensationStep);
        m_tvExposureCompensation.setText(text);
        if (notify)
            showMessage(text);
    }

    private void updateSceneModeImage(String mode)
    {
        //log(String.format("updateSceneModeImage %s\n", mode));
        if (mode.equals(CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE))
        {
            //noinspection ResourceType
            m_ivMode.setImageResource(SonyDrawables.s_16_dd_parts_osd_icon_mode_m);
            m_sceneMode = SceneMode.manual;
        }
        else if (mode.equals(CameraEx.ParametersModifier.SCENE_MODE_APERTURE_PRIORITY))
        {
            //noinspection ResourceType
            m_ivMode.setImageResource(SonyDrawables.s_16_dd_parts_osd_icon_mode_a);
            m_sceneMode = SceneMode.aperture;
        }
        else if (mode.equals(CameraEx.ParametersModifier.SCENE_MODE_SHUTTER_PRIORITY))
        {
            //noinspection ResourceType
            m_ivMode.setImageResource(SonyDrawables.s_16_dd_parts_osd_icon_mode_s);
            m_sceneMode = SceneMode.shutter;
        }
        else
        {
            //noinspection ResourceType
            m_ivMode.setImageResource(SonyDrawables.p_dialogwarning);
            m_sceneMode = SceneMode.other;
        }
    }

    private void updateViewVisibility()
    {
        m_vHist.setVisibility((m_viewFlags & VIEW_FLAG_HISTOGRAM) != 0 ? View.VISIBLE : View.GONE);
        m_vGrid.setVisibility((m_viewFlags & VIEW_FLAG_GRID) != 0 ? View.VISIBLE : View.GONE);
        m_lExposure.setVisibility((m_viewFlags & VIEW_FLAG_EXPOSURE) != 0 ? View.VISIBLE : View.GONE);
    }

    private void cycleVisibleViews()
    {
        if (++m_viewFlags > VIEW_FLAG_MASK)
            m_viewFlags = 0;
        updateViewVisibility();
    }

    private void updateSceneModeImage()
    {
        updateSceneModeImage(m_camera.getNormalCamera().getParameters().getSceneMode());
    }

    private void toggleSceneMode()
    {
        final String newMode;
        switch (m_sceneMode)
        {
            case manual:
                newMode = CameraEx.ParametersModifier.SCENE_MODE_APERTURE_PRIORITY;
                if (m_dialMode != DialMode.mode)
                    setDialMode(m_haveApertureControl ? DialMode.aperture : DialMode.iso);
                setMinShutterSpeed(m_prefs.getMinShutterSpeed());
                break;
            default:
                newMode = CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE;
                if (m_dialMode != DialMode.mode)
                    setDialMode(DialMode.shutter);
                setMinShutterSpeed(-1);
                break;
        }
        setSceneMode(newMode);
    }

    private void toggleDriveMode()
    {
        final Camera normalCamera = m_camera.getNormalCamera();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(normalCamera.getParameters());
        final String driveMode = paramsModifier.getDriveMode();
        final String newMode;
        final String newBurstSpeed;
        if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE))
        {
            newMode = CameraEx.ParametersModifier.DRIVE_MODE_BURST;
            newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH;
        }
        else if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_BURST))
        {
            final String burstDriveSpeed = paramsModifier.getBurstDriveSpeed();
            if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW))
            {
                newMode = CameraEx.ParametersModifier.DRIVE_MODE_SINGLE;
                newBurstSpeed = burstDriveSpeed;
            }
            else
            {
                newMode = driveMode;
                newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW;
            }
        }
        else
        {
            // Anything else...
            newMode = CameraEx.ParametersModifier.DRIVE_MODE_SINGLE;
            newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH;
        }

        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier newParamsModifier = m_camera.createParametersModifier(params);
        newParamsModifier.setDriveMode(newMode);
        newParamsModifier.setBurstDriveSpeed(newBurstSpeed);
        m_camera.getNormalCamera().setParameters(params);

        updateDriveModeImage();
    }

    private void updateDriveModeImage()
    {
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(m_camera.getNormalCamera().getParameters());
        final String driveMode = paramsModifier.getDriveMode();
        if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE))
        {
            //noinspection ResourceType
            m_ivDriveMode.setImageResource(SonyDrawables.p_drivemode_n_001);
        }
        else if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_BURST))
        {
            final String burstDriveSpeed = paramsModifier.getBurstDriveSpeed();
            if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW))
            {
                //noinspection ResourceType
                m_ivDriveMode.setImageResource(SonyDrawables.p_drivemode_n_003);
            }
            else if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH))
            {
                //noinspection ResourceType
                m_ivDriveMode.setImageResource(SonyDrawables.p_drivemode_n_002);
            }
        }
        else //if (driveMode.equals("bracket"))
        {
            // Don't really care about this here
            //noinspection ResourceType
            m_ivDriveMode.setImageResource(SonyDrawables.p_dialogwarning);
        }
    }

    private void dumpList(List list, String name)
    {
        log(name);
        log(": ");
        if (list != null)
        {
            for (Object o : list)
            {
                log(o.toString());
                log(" ");
            }
        }
        else
            log("null");
        log("\n");
    }

    private void togglePreviewMagnificationViews(boolean magnificationActive)
    {
        m_previewNavView.setVisibility(magnificationActive ? View.VISIBLE : View.GONE);
        m_tvMagnification.setVisibility(magnificationActive ? View.VISIBLE : View.GONE);
        m_lInfoBottom.setVisibility(magnificationActive ? View.GONE : View.VISIBLE);
        m_vHist.setVisibility(magnificationActive ? View.GONE : View.VISIBLE);
        setLeftViewVisibility(!magnificationActive);
    }

    private void setSceneMode(String mode)
    {
        Camera.Parameters params = m_camera.createEmptyParameters();
        params.setSceneMode(mode);
        m_camera.getNormalCamera().setParameters(params);
        updateSceneModeImage(mode);
    }

    private void saveDefaults()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        // Scene mode
        m_prefs.setSceneMode(params.getSceneMode());
        // Drive mode and burst speed
        m_prefs.setDriveMode(paramsModifier.getDriveMode());
        m_prefs.setBurstDriveSpeed(paramsModifier.getBurstDriveSpeed());
        // View visibility
        m_prefs.setViewFlags(m_viewFlags);

        // TODO: Dial mode
    }

    private void loadDefaults()
    {
        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);
        // Focus mode
        params.setFocusMode(CameraEx.ParametersModifier.FOCUS_MODE_MANUAL);
        // Scene mode
        final String sceneMode = m_prefs.getSceneMode();
        params.setSceneMode(sceneMode);
        // Drive mode and burst speed
        modifier.setDriveMode(m_prefs.getDriveMode());
        modifier.setBurstDriveSpeed(m_prefs.getBurstDriveSpeed());
        // Minimum shutter speed
        if (sceneMode.equals(CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE))
            modifier.setAutoShutterSpeedLowLimit(-1);
        else
            modifier.setAutoShutterSpeedLowLimit(m_prefs.getMinShutterSpeed());
        // Disable self timer
        modifier.setSelfTimer(0);
        // Force aspect ratio to 3:2
        modifier.setImageAspectRatio(CameraEx.ParametersModifier.IMAGE_ASPECT_RATIO_3_2);
        // Apply
        m_camera.getNormalCamera().setParameters(params);
        // View visibility
        m_viewFlags = m_prefs.getViewFlags(VIEW_FLAG_GRID | VIEW_FLAG_HISTOGRAM);
        // TODO: Dial mode?
        setDialMode(DialMode.shutter);
    }

    private void setMinShutterSpeed(int speed)
    {
        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);
        modifier.setAutoShutterSpeedLowLimit(speed);
        m_camera.getNormalCamera().setParameters(params);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        m_camera = CameraEx.open(0, null);
        m_surfaceHolder.addCallback(this);
        m_camera.startDirectShutter();
        m_autoReviewControl = new CameraEx.AutoPictureReviewControl();
        m_camera.setAutoPictureReviewControl(m_autoReviewControl);
        // Disable picture review
        m_pictureReviewTime = m_autoReviewControl.getPictureReviewTime();
        m_autoReviewControl.setPictureReviewTime(0);

        m_vGrid.setVideoRect(getDisplayManager().getDisplayedVideoRect());

        //log(String.format("getSavingBatteryMode %s\n", getDisplayManager().getSavingBatteryMode()));
        //log(String.format("getScreenGainControlType %s\n", getDisplayManager().getScreenGainControlType()));

        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);

        // Exposure compensation
        m_maxExposureCompensation = params.getMaxExposureCompensation();
        m_minExposureCompensation = params.getMinExposureCompensation();
        m_exposureCompensationStep = params.getExposureCompensationStep();
        m_curExposureCompensation = params.getExposureCompensation();
        updateExposureCompensation(false);

        /*
        log(String.format("isSupportedFocusHold %b\n", paramsModifier.isSupportedFocusHold()));
        log(String.format("isFocusDriveSupported %b\n", paramsModifier.isFocusDriveSupported()));
        log(String.format("MaxFocusDriveSpeed %d\n", paramsModifier.getMaxFocusDriveSpeed())); // 0
        log(String.format("MaxFocusShift %d\n", paramsModifier.getMaxFocusShift()));
        log(String.format("MinFocusShift %d\n", paramsModifier.getMinFocusShift()));
        log(String.format("isSupportedFocusShift %b\n", paramsModifier.isSupportedFocusShift()));
        dumpList(paramsModifier.getSupportedSelfTimers(), "SupportedSelfTimers");
        */

        //log(String.format("driveMode %s\n", paramsModifier.getDriveMode()));
        //log(String.format("burstDriveSpeed %s\n", paramsModifier.getBurstDriveSpeed()));
        //log(String.format("burstDriveButtonReleaseBehave %s\n", paramsModifier.getBurstDriveButtonReleaseBehave()));

        /*
        dumpList(paramsModifier.getSupportedDriveModes(), "SupportedDriveModes");   // single, burst, bracket
        dumpList(paramsModifier.getSupportedBurstDriveSpeeds(), "SupportedBurstDriveSpeeds");   // low, high
        dumpList(paramsModifier.getSupportedBurstDriveButtonReleaseBehaves(), "SupportedBurstDriveButtonReleaseBehaves");   // null
        dumpList(paramsModifier.getSupportedBracketModes(), "SupportedBracketModes");   // exposure, white-balance, dro
        dumpList(paramsModifier.getSupportedBracketOrders(), "SupportedBracketOrders"); // null
        dumpList(paramsModifier.getSupportedBracketStepPeriods(), "SupportedBracketStepPeriods");   // low, high
        dumpList(paramsModifier.getSupportedExposureBracketModes(), "SupportedExposureBracketModes");   // single, continue
        dumpList(paramsModifier.getSupportedExposureBracketPeriods(), "SupportedExposureBracketPeriods");   // 3, 5, 7, 10, 20, 30
        //dumpList(paramsModifier.getSupportedIsoAutoMinShutterSpeedModes(), "SupportedIsoAutoMinShutterSpeedModes"); // NoSuchMethodError
        log(String.format("isSupportedAutoShutterSpeedLowLimit %b\n", paramsModifier.isSupportedAutoShutterSpeedLowLimit()));
        log(String.format("AutoShutterSpeedLowLimit %d\n", paramsModifier.getAutoShutterSpeedLowLimit()));   // -1 = clear?
        dumpList(Settings.getSupportedAutoPowerOffTimes(), "getSupportedAutoPowerOffTimes");
        log(String.format("getAutoPowerOffTime %d\n", Settings.getAutoPowerOffTime()));  // in seconds
        */

        // Preview/Histogram
        m_camera.setPreviewAnalizeListener(new CameraEx.PreviewAnalizeListener()
        {
            @Override
            public void onAnalizedData(CameraEx.AnalizedData analizedData, CameraEx cameraEx)
            {
                if (analizedData != null && analizedData.hist != null && analizedData.hist.Y != null && m_vHist.getVisibility() == View.VISIBLE)
                    m_vHist.setHistogram(analizedData.hist.Y);
            }
        });

        // ISO
        m_camera.setAutoISOSensitivityListener(new CameraEx.AutoISOSensitivityListener()
        {
            @Override
            public void onChanged(int i, CameraEx cameraEx)
            {
                //log("AutoISOChanged " + String.valueOf(i) + "\n");
                m_tvISO.setText("\uE488 " + String.valueOf(i) + (m_curIso == 0 ? "(A)" : ""));
            }
        });

        // Shutter
        m_camera.setShutterSpeedChangeListener(this);
        m_camera.setShutterListener(this);

        /*
        m_camera.setCaptureStatusListener(new CameraEx.OnCaptureStatusListener()
        {
            @Override
            public void onEnd(int i, int i1, CameraEx cameraEx)
            {
                log(String.format("onEnd i %d i1 %d\n", i, i1));
            }

            @Override
            public void onStart(int i, CameraEx cameraEx)
            {
                log(String.format("onStart i %d\n", i));
            }
        });
        */

        /*
        m_camera.setSettingChangedListener(new CameraEx.SettingChangedListener()
        {
            @Override
            public void onChanged(int[] ints, Camera.Parameters parameters, CameraEx cameraEx)
            {
                for (int value : ints)
                {
                    log("Setting changed: " + String.valueOf(value) + "\n");
                }
            }
        });
        */

        /*
        m_camera.setAutoSceneModeListener(new CameraEx.AutoSceneModeListener()
        {
            @Override
            public void onChanged(String s, CameraEx cameraEx)
            {
                log(String.format("AutoSceneModeListener: %s\n", s));
            }
        });
        */

        // test: list scene modes
        /*
        List<String> scenesModes = params.getSupportedSceneModes();
        for (String s : scenesModes)
            log(s + "\n");
        log("Current scene mode: " + params.getSceneMode() + "\n");
        */

        // Aperture
        m_camera.setApertureChangeListener(new CameraEx.ApertureChangeListener()
        {
            @Override
            public void onApertureChange(CameraEx.ApertureInfo apertureInfo, CameraEx cameraEx)
            {
                // Disable aperture control if not available
                m_haveApertureControl = apertureInfo.currentAperture != 0;
                m_tvAperture.setVisibility(m_haveApertureControl ? View.VISIBLE : View.GONE);
                /*
                log(String.format("currentAperture %d currentAvailableMin %d currentAvailableMax %d\n",
                        apertureInfo.currentAperture, apertureInfo.currentAvailableMin, apertureInfo.currentAvailableMax));
                */
                final String text = String.format("f%.1f", (float)apertureInfo.currentAperture / 100.0f);
                m_tvAperture.setText(text);
                if (m_notifyOnNextApertureChange)
                {
                    m_notifyOnNextApertureChange = false;
                    showMessage(text);
                }
            }
        });

        // Exposure metering
        m_camera.setProgramLineRangeOverListener(new CameraEx.ProgramLineRangeOverListener()
        {
            @Override
            public void onAERange(boolean b, boolean b1, boolean b2, CameraEx cameraEx)
            {
                //log(String.format("onARRange b %b b1 %b b2 %b\n", Boolean.valueOf(b), Boolean.valueOf(b1), Boolean.valueOf(b2)));
            }

            @Override
            public void onEVRange(int ev, CameraEx cameraEx)
            {
                final String text;
                if (ev == 0)
                    text = "\u00B10.0";
                else if (ev > 0)
                    text = String.format("+%.1f", (float)ev / 3.0f);
                else
                    text = String.format("%.1f", (float)ev / 3.0f);
                m_tvExposure.setText(text);
                //log(String.format("onEVRange i %d %f\n", ev, (float)ev / 3.0f));
            }

            @Override
            public void onMeteringRange(boolean b, CameraEx cameraEx)
            {
                //log(String.format("onMeteringRange b %b\n", Boolean.valueOf(b)));
            }
        });

        m_supportedIsos = (List<Integer>)paramsModifier.getSupportedISOSensitivities();
        m_curIso = paramsModifier.getISOSensitivity();
        m_tvISO.setText(String.format("\uE488 %d", m_curIso));

        m_tvAperture.setText(String.format("f%.1f", (float)paramsModifier.getAperture() / 100.0f));

        Pair<Integer, Integer> sp = paramsModifier.getShutterSpeed();
        updateShutterSpeed(sp.first, sp.second);

        m_supportedPreviewMagnifications = (List<Integer>)paramsModifier.getSupportedPreviewMagnification();
        m_camera.setPreviewMagnificationListener(new CameraEx.PreviewMagnificationListener()
        {
            @Override
            public void onChanged(boolean enabled, int magFactor, int magLevel, Pair coords, CameraEx cameraEx)
            {
                // magnification / 100 = x.y
                // magLevel = value passed to setPreviewMagnification
                /*
                m_tvLog.setText("onChanged enabled:" + String.valueOf(enabled) + " magFactor:" + String.valueOf(magFactor) + " magLevel:" +
                    String.valueOf(magLevel) + " x:" + coords.first + " y:" + coords.second + "\n");
                */
                if (enabled)
                {
                    //log("m_curPreviewMagnificationMaxPos: " + String.valueOf(m_curPreviewMagnificationMaxPos) + "\n");
                    m_curPreviewMagnification = magLevel;
                    m_curPreviewMagnificationFactor = ((float)magFactor / 100.0f);
                    m_curPreviewMagnificationMaxPos = 1000 - (int)(1000.0f / m_curPreviewMagnificationFactor);
                    m_tvMagnification.setText(String.format("\uE012 %.2fx", (float)magFactor / 100.0f));
                    m_previewNavView.update(coords, m_curPreviewMagnificationFactor);
                }
                else
                {
                    m_previewNavView.update(null, 0);
                    m_curPreviewMagnification = 0;
                    m_curPreviewMagnificationMaxPos = 0;
                    m_curPreviewMagnificationFactor = 0;
                }
                togglePreviewMagnificationViews(enabled);
            }

            @Override
            public void onInfoUpdated(boolean b, Pair coords, CameraEx cameraEx)
            {
                // Useless?
                /*
                log("onInfoUpdated b:" + String.valueOf(b) +
                               " x:" + coords.first + " y:" + coords.second + "\n");
                */
            }
        });

        m_camera.setFocusDriveListener(new CameraEx.FocusDriveListener()
        {
            @Override
            public void onChanged(CameraEx.FocusPosition focusPosition, CameraEx cameraEx)
            {
                if (m_curPreviewMagnification == 0)
                {
                    m_lFocusScale.setVisibility(View.VISIBLE);
                    m_focusScaleView.setMaxPosition(focusPosition.maxPosition);
                    m_focusScaleView.setCurPosition(focusPosition.currentPosition);
                    m_handler.removeCallbacks(m_hideFocusScaleRunnable);
                    m_handler.postDelayed(m_hideFocusScaleRunnable, 2000);
                }
            }
        });

        loadDefaults();
        updateDriveModeImage();
        updateSceneModeImage();
        updateViewVisibility();

        /* - triggers NPE
        List<Integer> pf = params.getSupportedPreviewFormats();
        if (pf != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("SupportedPreviewFormats: ");
            for (Integer i : pf)
                sb.append(i.toString()).append(",");
            sb.append("\n");
            log(sb.toString());
        }
        */
        /* - return null
        List<Integer> pfr = params.getSupportedPreviewFrameRates();
        if (pfr != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("SupportedPreviewFrameRates: ");
            for (Integer i : pfr)
                sb.append(i.toString()).append(",");
            sb.append("\n");
            log(sb.toString());
        }
        List<Camera.Size> ps = params.getSupportedPreviewSizes();
        if (ps != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("SupportedPreviewSizes: ");
            for (Camera.Size s : ps)
                sb.append(s.toString()).append(",");
            sb.append("\n");
            log(sb.toString());
        }
        */
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        saveDefaults();

        m_surfaceHolder.removeCallback(this);
        m_autoReviewControl.setPictureReviewTime(m_pictureReviewTime);
        m_camera.setAutoPictureReviewControl(null);
        m_camera.getNormalCamera().stopPreview();
        m_camera.release();
        m_camera = null;
    }

    @Override
    public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo shutterSpeedInfo, CameraEx cameraEx)
    {
        updateShutterSpeed(shutterSpeedInfo.currentShutterSpeed_n, shutterSpeedInfo.currentShutterSpeed_d);
        if (m_bracketActive)
        {
            log(String.format("Want shutter speed %d/%d, got %d/%d\n",
                    m_bracketNextShutterSpeed.first, m_bracketNextShutterSpeed.second,
                    shutterSpeedInfo.currentShutterSpeed_n, shutterSpeedInfo.currentShutterSpeed_d
            ));

            if (shutterSpeedInfo.currentShutterSpeed_n == m_bracketNextShutterSpeed.first &&
                shutterSpeedInfo.currentShutterSpeed_d == m_bracketNextShutterSpeed.second)
            {
                // Focus speed adjusted, take next picture
                m_camera.burstableTakePicture();
            }
        }
    }

    @Override
    public void onShutter(int i, CameraEx cameraEx)
    {
        // i: 0 = success, 1 = canceled, 2 = error
        //log(String.format("onShutter i: %d\n", i));
        if (i != 0)
        {
            //log(String.format("onShutter ERROR %d\n", i));
            m_takingPicture = false;
        }
        m_camera.cancelTakePicture();

        if (m_timelapseActive)
            onShutterTimelapse(i);
        else if (m_bracketActive)
            onShutterBracket(i);
    }

    private void onShutterBracket(int i)
    {
        if (i == 0)
        {
            if (--m_bracketPicCount == 0)
                abortBracketing();
            else
            {
                m_bracketShutterDelta += m_bracketStep;
                final int shutterIndex = CameraUtil.getShutterValueIndex(getCurrentShutterSpeed());
                if (m_bracketShutterDelta % 2 == 0)
                {
                    log(String.format("Adjusting shutter speed by %d\n", -m_bracketShutterDelta));
                    // Even, reduce shutter speed
                    m_bracketNextShutterSpeed = new Pair<Integer, Integer>(CameraUtil.SHUTTER_SPEEDS[shutterIndex + m_bracketShutterDelta][0],
                        CameraUtil.SHUTTER_SPEEDS[shutterIndex + m_bracketShutterDelta][1]);
                    m_camera.adjustShutterSpeed(-m_bracketShutterDelta);
                }
                else
                {
                    log(String.format("Adjusting shutter speed by %d\n", m_bracketShutterDelta));
                    // Odd, increase shutter speed
                    m_bracketNextShutterSpeed = new Pair<Integer, Integer>(CameraUtil.SHUTTER_SPEEDS[shutterIndex - m_bracketShutterDelta][0],
                            CameraUtil.SHUTTER_SPEEDS[shutterIndex - m_bracketShutterDelta][1]);
                    m_camera.adjustShutterSpeed(m_bracketShutterDelta);
                }
            }
        }
        else
        {
            abortBracketing();
        }
    }

    private void onShutterTimelapse(int i)
    {
        if (i == 0)
        {
            ++m_timelapsePicsTaken;
            if (m_timelapsePicCount < 0 || m_timelapsePicCount == 1)
                abortTimelapse();
            else
            {
                if (m_timelapsePicCount != 0)
                    --m_timelapsePicCount;
                if (m_timelapseInterval >= 1000)
                {
                    if (m_timelapsePicCount > 0)
                        showMessage(String.format("%d pictures remaining", m_timelapsePicCount));
                    else
                        showMessage(String.format("%d pictures taken", m_timelapsePicsTaken));
                }
                if (m_timelapseInterval != 0)
                    m_handler.postDelayed(m_timelapseRunnable, m_timelapseInterval);
                else
                    m_camera.burstableTakePicture();
            }
        }
        else
        {
            abortTimelapse();
        }
    }

    // OnClickListener
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.ivDriveMode:
                toggleDriveMode();
                break;
            case R.id.ivMode:
                toggleSceneMode();
                break;
            case R.id.ivTimelapse:
                prepareTimelapse();
                break;
            case R.id.ivBracket:
                prepareBracketing();
                break;
        }
    }

    private void decrementTimelapseInterval()
    {
        if (m_timelapseInterval > 0)
        {
            if (m_timelapseInterval <= 1000)
                m_timelapseInterval -= 100;
            else
                m_timelapseInterval -= 1000;
        }
        updateTimelapseInterval();
    }

    private void incrementTimelapseInterval()
    {
        if (m_timelapseInterval < 1000)
            m_timelapseInterval += 100;
        else
            m_timelapseInterval += 1000;
        updateTimelapseInterval();
    }

    private void decrementBracketStep()
    {
        if (m_bracketStep > 1)
        {
            --m_bracketStep;
            updateBracketStep();
        }
    }

    private void incrementBracketStep()
    {
        if (m_bracketStep < 9)
        {
            ++m_bracketStep;
            updateBracketStep();
        }
    }

    private void decrementBracketPicCount()
    {
        if (m_bracketPicCount > 3)
        {
            m_bracketPicCount -= 2;
            updateBracketPicCount();
        }
    }

    private void incrementBracketPicCount()
    {
        if (m_bracketPicCount < m_bracketMaxPicCount)
        {
            m_bracketPicCount += 2;
            updateBracketPicCount();
        }
    }

    private Pair<Integer, Integer> getCurrentShutterSpeed()
    {
        final Camera.Parameters params = m_camera.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(params);
        return paramsModifier.getShutterSpeed();
    }

    private void calcMaxBracketPicCount()
    {
        final int index = CameraUtil.getShutterValueIndex(getCurrentShutterSpeed());
        final int maxSteps = Math.min(index, CameraUtil.SHUTTER_SPEEDS.length - 1 - index);
        m_bracketMaxPicCount = (maxSteps / m_bracketStep) * 2 + 1;
    }

    private void updateBracketStep()
    {
        m_tvMsg.setVisibility(View.VISIBLE);
        final int mod = m_bracketStep % 3;
        final int ev;
        if (mod == 0)
            ev = 0;
        else if (mod == 1)
            ev = 3;
        else
            ev = 7;
        m_tvMsg.setText(String.format("%d.%dEV", m_bracketStep / 3, ev));
    }

    private void updateBracketPicCount()
    {
        m_tvMsg.setVisibility(View.VISIBLE);
        m_tvMsg.setText(String.format("%d pictures", m_bracketPicCount));
    }

    private void updateTimelapseInterval()
    {
        m_tvMsg.setVisibility(View.VISIBLE);
        if (m_timelapseInterval == 0)
            m_tvMsg.setText("No delay");
        else if (m_timelapseInterval < 1000)
            m_tvMsg.setText(String.format("%d msec", m_timelapseInterval));
        else if (m_timelapseInterval == 1000)
            m_tvMsg.setText("1 second");
        else
            m_tvMsg.setText(String.format("%d seconds", m_timelapseInterval / 1000));
    }

    private void updateTimelapsePictureCount()
    {
        m_tvMsg.setVisibility(View.VISIBLE);
        if (m_timelapsePicCount == 0)
            m_tvMsg.setText("No picture limit");
        else
            m_tvMsg.setText(String.format("%d pictures", m_timelapsePicCount));
    }

    private void decrementTimelapsePicCount()
    {
        if (m_timelapsePicCount > 0)
            --m_timelapsePicCount;
        updateTimelapsePictureCount();
    }

    private void incrementTimelapsePicCount()
    {
        ++m_timelapsePicCount;
        updateTimelapsePictureCount();
    }

    private void prepareBracketing()
    {
        if (m_dialMode == DialMode.bracketSetStep || m_dialMode == DialMode.bracketSetPicCount)
            abortBracketing();
        else
        {
            if (m_sceneMode != SceneMode.manual)
            {
                showMessage("Scene mode must be set to manual");
                return;
            }
            if (m_curIso == 0)
            {
                showMessage("ISO must be set to manual");
                return;
            }

            setLeftViewVisibility(false);

            setDialMode(DialMode.bracketSetStep);
            m_bracketPicCount = 3;
            m_bracketStep = 3;
            m_bracketShutterDelta = 0;
            updateBracketStep();

            // Remember current shutter speed
            m_bracketNeutralShutterIndex = CameraUtil.getShutterValueIndex(getCurrentShutterSpeed());
        }
    }

    private void abortBracketing()
    {
        m_handler.removeCallbacks(m_countDownRunnable);
        //m_handler.removeCallbacks(m_timelapseRunnable);
        m_bracketActive = false;
        showMessage("Bracketing finished");
        setDialMode(DialMode.shutter);
        m_camera.startDirectShutter();
        m_camera.getNormalCamera().startPreview();

        // Update controls
        m_tvHint.setVisibility(View.GONE);
        setLeftViewVisibility(true);
        updateSceneModeImage();
        updateDriveModeImage();

        m_viewFlags = m_prefs.getViewFlags(m_viewFlags);
        updateViewVisibility();

        // Reset to previous shutter speed
        final int shutterDiff = m_bracketNeutralShutterIndex - CameraUtil.getShutterValueIndex(getCurrentShutterSpeed());
        if (shutterDiff != 0)
            m_camera.adjustShutterSpeed(-shutterDiff);
    }

    private void prepareTimelapse()
    {
        if (m_dialMode == DialMode.timelapseSetInterval || m_dialMode == DialMode.timelapseSetPicCount)
            abortTimelapse();
        else
        {
            setLeftViewVisibility(false);

            setDialMode(DialMode.timelapseSetInterval);
            m_timelapseInterval = 1000;
            updateTimelapseInterval();
            m_tvHint.setText("\uE4CD to set timelapse interval, \uE04C to confirm");
            m_tvHint.setVisibility(View.VISIBLE);

            // Not supported on some camera models
            try
            {
                m_autoPowerOffTimeBackup = Settings.getAutoPowerOffTime();
            }
            catch (NoSuchMethodError e)
            {
            }
        }
    }

    private void setLeftViewVisibility(boolean visibile)
    {
        final int visibility = visibile ? View.VISIBLE : View.GONE;
        m_ivTimelapse.setVisibility(visibility);
        m_ivDriveMode.setVisibility(visibility);
        m_ivMode.setVisibility(visibility);
        m_ivBracket.setVisibility(visibility);
    }

    private void startBracketCountdown()
    {
        m_bracketActive = true;
        m_camera.stopDirectShutter(new CameraEx.DirectShutterStoppedCallback()
        {
            @Override
            public void onShutterStopped(CameraEx cameraEx)
            {
            }
        });
        // Stop preview
        m_camera.getNormalCamera().stopPreview();

        // Hide some bottom views
        m_prefs.setViewFlags(m_viewFlags);
        m_viewFlags = 0;
        updateViewVisibility();

        // Start countdown
        m_countdown = COUNTDOWN_SECONDS;
        m_tvMsg.setText(String.format("Starting in %d...", m_countdown));
        m_handler.postDelayed(m_countDownRunnable, 1000);
    }

    private void startTimelapseCountdown()
    {
        m_timelapseActive = true;
        m_camera.stopDirectShutter(new CameraEx.DirectShutterStoppedCallback()
        {
            @Override
            public void onShutterStopped(CameraEx cameraEx)
            {
            }
        });
        m_tvHint.setText("\uE04C to abort");
        // Stop preview (doesn't seem to preserve battery life?)
        m_camera.getNormalCamera().stopPreview();

        // Hide some bottom views
        m_prefs.setViewFlags(m_viewFlags);
        m_viewFlags = 0;
        updateViewVisibility();

        // Start countdown
        m_countdown = COUNTDOWN_SECONDS;
        m_tvMsg.setText(String.format("Starting in %d...", m_countdown));
        m_handler.postDelayed(m_countDownRunnable, 1000);
    }

    private void startShootingBracket()
    {
        m_tvHint.setVisibility(View.GONE);
        m_tvMsg.setVisibility(View.GONE);
        // Take first picture at set shutter speed
        m_camera.burstableTakePicture();
    }

    private void startShootingTimelapse()
    {
        m_tvHint.setVisibility(View.GONE);
        m_tvMsg.setVisibility(View.GONE);
        try
        {
            Settings.setAutoPowerOffTime(m_timelapseInterval / 1000 * 2);
        }
        catch (NoSuchMethodError e)
        {
        }
        m_handler.post(m_timelapseRunnable);
    }

    private void abortTimelapse()
    {
        m_handler.removeCallbacks(m_countDownRunnable);
        m_handler.removeCallbacks(m_timelapseRunnable);
        m_timelapseActive = false;
        showMessage("Timelapse finished");
        setDialMode(DialMode.shutter);
        m_camera.startDirectShutter();
        m_camera.getNormalCamera().startPreview();

        // Update controls
        m_tvHint.setVisibility(View.GONE);
        setLeftViewVisibility(true);
        updateSceneModeImage();
        updateDriveModeImage();

        m_viewFlags = m_prefs.getViewFlags(m_viewFlags);
        updateViewVisibility();

        try
        {
            Settings.setAutoPowerOffTime(m_autoPowerOffTimeBackup);
        }
        catch (NoSuchMethodError e)
        {
        }
    }

    @Override
    protected boolean onUpperDialChanged(int value)
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewHorizontal(value * (int)(500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        else
        {
            switch (m_dialMode)
            {
                case shutter:
                    if (value > 0)
                        m_camera.incrementShutterSpeed();
                    else
                        m_camera.decrementShutterSpeed();
                    break;
                case aperture:
                    if (value > 0)
                        m_camera.incrementAperture();
                    else
                        m_camera.decrementAperture();
                    break;
                case iso:
                    final int iso = value < 0 ? getPreviousIso(m_curIso) : getNextIso(m_curIso);
                    if (iso != 0)
                        setIso(iso);
                    break;
                case exposure:
                    if (value < 0)
                        decrementExposureCompensation(false);
                    else
                        incrementExposureCompensation(false);
                    break;
                case timelapseSetInterval:
                    if (value < 0)
                        decrementTimelapseInterval();
                    else
                        incrementTimelapseInterval();
                    break;
                case timelapseSetPicCount:
                    if (value < 0)
                        decrementTimelapsePicCount();
                    else
                        incrementTimelapsePicCount();
                    break;
                case bracketSetStep:
                    if (value < 0)
                        decrementBracketStep();
                    else
                        incrementBracketStep();
                    break;
                case bracketSetPicCount:
                    if (value < 0)
                        decrementBracketPicCount();
                    else
                        incrementBracketPicCount();
                    break;
            }
            return true;
        }
    }

    private void setDialMode(DialMode newMode)
    {
        m_dialMode = newMode;
        m_tvShutter.setTextColor(newMode == DialMode.shutter ? Color.GREEN : Color.WHITE);
        m_tvAperture.setTextColor(newMode == DialMode.aperture ? Color.GREEN : Color.WHITE);
        m_tvISO.setTextColor(newMode == DialMode.iso ? Color.GREEN : Color.WHITE);
        m_tvExposureCompensation.setTextColor(newMode == DialMode.exposure ? Color.GREEN : Color.WHITE);
        if (newMode == DialMode.mode)
            m_ivMode.setColorFilter(Color.GREEN);
        else
            m_ivMode.setColorFilter(null);
        if (newMode == DialMode.drive)
            m_ivDriveMode.setColorFilter(Color.GREEN);
        else
            m_ivDriveMode.setColorFilter(null);
        if (newMode == DialMode.timelapse)
            m_ivTimelapse.setColorFilter(Color.GREEN);
        else
            m_ivTimelapse.setColorFilter(null);
        if (newMode == DialMode.bracket)
            m_ivBracket.setColorFilter(Color.GREEN);
        else
            m_ivBracket.setColorFilter(null);
    }

    private void movePreviewVertical(int delta)
    {
        int newY = m_curPreviewMagnificationPos.second + delta;
        if (newY > m_curPreviewMagnificationMaxPos)
            newY = m_curPreviewMagnificationMaxPos;
        else if (newY < -m_curPreviewMagnificationMaxPos)
            newY = -m_curPreviewMagnificationMaxPos;
        m_curPreviewMagnificationPos = new Pair<Integer, Integer>(m_curPreviewMagnificationPos.first, newY);
        m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
    }

    private void movePreviewHorizontal(int delta)
    {
        int newX = m_curPreviewMagnificationPos.first + delta;
        if (newX > m_curPreviewMagnificationMaxPos)
            newX = m_curPreviewMagnificationMaxPos;
        else if (newX < -m_curPreviewMagnificationMaxPos)
            newX = -m_curPreviewMagnificationMaxPos;
        m_curPreviewMagnificationPos = new Pair<Integer, Integer>(newX, m_curPreviewMagnificationPos.second);
        m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
    }

    @Override
    protected boolean onEnterKeyUp()
    {
        return true;
    }

    @Override
    protected boolean onEnterKeyDown()
    {
        /*
        Camera.Size s = m_camera.getNormalCamera().getParameters().getPreviewSize();
        if (s != null)
            log(String.format("previewSize width %d height %d\n", s.width, s.height));
        SurfaceView sv = (SurfaceView)findViewById(R.id.surfaceView);
        log(String.format("surfaceView width %d height %d left %d\n", sv.getWidth(), sv.getHeight(), sv.getLeft()));
        View v = findViewById(R.id.lRoot);
        log(String.format("root width %d height %d left %d\n", v.getWidth(), v.getHeight(), v.getLeft()));
        if (true)
            return true;
        */

        if (m_timelapseActive)
        {
            abortTimelapse();
            return true;
        }
        else if (m_bracketActive)
        {
            abortBracketing();
            return true;
        }
        else if (m_curPreviewMagnification != 0)
        {
            m_curPreviewMagnificationPos = new Pair<Integer, Integer>(0, 0);
            m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
            return true;
        }
        else if (m_dialMode == DialMode.iso)
        {
            // Toggle manual / automatic ISO
            setIso(m_curIso == 0 ? getFirstManualIso() : 0);
            return true;
        }
        else if (m_dialMode == DialMode.shutter && m_sceneMode == SceneMode.aperture)
        {
            // Set minimum shutter speed
            startActivity(new Intent(getApplicationContext(), MinShutterActivity.class));
            return true;
        }
        else if (m_dialMode == DialMode.exposure)
        {
            // Reset exposure compensation
            setExposureCompensation(0);
            return true;
        }
        else if (m_dialMode == DialMode.timelapseSetInterval)
        {
            setDialMode(DialMode.timelapseSetPicCount);
            m_tvHint.setText("\uE4CD to set picture count, \uE04C to confirm");
            m_timelapsePicCount = 0;
            updateTimelapsePictureCount();
            return true;
        }
        else if (m_dialMode == DialMode.timelapseSetPicCount)
        {
            startTimelapseCountdown();
            return true;
        }
        else if (m_dialMode == DialMode.bracketSetStep)
        {
            setDialMode(DialMode.bracketSetPicCount);
            m_tvHint.setText("\uE4CD to set picture count, \uE04C to confirm");
            calcMaxBracketPicCount();
            updateBracketPicCount();
            return true;
        }
        else if (m_dialMode == DialMode.bracketSetPicCount)
        {
            startBracketCountdown();
            return true;
        }
        else if (m_dialMode == DialMode.mode)
        {
            toggleSceneMode();
            return true;
        }
        else if (m_dialMode == DialMode.drive)
        {
            toggleDriveMode();
            return true;
        }
        else if (m_dialMode == DialMode.timelapse)
        {
            prepareTimelapse();
            return true;
        }
        else if (m_dialMode == DialMode.bracket)
        {
            prepareBracketing();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onUpKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onUpKeyUp()
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewVertical((int)(-500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        else
        {
            // Toggle visibility of some views
            cycleVisibleViews();
            return true;
        }
    }

    @Override
    protected boolean onDownKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onDownKeyUp()
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewVertical((int)(500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        else
        {
            switch (m_dialMode)
            {
                case shutter:
                    if (m_haveApertureControl)
                    {
                        setDialMode(DialMode.aperture);
                        break;
                    }
                case aperture:
                    setDialMode(DialMode.iso);
                    break;
                case iso:
                    setDialMode(DialMode.exposure);
                    break;
                case exposure:
                    setDialMode(m_haveTouchscreen ? DialMode.shutter : DialMode.mode);
                    break;
                case mode:
                    setDialMode(DialMode.drive);
                    break;
                case drive:
                    setDialMode(DialMode.timelapse);
                    break;
                case timelapse:
                    setDialMode(DialMode.bracket);
                    break;
                case bracket:
                    setDialMode(DialMode.shutter);
                    break;
            }
            return true;
        }
    }

    @Override
    protected boolean onLeftKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onLeftKeyUp()
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewHorizontal((int)(-500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRightKeyDown()
    {
        return true;
    }

    @Override
    protected boolean onRightKeyUp()
    {
        if (m_curPreviewMagnification != 0)
        {
            movePreviewHorizontal((int)(500.0f / m_curPreviewMagnificationFactor));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onShutterKeyUp()
    {
        m_shutterKeyDown = false;
        return true;
    }

    @Override
    protected boolean onShutterKeyDown()
    {
        // direct shutter...
        /*
        log("onShutterKeyDown\n");
        if (!m_takingPicture)
        {
            m_takingPicture = true;
            m_shutterKeyDown = true;
            m_camera.burstableTakePicture();
        }
        */
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        final int scanCode = event.getScanCode();
        if (m_timelapseActive && scanCode != ScalarInput.ISV_KEY_ENTER)
            return true;
        // TODO: Use m_supportedPreviewMagnifications
        if (m_dialMode != DialMode.timelapseSetInterval && m_dialMode != DialMode.timelapseSetPicCount)
        {
            if (scanCode == 610 && !m_zoomLeverPressed)
            {
                // zoom lever tele
                m_zoomLeverPressed = true;
                if (m_curPreviewMagnification == 0)
                {
                    m_curPreviewMagnification = 100;
                    m_lFocusScale.setVisibility(View.GONE);
                }
                else
                    m_curPreviewMagnification = 200;
                m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
                return true;
            }
            else if (scanCode == 611 && !m_zoomLeverPressed)
            {
                // zoom lever wide
                m_zoomLeverPressed = true;
                if (m_curPreviewMagnification == 200)
                {
                    m_curPreviewMagnification = 100;
                    m_camera.setPreviewMagnification(m_curPreviewMagnification, m_curPreviewMagnificationPos);
                }
                else
                {
                    m_curPreviewMagnification = 0;
                    m_camera.stopPreviewMagnification();
                }
                return true;
            }
            else if (scanCode == 645)
            {
                // zoom lever returned to neutral position
                m_zoomLeverPressed = false;
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try
        {
            Camera cam = m_camera.getNormalCamera();
            cam.setPreviewDisplay(holder);
            cam.startPreview();
        }
        catch (IOException e)
        {
            m_tvMsg.setText("Error starting preview!");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        //log(String.format("surfaceChanged width %d height %d\n", width, height));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    protected void setColorDepth(boolean highQuality)
    {
        super.setColorDepth(false);
    }
}
