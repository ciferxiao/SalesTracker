package com.cifer.zf_salestracker;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

//import android.telephony.gemini.GeminiSmsManager;
//xiao 1113

public class SalesTrackerReceiver extends BroadcastReceiver {
	public static final String ACTION_BOOTCOMPLETED = "android.intent.action.BOOT_COMPLETED";//获取开机ACTION
	public static final String DM_REGISTER_SMS_RECEIVED_ACTION = "android.intent.action.TL_REGISTER_SMS_RECEIVED";

	//public static final String ACTION_SIM_STATE_CHANGED = TelephonyIntents.ACTION_SERVICE_STATE_CHANGED;
	public static final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
	public static final int GEMINI_SIM_1 = 0;
	public static final int GEMINI_SIM_2 = 1;
	public static final String CHECK_FILE_PATH = "/system/private/salestracker_checkfile";
	public static final String DIALOG_SHOW_AND_BUTTON_NOT_CLICKED="dialog.show.and.button.not.clicked";

	private boolean isChecked = false;
	private boolean mRegister = false;

	//xiao 1113
	private static final String INTERNAL_FILE_PATH = "/system/private/";
	private static final String ST_FILE_NAME = "STNumber";
	private static final String ACTION_DIALER_BROADCAST = "android.intent.action.DIALER_BROADCAST";

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		String intentAction = null;
		mContext = context;
		mIntent = intent;
		intentAction = intent.getAction();
		Log.d(WYWTEST, "enter receiver");
		Log.d(WYWTEST, "The intent is " + intentAction);
		//获取TelephonyManager服务
		mTelephonyManager=(TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);

		if(!mRegister){
			mContext.getContentResolver().registerContentObserver(Settings.System
							.getUriFor(Settings.System.SALES_MODE_ON),
					false, mSalesEnableObserver);
			mRegister = true;
		}

		if(FeatureOption.TL_SMS_SALE_TRACKER){  //开启了销量验证模式
			if(intentAction.equals(ACTION_BOOTCOMPLETED)){//开机检测
				Log.d(WYWTEST, "ACTION_BOOTCOMPLETED");
				//if sms register is enabled in engineer mode
				if(enableSalesTracker()){
					//start Timer
					Intent bootCompletedIntent = new Intent();
					bootCompletedIntent.setAction("BOOTCOMPLETED");
					bootCompletedIntent.setClass(mContext, SalesTrackerService.class);//开启服务
					mContext.startService(bootCompletedIntent);
				}
			}else if(intentAction.equals(DM_REGISTER_SMS_RECEIVED_ACTION)){
				Log.d(WYWTEST, "DM_REGISTER_SMS_RECEIVED_ACTION");
				//if SMSREGBACK is received, save the IMSI info
				Log.w(WYWTEST, "Save the IMSI");
				int simID = 0;
				String IMSI = intent.getStringExtra("mIMSI");//获取imsi
				Log.w(WYWTEST, "The IMSI to save is  = [" + IMSI+"]");

				//int mResultCode = getResultCode();
				//int error = intent.getIntExtra("errorCode", 0);
				//Xlog.w(TAG, "send tracker sms result=" + mResultCode+ " error="+error);
				if (getResultCode() == Activity.RESULT_OK){ //如果获取成功
					File mSalesTrackerCheckFile = new File(CHECK_FILE_PATH);
					Log.d(WYWTEST,"mSalesTrackerCheckFile.exists() = " + mSalesTrackerCheckFile.exists());
					if(!mSalesTrackerCheckFile.exists()){
						try {
							if(mSalesTrackerCheckFile.createNewFile()){
								Log.d(WYWTEST,"create new file success!");
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					Log.i(WYWTEST, "Send success11");
					//写入priv-app
					String ststate = "success";
					Log.d(WYWTEST,"message send success");
					writeSTNumberToFile(ststate);
					Settings.System.putInt(context.getContentResolver(),
							Settings.System.SALES_MODE_ON, 0);
					mContext.stopService(new Intent(context, SalesTrackerService.class));	//停止服务

				}else{
					//3小时后再发
					if(enableSalesTracker()){
						Intent bootCompletedIntent = new Intent();
						bootCompletedIntent.setAction("RESEND_SALES_MESSEGAES");
						bootCompletedIntent.setClass(mContext, SalesTrackerService.class);
						mContext.startService(bootCompletedIntent);
					}

					Log.i(WYWTEST, "Send fail");
				}
				//stop service
				Log.w(WYWTEST, "send to Stop service");


			}else if(intentAction.equals(ACTION_SIM_STATE_CHANGED)){
				Log.d(WYWTEST, "ACTION_SIM_STATE_CHANGED");
				String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
				Log.d(WYWTEST, "sim state changed" + stateExtra);
				Log.d(WYWTEST, "IccCardConstants = "+IccCardConstants.INTENT_VALUE_ICC_LOADED+" stateExtra = "+stateExtra);
				//33333333333
				if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)){
					Log.d(WYWTEST, "sim state is loaded");
					if(enableSalesTracker()){
						Intent bootCompletedIntent = new Intent();
						bootCompletedIntent.setAction("SIM_STATE_CHANGED");
						bootCompletedIntent.setClass(mContext, SalesTrackerService.class);
						mContext.startService(bootCompletedIntent);
						Log.d("wywtest", "Sms register is enable !");


					}else{
						Log.d("wywtest", "Sms register is disabled by engineer mode !");
						//Xlog.d(TAG,"Sms register is disabled by engineer mode !");
					}

				}else{
					Log.d(WYWTEST, "the sim state is not loaded");
				}

			}else if(intentAction.equals(SalesTrackerService.TIMER_RECEIVED_ACTION)){
				Log.d(WYWTEST, "TIMER_RECEIVED_ACTION");
				Log.d(WYWTEST, "sim state is loaded");
				Intent timerIntent = new Intent();
				timerIntent.setAction(SalesTrackerService.TIMER_RECEIVED_ACTION);
				timerIntent.setClass(mContext, SalesTrackerService.class);
				mContext.startService(timerIntent);
			}else if(intentAction.equals("RESET_SALSE_TRACKER")){
				File salesFile = new File(CHECK_FILE_PATH);
				Log.d(WYWTEST,"salesFile.exists() = " + salesFile.exists());
				if(salesFile.exists()){
					try{
						boolean value = salesFile.delete();
						Log.i(WYWTEST, "salesFile delete value = "+value);
					}catch(Exception e){
						Log.i(WYWTEST, "salesFile delete fail e = "+e);
					}
				}
			}else if(intentAction.equals(Intent.ACTION_TIME_CHANGED)){
				/*Log.d(WYWTEST, "ACTION_TIME_CHANGED");
				if(enableSalesTracker()){
	                Intent timerIntent = new Intent();
				    timerIntent.setAction("ACTION_TIME_CHANGED");
				    timerIntent.setClass(mContext, SalesTrackerService.class);
	                mContext.startService(timerIntent);
				}*/
			}else if(intentAction.equals(ACTION_DIALER_BROADCAST)){ //xiao dialer broadcast *#72537#
				Intent dialerintent = new Intent();
				Log.d("xiao111","ACTION_DIALER_BROADCAST ===============");
				dialerintent.setAction("SHOW_FACTORYMODE_TOAST");
				dialerintent.setClass(mContext, SalesTrackerService.class);
				mContext.startService(dialerintent);
			}

		}else{
			Log.d(WYWTEST, "the TL_SMS_SALE_TRACKER not support");
		}
	}

	private ContentObserver mSalesEnableObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
		}
	};

	private boolean enableSalesTracker(){
		boolean enableSalesTracker = Settings.System.getInt(mContext.getContentResolver(),  Settings.System.SALES_MODE_ON, 1) ==1;

		Log.d(WYWTEST, "enableSalesTracker "+enableSalesTracker);
		return enableSalesTracker;
	}

	private Context mContext;
	private TelephonyManager mTelephonyManager;//主要提供Telephony相关信息的查询/修改功能
	private String TAG = "DM/SalesTracker";
	private String WYWTEST = "xiao111";
	private Intent mIntent;

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
			Log.d(WYWTEST,"success write in file  ==="+ saletracker);
			Process p = Runtime.getRuntime().exec("chmod 644 " + INTERNAL_FILE_PATH + ST_FILE_NAME);
		} catch (FileNotFoundException e) {
			Log.d(WYWTEST,"fail write in file  ==="+ e);
			e.printStackTrace();
		} catch (IOException e) {
			Log.d(WYWTEST,"fail write in file  ==="+ e);
			e.printStackTrace();
		}
	}

}