package com.example.audiobookmark;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MediaCtrCb cb = new MediaCtrCb();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MediaSessionManager mm = (MediaSessionManager) this.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        List<MediaController> controllers = mm.getActiveSessions(
                new ComponentName(this, NotificationListenerExampleService.class));
        for (MediaController controller : controllers) {
            controller.registerCallback(this.cb);
            String test = controller.getPackageName();
            int i = 2;
        }
        Log.i("MyApp", "found " + controllers.size() + " controllers");
    }
}
