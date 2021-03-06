package org.odk.collect.android.analytics;

import android.os.Bundle;

import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;

public class FirebaseAnalytics implements Analytics {

    //private final com.google.firebase.analytics.FirebaseAnalytics firebaseAnalytics;  // smap
    //private final GeneralSharedPreferences generalSharedPreferences;  // smap

    //public FirebaseAnalytics(com.google.firebase.analytics.FirebaseAnalytics firebaseAnalytics, GeneralSharedPreferences generalSharedPreferences) {
        // this.firebaseAnalytics = firebaseAnalytics;  // smap commented
        // this.generalSharedPreferences = generalSharedPreferences;  // smap commented
        // setupRemoteAnalytics();  // smap commented
    //}

    // smap alternate constructor
    public FirebaseAnalytics() {

    }

    @Deprecated
    @Override
    public void logEvent(String category, String action) {
        //Bundle bundle = new Bundle();     // smap
        //bundle.putString("action", action);
        //firebaseAnalytics.logEvent(category, bundle);
    }

    @Deprecated
    @Override
    public void logEvent(String category, String action, String label) {
        //Bundle bundle = new Bundle();     //smap
        //bundle.putString("action", action);
        //bundle.putString("label", label);
        //firebaseAnalytics.logEvent(category, bundle);
    }

    @Override
    public void logFormEvent(String event, String formId) {
        //Bundle bundle = new Bundle();  // smap
        //bundle.putString("form", formId);  // smap
        //firebaseAnalytics.logEvent(event, bundle);  // smap
    }

    @Override
    public void logServerEvent(String event, String serverHash) {
        //Bundle bundle = new Bundle();  // smap
        //bundle.putString("server", serverHash);  // smap
        //firebaseAnalytics.logEvent(event, bundle); //smap
    }

    private void setupRemoteAnalytics() {
        //boolean isAnalyticsEnabled = generalSharedPreferences.getBoolean(GeneralKeys.KEY_ANALYTICS, true);   // smap
        //setAnalyticsCollectionEnabled(isAnalyticsEnabled);    // smap
    }

    public void setAnalyticsCollectionEnabled(boolean isAnalyticsEnabled) {
        //firebaseAnalytics.setAnalyticsCollectionEnabled(isAnalyticsEnabled);  // smap
    }

    @Override
    public void setUserProperty(String name, String value) {
        //firebaseAnalytics.setUserProperty(name, value);
    }

    // smap - disabled
}
