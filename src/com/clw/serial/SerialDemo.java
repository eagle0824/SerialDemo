
package com.clw.serial;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ArrayAdapter;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.clw.serial.SerialService.Callback;
import com.clw.serial.SerialService.SerialBinder;

public class SerialDemo extends Activity implements ServiceConnection, OnCheckedChangeListener {

    private Context mContext;
    private SerialService mSerialService;
    private Switch mSerialSwitch;
    private String[] mDevices;
    private String[] mBaudrates;
    private Spinner mDevicesSpinner;
    private Spinner mBaudSpinner;
    private EditText mSendDataEditText;
    private TextView mSendTextView;
    private TextView mReceivedTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.main);
        initViews();
        doBindService();
    }

    private void initViews() {
        mSerialSwitch = (Switch) findViewById(R.id.serial_switch);
        mDevicesSpinner = (Spinner) findViewById(R.id.devices);
        mBaudSpinner = (Spinner) findViewById(R.id.baudrates);
        mBaudrates = getResources().getStringArray(R.array.baudrates);
        mBaudSpinner.setAdapter(new CustomApdater(mContext, android.R.layout.simple_dropdown_item_1line, mBaudrates));
        mSerialSwitch.setOnCheckedChangeListener(this);
        mSendDataEditText = (EditText) findViewById(R.id.send_ed);
        mSendTextView = (TextView) findViewById(R.id.send_data);
        mReceivedTextView = (TextView) findViewById(R.id.received_data);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    public void onSendClick(View view) {
        if (mSerialService != null && mSerialService.isSerialOpend()) {
            CharSequence data = mSendDataEditText.getText();
            if (TextUtils.isEmpty(data)) {
                return;
            }
            mSerialService.sendMessage(data.toString());
        } else {
            Toast.makeText(mContext, "Please open serial!", Toast.LENGTH_SHORT).show();
        }
    }

    public void onSendClear(View view) {
        mSendTextView.setText("");
    }

    public void onReceiveClear(View view) {
        mReceivedTextView.setText("");
    }

    private void doBindService() {
        Intent intent = new Intent(this, SerialService.class);
        mContext.startService(intent);
        mContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    private void doUnbindService() {
        if(mSerialService!=null && mSerialService.isSerialOpend()){
            mSerialService.closeSerial();
        }
        mContext.unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mSerialService = ((SerialBinder) service).getService();
        mSerialService.registerCallback(mCallback);
        mSerialSwitch.setSelected(mSerialService.isSerialOpend());
        mDevices = mSerialService.getSerialPorts();
        mDevicesSpinner.setAdapter(new CustomApdater(mContext,
                android.R.layout.simple_dropdown_item_1line, mDevices));
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mSerialService.unregisterCallback(mCallback);
        mSerialService = null;
    }

    private Callback mCallback = new Callback() {

        @Override
        public void onDataSend(String data) {
            CharSequence oldData = mSendTextView.getText();
            StringBuffer sb = new StringBuffer();
            if(!TextUtils.isEmpty(oldData)){
                sb.append(oldData);
                sb.append("\n");
            }
            sb.append(data);
            mSendTextView.setText(sb.toString());
        }

        @Override
        public void onDataReceived(String data) {
            CharSequence oldData = mReceivedTextView.getText();
            StringBuffer sb = new StringBuffer();
            if(!TextUtils.isEmpty(oldData)){
                sb.append(oldData);
                sb.append("\n");
            }
            sb.append(data);
            mReceivedTextView.setText(sb.toString());
        }
    };

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            String device = (String) mDevicesSpinner.getSelectedItem();
            String baudrate = (String) mBaudSpinner.getSelectedItem();
            if (TextUtils.isEmpty(device)) {
                Toast.makeText(mContext, "Device is null! Please selected device first!",
                        Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(baudrate)) {
                Toast.makeText(mContext, "Baudrate is null! Please selected baudrate first!",
                        Toast.LENGTH_SHORT).show();
            } else {
                mSerialService.openSerial(device, baudrate);
            }
        } else {
            mSerialService.closeSerial();
        }
    }

    private class CustomApdater extends ArrayAdapter<String> implements SpinnerAdapter {

        public CustomApdater(Context context, int resource, String[] datas) {
            super(context, resource, datas);
        }
    }
}
