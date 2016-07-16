package com.stuhorner.ensamble;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

public class MediaService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private static final String ACTION_PLAY = "com.stuhorner.ensamble.action.PLAY";
    String audioURL, UID;
    MediaPlayer mediaPlayer;
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference mRef = database.getReference();
    long server_start_time = 0;

    public MediaService() {
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("service", "onStartCommand");
        if (intent != null && intent.getStringExtra("url") != null) {
            audioURL = intent.getStringExtra("url");
            UID = intent.getStringExtra("uid");
            Log.d("url", audioURL);

            if (intent.getAction().equals(ACTION_PLAY)) {
                setForeground();
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.setOnPreparedListener(this);
                mediaPlayer.setOnCompletionListener(this);
                try {
                    mediaPlayer.setDataSource(audioURL);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.d("preparing", "media");
                mediaPlayer.prepareAsync();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCompletion(MediaPlayer player) {
        stopSelf();
    }

    @Override
    public void onPrepared(final MediaPlayer player) {
        prepareOffsetTiming();
        Log.d("onPrepared", "here");
        mRef.child("play_time").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.getValue().toString().equals("0")) {
                    server_start_time = Long.parseLong(dataSnapshot.getValue().toString());
                    mRef.child("sub_play_time").child(UID).setValue(ServerValue.TIMESTAMP);
                    mediaPlayer.start();
                    Log.d("starting", "mediaPlayer");
                    mRef.child("play_time").removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void prepareOffsetTiming() {
        mRef.child("sub_play_time").child(UID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.getValue().toString().equals("0")) {
                    long start_offset = Long.parseLong(dataSnapshot.getValue().toString()) - server_start_time;
                    Log.d("start_offset", Long.toString(start_offset));
                    Log.d("sub_play_time", dataSnapshot.getValue().toString());
                    Log.d("server_start_time", Long.toString(server_start_time));
                    Log.d("current position", Integer.toString(mediaPlayer.getCurrentPosition()));
                    if (mediaPlayer.getDuration() > (mediaPlayer.getCurrentPosition() + start_offset)) {
                        Log.d("applying offset", "here");
                        mediaPlayer.seekTo((int) (mediaPlayer.getCurrentPosition() + start_offset));
                    } else {
                        mediaPlayer.stop();
                    }
                    mRef.child("sub_play_time").child(UID).removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        mediaPlayer.release();
        mediaPlayer = null;
        mRef.child("play_time").setValue(0);
        mRef.child("sub_play_time").child(UID).setValue(0);

        //for slave device
        //restartService();
    }

    private void restartService() {
        Intent intent = new Intent(MediaService.this, MediaService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra("url", audioURL);
        intent.putExtra("uid", UID);
        startService(intent);
    }

    private void setForeground() {
        Notification notification = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.playing))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setOngoing(true)
                .setAutoCancel(true)
                .build();
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        startForeground(1, notification);
    }
}
