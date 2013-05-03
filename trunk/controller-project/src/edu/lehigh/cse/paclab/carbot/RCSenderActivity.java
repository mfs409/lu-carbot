package edu.lehigh.cse.paclab.carbot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

/**
 * This is the "controller" half of the remote-control station. We connect this activity to a wifi activity on the
 * robot, and then the phone running this activity can send commands to the robot.
 * 
 * [TODO] Long-term, we are going to require this code to send 'snap photo' requests, and to receive photos back from
 * the remote host. However, in the short term we're just focused on basic remote control functionality
 */
public class RCSenderActivity extends BasicBotActivityBeta
{
    /**
     * Track if we are connected
     */
    private boolean                    connected = false;

    /**
     * This provides a means of communicating to the network thread in a manner that avoids spinning
     */
    private ArrayBlockingQueue<String> queue     = new ArrayBlockingQueue<String>(20);

    /**
     * On activity creation, we just inflate a layout... for now, the layout will be the same as for TetheredBotBeta,
     * with 7 buttons.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tetheredbot_beta);
    }

    /**
     * Mandatory method for setting up the menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.listen_connect, menu);
        return true;
    }

    /**
     * Dispatch method for dealing with menu events
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {

            case R.id.menu_listen:
                // the 'listen' menu item does not apply to the sender code
                shortbread("You should run that on the robot");
                return true;
            case R.id.menu_connect:
                // the 'connect' menu item causes us to prompt for an IP and connect
                initiateConnection();
                return true;
            case R.id.menu_report:
                // the 'report' menu item reports our IP address
                longbread(getLocalIpAddress());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Whenever one of the buttons is pressed, we send the appropriate request over the network by putting a string in
     * the queue that the network thread is blocked on
     * 
     * @param v
     *            A reference to the button that was pressed
     */
    public void onClickImage(View v)
    {
        try {
            if (v == findViewById(R.id.ivTetherForward))
                queue.put("FWD");
            if (v == findViewById(R.id.ivTetherReverse))
                queue.put("REV");
            if (v == findViewById(R.id.ivTetherLeft))
                queue.put("PTL");
            if (v == findViewById(R.id.ivTetherRight))
                queue.put("PTR");
            if (v == findViewById(R.id.ivTetherRotPos))
                queue.put("CW");
            if (v == findViewById(R.id.ivTetherRotNeg))
                queue.put("CCW");
            if (v == findViewById(R.id.ivTetherStop))
                queue.put("STOP");
        }
        catch (InterruptedException ie) {
            // swallow the exception for now...
            longbread("Error while enqueueing: " + ie);
        }
    }

    /**
     * The client half of the communication protocol. Whenever a string arrives in the queue, we just send it and we're
     * done.
     * 
     * @param socket
     *            The open socket that the client uses to communicate with the server
     */
    void clientProtocol(Socket socket)
    {
        try {
            // set up streams for reading/writing on the socket... 'in' is unused for now...
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                    true);
            // main loop
            while (connected) {
                // get something from the queue, blocking if there is nothing to get
                String msg = queue.take();
                // send the message we received to the server
                out.println(msg);
            }
        }
        catch (Exception e) {
            longbread("Error while sending: " + e);
            e.printStackTrace();
        }
    }

    /**
     * When the client user clicks 'connect', we create a Dialog to ask for the IP address of the server, and if we get
     * a valid response, we initiate a connection request.
     */
    private void initiateConnection()
    {
        // create a dialog consisting of an EditText and two buttons
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Connect to Server");
        alert.setMessage("Enter server IP address");
        final EditText input = new EditText(this);
        // pre-fill with our address, since the prefix should be the same
        input.setText(getLocalIpAddress());
        alert.setView(input);

        // on 'OK', take the next step in starting a connection
        alert.setPositiveButton("OK", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                // if we're not connected, get the IP, make a connection thread, and go for it...
                if (!connected) {
                    String s = input.getEditableText().toString();
                    if (!s.equals("")) {
                        shortbread("Attempting to connect to " + s);
                        Thread cThread = new Thread(new ClientThread(s));
                        cThread.start();
                    }
                }
            }
        });
        // if the user clicks 'cancel', do nothing...
        alert.setNegativeButton("CANCEL", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                dialog.cancel();
            }
        });
        AlertDialog alertDialog = alert.create();
        alertDialog.show();
    }

    /**
     * The ClientThread Runnable Object is used to create a Thread for managing the client side of the communication
     */
    public class ClientThread implements Runnable
    {
        /**
         * Track the server IP address
         */
        String serverIpAddress;

        /**
         * Simple constructor
         * 
         * @param s
         *            the server ip address
         */
        ClientThread(String s)
        {
            serverIpAddress = s;
        }

        /**
         * The main routine is just to make a connection and then run the client protocol
         */
        public void run()
        {
            try {
                // attempt to connect to the server
                InetAddress serverAddr = InetAddress.getByName(serverIpAddress);
                Socket socket = new Socket(serverAddr, WIFICONTROLPORT);
                connected = true;
                shortbread("Connected!");
                // run the client protocol, then close the socket
                clientProtocol(socket);
                socket.close();
                shortbread("connection closed");
            }
            catch (Exception e) {
                shortbread("Error during I/O: " + e);
                e.printStackTrace();
                connected = false;
            }
        }
    }
}