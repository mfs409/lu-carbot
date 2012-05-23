package edu.lehigh.cse.paclab.carbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import edu.lehigh.cse.paclab.carbot.services.BTService;
import edu.lehigh.cse.paclab.carbot.services.BluetoothManager;

/**
 * An activity for controlling a robot remotely
 * 
 * The BTRemoteControl activity runs on one phone, and BTBotDriver runs on
 * another. The phone running BTBotDriver also uses the ArduinoService.
 * BTRemoteControl has a simple interface, which is then used to send messages
 * to the BTBotDriver. BTBotDriver receives messages and appropriate messages to
 * the Arduino to move or stop the robot.
 */
public class BTRemoteControl extends BTActivity
{
    // implement abstract message to handle when BT connects
    void onStateConnected()
    {
        TextView tv = (TextView) findViewById(R.id.tvBtTitleRight);
        tv.setText("connected to " + BluetoothManager.getDevName());
    }

    // implement abstract message to handle when BT tries to connect
    void onStateConnecting()
    {
        TextView tv = (TextView) findViewById(R.id.tvBtTitleRight);
        tv.setText("Connecting...");
    }

    // implement abstract message to handle when BT is not connected
    void onStateNone()
    {
        TextView tv = (TextView) findViewById(R.id.tvBtTitleRight);
        tv.setText("not connected");
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // set up custom title, inflate layout, create title, set default
        // message
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.btremotecontrollayout);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.bttitle);
        onStateNone();

        // initialize buttons
        Button mSendButton = (Button) findViewById(R.id.btnBTRCSendPic);
        mSendButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(View v)
            {
                sendBigMessage();
            }
        });

        Button mCmdButton = (Button) findViewById(R.id.btnBTRCSendFWD);
        mCmdButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(View v)
            {
                sendFwd();
            }
        });

    }

    // now we shall try to set up a 2-stage communication
    // snd -1: size
    // rcv -1: send ack
    // snd i: byte[512*i]...upto 512 more bytes
    // rcv i: send ack

    // the round that we are on
    int sendIter = -1;

    // are we sending or receiving?
    boolean sending = false;

    // the data
    byte data[];

    // the size of the data being sent
    int sendSize;

    // the message, if we are sending a short message
    String shortmessage = "";

    // send a simple message
    void sendFwd()
    {
        // Check that we're actually connected before trying anything
        if (BluetoothManager.getBtService().getState() != BTService.STATE_CONNECTED) {
            Toast.makeText(this, "Error: No connection!", Toast.LENGTH_SHORT).show();
            return;
        }

        sending = true;
        sendIter = -1;
        shortmessage = "FWD";
        sendSize = -1;
        data = null;
        sendMessage();
    }

    protected void sendBigMessage()
    {
        Log.i("CARBOT", "Sending big message");
        // Check that we're actually connected before trying anything
        if (BluetoothManager.getBtService().getState() != BTService.STATE_CONNECTED) {
            Toast.makeText(this, "Error: No connection!", Toast.LENGTH_SHORT).show();
            return;
        }

        // move FSM to sending mode
        sending = true;
        sendIter = -1;
        shortmessage = "";

        // get the data to send (NB: this will change)
        // 1 - find the file
        File fSDCard = new File("/sdcard");
        File fImage = new File(fSDCard.toString() + "/image.jpg");
        // 2 - get its length, make a buffer
        sendSize = (int) fImage.length();
        Log.i("CARBOT", "File Size is " + sendSize);
        data = new byte[sendSize];
        // 3 - get the data
        try {
            FileInputStream fis = new FileInputStream(fImage);
            fis.read(data);
            fis.close();
        }
        catch (Exception e) {
            e.printStackTrace();
            sending = false;
            return;
        }
        // start the send/receive handshaking
        sendMessage();
    }

    /**
     * Sends a message.
     */
    protected void sendMessage()
    {
        // case 1: we are sending a non-data message
        if (sending == true && data == null) {
            Log.i("CARBOT", "sending " + shortmessage);
            // send the actual message as a string
            BluetoothManager.getBtService().write(shortmessage.getBytes());
            return;
        }
        // case 2: we are sending the first chunk of a data message
        if (sending == true && sendIter == -1) {
            Log.i("CARBOT", "sending startbig size = " + sendSize);
            // send the size of the message as a string, advance to next
            BluetoothManager.getBtService().write(("" + sendSize).getBytes());
            sendIter++;
            return;
        }
        // case 3: we are sending the 'sendIter'th chunk of a data message
        if (sending == true) {
            // figure out which chunk to send (no more than 512 bytes!)
            int remain = sendSize - 512 * sendIter;
            int min = remain > 512 ? 512 : remain;
            // send bytes via offset/size variant of write command, advance to
            // next
            Log.i("CARBOT", "sending " + min + " bytes");
            BluetoothManager.getBtService().write(data, 512 * sendIter, min);
            sendIter++;
            return;
        }
        // case 4: we are in receiving mode, so send an ACK
        Log.i("CARBOT", "sending ack");
        BluetoothManager.getBtService().write("ACK".getBytes());
    }

    /**
     * Receive a message
     */
    protected void receiveMessage(byte[] readBuf, int bytes)
    {
        // case 1: we are the sender of a non-data message... this is an ACK
        if (sending == true && data == null) {
            // ignore the message, as it must be an ACK
            Log.i("CARBOT", "received " + readBuf);
            // the communication is done
            sending = false;
            return;
        }
        // case 2: we are the sender of a data message... this is an ACK
        if (sending == true) {
            // ignore the message, as it must be an ACK
            Log.i("CARBOT", "received " + readBuf);
            
            // do we need to send more data?
            if (sendIter * 512 >= sendSize) {
                sending = false;
            }
            else {
                sendMessage();
            }
            return;
        }
        // case 3: we are the receiver of a new message
        if (sendIter == -1) {
            String msg = new String(readBuf, 0, bytes);
            Log.i("CARBOT", "RECEIVED:::" + msg);
            // check for known non-int messages
            if (msg.equals("FWD")) {
                // it's forward: update the TV, send an ACK
                TextView tv = (TextView) findViewById(R.id.tvBTRCLastMsg);
                tv.setText(msg);
                sendMessage();
                return;
            }
            // other known messages would be handled here, or better yet, have a
            // function handle them!

            // ...

            // if we are here, then we think we've been sent an Integer, which
            // means we are starting a new data communication
            try {
                // total size can be determined by the payload of this
                // message
                sendSize = Integer.parseInt(new String(readBuf, 0, bytes));
                // get room for the data
                data = new byte[sendSize];
                // advance to next state
                sendIter++;
            }
            catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
            return;
        }
        // case 4: we are receiving the 'sendIter'th packet of data
        
        // figure out how much data is in this packet
        int remain = sendSize - 512 * sendIter;
        int min = remain > 512 ? 512 : remain;
        
        // figure out offset where data will go
        int start = 512 * sendIter;

        // copy data into buffer
        for (int i = 0; i < min; ++i)
            data[start + i] = readBuf[i];
        
        // advance to next state
        sendIter++;
        
        // ack the message
        sendMessage();

        // are we all done?
        if (sendIter * 512 >= sendSize) {
            // clear the counter
            sendIter = -1;

            // create a file and dump the byte stream into it (hard-code for Droid I)
            File fSDCard = new File("/mnt/sdcard");
            File fImage = new File(fSDCard.toString() + "/image.jpg");

            FileOutputStream fos;
            try {
                fos = new FileOutputStream(fImage);
                fos.write(data, 0, sendSize);
                fos.close();
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
            catch (IOException e) {
                e.printStackTrace();
                return;
            }
            // if that worked, then update the imageView
            ImageView iv = (ImageView) findViewById(R.id.ivBTRCImage);
            iv.setImageURI(Uri.fromFile(fImage));
        }
    }

}