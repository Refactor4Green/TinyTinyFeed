package org.poopeeland.tinytinyfeed.settings;

import android.app.Activity;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.apache.http.client.HttpClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.poopeeland.tinytinyfeed.R;
import org.poopeeland.tinytinyfeed.RequestTask;
import org.poopeeland.tinytinyfeed.TinyTinyFeedWidget;
import org.poopeeland.tinytinyfeed.exceptions.NoInternetException;
import org.poopeeland.tinytinyfeed.exceptions.TtrssError;
import org.poopeeland.tinytinyfeed.utils.Utils;

import java.net.MalformedURLException;
import java.net.URL;

public class SettingsActivity extends Activity implements View.OnClickListener {

    private static final String URL_SUFFIX = "/api/";
    private final String TAG = SettingsActivity.class.getSimpleName();
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ConnectivityManager connectivityManager;
    private SharedPreferences preferences;
    private boolean checked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        this.connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        findViewById(R.id.setup_check_button).setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.checked = false;
        setResult(RESULT_CANCELED);
    }

    @Override
    public void onClick(View view) {

        try {
            this.checkSetup();
        } catch (MalformedURLException e) {
            Toast.makeText(this, R.string.urlMalFormed, Toast.LENGTH_LONG).show();
            return;
        } catch (JSONException e) {
            Toast.makeText(this, String.format("%s", e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        } catch (NoInternetException ex) {
            Toast.makeText(this, R.string.noInternetConnection, Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = this.preferences.edit();
        editor.putBoolean(TinyTinyFeedWidget.CHECKED, this.checked);
        editor.apply();

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, resultValue);
        } else {
            setResult(RESULT_OK);
        }

        int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), TinyTinyFeedWidget.class));
        Intent updateIntent = new Intent(this, TinyTinyFeedWidget.class);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(updateIntent);

        finish();
    }

    private void checkSetup() throws MalformedURLException, JSONException, NoInternetException {
        Utils.checkNetwork(this.connectivityManager);

        String url = this.preferences.getString(TinyTinyFeedWidget.URL_KEY, "");
        String user = this.preferences.getString(TinyTinyFeedWidget.USER_KEY, "");
        String password = this.preferences.getString(TinyTinyFeedWidget.PASSWORD_KEY, "");
        String httpUser = this.preferences.getString(TinyTinyFeedWidget.HTTP_USER_KEY, "");
        String httpPassword = this.preferences.getString(TinyTinyFeedWidget.HTTP_PASSWORD_KEY, "");

        if (!url.endsWith(SettingsActivity.URL_SUFFIX)) {
            url = url + URL_SUFFIX;
        }
        new URL(url); // To check if the URL is a real one
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user", user);
        jsonObject.put("password", password);
        jsonObject.put("op", "login");
        HttpClient client = Utils.getNewHttpClient(this.preferences, httpUser, httpPassword);

        CheckSetupTask task = new CheckSetupTask(client, url);
        task.execute(jsonObject);
    }


    public class CheckSetupTask extends RequestTask {

        private ProgressDialog dialog;

        public CheckSetupTask(HttpClient client, String url) {
            super(client, url);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            this.dialog = ProgressDialog.show(SettingsActivity.this, getText(R.string.waitTitle), getText(R.string.waitCheck));
        }

        @Override
        protected void onPostExecute(JSONObject response) {
            super.onPostExecute(response);
            this.dialog.dismiss();

            try {
                if (response.getInt("status") != 0) {
                    TtrssError reason = TtrssError.valueOf(response.getJSONObject("content").getString("error"));
                    switch (reason) {
                        case LOGIN_ERROR:
                            Log.e(TAG, response.getJSONObject("content").getString("error"));
                            Toast.makeText(SettingsActivity.this, R.string.badLogin, Toast.LENGTH_LONG).show();
                            break;
                        case CLIENT_PROTOCOL_EXCEPTION:
                        case UNREACHABLE_TTRSS:
                        case IO_EXCEPTION:
                            Log.e(TAG, response.getJSONObject("content").getString("message"));
                            Toast.makeText(SettingsActivity.this, R.string.connectionError, Toast.LENGTH_LONG).show();
                            break;
                        case HTTP_AUTH_REQUIERED:
                            Log.e(TAG, response.getJSONObject("content").getString("message"));
                            Toast.makeText(SettingsActivity.this, R.string.connectionAuthError, Toast.LENGTH_LONG).show();
                            break;
                        case UNSUPPORTED_ENCODING:
                        case JSON_EXCEPTION:
                            Log.e(TAG, response.getJSONObject("content").getString("message"));
                            Toast.makeText(SettingsActivity.this, R.string.impossibleError, Toast.LENGTH_LONG).show();
                            break;
                        case API_DISABLED:
                            Log.e(TAG, "API Disabled....");
                            Toast.makeText(SettingsActivity.this, R.string.setupApiDisabled, Toast.LENGTH_LONG).show();
                            break;
                        default:
                            Log.e(TAG, response.getJSONObject("content").getString("message"));
                            Toast.makeText(SettingsActivity.this, R.string.unknownError, Toast.LENGTH_LONG).show();
                            break;
                    }
                    return;
                }
            } catch (JSONException ex) {
                // This can not happen
                Log.e(TAG, ex.getMessage());
                return;
            }

            SettingsActivity.this.checked = true;
        }
    }

}