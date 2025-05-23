/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.InstanceDatabaseMigrator;
import org.odk.collect.android.database.InstancesDatabaseHelper;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.instances.Instance;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.storage.StorageInitializer;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.android.utilities.Utilities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import javax.inject.Inject;

import timber.log.Timber;

import static org.odk.collect.android.database.DatabaseConstants.INSTANCES_TABLE_NAME;

public class InstanceProvider extends ContentProvider {

    private static final int INSTANCES = 1;
    private static final int INSTANCE_ID = 2;

    private static final UriMatcher URI_MATCHER;

    private static InstancesDatabaseHelper dbHelper;

    @Inject
    PermissionsProvider permissionsProvider;

    private synchronized InstancesDatabaseHelper getDbHelper() {
        // wrapper to test and reset/set the dbHelper based upon the attachment state of the device.
        try {
            new StorageInitializer().createOdkDirsOnStorage();
        } catch (RuntimeException e) {
            Timber.e(e);
            return null;
        }

        if (dbHelper == null) {
            recreateDatabaseHelper();
        }

        return dbHelper;
    }

    public static void recreateDatabaseHelper() {
        dbHelper = new InstancesDatabaseHelper(new InstanceDatabaseMigrator(), new StoragePathProvider());
    }

    @SuppressWarnings("PMD.NonThreadSafeSingleton") // PMD thinks the `= null` is setting a singleton here
    public static void releaseDatabaseHelper() {
        if (dbHelper != null) {
            dbHelper.close();
            dbHelper = null;
        }
    }

    // Do not call it in onCreate() https://stackoverflow.com/questions/23521083/inject-database-in-a-contentprovider-with-dagger
    private void deferDaggerInit() {
        DaggerUtils.getComponent(getContext()).inject(this);
    }

    @Override
    public boolean onCreate() {
        // must be at the beginning of any activity that can be called from an external intent
        InstancesDatabaseHelper h = getDbHelper();
        return h != null;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            Timber.i("Storage permissions not granted");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(INSTANCES_TABLE_NAME);
        qb.setStrict(true);

        switch (URI_MATCHER.match(uri)) {
            case INSTANCES:
                break;

            case INSTANCE_ID:
                qb.appendWhere(InstanceColumns._ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        Cursor c = null;
        InstancesDatabaseHelper instancesDatabaseHelper = getDbHelper();
        if (instancesDatabaseHelper != null) {
            c = qb.query(instancesDatabaseHelper.getReadableDatabase(), projection, selection, selectionArgs, null, null, sortOrder);

            // Tell the cursor what uri to watch, so it knows when its source data changes
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return c;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case INSTANCES:
                return InstanceColumns.CONTENT_TYPE;

            case INSTANCE_ID:
                return InstanceColumns.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            Timber.i("Storage permissions not granted");
            return null;
        }

        // Validate the requested uri
        if (URI_MATCHER.match(uri) != INSTANCES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        InstancesDatabaseHelper instancesDatabaseHelper = getDbHelper();
        if (instancesDatabaseHelper != null) {
        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = System.currentTimeMillis();

        // Make sure that the fields are all set
        if (!values.containsKey(InstanceColumns.LAST_STATUS_CHANGE_DATE)) {
            values.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, now);
        }

            if (!values.containsKey(InstanceColumns.STATUS)) {
                values.put(InstanceColumns.STATUS, Instance.STATUS_INCOMPLETE);
            }

            long rowId = instancesDatabaseHelper.getWritableDatabase().insert(INSTANCES_TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri instanceUri = ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(instanceUri, null);
            return instanceUri;
        }
        }

        throw new SQLException("Failed to insert into the instances database.");
    }

    public static String getDisplaySubtext(Context context, String state, Date date) {
        try {
            if (state == null) {
                return new SimpleDateFormat(context.getString(R.string.added_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (Instance.STATUS_INCOMPLETE.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(context.getString(R.string.saved_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (Instance.STATUS_COMPLETE.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(context.getString(R.string.finalized_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (Instance.STATUS_SUBMITTED.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(context.getString(R.string.sent_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (Instance.STATUS_SUBMISSION_FAILED.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(
                        context.getString(R.string.sending_failed_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else {
                return new SimpleDateFormat(context.getString(R.string.added_on_date_at_time),
                        Locale.getDefault()).format(date);
            }
        } catch (IllegalArgumentException e) {
            Timber.e(e);
            return "";
        }
    }

    public void deleteAllFilesInDirectory(File directory) {     // smap make public
        if (directory.exists()) {
            // do not delete the directory if it might be an
            // ODK Tables instance data directory. Let ODK Tables
            // manage the lifetimes of its filled-in form data
            // media attachments.
            if (directory.isDirectory() && !Collect.isODKTablesInstanceDataDirectory(directory)) {
                // delete all the files in the directory
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File f : files) {
                        // should make this recursive if we get worried about
                        // the media directory containing directories
                        f.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * This method removes the entry from the content provider, and also removes any associated
     * files.
     * files:  form.xml, [formmd5].formdef, formname-media {directory}
     */
    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            return 0;
        }

        int count = 0;
        InstancesDatabaseHelper instancesDatabaseHelper = getDbHelper();
        if (instancesDatabaseHelper != null) {
            SQLiteDatabase db = instancesDatabaseHelper.getWritableDatabase();

        switch (URI_MATCHER.match(uri)) {
            case INSTANCES:
                Cursor del = null;
                try {
                    del = this.query(uri, null, where, whereArgs, null);
                    if (del != null && del.getCount() > 0) {
                        del.moveToFirst();
                        do {
                                String instanceFile = new StoragePathProvider().getAbsoluteInstanceFilePath(del.getString(
                                        del.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH)));
                            File instanceDir = (new File(instanceFile)).getParentFile();
                            deleteAllFilesInDirectory(instanceDir);
                        } while (del.moveToNext());
                    }
                } finally {
                    if (del != null) {
                        del.close();
                    }
                }
                count = db.delete(INSTANCES_TABLE_NAME, where, whereArgs);
                break;

            case INSTANCE_ID:
                // Keep sent instance database rows but delete corresponding files
                String instanceId = uri.getPathSegments().get(1);

                Cursor c = null;
                String status = null;
                try {
                    c = this.query(uri, null, where, whereArgs, null);
                    if (c != null && c.getCount() > 0) {
                        c.moveToFirst();
                        status = c.getString(c.getColumnIndexOrThrow(InstanceColumns.STATUS));
                        do {
                                String instanceFile = new StoragePathProvider().getAbsoluteInstanceFilePath(c.getString(
                                        c.getColumnIndexOrThrow(InstanceColumns.INSTANCE_FILE_PATH)));
                            File instanceDir = (new File(instanceFile)).getParentFile();
                            deleteAllFilesInDirectory(instanceDir);
                        } while (c.moveToNext());
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }

                ContentValues cv = new ContentValues();
                if (status != null && status.equals(Instance.STATUS_SUBMITTED)) {

                    cv.put(InstanceColumns.DELETED_DATE, System.currentTimeMillis());

                } else {
                    // smap Update the deleted date and also change the assignment status to closed
                    cv.put(InstanceColumns.DELETED_DATE, System.currentTimeMillis());
                    cv.put(InstanceColumns.T_TASK_STATUS, Utilities.STATUS_T_CLOSED);
                }
                // Geometry fields represent data inside the form which can be very
                // sensitive so they are removed on delete.
                cv.put(InstanceColumns.GEOMETRY_TYPE, (String) null);
                cv.put(InstanceColumns.GEOMETRY, (String) null);
                count = Collect.getInstance().getContentResolver().update(uri, cv, null, null);

                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        }

        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String where, String[] whereArgs) {
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            return 0;
        }

        int count = 0;
        InstancesDatabaseHelper instancesDatabaseHelper = getDbHelper();
        if (instancesDatabaseHelper != null) {
            SQLiteDatabase db = instancesDatabaseHelper.getWritableDatabase();

        Long now = System.currentTimeMillis();

            // Make sure that the fields are all set
            if (!values.containsKey(InstanceColumns.LAST_STATUS_CHANGE_DATE)) {
                values.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, now);
            }

            // Don't update last status change date if an instance is being deleted
            if (values.containsKey(InstanceColumns.DELETED_DATE)) {
                values.remove(InstanceColumns.LAST_STATUS_CHANGE_DATE);
            }

            switch (URI_MATCHER.match(uri)) {
                case INSTANCES:
                    count = db.update(INSTANCES_TABLE_NAME, values, where, whereArgs);
                    break;

            case INSTANCE_ID:
                String instanceId = uri.getPathSegments().get(1);

                    String[] newWhereArgs;
                    if (whereArgs == null || whereArgs.length == 0) {
                        newWhereArgs = new String[] {instanceId};
                    } else {
                        newWhereArgs = new String[whereArgs.length + 1];
                        newWhereArgs[0] = instanceId;
                        System.arraycopy(whereArgs, 0, newWhereArgs, 1, whereArgs.length);
                    }

                count =
                            db.update(INSTANCES_TABLE_NAME,
                                    values,
                                    InstanceColumns._ID
                                            + "=?"
                                            + (!TextUtils.isEmpty(where) ? " AND ("
                                            + where + ')' : ""), newWhereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        }

        return count;
    }

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(InstanceProviderAPI.AUTHORITY, "instances", INSTANCES);
        URI_MATCHER.addURI(InstanceProviderAPI.AUTHORITY, "instances/#", INSTANCE_ID);
    }
}
