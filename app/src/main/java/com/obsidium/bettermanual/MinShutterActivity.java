package com.obsidium.bettermanual;

import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.ma1co.pmcademo.app.BaseActivity;
import com.github.ma1co.pmcademo.app.R;
import com.sony.scalar.hardware.CameraEx;

public class MinShutterActivity extends BaseActivity implements SeekBar.OnSeekBarChangeListener
{
    private SeekBar     m_sbShutter;
    private TextView    m_tvInfo;

    private CameraEx m_camera;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_min_shutter);

        m_sbShutter = (SeekBar) findViewById(R.id.sbShutter);
        m_sbShutter.setOnSeekBarChangeListener(this);
        m_sbShutter.setMax(CameraUtil.MIN_SHUTTER_VALUES.length - 1);

        m_tvInfo = (TextView) findViewById(R.id.tvInfo);

        Button btnOk = (Button) findViewById(R.id.btnOk);
        btnOk.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                onBackPressed();
            }
        });

        setTitle("Minimum Shutter Speed");
    }

    @Override
    public void onResume()
    {
        super.onResume();

        m_camera = CameraEx.open(0, null);

        m_camera.setShutterSpeedChangeListener(new CameraEx.ShutterSpeedChangeListener()
        {
            @Override
            public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo shutterSpeedInfo, CameraEx cameraEx)
            {
                int idx = CameraUtil.getShutterValueIndex(shutterSpeedInfo.currentAvailableMin_n, shutterSpeedInfo.currentAvailableMin_d);
                if (idx >= 0)
                {
                    m_sbShutter.setProgress(idx);
                    m_tvInfo.setText(CameraUtil.formatShutterSpeed(shutterSpeedInfo.currentAvailableMin_n, shutterSpeedInfo.currentAvailableMin_d));
                }
            }
        });
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        // Save minimum shutter speed
        Preferences prefs = new Preferences(this);
        final CameraEx.ParametersModifier paramsModifier = m_camera.createParametersModifier(m_camera.getNormalCamera().getParameters());
        prefs.setMinShutterSpeed(paramsModifier.getAutoShutterSpeedLowLimit());

        m_camera.release();
        m_camera = null;
    }

    @Override
    protected boolean onEnterKeyDown()
    {
        onBackPressed();
        return true;
    }

    @Override
    protected boolean onUpperDialChanged(int value)
    {
        m_sbShutter.incrementProgressBy(value);
        final Camera.Parameters params = m_camera.createEmptyParameters();
        m_camera.createParametersModifier(params).setAutoShutterSpeedLowLimit(CameraUtil.MIN_SHUTTER_VALUES[m_sbShutter.getProgress()]);
        m_camera.getNormalCamera().setParameters(params);
        return true;
    }

    /* OnSeekBarChangeListener */
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
        if (fromUser)
        {
            final Camera.Parameters params = m_camera.createEmptyParameters();
            m_camera.createParametersModifier(params).setAutoShutterSpeedLowLimit(CameraUtil.MIN_SHUTTER_VALUES[progress]);
            m_camera.getNormalCamera().setParameters(params);
        }
    }
    public void onStartTrackingTouch(SeekBar var1)
    {
    }
    public void onStopTrackingTouch(SeekBar var1)
    {
    }

    @Override
    protected void setColorDepth(boolean highQuality)
    {
        super.setColorDepth(false);
    }
}
