
package com.clw.serial;

import android.R.integer;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.hardware.SerialPort;
import android.hardware.SerialManager;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SerialService extends Service {

    private static final String TAG = SerialService.class.getSimpleName();

    private SerialBinder mSerialBinder;
    private SerialManager mSerialManager;
    private volatile SerialPort mSerialPort = null;

    private Object lock = new Object();

    private HandlerThread mHandlerThread;
    private BgHandler mBgHandler;
    private ByteBuffer mReadBuffer;
    private static final int READ_BUFFER_SIZE = 256;
    private Thread mReadThread;
    private Callback mCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        logD(TAG, "SerialService onCreate!");
        mSerialManager = (SerialManager) getSystemService(Context.SERIAL_SERVICE);
        mHandlerThread = new HandlerThread("serial_thread");
        mHandlerThread.start();
        mBgHandler = new BgHandler(mHandlerThread.getLooper());
        mReadBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    public String[] getSerialPorts() {
        logD(TAG, "getSerialPorts  : " + Arrays.toString(mSerialManager.getSerialPorts()));
        return mSerialManager.getSerialPorts();
    }

    public boolean isSerialOpend() {
        return mSerialPort != null;
    }

    public void openSerial(String name, String speed) {
        logD(TAG, "openSerial name : " + name + " speed : " + speed);
        if (isSerialOpend() || TextUtils.isEmpty(name)) {
            return;
        }
        mBgHandler.obtainMessage(EVENT_OPEN_SERIAL, Integer.valueOf(speed), 0, name).sendToTarget();
    }

    public void closeSerial() {
        if (isSerialOpend()) {
            mBgHandler.sendEmptyMessage(EVENT_CLOASE_SERAIL);
        }
    }

    public void sendMessage(String text) {
        mBgHandler.obtainMessage(EVENT_SEND_MESSAGES, text).sendToTarget();
    }

    private class ReadThread extends Thread {

        public ReadThread() {
            super("serial_read_thread");
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                if (isSerialOpend()) {
                    int size;
                    try {
                        size = mSerialPort.read(mReadBuffer);
                        if (size > 0) {
                            byte[] data = new byte[size];
                            System.arraycopy(mReadBuffer, 0, data, 0, size);
                            mHandler.obtainMessage(EVENT_MESSAGE_RECEIVED, data).sendToTarget();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        }
    }

    private static final int EVENT_OPEN_SERIAL = 1;
    private static final int EVENT_CLOASE_SERAIL = 2;
    private static final int EVENT_SEND_MESSAGES = 3;

    private class BgHandler extends Handler {
        public BgHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_OPEN_SERIAL: {
                    if (msg.obj != null) {
                        doOpenSerial((String) msg.obj, msg.arg1);
                    }
                }
                    break;
                case EVENT_CLOASE_SERAIL: {
                    doCloseSerial();
                }
                    break;
                case EVENT_SEND_MESSAGES: {
                    if (msg.obj != null) {
                        doSendMessage(((String) msg.obj).getBytes());
                    }
                }
                    break;
            }
        }
    }

    public static final int EVENT_MESSAGE_RECEIVED = 1;
    public static final int EVENT_MESSAGE_SENDED = 2;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_MESSAGE_RECEIVED:
                    if(msg.obj != null){
                        String data = new String((byte[])msg.obj);
                        if(mCallback != null){
                            mCallback.onDataReceived(data);
                        }
                    }
                    break;
                case EVENT_MESSAGE_SENDED:
                    if(msg.obj != null){
                        String data = new String((byte[])msg.obj);
                        if(mCallback != null){
                            mCallback.onDataSend(data);
                        }
                    }
                    break;
            }
        }

    };

    private void doCloseSerial() {
        if (isSerialOpend()) {
            try {
                mSerialPort.close();
            } catch (IOException e) {
                logE(TAG, e.getMessage());
            } finally {
                mSerialPort = null;
                mReadThread.interrupt();
                mReadThread = null;
                logD(TAG, "close serial successful!");
            }

        }
    }

    private void doOpenSerial(String name, int speed) {
        try {
            try {
                mSerialPort = mSerialManager.openSerialPort(name, speed);
            } catch (IOException e) {
                logE(TAG, e.getMessage());
                mSerialPort = null;
            }
        } finally {
            if (isSerialOpend()) {
                mReadThread = new ReadThread();
                mReadThread.start();
                logD(TAG, "open serial successful!");
            }
        }
    }

    private void doSendMessage(byte[] datas) {
        try {
            if (isSerialOpend()) {
                logD(TAG, "send data :" + new String(datas));
                mSerialPort.write(ByteBuffer.wrap(datas), datas.length);
                mHandler.obtainMessage(EVENT_MESSAGE_SENDED, datas).sendToTarget();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (mSerialBinder == null) {
            mSerialBinder = new SerialBinder();
        }
        return mSerialBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        mHandlerThread.quit();
        super.onDestroy();
    }

    public void registerCallback(Callback callback) {
        mCallback = callback;
    }

    public void unregisterCallback(Callback callback) {
        if (mCallback == callback) {
            mCallback = null;
        }
    }

    public interface Callback {
        public void onDataSend(String data);

        public void onDataReceived(String data);
    }

    public class SerialBinder extends Binder {

        public SerialService getService() {
            return SerialService.this;
        }
    }

    public void logD(String tag, String msg) {
        Log.d(tag, msg);
    }

    public void logE(String tag, String msg) {
        Log.e(tag, msg);
    }
}
