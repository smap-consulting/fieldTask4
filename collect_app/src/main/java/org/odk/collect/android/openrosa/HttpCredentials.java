package org.odk.collect.android.openrosa;

public class HttpCredentials implements HttpCredentialsInterface {

    private final String username;
    private final String password;
    private final boolean useToken;  // smap

    public HttpCredentials(String username, String password, boolean useToken) {
        this.username = (username == null) ? "" : username;
        this.password = (password == null) ? "" : password;
        this.useToken = useToken;
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
