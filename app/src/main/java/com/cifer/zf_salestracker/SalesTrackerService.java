package com.cifer.zf_salestracker;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.telephony.TelephonyManagerEx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

//xiao 1113


public class SalesTrackerService extends Service {
	public static final int CMCCID = 1;
	public static final int CUCOMID = 2;
	public static final int GEMINI_SIM_1 = 0;
	public static final int GEMINI_SIM_2 = 1;
	public static final String GEMINI_SIM_ID_KEY = "simId";
	public static int[] GEMSIM = { GEMINI_SIM_1, GEMINI_SIM_2 };
	/**
	 * SIM card state: Unknown. Signifies that the SIM is in transition between
	 * states. For example, when the user inputs the SIM pin under PIN_REQUIRED
	 * state, a query for sim status returns this state before turning to
	 * SIM_STATE_READY.
	 */
	public static final int SIM_STATE_UNKNOWN = 0;
	/** SIM card state: no SIM card is available in the device */
	public static final int SIM_STATE_ABSENT = 1;
	/** SIM card state: Locked: requires the user's SIM PIN to unlock */
	public static final int SIM_STATE_PIN_REQUIRED = 2;
	/** SIM card state: Locked: requires the user's SIM PUK to unlock */
	public static final int SIM_STATE_PUK_REQUIRED = 3;
	/** SIM card state: Locked: requires a network PIN to unlock */
	public static final int SIM_STATE_NETWORK_LOCKED = 4;
	/** SIM card state: Ready */
	public static final int SIM_STATE_READY = 5;
	private boolean isSendMsg = false;
	private static final String Dcontent = "Thank you for choosing QMobile, please press OK to get registered with us for better services, warranty support and product updates. (Standard SMS charges will be applied)";
	private static final String operatorMcc[] = { "470", "460"};//460 is for testing in China.

	private boolean isMcc = false;
	private int TimerInterval = 7200;
	private String smsAddress = "54242";
	private boolean TimerStart = false;
	private AlarmManager am = null;
	private long now = 0;
	private String msgBody = "";
	private boolean mSendFail = false;
	private boolean mRegister = false;
	//xiao 1113
	private static final String INTERNAL_FILE_PATH = "/system/private/";
	private static final String ST_FILE_NAME = "STNumber";
	private String STstate = "fail";
	private String salescontent ;
	private int Toastsimid;

	public static final String TIMER_RECEIVED_ACTION = "android.intent.action.TL_TIMER_RECEIVED";

	private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(WYWTEST, "BroadcastReceiver onReceive");
			if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
				Log.d(WYWTEST, "BroadcastReceiver AIRPLANE_MODE_CHANGED");
				boolean airplaneModeON = intent.getBooleanExtra("state", false);
				Log.d(WYWTEST,
						"BroadcastReceiver AIRPLANE_MODE_CHANGED airplaneModeON = "
								+ airplaneModeON);
				if (airplaneModeON) {
					stopService();
				}
			}
		}
	};

	@Override
	public void onCreate() {
		Log.v(WYWTEST, "onCreate");
		Log.d(WYWTEST, "onCreate");
		mSmsManager = SmsManagerEx.getDefault();
		// mSmsManager = SmsManager.getDefault();
		mTelephonyManager = (TelephonyManager) this.getSystemService(Service.TELEPHONY_SERVICE);
		registerObserver();
	}

	private void registerObserver(){
		if(!mRegister){
			if (FeatureOption.GEMINI_SUPPORT == true) {
				if (TelephonyManagerEx.getDefault().getSimState(            //getDefault()获取TelephonyManagerEx实例
						PhoneConstants.SIM_ID_1) != TelephonyManager.SIM_STATE_ABSENT   //有无卡状态判断
						|| TelephonyManagerEx.getDefault().getSimState(
						PhoneConstants.SIM_ID_2) != TelephonyManager.SIM_STATE_ABSENT) {
					TelephonyManagerEx.getDefault().listen(mPhoneStateListener,
							PhoneStateListener.LISTEN_SERVICE_STATE,
							PhoneConstants.SIM_ID_1);
					TelephonyManagerEx.getDefault().listen(
							mPhoneStateListenerGemini,
							PhoneStateListener.LISTEN_SERVICE_STATE,
							PhoneConstants.SIM_ID_2);

				} else {
					Log.d(WYWTEST, "There is no sim card");
					Log.e(TAG, "There is no sim card");
				}
				getSimCardMatchCustomizedGemini();

			} else {
				getSimCardMatchCustomized();
				if (mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT) {
					((TelephonyManager) this
							.getSystemService(Context.TELEPHONY_SERVICE)).listen(
							mPhoneStateListener,
							PhoneStateListener.LISTEN_SERVICE_STATE);
				}
			}
			IntentFilter itFilter = new IntentFilter();
			itFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
			registerReceiver(mIntentReceiver, itFilter);
			getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SALES_TIMER_INTERVAL),
					false, mTimerIntervalObserver);
			getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SALES_MOBILE_NUMBER),
					false, mMobileNumberObserver);
			mRegister = true;
		}
	}

	private void unregisterObserver(){
		if(mRegister){
			if (FeatureOption.GEMINI_SUPPORT) {

				if (mPhoneStateListener != null) {
					Log.i(WYWTEST, "unRegist service state listener for sim1.");
					TelephonyManagerEx.getDefault().listen(mPhoneStateListener,
							PhoneStateListener.LISTEN_NONE,
							PhoneConstants.SIM_ID_1);
					mPhoneStateListener = null;
				}

				if (mPhoneStateListenerGemini != null) {
					Log.i(WYWTEST,
							"unRegist service state listener gemini for sim2.");
					TelephonyManagerEx.getDefault().listen(
							mPhoneStateListenerGemini,
							PhoneStateListener.LISTEN_NONE,
							PhoneConstants.SIM_ID_2);
					mPhoneStateListenerGemini = null;
				}

			} else {
				if (mPhoneStateListener != null) {
					Log.i(WYWTEST, "unRegist service state listener for sim.");
					mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
					mPhoneStateListener = null;
				}
			}
			if (am != null) {
				Intent intent = new Intent(SalesTrackerService.this, SalesTrackerReceiver.class);
				intent.setAction(TIMER_RECEIVED_ACTION);
				PendingIntent sender = PendingIntent.getBroadcast(SalesTrackerService.this, 0, intent, 0);
				am.cancel(sender);
			}
			unregisterReceiver(mIntentReceiver);
			getContentResolver().unregisterContentObserver(mTimerIntervalObserver);
			getContentResolver().unregisterContentObserver(mMobileNumberObserver);

			mRegister = false;
		}
	}

	public void onStop() {
		Log.v(WYWTEST, "Stopdestory");

	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		unregisterObserver();
		Log.v(WYWTEST, "Service destroy");
		super.onDestroy();
	}

	protected void stopService() {
		Log.i(WYWTEST, "stop service.");
		unregisterObserver();
		TimerStart = false;
		cancelAlarm();
		//stopSelf();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		if (intent == null || intent.getAction() == null) {
			Log.d(WYWTEST, "returned");
			return;
		}
		String action = intent.getAction();
		Log.d(WYWTEST, "|------onStart-start--------| action = "+action);
		if (action.equals("SIM_STATE_CHANGED")) {
			Log.d(WYWTEST, "onStart SIM_STATE_CHANGED ----TimerStart = " + TimerStart
					+ "---isMcc = " + isMcc);
			if (isMcc && !TimerStart) {
				TimerStart = true;
				setAlarm(TIMER_RECEIVED_ACTION);
				showAlertDialog("SMS SalesTracker", Dcontent);
			}
		} else if (action.equals(TIMER_RECEIVED_ACTION)) {
			long time = System.currentTimeMillis();
			Log.d(WYWTEST, "timer received time " + time + " now " + now);
			long timedis = time - now;
			Log.d(WYWTEST, "timer received timedis " + timedis + " interval " + getTimeInterval());
			if (timedis >  (1000*30 + getTimeInterval())) {
				setAlarm(TIMER_RECEIVED_ACTION);
				TimerStart = true;
				Log.d(WYWTEST, "TIMER_RECEIVED_ACTION resend now = "+now);
			} else {
				timeout = true;
				Log.d(WYWTEST, "TIMER_RECEIVED_ACTION  showAlertDialog ");
				showAlertDialog("SMS SalesTracker", Dcontent);
			}

		}else if(action.equals("ACTION_TIME_CHANGED")){
        	/*Log.i(WYWTEST, "ACTION_TIME_CHANGED isMcc = "+isMcc+" TimerStart = "+TimerStart);
        	if(isMcc && TimerStart){
        		cancelAlarm();
        		setAlarm(TIMER_RECEIVED_ACTION);
        		Log.i(WYWTEST, "ACTION_TIME_CHANGED reset alarm");
        	}*/
		}
		else if(action.equals("RESEND_SALES_MESSEGAES")){
			if (isMcc) {
				mSendFail = true;
				setAlarm(TIMER_RECEIVED_ACTION);
				TimerStart = true;
			}
		}
		else if(action.equals("SHOW_FACTORYMODE_TOAST")){   //xiao service toast
			String msgstate = getSNNumberFromFile();
			Log.d("xiao111","ACTION_DIALER_BROADCAST ==msgstate =="+msgstate);
			DialerShowToast(msgstate);
		}

	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			timeout = true;
			showAlertDialog("SMS SalesTracker",Dcontent);
		}

	};

	private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
		@Override
		public void onServiceStateChanged(ServiceState serviceState) {
			if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
				Log.i(WYWTEST, "ServiceState.STATE_IN_SERVIC S" + TimerStart
						+ isMcc);
				if (TimerStart == false) {
					getSimCardMatchCustomized();
					if ((isMcc == true) && (TimerStart == false)) {
						setAlarm(TIMER_RECEIVED_ACTION);
						TimerStart = true;
					}
				}

			}
		}
	};

	private PhoneStateListener mPhoneStateListenerGemini = new PhoneStateListener() {
		@Override
		public void onServiceStateChanged(ServiceState serviceState) {
			Log.i(WYWTEST, "serviceState.getState()" + serviceState.getState());
			if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
				Log.i(WYWTEST, "ServiceState.STATE_IN_SERVIC D" + " TimerStart = " + TimerStart + " isMcc = " + isMcc);
				if (TimerStart == false) {
					getSimCardMatchCustomizedGemini();
					if ((isMcc == true) && (TimerStart == false)) {

						/*
						 * mtimerTask = new TimerTask() {
						 *
						 * @Override public void run() { Message message = new
						 * Message(); handler.sendMessage(message); } };
						 * searchNetDelay = TimerInterval*1000;
						 * mTimer.schedule(mtimerTask, searchNetDelay);
						 */
						setAlarm(TIMER_RECEIVED_ACTION);
						Log.i(WYWTEST, "onServiceStateChanged start timer");
						TimerStart = true;
					}
				}
			}
		}
	};

	private int CustomOperatorID;
	// private int simCount = 0;
	private Boolean timeout = false;
	private int runCount = 0;
	private Boolean serviceAlive = true;

	private TelephonyManager mTelephonyManager;
	// private GeminiSmsManager mGeminiSmsManager;
	private SmsManagerEx mSmsManager;
	// private SmsManager mSmsManager;

	private Timer mTimer = new Timer();
	private TimerTask mtimerTask;
	private long searchNetDelay = 300000;
	private String TAG = "DM/SalesTracker";
	private String WYWTEST = "wywtest";

	private void getSimCardMatchCustomized() {

		int simState = mTelephonyManager.getSimState();
		if (SIM_STATE_READY == simState) {
			Log.i(WYWTEST, "there is a sim card is ready the operator is "
					+ mTelephonyManager.getSimOperator());
			String mcc = mTelephonyManager.getSimOperator().substring(0, 3);
			Log.i(WYWTEST, "there is a sim card is ready the operator is "
					+ mcc);
			for (int j = 0; j < operatorMcc.length; j++) {
				if (operatorMcc[j] != null && operatorMcc[j].equals(mcc)) {
					isMcc = true;
					break;
				}
			}
			Log.i(WYWTEST, "isMcc =  "+isMcc);
		}
		return;
	}

	private void getSimCardMatchCustomizedGemini() {

		for (int i = 0; i < 2; i++) {
			// get sim card state
			int simState = TelephonyManagerEx.getDefault().getSimState(GEMSIM[i]);
			if (SIM_STATE_READY == simState) {
				String op = TelephonyManagerEx.getDefault().getSimOperator(GEMSIM[i]);
				Log.i(WYWTEST, "getSimCardMatchCustomizedGemini there is a sim card is ready the operator is "  + i + " op = " + op + " length = " + op.length());
				if ((op != null) && (op.length() > 3)) {
					Log.i(WYWTEST, "getSimCardMatchCustomizedGemini there is a sim card is ready the operator is " + op.substring(0, 3));
					for (int j = 0; j < operatorMcc.length; j++) {
						if (operatorMcc[j] != null
								&& operatorMcc[j].equals(TelephonyManagerEx.getDefault().getSimOperator(GEMSIM[i]).substring(0, 3))) {
							isMcc = true;

							smsAddress = getSimAddress(op);
							Log.i(WYWTEST, "getSimCardMatchCustomizedGemini smsAddress " + smsAddress);
							return;
						}
					}
				}
			}
		}
		return;
	}

	private void setAlarm(String action){
		now = System.currentTimeMillis();
		long time  = now + getTimeInterval();
		Log.d(WYWTEST, "setAlarm now = "+now);
		am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(SalesTrackerService.this, SalesTrackerReceiver.class);
		intent.setAction(action);
		PendingIntent sender = PendingIntent.getBroadcast(SalesTrackerService.this, 0, intent, 0);

		am.setExact(AlarmManager.RTC_WAKEUP, time, sender);
	}

	//发送短信内容 111111111
	String getSalesSmsContent(int simId) {
		TelephonyManager mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		GsmCellLocation location  =(GsmCellLocation)mTelephonyManager.getCellLocation();
		String cellid = String.valueOf(location.getCid());
		String lac = String.valueOf(location.getLac());
		//MAKER:Build.BRAND  imei：getDeviceId  cell id :getCid()  LAC:getLac()
		salescontent = Build.BRAND + " " +"IMEI" +" "+ Build.MODEL+" " + TelephonyManagerEx.getDefault().getDeviceId(0) + " " + TelephonyManagerEx.getDefault().getDeviceId(1) + " "+lac+" "+cellid;
		Toastsimid = simId;

		Log.i(WYWTEST, "getSalesSmsContent content = " + salescontent);
		return salescontent;
	}

	private void sendTrackerMessage() {
		int simId = -1;
		if (FeatureOption.GEMINI_SUPPORT == true) {
			//sim1 与 sim2 发送逻辑	： sim1 优先，1,2同时存在,仅发送一次
			if (TelephonyManagerEx.getDefault().getSimState(
					PhoneConstants.SIM_ID_1) == TelephonyManager.SIM_STATE_READY) {
				Log.e(WYWTEST, "sim1 card is ready");
				simId = PhoneConstants.SIM_ID_1;
			} else if (TelephonyManagerEx.getDefault().getSimState(
					PhoneConstants.SIM_ID_2) == TelephonyManager.SIM_STATE_READY) {
				Log.e(WYWTEST, "sim2 card is ready");
				//xiao
				if(simId == -1){
					simId = PhoneConstants.SIM_ID_2;
				}

			} else {
				Log.e(WYWTEST, "There is no sim card");
				return;
			}
			String smsContent = getSalesSmsContent(simId);
			PendingIntent mPendingIntent = getSendPendingIntent(simId);
			Log.d(WYWTEST, "sendTrackerMessage smsContent = " + smsContent +" smsAddress = "+smsAddress);
			// send message
			try {
				//smsAddress：收件人地址，smsContent：内容，mPendingIntent:成功或者失败的信息报告通过PendingIntent来广播
				//simId:区分sim卡1卡2 ?
				mSmsManager.sendTextMessage(smsAddress, null, smsContent, mPendingIntent, null, simId);


			} catch (Exception e) {
				String ststate = "fail";
				writeSTNumberToFile(ststate);
				Log.d(WYWTEST,"message send failed e "+e);
			}
		}

	}


	private PendingIntent getSendPendingIntent(int simId) {
		Log.i(WYWTEST, "get Pending Intent begin");
		String IMSI = null;
		Intent intent = new Intent();
		intent.setAction("android.intent.action.TL_REGISTER_SMS_RECEIVED");
		if (FeatureOption.GEMINI_SUPPORT == true) {
			Log.i(WYWTEST, "Gemini, simId = " + simId);
			intent.putExtra("SimID", simId);
			IMSI = TelephonyManagerEx.getDefault().getSubscriberId(simId);
			Log.i(WYWTEST, "Gemini IMSI = " + IMSI);
			intent.putExtra("mIMSI", IMSI);
		} else {
			IMSI = mTelephonyManager.getSubscriberId();
			Log.i(WYWTEST, "Gemini IMSI = " + IMSI);
			intent.putExtra("mIMSI", IMSI);
		}
		PendingIntent sendPendingIntent = PendingIntent.getBroadcast(this, 0,intent, PendingIntent.FLAG_UPDATE_CURRENT);

		return sendPendingIntent;
	}

	DialogInterface.OnKeyListener sOnKeyListener = new DialogInterface.OnKeyListener() {
		public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
			boolean isReturn = false;
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				isReturn = true;
			}
			return isReturn;
		}
	};

	private ContentObserver mTimerIntervalObserver = new ContentObserver(
			new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			if(enableSalesTracker()){
				//resetAlarm();
				Log.d(WYWTEST, "mTimerIntervalObserver, SalesTracker enabled! Reset alarm__111111");
			}else{
				Log.d(WYWTEST, "mTimerIntervalObserver, SalesTracker disabled! ");
			}


		}
	};

//	private ContentObserver mSalesEnableObserver = new ContentObserver(new Handler()) {
//		@Override
//		public void onChange(boolean selfChange) {
//			super.onChange(selfChange);
//			if(enableSalesTracker()){
//				resetAlarm();
//				Log.d(WYWTEST, " mSalesEnableObserver, SalesTracker enabled! Rreset alarm__222222");
//			}else{
//				if (am != null) {
//					Intent intent = new Intent(SalesTrackerService.this,
//							SalesTrackerReceiver.class);
//					intent.setAction(TIMER_RECEIVED_ACTION);
//					PendingIntent sender = PendingIntent.getBroadcast(
//							SalesTrackerService.this, 0, intent, 0);
//					am.cancel(sender);
//				}
//				Log.d(WYWTEST, "mSalesEnableObserver, SalesTracker disabled!  Cancel Alarm");
//			}
//		}
//	};

	private ContentObserver mMobileNumberObserver = new ContentObserver(
			new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			if(enableSalesTracker()){
				Log.d(WYWTEST, "mMobileNumberObserver, SalesTracker enabled! Rreset alarm__333333");
			}else{
				Log.d(WYWTEST, "mMobileNumberObserver,SalesTracker disabled!");
			}
		}
	};

	private boolean enableSalesTracker(){
		return Settings.System.getInt(getContentResolver(), Settings.System.SALES_MODE_ON, 1) ==1;
	}

	private void showAlertDialog(String title, String message) {
		STstate = getSNNumberFromFile();
		if(STstate.equals("fail")){
			Log.i(WYWTEST, "Show alert dialog");

			Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(title).setMessage(message);
			builder.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Log.d(WYWTEST, "Click OK in alert dialog");
							//按下ok 发送短信
							sendSmsMessage();

						}
					});
			builder.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Log.d(WYWTEST, "Click Cancel in alert dialog");

						}
					});
			builder.setOnKeyListener(sOnKeyListener);
			AlertDialog dialog = builder.create();
			dialog.getWindow()
					.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
			dialog.setCanceledOnTouchOutside(false);
			dialog.show();
		}else {
			//已经统计过：
			stopService();
			onDestroy();
		}
	}

	private void sendSmsMessage(){
		Log.d(WYWTEST, "sendSmsMessage");
		sendTrackerMessage();

		TimerStart = false;
		stopService();
	}

	//设置再次发送时间 2222222
	private int getTimeInterval(){
		int time = 0;
		if(mSendFail){
			time = 3*60*60*1000; //3小时 3*60*100;//

		}else{
			time = (Settings.System.getInt(this.getContentResolver(), Settings.System.SALES_TIMER_INTERVAL, 3*60*60)*1000);
			//time = (Settings.System.getInt(this.getContentResolver(), Settings.System.SALES_TIMER_INTERVAL, 3*60)*1000);
		}
		Log.d(WYWTEST, "getTimeInterval time = "+time);
		return time;
	}

	private String getSimAddress(String operatorStr){
		String address = Settings.System.getString(getContentResolver(), Settings.System.SALES_MOBILE_NUMBER);

		Log.i(TAG, "getSimAddress operatorStr = "+operatorStr);
		//是否设置号码
		if(address != null && !address.isEmpty()){
			return address;
		}

		String defaultAddress = "8646";//发送地址号码 33333333
		return defaultAddress;
	}

	private void cancelAlarm(){
		am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(SalesTrackerService.this, SalesTrackerReceiver.class);
		intent.setAction(TIMER_RECEIVED_ACTION);
		PendingIntent sender = PendingIntent.getBroadcast(SalesTrackerService.this, 0, intent, 0);
		am.cancel(sender);
		Log.i(TAG, "cancelAlarm");
	}
	//xiao 1103
	private void writeSTNumberToFile(String saletracker) {
		byte buf[] = saletracker.getBytes();

		try {
			File file = new File(INTERNAL_FILE_PATH, ST_FILE_NAME);
			FileOutputStream fos = new FileOutputStream(file, false);
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fos);
			bufferedOutputStream.write(buf, 0, buf.length);
			bufferedOutputStream.flush();
			bufferedOutputStream.close();
			fos.close();
			Process p = Runtime.getRuntime().exec("chmod 644 " + INTERNAL_FILE_PATH + ST_FILE_NAME);
			Log.d(WYWTEST,"success write in file  ==="+ saletracker);
		} catch (FileNotFoundException e) {
			Log.d(WYWTEST,"fail write in file  ==="+ e);
			e.printStackTrace();
		} catch (IOException e) {
			Log.d(WYWTEST,"fail write in file  ==="+ e);
			e.printStackTrace();
		}
	}
	private String getSNNumberFromFile() {
		byte buf[] = new byte[11];

		try {
			File file = new File(INTERNAL_FILE_PATH, ST_FILE_NAME);
			FileInputStream fis = new FileInputStream(file);
			BufferedInputStream bufferedInputStream = new BufferedInputStream(fis);
			bufferedInputStream.read(buf);
			bufferedInputStream.close();
			fis.close();
			return new String(buf);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "fail";
	}
	private void DialerShowToast(String state){
		String toaststring = "MAKER<Space>IMEI<Space>ModelNumber<Space>IMEI1<Space>IMEI2<Space>LAC<SPACE>Cell ID";
		salescontent = getSalesSmsContent(0);
		switch(state){
			case "fail":
				Toast.makeText(getApplicationContext(), "Not SEND YET ,simid = "+Toastsimid + "   "+toaststring+"   " +"IMEI :"+TelephonyManagerEx.getDefault().getDeviceId(0) + " " + TelephonyManagerEx.getDefault().getDeviceId(1), Toast.LENGTH_LONG)
						.show();
				break;

			case "success":
				Toast.makeText(getApplicationContext(), "SEND SUCCESS , simid = "+Toastsimid + "   "+toaststring+"   " +"IMEI :"+TelephonyManagerEx.getDefault().getDeviceId(0) + " " + TelephonyManagerEx.getDefault().getDeviceId(1), Toast.LENGTH_LONG)
						.show();

				break;
			default:
				Toast.makeText(getApplicationContext(),"ERROR", Toast.LENGTH_LONG)
						.show();
				break;
		}
		stopService();
		onDestroy();
	}


}