package org.odk.collect.android.openrosa;

public interface HttpCredentialsInterface {
    String getUsername();

    String getPassword();

    boolean getUseToken();

    String getAuthToken();
}
