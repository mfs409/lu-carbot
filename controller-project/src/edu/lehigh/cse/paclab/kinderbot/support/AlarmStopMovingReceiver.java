package edu.lehigh.cse.paclab.kinderbot.support;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmStopMovingReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        ConfigurationActivity.self.robotStop();
    }
}