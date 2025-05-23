
package org.odk.collect.android.tasks;

import android.os.AsyncTask;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.SmapLoginListener;
import org.odk.collect.android.openrosa.HttpCredentials;
import org.odk.collect.android.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.utilities.WebCredentialsUtils;

import java.net.URI;
import java.net.URL;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Background task for getting values from the server
 */
public class SmapLoginTask extends AsyncTask<String, Void, String> {

    private SmapLoginListener remoteListener;

    @Inject
    OpenRosaHttpInterface httpInterface;

    @Inject
    WebCredentialsUtils webCredentialsUtils;

    public SmapLoginTask(){
        Collect.getInstance().getComponent().inject(this);
    };

    @Override
    protected String doInBackground(String... params) {

        String useTokenString = params[0];
        String server = params[1];
        String username = params[2];
        String password = params[3];
        String token = params[4];
        String status = null;

        boolean useToken = useTokenString.equals("true");

        try {

            URL url = new URL(server + "/login");
            URI uri = url.toURI();

            status = httpInterface.loginRequest(uri, null,
                    new HttpCredentials(username, password, useToken, token));

        } catch (Exception e) {
            status = "error: " + e.getLocalizedMessage();
        }

        return status;
    }

    @Override
    protected void onPostExecute(String status) {
        synchronized (this) {
            try {
                if (remoteListener != null) {
                    remoteListener.loginComplete(status);
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    public void setListener(SmapLoginListener sl) {
        synchronized (this) {
            remoteListener = sl;
        }
    }


}