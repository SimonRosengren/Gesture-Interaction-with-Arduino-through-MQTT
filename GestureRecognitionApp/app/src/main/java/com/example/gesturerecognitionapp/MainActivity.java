package com.example.gesturerecognitionapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.lazy.KStar;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.datagenerators.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private Button mUpButton;
    private Button mDownButton;
    private Button mLeftButton;
    private Button mRightButton;

    private MqttAndroidClient mqttAndroidClient;

    BufferedReader reader;

    private MqttAndroidClient client;

    private String BROKER_URL = "tcp://m14.cloudmqtt.com:16303";

    BluetoothAdapter mBluetoothAdapter;
    ConnectedThread mConnectedThread;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //WEKA CLASSIFIER
        InputStream isTrainData = getResources().openRawResource(R.raw.traindata);
        reader = new BufferedReader(new InputStreamReader(isTrainData));



        //TRAIN
        Instances TrainData = null;
        try {
            TrainData = new Instances(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // setting class attribute
        TrainData.setClassIndex(TrainData.numAttributes() - 1);

        //TEST
        InputStream isTestData = getResources().openRawResource(R.raw.testdata);
        reader = new BufferedReader(new InputStreamReader(isTestData));
        Instances TestData = null;
        try {
            TestData = new Instances(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // setting class attribute
        TestData.setClassIndex(TrainData.numAttributes() - 1);

        //J48 mTree = new J48();
        MultilayerPerceptron mTree = new MultilayerPerceptron();
        try {
            mTree.buildClassifier(TrainData);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int classIndex = TrainData.numAttributes() -1;
        Instances labeledData = new Instances(TestData);
        for (int i = 0; i < TestData.numInstances(); i++) {
            double clsLabel = 0;
            try {
                clsLabel = mTree.classifyInstance(TestData.instance(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
            labeledData.instance(i).setClassValue(clsLabel);
            Log.d("Labeled attributes", labeledData.instance(i).attribute(classIndex).value((int)clsLabel));
        }

        File path = getApplicationContext().getExternalFilesDir(null);
        File file = new File(path, "labeleddata.arff");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try{
            stream.write(labeledData.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        FileInputStream fiLabeledData = null;
        try {
            fiLabeledData = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int contentTst;
        try {
            while ((contentTst = fiLabeledData.read()) != -1)
            {
                char a = (char) contentTst;
                char b = a;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        //ConnectToBluetooth();

        client = getMqttClient(getApplicationContext(), BROKER_URL, "androidkt");

        mUpButton = (Button)findViewById(R.id.Up_Button);
        mDownButton = (Button)findViewById(R.id.Down_Button);
        mLeftButton = (Button)findViewById(R.id.Left_Button);
        mRightButton = (Button)findViewById(R.id.Right_Button);


        BindButtons();


    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }
        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {

            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                    for(int i = begin; i < bytes; i++) {
                        if(buffer[i] == "#".getBytes()[0]) {
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if(i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            switch(msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin, end);
                    break;
            }
        }
    };


    private void ConnectToBluetooth()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device doesn't support Bluetooth
        }

        if (!mBluetoothAdapter.isEnabled()) {
            //Prompt to turn on BT
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        BluetoothDevice mDevice = null;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mDevice = device;
            }
        }

        ConnectThread mConnectThread = new ConnectThread(mDevice);
        mConnectThread.start();

    }

    private MqttConnectOptions getMqttConnectionOption() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setAutomaticReconnect(true);
        //mqttConnectOptions.setWill(Constants.PUBLISH_TOPIC, "I am going offline".getBytes(), 1, true);
        mqttConnectOptions.setUserName("knftdzxy");
        mqttConnectOptions.setPassword("GkpGmebv6Tk7".toCharArray());
        return mqttConnectOptions;
    }
    private DisconnectedBufferOptions getDisconnectedBufferOptions() {
        DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
        disconnectedBufferOptions.setBufferEnabled(true);
        disconnectedBufferOptions.setBufferSize(100);
        disconnectedBufferOptions.setPersistBuffer(true);
        disconnectedBufferOptions.setDeleteOldestMessages(false);
        return disconnectedBufferOptions;
    }

    public MqttAndroidClient getMqttClient(Context context, String brokerUrl, String clientId) {
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), brokerUrl, clientId);
        try {
            IMqttToken token = mqttAndroidClient.connect(getMqttConnectionOption());
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    mqttAndroidClient.setBufferOpts(getDisconnectedBufferOptions());
                    Log.d("MainActivity", "Success!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d("MainActivity", "Failure " + exception.toString());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
        return mqttAndroidClient;
    }

    public void publishMessage(@NonNull MqttAndroidClient client,
                               @NonNull String msg, int qos, @NonNull String topic)
            throws MqttException, UnsupportedEncodingException {
        byte[] encodedPayload = new byte[0];
        encodedPayload = msg.getBytes("UTF-8");
        MqttMessage message = new MqttMessage(encodedPayload);
        message.setId(5866);
        message.setRetained(true);
        message.setQos(qos);
        client.publish(topic, message);
    }

    private void BindButtons()
    {
        mUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    publishMessage(client, "UP", 0, "Gesture");
                } catch (MqttException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        });
        mDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    publishMessage(client, "DOWN", 0, "Gesture");
                } catch (MqttException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        });
        mLeftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    publishMessage(client, "LEFT", 0, "Gesture");
                } catch (MqttException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        });
        mRightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    publishMessage(client, "RIGHT", 0, "Gesture");
                } catch (MqttException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        });
    }



}
