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
import android.media.SoundPool.Builder;
import android.media.AudioAttributes;
import android.media.AudioAttributes.Builder;

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

	private static final String LOGTAG = "NativeAudio";
	
	private static HashMap<String, NativeAudioAsset> assetMap;
    private static ArrayList<NativeAudioAsset> resumeList;
    private static HashMap<String, CallbackContext> completeCallbacks;
    private static HashMap<String, Integer> soundMap;
    private static HashMap<Integer, String> waitingMap; // 読み込み待ち
    private static SoundPool soundPool;
    public static HandlerThread stopThread = new HandlerThread("STOP-AUDIO-THREAD");

    // preloadSimple
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
                File file = new File(assetPath);
                if (!file.exists()) {
                	if (assetPath.indexOf("./") >= 0) {
                		assetPath = assetPath.replace("./", "");
                	}
                    assetPath = "www/".concat(assetPath);
                }
                int id = soundPool.load(assetPath, 0);
                waitingMap.put(id, audioID); // waiting loading. if load success replace by soundId
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

    public void onLoadComplete (SoundPool soundPool, int sampleId, int status) {
    	// success
    	if (status == 0 && waitingMap.containsKey(sampleId)) {
    		String audioKey = waitingMap.getString(sampleId);
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
				Log.d(LOGTAG, "preloadComplex - " + audioID + ": " + assetPath);
				
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
		final String audioID, startTime, duration;
		try {
			audioID = data.getString(0);
			//Log.d( LOGTAG, "play - " + audioID );
            startTime = data.getString(1);
            duration = data.getString(2);

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				if (LOOP.equals(action))
					asset.loop();
				else
					asset.play(Float.parseFloat(startTime),
                               Float.parseFloat(duration),
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
				// soundMapを検索
				if (soundMap.containsKey(audioID)) {
					int status = soundPool.play(soundMap.getInt(audioID), 1.0f, 1.0f, 5, 0, 1.0f);
					// status == 0 is fail
					if (start == 0) {
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
				if (!soundPool.unload(soundMap.getInt(audioID))) {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
			if (assetMap.containsKey(audioID)) {
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
			soundPool = new SoundPool.Builder().setMaxStreams(10)
							.setAudioAttributes(new AudioAttributes.Builder()
								.setUsage(AudioAttributes.USAGE_GAME)
								.build())
							.build();
		}

        if (resumeList == null) {
            resumeList = new ArrayList<NativeAudioAsset>();
        }

        // init Stop thred
		if (!stopThread.isAlive()) {
			stopThread.start();
		}
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
        while (!resumeList.isEmpty()) {
            NativeAudioAsset asset = resumeList.remove(0);
            asset.resume();
        }
    }
}