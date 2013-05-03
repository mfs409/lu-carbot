package edu.lehigh.cse.paclab.carbot;

import android.os.Bundle;
import android.view.View;

/**
 * This is the new interface to TetheredBot. It uses DTMF to communicate with the robot.
 * 
 * TODO:
 * 
 * (1) STOP produces a pulse
 * 
 * (2) All sounds are a short pulse
 * 
 * (3) Change pulse mechanism, switch between headphone jack and speaker between pulses
 * 
 * (4) Get everything to work while playing music
 * 
 * (5) Get everything to work while doing TTS
 * 
 * @author ArmonShariati
 * 
 */
public class TetheredBotBeta extends BasicBotActivityBeta
{

    /**
     * On activity creation, we just inflate a menu
     */
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Use this code for managing screens of different size...this uses a more recent API than the previous
        // TetheredBot
        //
        // Display display = getWindowManager().getDefaultDisplay(); Point size = new Point(); display.getSize(size);
        // int width = size.x; int height = size.y;
        setContentView(R.layout.tetheredbot_beta);
    }

    /**
     * Whenever one of the buttons is pressed, we issue the appropriate DTMF command
     * 
     * @param v
     *            A reference to the button that was pressed
     */
    public void onClickImage(View v)
    {
        if (v == findViewById(R.id.ivTetherForward))
            robotForward();
        if (v == findViewById(R.id.ivTetherReverse))
            robotReverse();
        if (v == findViewById(R.id.ivTetherLeft))
            robotPointTurnLeft();
        if (v == findViewById(R.id.ivTetherRight))
            robotPointTurnRight();
        if (v == findViewById(R.id.ivTetherRotPos))
            robotClockwise();
        if (v == findViewById(R.id.ivTetherRotNeg))
            robotCounterClockwise();
        if (v == findViewById(R.id.ivTetherStop))
            robotStop();
    }
}