//
//
//  NativeAudioAssetComplex.java
//
//  Created by Sidney Bofah on 2014-06-26.
//

package com.rjfun.cordova.plugin.nativeaudio;

import java.io.IOException;
import java.util.concurrent.Callable;

import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Handler;

public class NativeAudioAssetComplex implements OnPreparedListener, OnCompletionListener, OnErrorListener {

	private static final int INVALID = 0;
	private static final int PREPARED = 1;
	private static final int PENDING_PLAY = 2;
	private static final int PLAYING = 3;
	private static final int PENDING_LOOP = 4;
	private static final int LOOPING = 5;

	private MediaPlayer mp;
	private int state;
    Callable<Void> completeCallback;
    private float startTime = 0;
    private float duration = 1;
    private int playCounter = 0;

	public NativeAudioAssetComplex( AssetFileDescriptor afd, float volume)  throws IOException
	{
		state = INVALID;
		mp = new MediaPlayer();
        mp.setOnCompletionListener(this);
        mp.setOnPreparedListener(this);
        mp.setOnErrorListener(this);
		mp.setDataSource( afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
		mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mp.setVolume(volume, volume);
		mp.prepare();
	}

    /**
     * リソース直指定用
     */
    public NativeAudioAssetComplex(String path, float volume) throws IOException {
        state = INVALID;
        mp = new MediaPlayer();
        mp.setOnCompletionListener(this);
        mp.setOnPreparedListener(this);
        mp.setDataSource(path);
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setVolume(volume, volume);
        mp.prepare();
    }

	public void play(Callable<Void> completeCb) throws IOException
	{
        completeCallback = completeCb;
		invokePlay(0f, 1f, 1f, false);
	}

    /**
     * 再生開始時間と再生時間指定
     * @param startTime millis
     * @param duration millis
     * @param rate float
     */
    public void play(float startTime, float duration, float rate, Callable<Void> completeCb) throws IOException {
        completeCallback = completeCb;
        this.startTime = startTime;
        this.duration = duration;
        if ((this.playCounter + 1) < Integer.MAX_VALUE) {
	        this.playCounter++;
        } else {
        	this.playCounter = 0;
        }
        invokePlay(startTime, duration, rate, false);
    }

	private void invokePlay(float startTime, float duration, float rate, Boolean loop)
	{
		Boolean playing = ( mp.isLooping() || mp.isPlaying() );
		if ( playing )
		{
			mp.pause();
			mp.setLooping(loop);
			mp.seekTo((int) (startTime * 1000));
			mp.start();
		}
		if ( !playing && state == PREPARED )
		{
			state = (loop ? PENDING_LOOP : PENDING_PLAY);
			onPrepared( mp );
		}
		else if ( !playing )
		{
			state = (loop ? PENDING_LOOP : PENDING_PLAY);
			mp.setLooping(loop);
			mp.seekTo((int) (startTime * 1000));
			mp.start();
		}
        // duration後に停止
        if (duration > 0) {
	        new Handler(NativeAudio.stopThread.getLooper()).postDelayed(new Runnable() {
	        	private int myCounter = playCounter;
	            public void run() {
					if (myCounter == playCounter) {
		                pause();
		            }
	            }
	        }, (long) (duration * 1000));
    	}
	}

	public boolean pause()
	{
		try
		{
            if ( mp.isLooping() || mp.isPlaying() )
            {
                mp.pause();
                return true;
            }
        }
		catch (IllegalStateException e)
		{
		// I don't know why this gets thrown; catch here to save app
		}
		return false;
	}

	public void resume()
	{
		mp.start();
	}

    public void stop()
	{
		try
		{
			if ( mp.isLooping() || mp.isPlaying() )
			{
				state = INVALID;
				mp.pause();
				mp.seekTo((int) (startTime * 1000));
            }
		}
        catch (IllegalStateException e)
        {
        // I don't know why this gets thrown; catch here to save app
        }
	}

	public void setVolume(float volume)
	{
        try
        {
            mp.setVolume(volume,volume);
        }
        catch (IllegalStateException e)
		{
            // I don't know why this gets thrown; catch here to save app
		}
	}

	public void loop() throws IOException
	{
		invokePlay(0, 1, 1, true);
	}

	public void unload() throws IOException
	{
		this.stop();
		mp.release();
	}

	public void onPrepared(MediaPlayer mPlayer)
	{
		if (state == PENDING_PLAY)
		{
			mp.setLooping(false);
			mp.seekTo((int) (startTime * 1000));
			mp.start();
			state = PLAYING;
		}
		else if ( state == PENDING_LOOP )
		{
			mp.setLooping(true);
			mp.seekTo((int) (startTime * 1000));
			mp.start();
			state = LOOPING;
		}
		else
		{
			state = PREPARED;
			mp.seekTo(0);
		}
	}

	public void onCompletion(MediaPlayer mPlayer)
	{
		if (state != LOOPING)
		{
			this.state = INVALID;
			try {
				this.stop();
                completeCallback.call();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	public boolean onError(MediaPlayer mPlayer, int what, int extra) {
		System.out.println("MediaPlayer onError:what=" + what + " extra=" + extra);
		return true;
	}
}
