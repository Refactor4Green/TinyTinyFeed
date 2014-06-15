package org.poopeeland.tinytinyfeed.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.poopeeland.tinytinyfeed.Article;
import org.poopeeland.tinytinyfeed.TinyTinyFeedWidget;
import org.poopeeland.tinytinyfeed.exceptions.CheckException;
import org.poopeeland.tinytinyfeed.exceptions.RequiredInfoNotRegistred;

import java.util.concurrent.ExecutionException;


/**
 * Created by eric on 11/06/14.
 */
public class ArticleManagementActivity extends Activity {

    private static final String TAG = "ArticleManagementActivity";
    private TtrssService service;
    private boolean bound;
    private Article article;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            TtrssService.LocalBinder mbinder = (TtrssService.LocalBinder) binder;
            service = mbinder.getService();
            bound = true;
            try {
                service.setArticleToRead(article);
            } catch (CheckException | InterruptedException | ExecutionException | JSONException | RequiredInfoNotRegistred e) {
                Log.e(TAG, e.getMessage());
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
            Log.d(TAG, "bounded!");

            // Retrieve the widgets Ids
            int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), TinyTinyFeedWidget.class));
            Intent updateIntent = new Intent(ArticleManagementActivity.this, TinyTinyFeedWidget.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(updateIntent);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Creation");


        // Bound to service
        Intent intentBound = new Intent(this, TtrssService.class);
        bindService(intentBound, mConnection, Context.BIND_AUTO_CREATE);

        // Retrieve the article
        this.article = (Article) getIntent().getExtras().getSerializable("article");


    }

    @Override
    protected void onResume() {
        super.onResume();

        // Open it
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(article.getUrl()));
        startActivity(intent);

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            Log.d(TAG, "unbound!");
            unbindService(mConnection);
            bound = false;
        }
    }
}