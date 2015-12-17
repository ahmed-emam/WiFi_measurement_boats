package ahmedemam.wifi_measurement_boats;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;


import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

public class MainActivity extends ActionBarActivity {
    public static String TAG = "Measurement";
    private static boolean D = true;
    private static FileOutputStream logfile;
    private static FileOutputStream wifiRSSI;
    private static FileOutputStream wifi_RTT;
    private static FileOutputStream wifi_LOSS;
    private static FileOutputStream wifi_THROUGHPUT;
    private static FileOutputStream locationFile;
    public int Distance = 0;
    int sequence = 0;

    boolean[] received;
    long[] wifi_rtt_values;
    long[] wifi_rtt_start;
    int[] wifi_rssi_values;
    long[] wifi_throughPut_delays;
    long[] wifi_throughPut_recieved_bytes;


    int turn = 0;                                       //Count in which Turn am I
    int succesfullTCPConnections = 0;
    public int NUM_PACKETS_LOSS = 1000;
    public static final int NUM_PACKETS_RTT = 10;
    public static final int NUM_RTTS = 50;

    public static final int NUM_ThroughPut = 5;
    public static final int RSSI = 10;
    public static final int NUM_ThroughPut_TRIALS = 50;

    public static final int TOAST_MSG = 0;
    public static final int TOAST_MSG_SHORT = 1;
    public static final int TEXT_MSG = 2;

    public boolean rtt_enabled = false;
    public boolean packet_loss_enabled = false;
    public boolean throughPut_enabled = false;

    public int packetsReceived = 0;
    int packets_discarded = 0;

    DatagramSocket socket = null;


    DatagramSocket PacketLossSocket = null;
    boolean server = false;
    public static int DEVICE_ID = 0;
    public static String rootDir = Environment.getExternalStorageDirectory().toString() + "/MobiBots/Measurement/Wifi/";
    String environment = "Boats/";
    public int STATE = 0;
    Handler handel = new Handler();

    public MainActivity mainActivity;


    WifiServerThread serverThread = null;
    WifiClientThread clientThread = null;


    PacketLossServerThread loss_server_thread = null;
    PacketLossClientThread loss_client_thread = null;

    WifiTCPServerThread ThroughPutServer = null;
    WifiTCPClientThread ThroughPutClient = null;
    private Location currentLocation;
    Ping pingThread = null;

    private OutputStream commandCenterOutputStream;

    public String[] device_Wifi_adresses = {
            "D8:50:E6:83:D0:2A",
            "D8:50:E6:83:68:D0",
            "D8:50:E6:80:51:09",
            "24:DB:ED:03:47:C2",
            "24:DB:ED:03:49:5C",
            "8c:3a:e3:6c:a2:9f",
            "8c:3a:e3:5d:1c:ec",
            "c4:43:8f:f6:3f:cd",
            "f8:a9:d0:02:0d:2a",
            "10:bf:48:ef:e9:c1",
            "30:85:a9:60:07:3b"
    };

    public String[] device_ip_adresses = {
            "",
            "",
            "",
            "",
            "",
            "10.0.0.1",
            "10.0.0.2",
            "10.0.0.3",
            "10.0.0.4",
            "",
            ""
    };

    /**
     * Return device's ID mapping according to the device's wifi mac address
     * @param deviceAddress     Bluetooth Address
     * @return device ID
     */
    public int findDevice_Wifi(String deviceAddress) {
        for (int i = 0; i < device_Wifi_adresses.length; i++) {
            if (device_Wifi_adresses[i].equalsIgnoreCase(deviceAddress)) {
                return (i + 1);
            }
        }
        return -1;
    }

    /**
     * Print out debug messages if "D" (debug mode) is enabled
     * @param message
     */
    public void debug(String message) {
        if (D) {
            Log.d(TAG, message);


        }
    }


    public void sendToCommandCenter(String msg){
        if(commandCenterOutputStream != null)
            try {
                commandCenterOutputStream.write((msg + "\n\r").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public synchronized String getTimeStamp() {
        return (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date()));
    }

    /**
     * Function that write 'message' to the log file
     * @param message
     */
    public synchronized void log(FileOutputStream log, String message) {
        StringBuilder log_message = new StringBuilder(message.length());
        log_message.append(getTimeStamp()+"\t");
        log_message.append(message);
        log_message.append("\n");

        try {

            log.write(log_message.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void initLogLocationFilee() {
        File mediaStorageDir = new File(rootDir + environment);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }
        String timeStamp = new SimpleDateFormat("yyyy_MM_dd").format(new Date());

        mediaStorageDir = new File(rootDir + environment + timeStamp);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }

        try {


            File  file = new File(rootDir + environment + timeStamp + "/location.txt");
            if (!file.exists())
                file.createNewFile();
            locationFile = new FileOutputStream(file, true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initLogFiles(int Distance) {
        File mediaStorageDir = new File(rootDir + environment);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }
        String timeStamp = new SimpleDateFormat("yyyy_MM_dd").format(new Date());

        mediaStorageDir = new File(rootDir + environment + timeStamp);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }

        try {
            File file = new File(rootDir + environment + timeStamp + "/" + "rssi_" + Distance + "m.txt");

            if (!file.exists())
                file.createNewFile();
            wifiRSSI = new FileOutputStream(file, true);

            file = new File(rootDir + environment + timeStamp + "/" + "rtt_" + Distance + "m.txt");
            if (!file.exists())
                file.createNewFile();
            wifi_RTT = new FileOutputStream(file, true);

            file = new File(rootDir + environment + timeStamp + "/" + "loss_" + Distance + "m.txt");
            if (!file.exists())
                file.createNewFile();
            wifi_LOSS = new FileOutputStream(file, true);

            file = new File(rootDir + environment + timeStamp + "/" + "tput_" + Distance + "m.txt");
            if (!file.exists())
                file.createNewFile();
            wifi_THROUGHPUT = new FileOutputStream(file, true);


//            file = new File(rootDir + environment + timeStamp + "/location.txt");
//            if (!file.exists())
//                file.createNewFile();
//            locationFile = new FileOutputStream(file, true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printOnUIScreen(String msg) {
        Message textMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
        byte[] toastMSG_bytes = msg.getBytes();
        textMSG.obj = toastMSG_bytes;
        textMSG.arg1 = toastMSG_bytes.length;
        mainActivity.mHandler.sendMessage(textMSG);
    }

    public final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TOAST_MSG:
                    byte[] message = (byte[]) msg.obj;
                    String theMessage = new String(message, 0, msg.arg1);
                    Toast.makeText(getApplicationContext(), theMessage, Toast.LENGTH_LONG).show();
                    break;
                case TOAST_MSG_SHORT:
                    message = (byte[]) msg.obj;
                    theMessage = new String(message, 0, msg.arg1);
                    Toast.makeText(getApplicationContext(), theMessage, Toast.LENGTH_SHORT).show();
                    break;
                case TEXT_MSG:
                    TextView view = (TextView) findViewById(R.id.textView2);
//                    String previousText = view.getText().toString();
                    message = (byte[]) msg.obj;
                    theMessage = new String(message, 0, msg.arg1);
                    theMessage += "\n";
//                    String newText = previousText+"\n"+theMessage;
//                    view.setText(newText);
                    view.append(theMessage);
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;


        WifiManager myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo myWifiInfo = myWifiManager.getConnectionInfo();
        DEVICE_ID = findDevice_Wifi(myWifiInfo.getMacAddress());
        initLogLocationFilee();
        debug("Link Speed: " + myWifiInfo.getLinkSpeed());


        Switch s = (Switch) findViewById(R.id.switch1);
        if (DEVICE_ID == 8) {
            s.setChecked(true);
            server = true;
            debug("You are the server");
        } else {
            s.setChecked(false);
            server = false;
            debug("You are the client");
        }


        new CommandsThread().start();

        TextView view = (TextView) findViewById(R.id.textView2);
        view.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Hook up to the GPS system
        LocationManager gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria c = new Criteria();
        c.setAccuracy(Criteria.ACCURACY_FINE);
        c.setPowerRequirement(Criteria.NO_REQUIREMENT);
        String provider = gps.getBestProvider(c, false);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        gps.requestLocationUpdates(provider, 0, 0, locationListener);
    }

    /**
     * Handles GPS updates by calling the appropriate update.
     */
    private LocationListener locationListener = new LocationListener() {
        public void onStatusChanged(String provider, int status, Bundle extras) {

            String a = String.format("onStatusChanged: provider = %s, status= %d", provider, status);
            Log.w(TAG, a);
        }

        public void onProviderEnabled(String provider) {
            Log.w(TAG, "onProviderEnabled");
        }

        public void onProviderDisabled(String provider) {
        }

        public void onLocationChanged(Location location) {
// Convert from lat/long to UTM coordinates
            debug("Current Location: " + location);
            if(location.hasSpeed())
                log(locationFile, location.getLatitude()+"\t"+location.getLongitude()+
                        "\t"+location.getAltitude()+"\t"+location.getSpeed());
            else
                log(locationFile, location.getLatitude() + "\t" + location.getLongitude() +
                        "\t" + location.getAltitude());
            currentLocation = location;


        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from GPS updates
        LocationManager gps;
        gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        gps.removeUpdates(locationListener);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    public void CloseApp(View v){
        finish();
        System.exit(0);
    }

    public void GetRSSI(){
        int rssi_values = 0;
        int failedToFind = 0;
        Message  toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
        byte[] toastMSG_bytes =  ("Getting RSSI").getBytes();
        toastMSG.obj = toastMSG_bytes;
        toastMSG.arg1 = toastMSG_bytes.length;
        mainActivity.mHandler.sendMessage(toastMSG);
        Process su = null;
        DataOutputStream stdin = null ;


        double min = Double.MAX_VALUE;
        double max = Double.NEGATIVE_INFINITY;

        double average = 0;

        while ((rssi_values+failedToFind < RSSI))
        {
            try {
                su = Runtime.getRuntime().exec(new String[]{"su", "-c", "system/bin/sh"});
                stdin = new DataOutputStream(su.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Getting rssi");

            try {
                stdin.writeBytes("iw ibss leave");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {

                }


                stdin.writeBytes("iw dev wlan0 scan\n");
                InputStream stdout = su.getInputStream();
                byte[] buffer = new byte[1024];
                int read;
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {

                }

                String out = new String();
                //read method will wait forever if there is nothing in the stream
                //so we need to read it in another way than while((read=stdout.read(buffer))>0)
                while(true){
                    if(stdout.available() <= 0){
                        break;
                    }
                    read = stdout.read(buffer);
                    out += new String(buffer, 0, read);

//                    Log.d(TAG, out);
//                    Log.d(TAG, "\n");
                    if(read<1024) {
                        //we have read everything
                        break;
                    }
                }
                if(!out.isEmpty()){
                    boolean found = false;
                    Scanner lineScanner = new Scanner(out);

                    String line;
                    while(lineScanner.hasNextLine())
                    {
                        line = lineScanner.nextLine();
//                    Log.d(TAG, line);
                        if(line.contains("a2:aa:79:54:35:59")){
                            while(lineScanner.hasNextLine()){
                                line = lineScanner.nextLine();
                                if(line.contains("signal:")){
                                    Scanner scan = new Scanner(line);
                                    scan.next();
                                    double RSSI_value = scan.nextDouble();
                                    Log.d(TAG, "RSSI: "+RSSI_value);
                                    log(wifiRSSI, "" + RSSI_value);

                                    rssi_values++;

                                    toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                                    toastMSG_bytes =  ("RSSI: Turn "+(rssi_values+failedToFind)
                                            +"/"+RSSI+" Value:"+RSSI_value).getBytes();
                                    toastMSG.obj = toastMSG_bytes;
                                    toastMSG.arg1 = toastMSG_bytes.length;
                                    mainActivity.mHandler.sendMessage(toastMSG);

                                    average += RSSI_value;
                                    if(RSSI_value < min)
                                        min = RSSI_value;
                                    if(RSSI_value > max)
                                        max = RSSI_value;


                                    found = true;
                                    break;
                                }
                            }
                        }
                    }

                    if(!found){
                        Log.d(TAG, "Adhoc network is not available");
                        failedToFind++;
                        Log.d(TAG, "RSSI: "+0);
                        log(wifiRSSI, "" + 0);
                    }
                }
                else{
                    Log.d(TAG, "Output empty");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {

            }
        }

        average /= rssi_values;

        toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
        toastMSG_bytes =  ("RSSI: "+min+"/"+average+"/"+max).getBytes();
        toastMSG.obj = toastMSG_bytes;
        toastMSG.arg1 = toastMSG_bytes.length;
        mainActivity.mHandler.sendMessage(toastMSG);

        debug("Done with RSSI");
        Message msg = mainActivity.mHandler.obtainMessage(TOAST_MSG);
        toastMSG_bytes =  ("Done with RSSI").getBytes();
        msg.obj = toastMSG_bytes;
        msg.arg1 = toastMSG_bytes.length;
        mainActivity.mHandler.sendMessage(msg);
    }




    public void new_Experiment(int distance){
        debug("Creating new experiment with distance "+distance);
        Distance = distance;
        initLogFiles(Distance);
        STATE = 0;

        wifi_rssi_values = new int[RSSI];
        wifi_rtt_values = new long[NUM_PACKETS_RTT];
        wifi_rtt_start = new long[NUM_RTTS];
        received = new boolean[NUM_PACKETS_RTT];
        wifi_throughPut_delays = new long[NUM_ThroughPut];
        wifi_throughPut_recieved_bytes = new long[NUM_ThroughPut];


        throughPut_enabled = false;
        rtt_enabled = false;
        packet_loss_enabled = false;

        sequence = 0;
        turn = 0;
    }

    public void RSSI(View v){
        GetRSSI();
    }

    public void log_RTT(){
//        Toast.makeText(getApplicationContext(), "Logging RTTs", Toast.LENGTH_SHORT).show();
        debug("Logging rtts");
//        debug("Current Location: "+currentLocation);
//        log(wifi_RTT, "Current Location: "+currentLocation);

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        double average = 0;

//        Message  toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//        byte[] toastMSG_bytes =  ("Logging RTT").getBytes();
//        toastMSG.obj = toastMSG_bytes;
//        toastMSG.arg1 = toastMSG_bytes.length;
//        mainActivity.mHandler.sendMessage(toastMSG);

        for(int i = 0; i < NUM_PACKETS_RTT; i++){
//            log(wifi_RTT, ""+ wifi_rtt_values[i]);
            debug( ""+ wifi_rtt_values[i]);
            if(wifi_rtt_values[i] < min)
                min = wifi_rtt_values[i];
            if(wifi_rtt_values[i] > max)
                max = wifi_rtt_values[i];
            average += wifi_rtt_values[i];
        }

        average /= (double) NUM_PACKETS_RTT;

//        toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//        toastMSG_bytes =  ("RTT: "+min+"/"+average+"/"+max).getBytes();
//        toastMSG.obj = toastMSG_bytes;
//        toastMSG.arg1 = toastMSG_bytes.length;
//        mainActivity.mHandler.sendMessage(toastMSG);
        sendToCommandCenter("RTT: " + min + "/" + average + "/" + max);
        debug("RTT: " + min + "/" + average + "/" + max);
//        log(wifi_RTT, "RTT: "+min+"/"+average+"/"+max);

//        Toast.makeText(getApplicationContext(), "DONE Logging RTTs", Toast.LENGTH_SHORT).show();
    }

    public void ping(){
        if(pingThread != null){
            pingThread.cancel();
            pingThread = null;


        }
        else{
            pingThread = new Ping();
            pingThread.start();

        }
    }

    public void RTT(){
        if(rtt_enabled){
            if(clientThread != null){
                debug("KILLING RTT client");
                clientThread.cancel();
                clientThread = null;
            }
            else
                debug("KILLING RTT Server");
            serverThread.cancel();
            serverThread = null;
            rtt_enabled = false;

            if(!server)
                log_RTT();
        }
        else {
            turn = 0;
            wifi_rtt_values = new long[NUM_PACKETS_RTT];
            wifi_rtt_start = new long[NUM_RTTS];
            received = new boolean[NUM_PACKETS_RTT];

            if (server) {
                debug("Starting RTT Server");
                serverThread = new WifiServerThread(true);
                serverThread.start();

            } else {
                debug("Starting RTT Client");
                serverThread = new WifiServerThread(true);
                serverThread.start();

                if(DEVICE_ID == 8){
                    clientThread = new WifiClientThread("10.0.0.4", true);
                    clientThread.start();
                }
                else if(DEVICE_ID == 9){
                    clientThread = new WifiClientThread("10.0.0.3", true);
                    clientThread.start();
                }
            }
            rtt_enabled = true;

        }
    }


    public void log_packet_loss() {
        debug("Logging packet loss");
//        debug("Current Location: "+currentLocation);
//        log(wifi_LOSS, "Current Location: "+currentLocation);

        double Totalpackets = NUM_PACKETS_LOSS;
        double TotalpacketsLost = (NUM_PACKETS_LOSS - packetsReceived);
        debug("Total Packets:"+Totalpackets + " Packets Loss:" + TotalpacketsLost);

//        Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//        byte[] toastMSG_bytes =  ("Logging Packet Loss").getBytes();
//        toastMSG.obj = toastMSG_bytes;
//        toastMSG.arg1 = toastMSG_bytes.length;
//        mainActivity.mHandler.sendMessage(toastMSG);


        double percentage = TotalpacketsLost/Totalpackets;
        debug( Double.toString(percentage * 100)+"%");

//        toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//        toastMSG_bytes =  ("Packet Loss: "+(percentage*100)).getBytes();
//        toastMSG.obj = toastMSG_bytes;
//        toastMSG.arg1 = toastMSG_bytes.length;
//        mainActivity.mHandler.sendMessage(toastMSG);
//
//        toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//        toastMSG_bytes =  ("Discarded Packets: "+(packets_discarded)).getBytes();
//        toastMSG.obj = toastMSG_bytes;
//        toastMSG.arg1 = toastMSG_bytes.length;
//        mainActivity.mHandler.sendMessage(toastMSG);

        log(wifi_LOSS, Double.toString((percentage * 100)));
        debug("Discarded Packets: " + Double.toString(packets_discarded));


    }


    public void packet_loss(){

        if(packet_loss_enabled){
            if(loss_client_thread != null){
                debug("KILLING Packet loss client");
                sendToCommandCenter("KILLING Packet loss client");

                loss_client_thread.cancel();
                loss_client_thread = null;
            }
            if(loss_server_thread != null) {
                debug("KILLING Packet loss server");
                sendToCommandCenter("KILLING Packet loss server");
                loss_server_thread.cancel();
                loss_server_thread = null;
            }

            packet_loss_enabled = false;

            if (server)
                log_packet_loss();

        }
        else {
            turn = 0;
            packetsReceived = 0;

            if (server) {
                debug("Starting Packet loss server");
                sendToCommandCenter("Starting Packet loss server");
                loss_server_thread = new PacketLossServerThread();
                loss_server_thread.start();

            } else {
                debug("Starting Packet loss client");
                sendToCommandCenter("Starting Packet loss client");
                if(DEVICE_ID == 8){
                    loss_client_thread = new PacketLossClientThread("10.0.0.4");
                    loss_client_thread.start();
                }
                else if(DEVICE_ID == 9){
                    loss_client_thread = new PacketLossClientThread("10.0.0.3");
                    loss_client_thread.start();
                }
            }
            packet_loss_enabled = true;

        }
    }

    public void log_throughPut(){
        debug("Logging ThroughPut");
//        debug("Current Location: "+currentLocation);
//        log(wifi_THROUGHPUT, "Current Location: "+currentLocation);
//        Toast.makeText(getApplicationContext(), "Logging Throughput", Toast.LENGTH_SHORT).show();
//
//        Message  toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//        byte[] toastMSG_bytes =  ("Logging ThroughPut").getBytes();
//        toastMSG.obj = toastMSG_bytes;
//        toastMSG.arg1 = toastMSG_bytes.length;
//        mainActivity.mHandler.sendMessage(toastMSG);


        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double average = 0;

        for(int i = 0; i < NUM_ThroughPut; i++){
//            log(wifi_THROUGHPUT, wifi_throughPut_recieved_bytes[i] + " " + wifi_throughPut_delays[i]);
            debug(wifi_throughPut_recieved_bytes[i]+" "+wifi_throughPut_delays[i]);

//            toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//            toastMSG_bytes =  (wifi_throughPut_recieved_bytes[i] + " " + wifi_throughPut_delays[i]).getBytes();
//            toastMSG.obj = toastMSG_bytes;
//            toastMSG.arg1 = toastMSG_bytes.length;
//            mainActivity.mHandler.sendMessage(toastMSG);


            double sizeInKB =((double) wifi_throughPut_recieved_bytes[i])/ ((double)1024);
            double timeInSec =((double) wifi_throughPut_delays[i])/ ((double)1000);

            double Kb_in_sec = sizeInKB/timeInSec;
            if(Kb_in_sec < min)
                min = Kb_in_sec;
            if(Kb_in_sec > max)
                max = Kb_in_sec;

            average += Kb_in_sec;
        }

        average /= NUM_ThroughPut;

        min = Math.round(min);
        max = Math.round(max);
        average = Math.round(average);

//        toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
//        toastMSG_bytes =  ("ThroughPut: "+min+"/"+average+"/"+max).getBytes();
//        toastMSG.obj = toastMSG_bytes;
//        toastMSG.arg1 = toastMSG_bytes.length;
//        mainActivity.mHandler.sendMessage(toastMSG);

        debug("ThroughPut: " + min + "/" + average + "/" + max);
//        log(wifi_THROUGHPUT, "ThroughPut: "+min+"/"+average+"/"+max);
        sendToCommandCenter("ThroughPut: "+min+"/"+average+"/"+max);
//        Toast.makeText(getApplicationContext(), "DONE Logging Throughput", Toast.LENGTH_SHORT).show();

    }

    public void calc_throughput(){

        if(throughPut_enabled){
            handel.removeCallbacks(kill);
            if(ThroughPutClient != null){
                debug("KILLING tput client");
                sendToCommandCenter("KILLING tput client");
                ThroughPutClient.cancel();
                ThroughPutClient = null;
            }
            if(ThroughPutServer != null) {
                debug("KILLING tput server");
                sendToCommandCenter("KILLING tput server");
                ThroughPutServer.cancel();
                ThroughPutServer = null;
            }

            throughPut_enabled = false;

            if(server)
                log_throughPut();
        }
        else {
            turn = 0;
            succesfullTCPConnections = 0;
            wifi_throughPut_delays = new long[NUM_ThroughPut];
            wifi_throughPut_recieved_bytes = new long[NUM_ThroughPut];

            if (server) {
                debug("Starting tput server");
                sendToCommandCenter("Starting tput server");
                ThroughPutServer = new WifiTCPServerThread();
                ThroughPutServer.start();
            }
            else {
                debug("Starting tput client");
                sendToCommandCenter("Starting tput client");
                if (DEVICE_ID == 8) {
                    ThroughPutClient = new WifiTCPClientThread("10.0.0.4");
                    ThroughPutClient.start();
                } else if (DEVICE_ID == 9) {
                    ThroughPutClient = new WifiTCPClientThread("10.0.0.3");
                    ThroughPutClient.start();
                }
            }
            throughPut_enabled = true;

        }
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }




    public class CommandsThread extends Thread{

        ServerSocket serverSocket = null;
        boolean serverOn = true;
        Socket client = null;

        public CommandsThread() {

            try {
                serverSocket = new ServerSocket(5555);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void writeInstructions(OutputStream outputStream){
            String out = "*****WELCOME to the command center for Bluetooth Experiment*****\n" +
                    "You are the "+(server? "server" : "client")+"\n"+
                    "Usage: <command>\n" +
                    "command\tDescription\n" +
                    "new <Distance>\tStart a new Experiement with <Distance> as the new distance\n" +
                    "tput\tStart/terminate tput experiment\n" +
                    "rtt\tStart/terminate rtt experiment\n" +
                    "loss\tStart/terminate packet loss experiment\n"+
                    "rssi\tStart RSSI Experiment\n\r";
            try {
                outputStream.write(out.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while(serverOn){
                try {
                    client = serverSocket.accept();
                    Log.d(TAG, "Connected to: " + client.getInetAddress().getHostAddress());
                    Log.d(TAG, "Connected to Local Address: " + client.getLocalAddress().getHostAddress());

                    Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    byte[] toastMSG_bytes = ("Connected to: " + client.getInetAddress().getHostAddress()).getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);

                    commandCenterOutputStream= client.getOutputStream();
                    writeInstructions(commandCenterOutputStream);

                    InputStream inputStream = client.getInputStream();
                    int len;
                    byte[] buf = new byte[1024];
                    commandCenterOutputStream.write("Type in your command: ".getBytes());
                    while((len = inputStream.read(buf)) > 0){
                        String newCommand = new String(buf, 0, len);
                        debug("COMMAND: "+newCommand);
                        Scanner lineScanner = new Scanner(newCommand);

                        if(newCommand.contains("new")){
                            lineScanner.next();
                            int distance = lineScanner.nextInt();
                            new_Experiment(distance);
                        }
                        else if(newCommand.contains("tput")){
                            lineScanner.nextLine();
                            if(Distance == -1){
                                commandCenterOutputStream.write("Please type new <Distance> first\n\r".getBytes());
                            }else {

                                commandCenterOutputStream.write(("Running tput for " + (Distance) + " meters\n\r").getBytes());
                                calc_throughput();
                            }
                        }
                        else if(newCommand.contains("loss")){
                            lineScanner.nextLine();
                            if(Distance == -1){
                                commandCenterOutputStream.write("Please type new <Distance> first\n\r".getBytes());
                            }else {
                                commandCenterOutputStream.write(("Running packet loss for " + (Distance) + " meters\n\r").getBytes());
                                packet_loss();
                            }
                        }
                        else if(newCommand.contains("rtt")){
                            lineScanner.nextLine();
                            if(Distance == -1){
                                commandCenterOutputStream.write("Please type new <Distance> first\n\r".getBytes());
                            }else {
                                commandCenterOutputStream.write(("Running rtt for " + (Distance) + " meters\n\r").getBytes());
                                RTT();
                            }
                        }
                        else{
                            debug("Invalid command");
                            commandCenterOutputStream.write((lineScanner.nextLine() +
                                    "\tNOT SUPPORTED\n\r").getBytes());
                        }

                        commandCenterOutputStream.write("Type in your command: ".getBytes());
                    }
                }catch(IOException e){
                    cancel();
                    e.printStackTrace();
                }
            }
        }


        public void cancel(){
            try {
                serverOn = false;
                serverSocket.close();
                if(client!=null)
                    client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class PacketLossServerThread extends Thread{
        boolean serverOn = true;
        int[] packets = new int[NUM_PACKETS_LOSS];
        int[] discarded_packets = new int[NUM_PACKETS_LOSS];
        int duplicates = 0;

        public PacketLossServerThread() {
            try {
                PacketLossSocket = new DatagramSocket(7000);
                Log.d(TAG, "Buffer Size: " + PacketLossSocket.getReceiveBufferSize());
                PacketLossSocket.setReceiveBufferSize(1024*1024);
                Log.d(TAG, "Buffer Size: " + PacketLossSocket.getReceiveBufferSize());
                debug("Socket Created");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            sequence++;
            duplicates = 0;
            packets_discarded = 0;
            try {

                byte[] buff = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buff, buff.length);

                Message msg_1 = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                byte[] toastMSG = ("Experiment Number: "+sequence).getBytes();
                msg_1.obj = toastMSG;
                msg_1.arg1 = toastMSG.length;
                mainActivity.mHandler.sendMessage(msg_1);

                debug("Experiment Number " + sequence);
                sendToCommandCenter("Experiment Number "+sequence);
                int index = 0;
                while (serverOn) {


                    PacketLossSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());

                    int idx = Integer.parseInt(msg);
                    int sequence_num = idx%10;
                    idx /= 10;


                    debug("Got packet " + msg);
                    debug("Idx:"+idx+" "+" sequence:"+sequence_num);
                    if ((sequence_num == sequence)) {

                        if (packets[idx] == 0) {
                            if(packetsReceived == 0){
                                log(wifi_LOSS, "First packet");
                                sendToCommandCenter("First packet");
                            }
                            packets[idx] = idx;
                            packetsReceived++;
                        } else {
                            debug("WE GOT A DUP!!!");
                            duplicates++;
                            msg_1 = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                            toastMSG = ("WE GOT A DUP!!!").getBytes();
                            msg_1.obj = toastMSG;
                            msg_1.arg1 = toastMSG.length;
                            mainActivity.mHandler.sendMessage(msg_1);
                        }
                    }
                    else {

                        if (discarded_packets[idx] == 0) {
                            discarded_packets[idx] = idx;
                            packets_discarded++;
                        }

                        debug("Packet discarded");

                        msg_1 = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                        toastMSG = ("Packet discarded!!!").getBytes();
                        msg_1.obj = toastMSG;
                        msg_1.arg1 = toastMSG.length;
                        mainActivity.mHandler.sendMessage(msg_1);
                    }
                }

            }catch (IOException e) {
                e.printStackTrace();
                Message msg_1 = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                byte[] toastMSG = (e.getMessage()).getBytes();
                msg_1.obj = toastMSG;
                msg_1.arg1 = toastMSG.length;
                mainActivity.mHandler.sendMessage(msg_1);

                cancel();
            }




            Message msg_1 = mainActivity.mHandler.obtainMessage(TEXT_MSG);
            byte[] toastMSG = ("Duplicates: "+duplicates).getBytes();
            msg_1.obj = toastMSG;
            msg_1.arg1 = toastMSG.length;
            mainActivity.mHandler.sendMessage(msg_1);

            debug("Duplicates: "+duplicates);

        }
        public void cancel(){
            serverOn = false;
            PacketLossSocket.close();
        }
    }

    public class PacketLossClientThread extends Thread{

        String hostAddress;
        public PacketLossClientThread(String host) {
            hostAddress = host;
            try {
                PacketLossSocket = new DatagramSocket(7000);
                debug("Socket Created");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            sequence++;
            Message msg_1 = mainActivity.mHandler.obtainMessage(TEXT_MSG);
            byte[] toastMSG = ("Experiment Number: "+sequence).getBytes();
            msg_1.obj = toastMSG;
            msg_1.arg1 = toastMSG.length;
            mainActivity.mHandler.sendMessage(msg_1);


            /**
             * Listing 16-26: Creating a client Socket
             */
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int port = 7000;


            for (int idx = 0; idx < NUM_PACKETS_LOSS; idx++) {
                InetAddress group = null;
                try {
                    group = InetAddress.getByName(hostAddress);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                try {
                    byte[] buf = (""+ idx+""+sequence).getBytes();
                    DatagramPacket packet;
                    packet = new DatagramPacket(buf, buf.length, group, port);
                    debug("Sending " + (""+ idx) + " to " + packet.getAddress());
                    PacketLossSocket.send(packet);
//                    Thread.sleep(5);
                } catch (IOException e) {
                    e.printStackTrace();

                    msg_1 = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    toastMSG = (e.getMessage()).getBytes();
                    msg_1.obj = toastMSG;
                    msg_1.arg1 = toastMSG.length;
                    mainActivity.mHandler.sendMessage(msg_1);

                    cancel();
                }
//                catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            }

            sendToCommandCenter("Done with Packet loss");

            Message msg = mainActivity.mHandler.obtainMessage(TOAST_MSG);
            toastMSG =  ("Sent " + NUM_PACKETS_LOSS + " Packets").getBytes();
            msg.obj = toastMSG;
            msg.arg1 = toastMSG.length;
            mainActivity.mHandler.sendMessage(msg);

            debug("Sent " + NUM_PACKETS_LOSS + " Packets");
        }
        public void cancel(){

            PacketLossSocket.close();
        }
    }


    /**
     * Thread responsible of starting a listening wifi socket and accepting new coming connection
     */

    public class WifiServerThread extends Thread {


        boolean serverOn = true;
        String peer;
        DatagramSocket listenSocket = null;
        boolean getRTT = false;
        public WifiServerThread(boolean RTT) {
//            peer = host;
            getRTT = RTT;

            try {
                socket = new DatagramSocket(8000);

                listenSocket = new DatagramSocket(8001);
                debug("Socket Created");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            int index = 0;
            try {

                byte[] buff = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buff, buff.length);
                int packetReceived = 0;


                while (serverOn) {
                    if(server){
                        socket.receive(packet);

                        DatagramPacket Sendpacket = new DatagramPacket(packet.getData(), packet.getLength(), packet.getAddress(), 8001);
                        socket.send(Sendpacket);
                        debug("Sending ack to " + Sendpacket.getAddress());

                    }
                    else{

                        debug("This is my " + turn + " turn");
                        listenSocket.receive(packet);
                        long time = System.currentTimeMillis();

                        String msg = new String(packet.getData(), 0, packet.getLength());

                        Scanner scan = new Scanner(msg);
                        int idx = scan.nextInt();
                        index = idx+1;

                        Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                        byte[] toastMSG_bytes =  ("Received "+turn+" ACKS for "+(idx+1)+" packets sent").getBytes();
                        toastMSG.obj = toastMSG_bytes;
                        toastMSG.arg1 = toastMSG_bytes.length;
                        mainActivity.mHandler.sendMessage(toastMSG);

                        debug("Received " + turn + " ACKS for "+(idx+1)+" packets sent");

                        wifi_rtt_values[turn] = time - wifi_rtt_start[idx];
                        log(wifi_RTT, ""+wifi_rtt_values[turn]);
                        turn++;


                        if(turn == NUM_PACKETS_RTT){
                            debug("Got " + NUM_PACKETS_RTT + " RTTs....quiting");

                            toastMSG = mainActivity.mHandler.obtainMessage(TOAST_MSG);
                            toastMSG_bytes =  ("GOT " + turn + " ACKS").getBytes();
                            toastMSG.obj = toastMSG_bytes;
                            toastMSG.arg1 = toastMSG_bytes.length;
                            mainActivity.mHandler.sendMessage(toastMSG);

                            serverOn = false;
                        }
                        else if(idx >= NUM_RTTS){
                            toastMSG = mainActivity.mHandler.obtainMessage(TOAST_MSG);
                            toastMSG_bytes =  ("SENT " + idx + " RTTs").getBytes();
                            toastMSG.obj = toastMSG_bytes;
                            toastMSG.arg1 = toastMSG_bytes.length;
                            mainActivity.mHandler.sendMessage(toastMSG);

                            debug("SENT " + idx + " RTTs");
                            serverOn = false;
                        }
                    }
                }


            }
            catch (IOException e) {

                Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                byte[] toastMSG_bytes =  (e.getMessage()).getBytes();
                toastMSG.obj = toastMSG_bytes;
                toastMSG.arg1 = toastMSG_bytes.length;
                mainActivity.mHandler.sendMessage(toastMSG);

            }



            Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
            byte[] toastMSG_bytes =  ("Done with RTT").getBytes();
            toastMSG.obj = toastMSG_bytes;
            toastMSG.arg1 = toastMSG_bytes.length;
            mainActivity.mHandler.sendMessage(toastMSG);
            sendToCommandCenter("Done with RTT");

            debug("Received " + turn+" ACKS for "+index+" packets sent");
//            log(wifi_RTT, "Received "+turn+" ACKS for "+index+" packets sent");

        }

        public void cancel(){
            serverOn = false;
            socket.close();
            listenSocket.close();
        }
    }


    /**
     * Thread responsible of connecting to a specific host address and acquiring a communication
     * socket with that host
     */
    public class WifiClientThread extends Thread {
        String hostAddress;
        DataInputStream inputStream;
        DataOutputStream outputStream;
        int device_Id;

        boolean senderON = true;

        boolean get_RTT = false;

        public WifiClientThread(String host, boolean RTT) {
            get_RTT = RTT;
            hostAddress = host;

        }

        @Override
        public void run() {
            /**
             * Listing 16-26: Creating a client Socket
             */
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int port = 8000;


            int index = 0;
            while(senderON) {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    debug("This is my "+turn+" turn");
                    InetAddress group = InetAddress.getByName(hostAddress);
                    String message = "" + index;

                    byte[] buf = message.getBytes();
                    DatagramPacket packet;
                    packet = new DatagramPacket(buf, buf.length, group, port);

                    if((turn < NUM_PACKETS_RTT) && (index < NUM_RTTS)) {

                        Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                        byte[] toastMSG_bytes =  ("This is my "+(index+1)+" packet").getBytes();
                        toastMSG.obj = toastMSG_bytes;
                        toastMSG.arg1 = toastMSG_bytes.length;
                        mainActivity.mHandler.sendMessage(toastMSG);

                        debug("Sending " + message + " to " + packet.getAddress());

                        wifi_rtt_start[index++] = System.currentTimeMillis();
                        socket.send(packet);
                    }

                } catch (IOException e) {
                    Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    byte[] toastMSG_bytes =  (e.getMessage()).getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);


                }
            }


            // TODO Start Receiving Messages
        }
        public void cancel(){
            senderON = false;
            socket.close();
        }
    }

    Runnable kill = new Runnable() {
        @Override
        public void run() {
//            turn++;
            if(ThroughPutServer != null) {
                ThroughPutServer.closeSocket();
            }
            if(ThroughPutClient != null) {
                ThroughPutClient.closeSocket();

            }


        }
    };



    public class WifiTCPClientThread extends Thread {
        String hostAddress;
        DataInputStream inputStream;
        DataOutputStream outputStream;
        Socket socket = null;
        boolean keepConnecting = false;

        public WifiTCPClientThread(
                String host) {
            hostAddress = host;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            /**
             * Listing 16-26: Creating a client Socket
             */
            int timeout = 500;
            int port = 6999;
            int arraySize = 1024*1024*10;
            keepConnecting = true;
            long duration = 10000; //10 Seconds


            while((succesfullTCPConnections < NUM_ThroughPut) && (turn < NUM_ThroughPut_TRIALS)  && (keepConnecting)) {
                try {
                    debug("Connecting to " + hostAddress);

                    turn++;

                    Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    byte[] toastMSG_bytes = ("Connecting to " + hostAddress+" this is "+turn+" turn.").getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);
                    sendToCommandCenter("Connecting to " + hostAddress+" this is "+turn+" trial");
                    socket = new Socket();

                    socket.bind(null);
                    socket.connect((new InetSocketAddress(hostAddress, port)), timeout);

                    handel.removeCallbacks(kill);
                    succesfullTCPConnections++;

                    toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    toastMSG_bytes = ("ThroughPut: "+succesfullTCPConnections+" successful trials out of "+turn+" trials").getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);



//                    FileInputStream inputStream = new FileInputStream((mainActivity.rootDir + "40M_20141111_140119.txt"));
                    FileInputStream inputStream = new FileInputStream((mainActivity.rootDir + "5MB"));
                    long timeStart = System.currentTimeMillis();
                    int size = inputStream.available();
                    byte buf[] = new byte[size];

                    debug("File is " + size + " bytes");
                    OutputStream outputStream = socket.getOutputStream();

                    handel.postDelayed(kill, 4000);

                    outputStream.write(buf, 0, buf.length);

                    debug("Done writing");
                    socket.close();
                } catch (IOException e) {

                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());

                    handel.removeCallbacks(kill);
//                    handel.postDelayed(kill, 2000);

                    Message msg = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    byte[] toastMSG = e.getMessage().getBytes();
                    msg.obj = toastMSG;
                    msg.arg1 = toastMSG.length;
                    mainActivity.mHandler.sendMessage(msg);

                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
            byte[] toastMSG_bytes = ("Done with Throughput\n" +
                    "ThroughPut: "+succesfullTCPConnections+" successful trials out of "+turn+" trials").getBytes();
            toastMSG.obj = toastMSG_bytes;
            toastMSG.arg1 = toastMSG_bytes.length;
            mainActivity.mHandler.sendMessage(toastMSG);
            debug("Done with Throughput\n" +
                    "ThroughPut: " + succesfullTCPConnections + " successful trials out of " + turn + " trials");
            sendToCommandCenter("Done with Throughput\n" +
                    "ThroughPut: " + succesfullTCPConnections + " successful trials out of " + turn + " trials");

            toastMSG = mainActivity.mHandler.obtainMessage(TOAST_MSG_SHORT);
            toastMSG_bytes = ("Done with throughput").getBytes();
            toastMSG.obj = toastMSG_bytes;
            toastMSG.arg1 = toastMSG_bytes.length;
            mainActivity.mHandler.sendMessage(toastMSG);

            log(wifi_THROUGHPUT, "ThroughPut: "+succesfullTCPConnections+" successful trials out of "+turn+" trials");
            // TODO Start Receiving Messages
        }
        public void closeSocket(){
            try {

                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        public void cancel(){
            try {
                keepConnecting = false;
                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class WifiTCPServerThread extends Thread {
        ServerSocket serverSocket = null;
        boolean serverOn = true;
        Socket client = null;
        long timeStart = 0;
        long timeEnd = 0;
        public WifiTCPServerThread() {

            try {
                serverSocket = new ServerSocket(6999);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            int index = 0;
            int receivedBytes = 0;
            while (serverOn) {
                try {
                    Log.d(TAG, "Server Thread");

                    timeStart = -1;
                    timeEnd = -1;
                    receivedBytes = 0;

                    client = serverSocket.accept();
                    Log.d(TAG, "Connected to: " + client.getInetAddress().getHostAddress());
                    Log.d(TAG, "Connected to Local Address: " + client.getLocalAddress().getHostAddress());

                    Message  toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    byte[] toastMSG_bytes = ( "Connected to: " + client.getInetAddress().getHostAddress()).getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);

                    debug("This is the connection number " + (index + 1));
                    sendToCommandCenter("This is the connection number "+(index+1));

                    InputStream inputStream = client.getInputStream();
                    int len = 0;
                    int size = 5242880; //5 MB
//                    int size =  41943040; //20 MB
                    byte[]buf = new byte[size];

                    handel.removeCallbacks(kill);
                    handel.postDelayed(kill, 2000);

                    timeStart = System.currentTimeMillis();
                    while((len = inputStream.read(buf)) > 0){
                        receivedBytes += len;
                    }

                    debug("Received "+receivedBytes+" bytes");
                    debug("TimeEnd after loop");
                    timeEnd = System.currentTimeMillis();
                    wifi_throughPut_delays[index] = (timeEnd - timeStart);
                    wifi_throughPut_recieved_bytes[index] = receivedBytes;

                    log(wifi_THROUGHPUT, wifi_throughPut_recieved_bytes[index] + "\t" + wifi_throughPut_delays[index]);


                    toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    toastMSG_bytes = ("Received "+receivedBytes+" bytes").getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);



                    index++;

                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());

                    Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    byte[] toastMSG_bytes = e.getMessage().getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);


                    if(index < NUM_ThroughPut) {
                        debug("Received " + receivedBytes + " bytes");
                        debug("TimeEnd IO Exception");

                        toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                        toastMSG_bytes = ("Received "+receivedBytes+" bytes").getBytes();
                        toastMSG.obj = toastMSG_bytes;
                        toastMSG.arg1 = toastMSG_bytes.length;
                        mainActivity.mHandler.sendMessage(toastMSG);


                        timeEnd = System.currentTimeMillis();
                        wifi_throughPut_delays[index] = (timeEnd - timeStart);
                        wifi_throughPut_recieved_bytes[index] = receivedBytes;
                        log(wifi_THROUGHPUT, wifi_throughPut_recieved_bytes[index] + "\t" + wifi_throughPut_delays[index]);
                        index++;
                    }


                }
            }

//            log_throughPut();
        }

        public void closeSocket(){
            try {

                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void cancel(){
            try {
                serverOn = false;
                serverSocket.close();
                if(client!=null)
                    client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public class Ping extends Thread {
        boolean keepRunning = true;
        Process su = null;
        DataOutputStream stdin = null;
        public Ping() {
            try {
                su = Runtime.getRuntime().exec(new String[]{"su", "-c", "system/bin/sh"});
                stdin = new DataOutputStream(su.getOutputStream());
                keepRunning = true;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

        }

        @Override
        public void run() {
            while (keepRunning) {


                Log.d(TAG, "Pinging");

                try {

                    String otherDeviceIpAdress = "";
                    if(DEVICE_ID == 9)
                        otherDeviceIpAdress = device_ip_adresses[7];
                    else
                        otherDeviceIpAdress = device_ip_adresses[8];
                    stdin.writeBytes("ping "+otherDeviceIpAdress+" &\n");
                    InputStream stdout = su.getInputStream();
                    byte[] buffer = new byte[1024];
                    int read;



                    //read method will wait forever if there is nothing in the stream
                    //so we need to read it in another way than while((read=stdout.read(buffer))>0)
                    while (keepRunning) {
//                        if (stdout.available() <= 0) {
//                            break;
//                        }
                        read = stdout.read(buffer);
                        String out = new String(buffer, 0, read);

                        Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                        byte[] toastMSG_bytes = out.getBytes();
                        toastMSG.obj = toastMSG_bytes;
                        toastMSG.arg1 = toastMSG_bytes.length;
                        mainActivity.mHandler.sendMessage(toastMSG);
//                        out += new String(buffer, 0, read);
//
////                    Log.d(TAG, out);
////                    Log.d(TAG, "\n");
//                        if (read < 1024) {
//                            //we have read everything
//                            break;
//                        }
                    }
                } catch (IOException e) {

                }
            }
        }

        public void cancel(){
            keepRunning = false;
            try {
//                stdin.writeBytes(x03);
//                char ctrlC = '\u0003';
                stdin.writeBytes("killall ping\n");

                InputStream stdout = su.getInputStream();
                byte[] buffer = new byte[1024];
                int read;

                while (true) {
                    if (stdout.available() <= 0) {
                        break;
                    }
                    read = stdout.read(buffer);
                    String out = new String(buffer, 0, read);

                    out += new String(buffer, 0, read);

                    Message toastMSG = mainActivity.mHandler.obtainMessage(TEXT_MSG);
                    byte[] toastMSG_bytes = out.getBytes();
                    toastMSG.obj = toastMSG_bytes;
                    toastMSG.arg1 = toastMSG_bytes.length;
                    mainActivity.mHandler.sendMessage(toastMSG);
//                    Log.d(TAG, out);
//                    Log.d(TAG, "\n");
                    if (read < 1024) {
                        //we have read everything
                        break;
                    }
                }

//                su.destroy();
//                stdin.writeByte(ctrlC);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
