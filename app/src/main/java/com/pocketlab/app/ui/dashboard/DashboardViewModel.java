package com.pocketlab.app.ui.dashboard;

import androidx.lifecycle.ViewModel;

import com.pocketlab.app.sensor.SamplingRate;

/** Keeps the chosen sampling rate across screen rotation. */
public class DashboardViewModel extends ViewModel {
    private SamplingRate rate = SamplingRate.UI;

    public SamplingRate getRate() { return rate; }
    public void setRate(SamplingRate rate) { this.rate = rate; }
}
