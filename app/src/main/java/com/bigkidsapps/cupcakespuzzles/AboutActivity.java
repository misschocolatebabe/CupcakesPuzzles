package com.bigkidsapps.cupcakespuzzles;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.startapp.android.publish.Ad;
import com.startapp.android.publish.AdEventListener;
import com.startapp.android.publish.StartAppAd;
import com.startapp.android.publish.StartAppSDK;
import com.startapp.android.publish.nativead.NativeAdDetails;
import com.startapp.android.publish.nativead.NativeAdPreferences;
import com.startapp.android.publish.nativead.StartAppNativeAd;
import com.startapp.android.publish.splash.SplashConfig;
import com.startapp.android.publish.video.VideoListener;

import java.util.ArrayList;

public class AboutActivity extends AppCompatActivity {

	/** StartAppAd object declaration */
	private StartAppAd startAppAd = new StartAppAd(this);

	/** StartApp Native Ad declaration */
	private StartAppNativeAd startAppNativeAd = new StartAppNativeAd(this);
	private NativeAdDetails nativeAd = null;

	private ImageView imgFreeApp = null;

	/** Native Ad Callback */
	private AdEventListener nativeAdListener = new AdEventListener() {

		@Override
		public void onReceiveAd(Ad ad) {

			// Get the native ad
			ArrayList<NativeAdDetails> nativeAdsList = startAppNativeAd.getNativeAds();
			if (nativeAdsList.size() > 0){
				nativeAd = nativeAdsList.get(0);
			}

			// Verify that an ad was retrieved
			if (nativeAd != null){

				// When ad is received and displayed - we MUST send impression
				nativeAd.sendImpression(AboutActivity.this);

				if (imgFreeApp != null){

					// Set button as enabled
					imgFreeApp.setEnabled(true);

					// Set ad's image
					imgFreeApp.setImageBitmap(nativeAd.getImageBitmap());

				}
			}
		}

		@Override
		public void onFailedToReceiveAd(Ad ad) {


		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);

		StartAppSDK.init(this, "205717470", true); //TODO: Replace with your Application ID

		/** Create Splash Ad **/
		StartAppAd.showSplash(this, savedInstanceState,
				new SplashConfig()
						.setTheme(SplashConfig.Theme.BLAZE)
						.setAppName("Wait a moment....")
		);

		/** Initialize Native Ad views **/
		imgFreeApp = (ImageView) findViewById(R.id.imgFreeApp);


		/**
		 * Load Native Ad with the following parameters:
		 * 1. Only 1 Ad
		 * 2. Download ad image automatically
		 * 3. Image size of 150x150px
		 */
		startAppNativeAd.loadAd(
				new NativeAdPreferences()
						.setAdsNumber(1)
						.setAutoBitmapDownload(true)
						.setImageSize(NativeAdPreferences.NativeAdBitmapSize.SIZE150X150),
				nativeAdListener);
	}


	public void btnShowRewardedClick(View view) {
		final StartAppAd rewardedVideo = new StartAppAd(this);

		/**
		 * This is very important: set the video listener to be triggered after video
		 * has finished playing completely
		 */
		rewardedVideo.setVideoListener(new VideoListener() {

			@Override
			public void onVideoCompleted() {
				Toast.makeText(AboutActivity.this, "Rewarded video has completed - grant the user his reward", Toast.LENGTH_LONG).show();
			}
		});

		/**
		 * Load rewarded by specifying AdMode.REWARDED
		 * We are using AdEventListener to trigger ad show
		 */
		rewardedVideo.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, new AdEventListener() {

			@Override
			public void onReceiveAd(Ad arg0) {
				rewardedVideo.showAd();
			}

			@Override
			public void onFailedToReceiveAd(Ad arg0) {
				/**
				 * Failed to load rewarded video:
				 * 1. Check that FullScreenActivity is declared in AndroidManifest.xml:
				 * See https://github.com/StartApp-SDK/Documentation/wiki/Android-InApp-Documentation#activities
				 * 2. Is android API level above 16?
				 */
				Log.e("AboutActivity", "Failed to load rewarded video with reason: " + arg0.getErrorMessage());
			}
		});
	}

	public void freeAppClick(View view){
		if (nativeAd != null){
			nativeAd.sendClick(this);
		}
	}

	public void openBrowser(View view) {
		String url = (String)view.getTag();
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_VIEW);
		intent.addCategory(Intent.CATEGORY_BROWSABLE);
		intent.setData(Uri.parse(url));
		startActivity(intent);
	}

	/**
	 * Part of the activity's life cycle, StartAppAd should be integrated here.
	 */
	@Override
	public void onResume(){
		super.onResume();
	}
	
	/**
	 * Part of the activity's life cycle, StartAppAd should be integrated here
	 * for the home button exit ad integration.
	 */
	@Override
	public void onPause(){
		super.onPause();
	}
	
	/**
	 * Part of the activity's life cycle, StartAppAd should be integrated here
	 * for the back button exit ad integration.
	 */
	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}

}
