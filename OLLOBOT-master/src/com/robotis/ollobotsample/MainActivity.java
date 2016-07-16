/*
 * Copyright (C) 2016 ROBOTIS OLLOBOT Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.robotis.ollobotsample;

import java.io.BufferedReader;
import java.lang.Math.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;




import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.robotis.ollobotsample.bluetooth.BluetoothManager;
import com.robotis.ollobotsample.service.BTConnectionService;
import com.robotis.ollobotsample.utils.Constants;
import com.robotis.ollobotsample.utils.Dynamixel;
import com.robotis.ollobotsample.utils.OLLOBOT;

public class MainActivity extends Activity implements OnClickListener {

    // Debugging
    private static final String TAG = "ROBOTIS.MainActivity";
    
	// Context, System
	private BTConnectionService mService;
	private ActivityHandler mActivityHandler;
	
	private ImageView mImageBT = null;
	private TextView mTextStatus = null;

	private TextView mTvInstructionPacket = null;
	private TextView mTvStatusPacket = null;
	
	private boolean mIsServiceBound = false;
		
	/*****************************************************
	 *	 Overrided methods
	 ******************************************************/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Firebase.setAndroidContext(this);
		final Firebase myFirebaseRef = new Firebase("https://troll3333.firebaseio.com/");
		myFirebaseRef.child("left").addValueEventListener(new ValueEventListener() {
			  @Override
			  public void onDataChange(DataSnapshot snapshot) {
//			    System.out.println();  //prints "Do you have data? You'll love Firebase."
//				Log.d("firebasel",""+snapshot.getValue());
			    byte[] i1 = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_1_MOTOR_SPEED, safeLongToInt((long)snapshot.getValue())*-1);
//				byte[] i1 = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.GREEN_LED, 0);
			    myFirebaseRef.child("leftspeed").setValue(safeLongToInt((long)snapshot.getValue()));
			    mService.sendMessageToRemote(i1);
				mTvInstructionPacket.setText(Dynamixel.packetToString(i1));
				mTvInstructionPacket.setText("");
				executeCommand("GREEN_LED", 0);
			  }
			  @Override public void onCancelled(FirebaseError error) { }
			});
		
		myFirebaseRef.child("right").addValueEventListener(new ValueEventListener() {
			  @Override
			  public void onDataChange(DataSnapshot snapshot) {
//			    System.out.println();  //prints "Do you have data? You'll love Firebase."
				  
//				Log.d("firebaser",""+safeLongToInt((long)snapshot.getValue()));
			    byte[] i1 = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_2_MOTOR_SPEED, safeLongToInt((long)snapshot.getValue()));
			    myFirebaseRef.child("rightspeed").setValue(safeLongToInt((long)snapshot.getValue()));
			    mService.sendMessageToRemote(i1);
				mTvInstructionPacket.setText(Dynamixel.packetToString(i1));
				mTvInstructionPacket.setText("");
//				executeCommand("PORT_2_MOTOR_SPEED", 100);
				executeCommand("GREEN_LED", 1);
			    
			  }
			  @Override public void onCancelled(FirebaseError error) { }
			});

		mActivityHandler = new ActivityHandler();
		
		setContentView(R.layout.activity_main);

		// Setup views
		mImageBT = (ImageView) findViewById(R.id.status_title);
		mImageBT.setImageDrawable(getResources().getDrawable(android.R.drawable.presence_invisible));
		mTextStatus = (TextView) findViewById(R.id.status_text);
		mTextStatus.setText(getResources().getString(R.string.bt_state_init));
		
		// Do data initialization after service started and binded
		doStartService();
		
		mTvInstructionPacket = (TextView) findViewById(R.id.tv_instruction_packet);
		mTvStatusPacket = (TextView) findViewById(R.id.tv_status_packet);
		
		// Setup button for H/W test
		Button btnLedOn = (Button) findViewById(R.id.btn_led_on);
		Button btnLedOff = (Button) findViewById(R.id.btn_led_off);
		Button btnLedStatusf = (Button) findViewById(R.id.btn_led_status);
		
		btnLedOn.setOnClickListener(this);
		btnLedOff.setOnClickListener(this);
		btnLedStatusf.setOnClickListener(this);
		
		// Setup IFTTT info.
		final EditText etKey = (EditText) findViewById(R.id.et_ifttt_key);
		etKey.setText(getSharedPreferences(Constants.PREFERENCE_NAME, MODE_PRIVATE).getString(Constants.PREFERENCE_IFTTT_KEY, ""));
		etKey.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				getSharedPreferences(Constants.PREFERENCE_NAME, MODE_PRIVATE).edit().putString(Constants.PREFERENCE_IFTTT_KEY, s.toString()).commit();
			}
		});
		
		final EditText etEvent = (EditText) findViewById(R.id.et_ifttt_event);
		etEvent.setText(getSharedPreferences(Constants.PREFERENCE_NAME, MODE_PRIVATE).getString(Constants.PREFERENCE_IFTTT_EVENT, ""));
		etEvent.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				getSharedPreferences(Constants.PREFERENCE_NAME, MODE_PRIVATE).edit().putString(Constants.PREFERENCE_IFTTT_EVENT, s.toString()).commit();
			}
		});
		
		Button btnEvent = (Button) findViewById(R.id.btn_ifttt_event); 
		btnEvent.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
			}
		});
		
	}

	@Override
	public synchronized void onStart() {
		super.onStart();
	}
	
	@Override
	public synchronized void onPause() {
		super.onPause();
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}
	
	@Override
	public void onDestroy() {
		if (mService != null && mService.getBtStatus() != BluetoothManager.STATE_CONNECTED) {
			doStopService();
		}
		
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_scan:
			mService.disconnectDevice();
			doScan();
			return true;
		}
		return false;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig){
		super.onConfigurationChanged(newConfig);
	}
	
	/*****************************************************
	 *	Private methods
	 ******************************************************/
	public static int safeLongToInt(long l) {
	    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
	        throw new IllegalArgumentException
	            (l + " cannot be cast to int without changing its value.");
	    }
	    return (int) l;
	}
	/**
	 * Service connection
	 */
	private ServiceConnection mServiceConn = new ServiceConnection() {
		
		public void onServiceConnected(ComponentName className, IBinder binder) {
			Log.d(TAG, "# Activity - Service connected");
			
			mService = ((BTConnectionService.ServiceBinder) binder).getService();
			
			// Activity couldn't work with mService until connections are made
			// So initialize parameters and settings here. Do not initialize while running onCreate()
			initialize();
		}

		public void onServiceDisconnected(ComponentName className) {
			Log.d(TAG, "# Activity - Service disconnected");
			mService = null;
		}
	};
	
	private class MyAsyncTask extends AsyncTask<String, String, String>{
		  @Override
		  protected String doInBackground(String... params) {
			// TODO Auto-generated method stub
			GetRequests();
			return null;
		  }
		  public void GetRequests()
		  {
				HttpClient client = new DefaultHttpClient();
				HttpResponse httpResponse;
				try {
					Log.d("HTTP", "sent");
					System.out.println("hey");
					httpResponse = client.execute(new HttpGet("http://a1a0d55c.ngrok.io/command"));
					Log.d("HTTP", "execute");
					int statusCode = httpResponse.getStatusLine().getStatusCode();
					if (statusCode == HttpStatus.SC_OK) {
						Log.d("GetRqeust","i'm here");
						HttpEntity entity = httpResponse.getEntity();
						final String response = EntityUtils.toString(entity, "UTF-8");
					    runOnUiThread(new Runnable() {
					        @Override
					        public void run() {
					        	Log.d("GetRequest", response);
					        	executeCommand(response.substring(0, response.indexOf(" ")), Integer.parseInt(response.substring(response.indexOf(" ")+1)));
					       }
					   });
					}
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
		  }
	}
	
	/** 
	 * start the get requests to receive commands for ollobot
	 *
	 */
	

	
	private void doGetRequests() {
		    final Handler handler = new Handler();
		    Timer timer = new Timer();
		    TimerTask doAsynchronousTask = new TimerTask() {       
		        @Override
		        public void run() {
		            handler.post(new Runnable() {
		                public void run() {       
		                    try {
		                        MyAsyncTask performBackgroundTask = new MyAsyncTask();
		                        // PerformBackgroundTask this class is the class that extends AsynchTask 
		                        performBackgroundTask.execute();
		                    } catch (Exception e) {
		                        // TODO Auto-generated catch block
		                    }
		                }
		            });
		        }
		    };
		    timer.schedule(doAsynchronousTask, 0, 2000); //execute in every 50000 ms
	}
	
	/** 
	 * does some stuff with the ollobot
	 * @param command
	 */
	private void executeCommand(String command, int value) {
		byte[] packet = null;
		Log.d("executeCommand", command + value);
		
		if (command.equalsIgnoreCase("GREEN_LED")) {
			packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.GREEN_LED, value);
		}
		else if (command.equalsIgnoreCase("BLUE_LED")) {
			packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.BLUE_LED, value);
		}
		else if (command.equalsIgnoreCase("CONTROLLER_X_AXIS_VALUE")) {
			packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.CONTROLLER_X_AXIS_VALUE, value);
		}
		else if (command.equalsIgnoreCase("CONTROLLER_Y_AXIS_VALUE")) {
			packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.CONTROLLER_Y_AXIS_VALUE, value);
		}
		else if (command.equalsIgnoreCase("PORT_1_SERVO_MODE")) {
			packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.PORT_1_SERVO_MODE, value);
		}
		else if (command.equalsIgnoreCase("PORT_2_SERVO_MODE")) {
			packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.PORT_2_SERVO_MODE, value);
		}
		else if (command.equalsIgnoreCase("PORT_1_MOTOR_SPEED")) {
			packet = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_1_MOTOR_SPEED, value);
		}
		else if (command.equalsIgnoreCase("PORT_2_MOTOR_SPEED")) {
			packet = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_2_MOTOR_SPEED, value);
		}
		
		else if (command.equalsIgnoreCase("PORT_1_SERVO_POSITION")) {
			Log.d("executeCommand", ""+value);
			packet = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_1_SERVO_POSITION, value);
		}
		else if (command.equalsIgnoreCase("PORT_2_SERVO_POSITION")) {
			packet = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_2_SERVO_POSITION, value);
		}
		else if (command.equalsIgnoreCase("BOTH")) {
			
			byte[] i1 = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_1_MOTOR_SPEED, value * -1);
			mService.sendMessageToRemote(i1);
			mTvInstructionPacket.setText(Dynamixel.packetToString(i1));
			
			byte[] i2 = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_2_MOTOR_SPEED, value);
			mService.sendMessageToRemote(i2);
			mTvInstructionPacket.setText(Dynamixel.packetToString(i2));
			
			packet = null;
			
			
		}
		else {
			packet = null;
		}
		
//		packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.GREEN_LED, 1);
//		packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.PORT_1_MOTOR_SPEED, 500);
	

		if (packet != null) {
			mService.sendMessageToRemote(packet);
			Log.d("executeCommand", "not null"+value);
			mTvInstructionPacket.setText(Dynamixel.packetToString(packet));
		} else {
			mTvInstructionPacket.setText("");
		}
		mTvStatusPacket.setText("");
	}
	
	/**
	 * Start service if it's not running
	 */
	private void doStartService() {
//		Log.d(TAG, "# Activity - doStartService()");
		startService(new Intent(this, BTConnectionService.class));
		bindService(new Intent(this, BTConnectionService.class), mServiceConn, Context.BIND_AUTO_CREATE);
		mIsServiceBound = true;
	}
	
	/**
	 * Stop the service
	 */
	private void doStopService() {
//		Log.d(TAG, "# Activity - doStopService()");
		mService.finalizeService();
		if (mIsServiceBound) {
			unbindService(mServiceConn);
		}
		stopService(new Intent(this, BTConnectionService.class));
		mIsServiceBound = false;
	}
	
	/**
	 * Initialization / Finalization
	 */
	private void initialize() {
//		Log.d(TAG, "# Activity - initialize()");
		mService.setupService(mActivityHandler);
		
		// If BT is not on, request that it be enabled.
		// RetroWatchService.setupBT() will then be called during onActivityResult
		if(!mService.isBluetoothEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT);
		}
	}
	
	/**
	 * Launch the DeviceListActivity to see devices and do scan
	 */
	private void doScan() {
		Intent intent = new Intent(this, DeviceListActivity.class);
		startActivityForResult(intent, Constants.REQUEST_CONNECT_DEVICE);
	}
	
	/*****************************************************
	 *	Public classes
	 ******************************************************/
	
	/**
	 * Receives result from external activity
	 */
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
//		Log.d(TAG, "onActivityResult " + resultCode);
		
		switch(requestCode) {
		case Constants.REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Attempt to connect to the device
				if(address != null && mService != null) {
					mService.connectDevice(address);
				}
			}
			break;
			
		case Constants.REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up a BT session
				mService.setupBT();
			} else {
				// User did not enable Bluetooth or an error occured
				Log.e(TAG, "BT is not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
			}
			break;
		}	// End of switch(requestCode)
	}
	
	
	
	/*****************************************************
	 *	Handler, Callback, Sub-classes
	 ******************************************************/
	
	public class ActivityHandler extends Handler {
		@Override
		public void handleMessage(Message msg) 
		{
			switch(msg.what) {
			// Receives BT state messages from service 
			// and updates BT state UI
			case Constants.MESSAGE_BT_STATE_INITIALIZED:
				mTextStatus.setText(getResources().getString(R.string.bt_title) + ": " + 
						getResources().getString(R.string.bt_state_init));
				mImageBT.setImageDrawable(getResources().getDrawable(android.R.drawable.presence_invisible));
				break;
			case Constants.MESSAGE_BT_STATE_LISTENING:
				mTextStatus.setText(getResources().getString(R.string.bt_title) + ": " + 
						getResources().getString(R.string.bt_state_wait));
				mImageBT.setImageDrawable(getResources().getDrawable(android.R.drawable.presence_invisible));
				break;
			case Constants.MESSAGE_BT_STATE_CONNECTING:
				mTextStatus.setText(getResources().getString(R.string.bt_title) + ": " + 
						getResources().getString(R.string.bt_state_connect));
				mImageBT.setImageDrawable(getResources().getDrawable(android.R.drawable.presence_away));
				break;
			case Constants.MESSAGE_BT_STATE_CONNECTED:
				if(mService != null) {
					String deviceName = mService.getDeviceName();
					if(deviceName != null) {
						mTextStatus.setText(getResources().getString(R.string.bt_title) + ": " + 
								getResources().getString(R.string.bt_state_connected) + " " + deviceName);
						mImageBT.setImageDrawable(getResources().getDrawable(android.R.drawable.presence_online));
					}
				}
				break;
			case Constants.MESSAGE_BT_STATE_ERROR:
				mTextStatus.setText(getResources().getString(R.string.bt_state_error));
				mImageBT.setImageDrawable(getResources().getDrawable(android.R.drawable.presence_busy));
				break;
			
			// BT Command status
			case Constants.MESSAGE_CMD_ERROR_NOT_CONNECTED:
				mTextStatus.setText(getResources().getString(R.string.bt_cmd_sending_error));
				mImageBT.setImageDrawable(getResources().getDrawable(android.R.drawable.presence_busy));
				break;
				
				
				
				
				
			case Constants.MESSAGE_STATUS_PACKET:
				String packet = Dynamixel.packetToString((byte[]) msg.obj);
				
				if (packet.length() <= 11) {
					//write packet
					mTvStatusPacket.setText(packet);
				} else {
					//read packet : RED color for values (param1...N). 
					SpannableStringBuilder sp = new SpannableStringBuilder(packet);
					// "27" to "packet.length() - 6" is range of values(param1...N) that changed to string format by Dynamixel.packetToString();  
					// msg.obj[9] to msg.obj[(msg.obj.length() - 1) - 2] (for crc 2 bytes) is range of value(param1...N) for raw byte array.
					sp.setSpan(new ForegroundColorSpan(Color.RED), 27, packet.length() - 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					mTvStatusPacket.append(sp);
				}
			default:
				break;
			}
			
			super.handleMessage(msg);
		}
	}	// End of class ActivityHandler




	@Override
	public void onClick(View v) {
		byte[] packet = null;
		switch (v.getId()) {
			case R.id.btn_led_on:
				packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.BLUE_LED, 1);
				doGetRequests();
//				packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.PORT_1_SERVO_POSITION, 1280);
//				packet = Dynamixel.packetWriteWord(OLLOBOT.ID, OLLOBOT.Address.PORT_1_MOTOR_SPEED, 0);
				break;
			case R.id.btn_led_off:
				packet = Dynamixel.packetWriteByte(OLLOBOT.ID, OLLOBOT.Address.BLUE_LED, 0);
				break;
			case R.id.btn_led_status:
				packet = Dynamixel.packetRead(OLLOBOT.ID, OLLOBOT.Address.BLUE_LED, OLLOBOT.Length.BLUE_LED);
				break;
			default:
				break;
		}
		
		if (packet != null) {
			mService.sendMessageToRemote(packet);
			mTvInstructionPacket.setText(Dynamixel.packetToString(packet));
		} else {
			mTvInstructionPacket.setText("");
		}
		mTvStatusPacket.setText("");
	}
}
