package com.example.audiobookmark;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.audiobookmark.receiver.AudioBroadcastReceiver;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private BookmarkViewModel viewModel;
    static final String TAG = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        viewModel = ViewModelProviders.of(this).get(BookmarkViewModel.class);


        RecyclerView recyclerView = findViewById(R.id.recyclerview);
        final BookmarkListAdapter adapter = new BookmarkListAdapter(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));

        viewModel.getAllBookmarks().observe(this, new Observer<List<Bookmark>>() {
            @Override
            public void onChanged(List<Bookmark> words) {
                adapter.setWords(words);
            }
        });

        Intent intent = new Intent(this, MediaCallbackService.class);
        startService(intent);


        if (false) {
            MediaSessionManager mm = (MediaSessionManager) this.getSystemService(
                    Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = mm.getActiveSessions(
                    new ComponentName(getApplicationContext(), NotificationListenerExampleService.class));
            MediaController catcherMediaController = null;
            for (MediaController controller : controllers) {
                if (controller.getPackageName().contains("pocket")) {
                    catcherMediaController = controller;
                    break;
                }
            }

            if (catcherMediaController != null) {
                Log.d(TAG, "Controller: " + catcherMediaController.toString());
                //catcherMediaController.registerCallback(MainActivity.cb);
                Log.d(TAG, "Registered CB");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // action with ID action_refresh was selected
            case R.id.action_settings:
                Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }

        return true;
    }


}
