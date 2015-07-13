//
//
//  NativeAudio.java
//
//  Created by Sidney Bofah on 2014-06-26.
//

package com.rjfun.cordova.plugin.nativeaudio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.io.File;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.os.HandlerThread;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.media.AudioAttributes;
import android.os.Build;
import android.annotation.TargetApi;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;


public class NativeAudio extends CordovaPlugin implements AudioManager.OnAudioFocusChangeListener,
	SoundPool.OnLoadCompleteListener {

	public static final String ERROR_NO_AUDIOID="A reference does not exist for the specified audio id.";
	public static final String ERROR_AUDIOID_EXISTS="A reference already exists for the specified audio id.";

	public static final String PRELOAD_SIMPLE="preloadSimple";
	public static final String PRELOAD_COMPLEX="preloadComplex";
	public static final String PLAY="play";
	public static final String STOP="stop";
	public static final String LOOP="loop";
	public static final String UNLOAD="unload";
    public static final String ADD_COMPLETE_LISTENER="addCompleteListener";
	public static final String SET_VOLUME_FOR_COMPLEX_ASSET="setVolumeForComplexAsset";

	// Set stream count for SoundPool up to 10
	public static final int STREAM_COUNT = 10;

	private static final String LOGTAG = "NativeAudio";

	private static HashMap<String, NativeAudioAsset> assetMap;
    private static ArrayList<NativeAudioAsset> resumeList;
    private static HashMap<String, CallbackContext> completeCallbacks;
    // To hold loaded sound source id
    private static HashMap<String, Integer> soundMap;
    // To hold loading sound source id
    private static HashMap<Integer, String> waitingMap;
    // SoundPool instance
    private static SoundPool soundPool;
    public static HandlerThread stopThread = new HandlerThread("STOP-AUDIO-THREAD");

    /**
     * 2015/05/30 by Kawamura
     * Let preloadSimple function to use SoundPool.
     */
    private PluginResult execPreloadSimple(JSONArray data) {
    	String audioID;
    	try {
    		audioID = data.getString(0);
    		if (!soundMap.containsKey(audioID)) {
    			String assetPath = data.getString(1);
    			Log.d(LOGTAG, "preloadSimple - " + audioID + ": " + assetPath);

				double volume;
				if (data.length() <= 2) {
					volume = 1.0;
				} else {
					volume = data.getDouble(2);
				}

				int voices;
				if (data.length() <= 3) {
					voices = 1;
				} else {
					voices = data.getInt(3);
				}

                // ファイルが存在するならフルパスで指定
                int id = -1;
                File file = new File(assetPath);
                if (!file.exists()) {
                	if (assetPath.indexOf("./") >= 0) {
                		assetPath = assetPath.replace("./", "");
                	}
                    String fullPath = "www/".concat(assetPath);
                    Context ctx = cordova.getActivity().getApplicationContext();
                    AssetManager am = ctx.getResources().getAssets();
                    AssetFileDescriptor afd = am.openFd(fullPath);
	                id = soundPool.load(afd, 0);
                    waitingMap.put(id, audioID); // waiting loading. if load success replace by soundId
                } else {
					id = soundPool.load(assetPath, 0);
                    waitingMap.put(id, audioID); // waiting loading. if load success replace by soundId
                }
                file = null;

				return new PluginResult(Status.OK);
    		} else {
				return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
			}
    	} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (Exception e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
    }

    /**
     * 2015/05/30 by Kawamura
     * SoundPool callback
     */
    public void onLoadComplete (SoundPool soundPool, int sampleId, int status) {
//    	Log.d(LOGTAG, "onLoadComplete ID:" + sampleId + " status:" + status + " isWaiting:" + waitingMap.containsKey(sampleId));
    	// success
    	if (status == 0 && waitingMap.containsKey(sampleId)) {
    		String audioKey = (String) waitingMap.get(sampleId);
    		soundMap.put(audioKey, sampleId);
    		synchronized (waitingMap) {
    			waitingMap.remove(sampleId);
    		}
    	}
    }

	private PluginResult executePreload(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			if (!assetMap.containsKey(audioID)) {
				String assetPath = data.getString(1);

				double volume;
				if (data.length() <= 2) {
					volume = 1.0;
				} else {
					volume = data.getDouble(2);
				}

				int voices;
				if (data.length() <= 3) {
					voices = 1;
				} else {
					voices = data.getInt(3);
				}

                NativeAudioAsset asset = null;
                // ファイルが存在するならフルパスで指定
                File file = new File(assetPath);
                if (!file.exists()) {
                	if (assetPath.indexOf("./") >= 0) {
                		assetPath = assetPath.replace("./", "");
                	}
                    String fullPath = "www/".concat(assetPath);
                    Context ctx = cordova.getActivity().getApplicationContext();
                    AssetManager am = ctx.getResources().getAssets();
                    AssetFileDescriptor afd = am.openFd(fullPath);
                    asset = new NativeAudioAsset(afd, voices, (float)volume);

                } else {
                    asset = new NativeAudioAsset(assetPath, voices, (float)volume);
                }
                if (asset != null) assetMap.put(audioID, asset);
                file = null;

				return new PluginResult(Status.OK);
			} else {
				return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
	}

	private PluginResult executePlayOrLoop(String action, JSONArray data) {
		final String audioID;
		try {
			audioID = data.getString(0);

			if (assetMap.containsKey(audioID)) {
				String startTime = "0", duration = "1", rate = "1";
				if (data.length() > 1) {
		            startTime = data.getString(1);
		            duration = data.getString(2);
								rate = data.getString(3);
				}
				NativeAudioAsset asset = assetMap.get(audioID);
				if (LOOP.equals(action))
					asset.loop();
				else
					asset.play(Float.parseFloat(startTime),
											Float.parseFloat(duration),
											Float.parseFloat(rate),
                               new Callable<Void>() {
                        public Void call() throws Exception {
                            CallbackContext callbackContext = completeCallbacks.get(audioID);
                            if (callbackContext != null) {
                                JSONObject done = new JSONObject();
                                done.put("id", audioID);
                                callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
                            }
                            return null;
                        }
                    });
			} else {
				// Search soundMap
				if (soundMap.containsKey(audioID)) {
					int status = soundPool.play((int) soundMap.get(audioID), 1.0f, 1.0f, 5, 0, 1.0f);
					// status == 0 is fail
					if (status == 0) {
						return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
					}
				} else {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}

		return new PluginResult(Status.OK);
	}

	private PluginResult executeStop(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			//Log.d( LOGTAG, "stop - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.stop();
			} else if (soundMap.containsKey(audioID)) {
				soundPool.stop((int) soundMap.get(audioID));
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}

		return new PluginResult(Status.OK);
	}

	private PluginResult executeUnload(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			Log.d( LOGTAG, "unload - " + audioID );
			// SoundPoolに入っている場合
			if (soundMap.containsKey(audioID)) {
				if (!soundPool.unload((int) soundMap.get(audioID))) {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
				synchronized (soundMap) {
					soundMap.remove(audioID);
				}
			} else if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.unload();
				assetMap.remove(audioID);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}

		return new PluginResult(Status.OK);
	}

	private PluginResult executeSetVolumeForComplexAsset(JSONArray data) {
		String audioID;
		float volume;
		try {
			audioID = data.getString(0);
			volume = (float) data.getDouble(1);
			Log.d( LOGTAG, "setVolume - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.setVolume(volume);
			} else if (soundMap.containsKey(audioID)) {
				soundPool.setVolume((int) soundMap.get(audioID), (float) volume, (float) volume);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		return new PluginResult(Status.OK);
	}
	@Override
	protected void pluginInitialize() {
		AudioManager am = (AudioManager)cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);

	        int result = am.requestAudioFocus(this,
	                // Use the music stream.
	                AudioManager.STREAM_MUSIC,
	                // Request permanent focus.
	                AudioManager.AUDIOFOCUS_GAIN);

		// Allow android to receive the volume events
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_DOWN, false);
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_UP, false);
	}

	@Override
	public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
		Log.d(LOGTAG, "Plugin Called: " + action);

		PluginResult result = null;
		initSoundPool();

		try {
			if (PRELOAD_SIMPLE.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
//		            	callbackContext.sendPluginResult( executePreload(data) );
		            	callbackContext.sendPluginResult(execPreloadSimple(data));
		            }
		        });

			} else if (PRELOAD_COMPLEX.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreload(data) );
		            }
		        });

			} else if (PLAY.equals(action) || LOOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePlayOrLoop(action, data) );
		            }
		        });

			} else if (STOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executeStop(data) );
		            }
		        });

            } else if (UNLOAD.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        executeStop(data);
                        callbackContext.sendPluginResult( executeUnload(data) );
                    }
                });
            } else if (ADD_COMPLETE_LISTENER.equals(action)) {
                if (completeCallbacks == null) {
                    completeCallbacks = new HashMap<String, CallbackContext>();
                }
                try {
                    String audioID = data.getString(0);
                    completeCallbacks.put(audioID, callbackContext);
                } catch (JSONException e) {
                    callbackContext.sendPluginResult(new PluginResult(Status.ERROR, e.toString()));
		}
	    } else if (SET_VOLUME_FOR_COMPLEX_ASSET.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
			public void run() {
	                        callbackContext.sendPluginResult( executeSetVolumeForComplexAsset(data) );
                    }
                 });
	    }
            else {
                result = new PluginResult(Status.OK);
            }
		} catch (Exception ex) {
			result = new PluginResult(Status.ERROR, ex.toString());
		}

		if(result != null) callbackContext.sendPluginResult( result );
		return true;
	}

	private void initSoundPool() {

		if (assetMap == null) {
			assetMap = new HashMap<String, NativeAudioAsset>();
		}

		// soundMap init
		if (soundMap == null) {
			soundMap = new HashMap<String, Integer>();
			waitingMap = new HashMap<Integer, String>();
			// Init SoundPool instance
			Log.d(LOGTAG, "[SDK VERSION]:" + Build.VERSION.SDK_INT);
			Log.d(LOGTAG, "[LOLLIPOP VERSION]:" + Build.VERSION_CODES.LOLLIPOP);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				initSoundPoolInstanceForRecent(soundPool, STREAM_COUNT);
			} else {
				initSoundPoolInstanceForOlder(soundPool, STREAM_COUNT);
			}
			if (soundPool != null) {
				soundPool.setOnLoadCompleteListener(this);
			}
		}

        if (resumeList == null) {
            resumeList = new ArrayList<NativeAudioAsset>();
        }

        // init Stop thred
		if (!stopThread.isAlive()) {
			stopThread.start();
		}
	}

	/**
	 * 2015/05/30 by Kawamura
	 * Create SoundPool instance for LOLIPOP or later.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private SoundPool createSoundPoolForRecent(SoundPool sp, int streamCount) {
		Log.d("Sound", "Initialize Audio Attributes.");
        // Initialize AudioAttributes.
        AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        Log.d("Sound", "Set AudioAttributes for SoundPool.");
        // Set the audioAttributes for the SoundPool and specify maximum number of streams.
        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(streamCount)
                .build();

        return soundPool;
	}

	/**
	 * 2015/05/30 by Kawamura
	 * Create SoundPool instance for older version than LOLIPOP.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void initSoundPoolInstanceForOlder(SoundPool sp, int streamCount) {
		soundPool = new SoundPool(streamCount, AudioManager.STREAM_MUSIC, 0);
	}

	/**
	 * 2015/05/30 by Kawamura
	 * Create SoundPool instance for LOLIPOP or later.
	 */
	@SuppressWarnings("deprecation")
	private void initSoundPoolInstanceForRecent(SoundPool sp, int streamCount) {
		soundPool = createSoundPoolForRecent(soundPool, STREAM_COUNT);
	}

    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // Pause playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // Resume playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // Stop playback
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);

        // Pause all SoundPool
        soundPool.autoPause();

        for (HashMap.Entry<String, NativeAudioAsset> entry : assetMap.entrySet()) {
            NativeAudioAsset asset = entry.getValue();
            boolean wasPlaying = asset.pause();
            if (wasPlaying) {
                resumeList.add(asset);
            }
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);

        // Resume all SoundPool
        soundPool.autoResume();

        while (!resumeList.isEmpty()) {
            NativeAudioAsset asset = resumeList.remove(0);
            asset.resume();
        }
    }
}
