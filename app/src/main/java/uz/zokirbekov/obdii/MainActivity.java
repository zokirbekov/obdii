package uz.zokirbekov.obdii;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.enums.ObdProtocols;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private boolean isError = false;

    Button btnConnect;
    Button btnCommand;

    BluetoothSocket socket;
    BluetoothAdapter btAdapter;

    LinearLayout content_layout;

    String deviceAddress;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        content_layout = findViewById(R.id.info_content);

        btnConnect = findViewById(R.id.button_connect);
        btnCommand = findViewById(R.id.button_command);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions();
                ArrayList<String> deviceStrs = new ArrayList<String>();
                final ArrayList<String> devices = new ArrayList<String>();

                Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
                if (pairedDevices.size() > 0)
                {
                    for (BluetoothDevice device : pairedDevices)
                    {
                        deviceStrs.add(device.getName() + "\n" + device.getAddress());
                        devices.add(device.getAddress());
                    }
                }

// show list
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.select_dialog_singlechoice,
                        deviceStrs.toArray(new String[deviceStrs.size()]));

                alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                        deviceAddress = devices.get(position);
                        new ConnectAsync().execute();
                    }
                });

                alertDialog.setTitle("Choose Bluetooth device");
                alertDialog.show();
            }
        });
        btnCommand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    commands();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, 1);
        }
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 2);
        }
    }
    private void requestPermissions()
    {
        //if (ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.BLUETOOTH},1);
    }
    private void connect() throws IOException
    {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        socket.connect();
    }
    private void initObdApi() throws IOException,InterruptedException
    {
        new EchoOffCommand().run(socket.getInputStream(), socket.getOutputStream());
        new LineFeedOffCommand().run(socket.getInputStream(), socket.getOutputStream());
        new TimeoutCommand(20000).run(socket.getInputStream(), socket.getOutputStream());
        new SelectProtocolCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());


    }
    private void commands() throws IOException,InterruptedException
    {
        RPMCommand engineRpmCommand = new RPMCommand();
        SpeedCommand speedCommand = new SpeedCommand();
        while (!Thread.currentThread().isInterrupted())
        {
            engineRpmCommand.run(socket.getInputStream(), socket.getOutputStream());
            speedCommand.run(socket.getInputStream(), socket.getOutputStream());
            content_layout.addView(newInfo(engineRpmCommand.getClass().getName(),engineRpmCommand.getFormattedResult()));
            content_layout.addView(newInfo(speedCommand.getClass().getName(),speedCommand.getFormattedResult()));
            // TODO handle commands result
            Log.d("MAIN", "RPM: " + engineRpmCommand.getFormattedResult());
            Log.d("MAIN", "Speed: " + speedCommand.getFormattedResult());
        }
    }

    private class ConnectAsync extends AsyncTask<Void,Void,Void>
    {

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                connect();
                btnCommand.setEnabled(true);
            } catch (final IOException e) {
                isError = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Error in connection : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

                e.printStackTrace();

            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            Toast.makeText(MainActivity.this, "Connecting...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!isError) {
                Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                try {
                    initObdApi();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Error in initialize OBD Api : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    private View newInfo(String key, String value)
    {
        View v = View.inflate(this,R.layout.layout_info,content_layout);
        ((TextView)v.findViewById(R.id.key)).setText(key);
        ((TextView)v.findViewById(R.id.value)).setText(value);
        return v;
    }

}
