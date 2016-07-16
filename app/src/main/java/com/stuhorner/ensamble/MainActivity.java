package com.stuhorner.ensamble;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_PLAY = "com.stuhorner.ensamble.action.PLAY";
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference mRef = database.getReference();
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReferenceFromUrl("gs://ensamble-82ea3.appspot.com/audio.mp3");
    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    FirebaseAuth.AuthStateListener mAuthListener;
    String UID;             //User's ID
    String audioURL;        //Location of the audio file
    TextView prepare, download, waiting;
    ImageView prepare_done, download_done, waiting_done;
    Button play;
    ProgressBar download_progress, waiting_progress, prepare_progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setUpUI();
        logInAnonymousUser();
    }

    private void setUpUI() {
        prepare = (TextView) findViewById(R.id.prepare);
        download = (TextView) findViewById(R.id.download);
        waiting = (TextView) findViewById(R.id.waiting);
        prepare_done = (ImageView) findViewById(R.id.prepare_done);
        prepare_progress = (ProgressBar) findViewById(R.id.prepare_progress);
        download_done = (ImageView) findViewById(R.id.download_done);
        download_progress = (ProgressBar) findViewById(R.id.download_progress);
        waiting_done = (ImageView) findViewById(R.id.waiting_done);
        waiting_progress = (ProgressBar) findViewById(R.id.waiting_progress);
        play = (Button) findViewById(R.id.play);
        addAnimation(play);

        prepare.setTextColor(getResources().getColor(R.color.textColorActive));
        prepare_done.setColorFilter(getResources().getColor(R.color.green));
        waiting_done.setColorFilter(getResources().getColor(R.color.green));
        download_done.setColorFilter(getResources().getColor(R.color.green));
    }

    private void getFileFromFirebase() {
        try {
            final File localAudioFile = File.createTempFile("audio", "wav");
            storageRef.getFile(localAudioFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    audioURL = localAudioFile.getAbsolutePath();
                    setDeviceReady();
                    checkStateOfOtherDevices();

                    //update UI
                    download_done.setVisibility(View.VISIBLE);
                    download_progress.setVisibility(View.INVISIBLE);
                    waiting.setTextColor(getResources().getColor(R.color.textColorActive));
                    waiting_progress.setVisibility(View.VISIBLE);

                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    // Handle any errors
                }
            });
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkStateOfOtherDevices() {
        mRef.child("ready").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Long> map = (Map)dataSnapshot.getValue();
                boolean ready = true;
                for (Long entry : map.values())
                {
                    if (entry == 0) {
                        ready = false;
                    }
                }
                if (ready) {
                    allDevicesReady();
                    mRef.removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void setDeviceNotReady() {
        mRef.child("ready").child(UID).setValue(0);
    }

    private void setDeviceReady() {
        mRef.child("ready").child(UID).setValue(1);
    }

    private void logInAnonymousUser() {
        //listen for changes in the authentication state
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    //Logged in
                    UID = user.getUid();
                    setDeviceNotReady();
                    getFileFromFirebase();

                    //update UI
                    prepare_done.setVisibility(View.VISIBLE);
                    prepare_progress.setVisibility(View.INVISIBLE);
                    download.setTextColor(getResources().getColor(R.color.textColorActive));
                    download_progress.setVisibility(View.VISIBLE);
                } else {
                    //Logged out
                }
            }
        };

        // sign the user in anonymously
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (!task.isSuccessful()) {
                            Toast.makeText(getApplicationContext(), "Authentication failed.",Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    private void addAnimation(final Button button) {
        final Animation scaleDown = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.scale_down);
        scaleDown.setFillAfter(true);
        final Animation scaleUp = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.scale_up);
        scaleUp.setFillAfter(true);

        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        button.startAnimation(scaleDown);
                        break;
                    case MotionEvent.ACTION_UP:
                        button.startAnimation(scaleUp);
                        handleButtonClicked(button);
                        break;
                }
                return false;
            }
        });
    }

    private void handleButtonClicked(Button button) {
        //the intent to start the service
        final Intent intent = new Intent(MainActivity.this, MediaService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra("url", audioURL);
        intent.putExtra("uid", UID);

        //start service
        if (button.getText().toString().equals(getString(R.string.play))) {

            //set the play time
            mRef.child("play_time").setValue(ServerValue.TIMESTAMP);
                startService(intent);
                button.setText(R.string.stop);
        }
        else {
            if (isServiceRunning(MediaService.class)) {
                stopService(intent);
            }
            button.setText(R.string.play);
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void startServiceForReceivers() {
        Intent intent = new Intent(MainActivity.this, MediaService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra("url", audioURL);
        intent.putExtra("uid", UID);
        startService(intent);
    }

    private void allDevicesReady() {
        waiting_progress.setVisibility(View.INVISIBLE);
        waiting_done.setVisibility(View.VISIBLE);

        //for master device
         play.setVisibility(View.VISIBLE);

        //for slave devices
        //startServiceForReceivers();
    }

}
