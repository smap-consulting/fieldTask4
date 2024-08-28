package org.odk.collect.android.openrosa;

public class HttpCredentials implements HttpCredentialsInterface {

    private final String username;
    private final String password;
    private final boolean useToken;  // smap
    private final String authToken;  // Smap

    public HttpCredentials(String username, String password, boolean useToken, String authToken) {
        this.username = (username == null) ? "" : username;
        this.password = (password == null) ? "" : password;
        this.useToken = useToken;
        this.authToken = authToken;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean getUseToken() {
        return useToken;
    }

    @Override
    public String getAuthToken() {
        return authToken;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (super.equals(obj)) {
            return true;
        }

        return ((HttpCredentials) obj).getUsername().equals(getUsername()) &&
                ((HttpCredentials) obj).getPassword().equals(getPassword());
    }

    @Override
    public int hashCode() {
        return (getUsername() + getPassword()).hashCode();
    }
}
