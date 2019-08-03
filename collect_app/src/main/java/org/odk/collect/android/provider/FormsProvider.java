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
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.ItemsetDbAdapter;
import org.odk.collect.android.database.ODKSQLiteOpenHelper;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.MediaUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

import static org.odk.collect.android.utilities.PermissionUtils.areStoragePermissionsGranted;

public class FormsProvider extends ContentProvider {

    private static final String t = "FormsProvider";

    private static final String DATABASE_NAME = "forms.db";
    private static final int DATABASE_VERSION = 9;    // smap must be greater than 4
    private static final String FORMS_TABLE_NAME = "forms";

    private static HashMap<String, String> sFormsProjectionMap;

    private static final int FORMS = 1;
    private static final int FORM_ID = 2;
    // Forms unique by ID, keeping only the latest one downloaded
    private static final int NEWEST_FORMS_BY_FORM_ID = 3;

    private static final UriMatcher URI_MATCHER;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends ODKSQLiteOpenHelper {
        // These exist in database versions 2 and 3, but not in 4...
        private static final String TEMP_FORMS_TABLE_NAME = "forms_v4";
        private static final String MODEL_VERSION = "modelVersion";

        DatabaseHelper(String databaseName) {
            super(Collect.METADATA_PATH, databaseName, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            onCreateNamed(db, FORMS_TABLE_NAME);
        }

        private void onCreateNamed(SQLiteDatabase db, String tableName) {
            db.execSQL("CREATE TABLE " + tableName + " (" + FormsColumns._ID
                    + " integer primary key, " + FormsColumns.DISPLAY_NAME
                    + " text not null, " + FormsColumns.DISPLAY_SUBTEXT
                    + " text not null, " + FormsColumns.DESCRIPTION
                    + " text, "
                    + FormsColumns.JR_FORM_ID
                    + " text not null, "
                    + FormsColumns.JR_VERSION
                    + " text, "
                    + FormsColumns.PROJECT        // smap
                    + " text, "                    // smap
                    + FormsColumns.TASKS_ONLY    // smap
                    + " text, "                    // smap
                    + FormsColumns.SOURCE        // smap
                    + " text, "                    // smap
                    + FormsColumns.MD5_HASH
                    + " text not null, "
                    + FormsColumns.DATE
                    + " integer not null, " // milliseconds
                    + FormsColumns.FORM_MEDIA_PATH + " text not null, "
                    + FormsColumns.FORM_FILE_PATH + " text not null, "
                    + FormsColumns.LANGUAGE + " text, "
                    + FormsColumns.SUBMISSION_URI + " text, "
                    + FormsColumns.BASE64_RSA_PUBLIC_KEY + " text, "
                    + FormsColumns.JRCACHE_FILE_PATH + " text not null, "
                    + FormsColumns.AUTO_SEND + " text,"
                    + FormsColumns.AUTO_DELETE + " text,"
                    + FormsColumns.LAST_DETECTED_FORM_VERSION_HASH + " text);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            int initialVersion = oldVersion;
            if (oldVersion < 2) {
                Timber.w("Upgrading database from version " + oldVersion
                        + " to " + newVersion
                        + ", which will destroy all old data");
                db.execSQL("DROP TABLE IF EXISTS " + FORMS_TABLE_NAME);
                onCreate(db);
                return;
            } else if (oldVersion < 6) {
                Timber.w("Upgrading database from version " + oldVersion        // smap
                        + " to " + newVersion);
                // adding BASE64_RSA_PUBLIC_KEY and changing type and name of
                // integer MODEL_VERSION to text VERSION
                db.execSQL("DROP TABLE IF EXISTS " + TEMP_FORMS_TABLE_NAME);
                onCreateNamed(db, TEMP_FORMS_TABLE_NAME);
                db.execSQL("INSERT INTO "
                        + TEMP_FORMS_TABLE_NAME
                        + " ("
                        + FormsColumns._ID
                        + ", "
                        + FormsColumns.DISPLAY_NAME
                        + ", "
                        + FormsColumns.DISPLAY_SUBTEXT
                        + ", "
                        + FormsColumns.DESCRIPTION
                        + ", "
                        + FormsColumns.JR_FORM_ID
                        + ", "
                        + FormsColumns.MD5_HASH
                        + ", "
                        + FormsColumns.DATE
                        + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH
                        + ", "
                        + FormsColumns.FORM_FILE_PATH
                        + ", "
                        + FormsColumns.LANGUAGE
                        + ", "
                        + FormsColumns.SUBMISSION_URI
                        + ", "
                        + ((oldVersion > 3) ? ""                    // smap
                        : (FormsColumns.JR_VERSION + ", "))    // smap
                        + ((oldVersion < 5) ? ""                    // smap
                        : (FormsColumns.PROJECT + ", "))    // smap
                        + ((oldVersion < 6) ? ""                    // smap
                        : (FormsColumns.SOURCE + ", "))    // smap
                        + ((oldVersion < 4) ? ""                    // smap
                        : (FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "))
                        + FormsColumns.JRCACHE_FILE_PATH
                        + ") SELECT "
                        + FormsColumns._ID
                        + ", "
                        + FormsColumns.DISPLAY_NAME
                        + ", "
                        + FormsColumns.DISPLAY_SUBTEXT
                        + ", "
                        + FormsColumns.DESCRIPTION
                        + ", "
                        + FormsColumns.JR_FORM_ID
                        + ", "
                        + FormsColumns.MD5_HASH
                        + ", "
                        + FormsColumns.DATE
                        + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH
                        + ", "
                        + FormsColumns.FORM_FILE_PATH
                        + ", "
                        + FormsColumns.LANGUAGE
                        + ", "
                        + FormsColumns.SUBMISSION_URI
                        + ", "
                        + ((oldVersion > 3) ? ""                            // smap
                        : (
                        "CASE WHEN "        // smap
                                + MODEL_VERSION
                                + " IS NOT NULL THEN "
                                + "CAST("
                                + MODEL_VERSION
                                + " AS TEXT) ELSE NULL END, "
                ))
                        + ((oldVersion < 5) ? ""                        // smap
                        : (FormsColumns.PROJECT + ", "))        // smap
                        + ((oldVersion < 6) ? ""                        // smap
                        : (FormsColumns.SOURCE + ", "))            // smap
                        + ((oldVersion < 4) ? ""                        // smap
                        : (FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "))
                        + FormsColumns.JRCACHE_FILE_PATH + " FROM "
                        + FORMS_TABLE_NAME);

                // risky failures here...
                db.execSQL("DROP TABLE IF EXISTS " + FORMS_TABLE_NAME);
                onCreateNamed(db, FORMS_TABLE_NAME);
                db.execSQL("INSERT INTO "
                        + FORMS_TABLE_NAME
                        + " ("
                        + FormsColumns._ID
                        + ", "
                        + FormsColumns.DISPLAY_NAME
                        + ", "
                        + FormsColumns.DISPLAY_SUBTEXT
                        + ", "
                        + FormsColumns.DESCRIPTION
                        + ", "
                        + FormsColumns.JR_FORM_ID
                        + ", "
                        + FormsColumns.MD5_HASH
                        + ", "
                        + FormsColumns.DATE
                        + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH + ", "
                        + FormsColumns.FORM_FILE_PATH + ", "
                        + FormsColumns.LANGUAGE + ", "
                        + FormsColumns.SUBMISSION_URI + ", "
                        + FormsColumns.JR_VERSION + ", "
                        + FormsColumns.PROJECT + ", "                // smap
                        + FormsColumns.SOURCE + ", "                // smap
                        + FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "
                        + FormsColumns.JRCACHE_FILE_PATH + ") SELECT "
                        + FormsColumns._ID + ", "
                        + FormsColumns.DISPLAY_NAME
                        + ", "
                        + FormsColumns.DISPLAY_SUBTEXT
                        + ", "
                        + FormsColumns.DESCRIPTION
                        + ", "
                        + FormsColumns.JR_FORM_ID
                        + ", "
                        + FormsColumns.MD5_HASH
                        + ", "
                        + FormsColumns.DATE
                        + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH + ", "
                        + FormsColumns.FORM_FILE_PATH + ", "
                        + FormsColumns.LANGUAGE + ", "
                        + FormsColumns.SUBMISSION_URI + ", "
                        + FormsColumns.JR_VERSION + ", "
                        + FormsColumns.PROJECT + ", "                // smap
                        + FormsColumns.SOURCE + ", "                // smap
                        + FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "
                        + FormsColumns.JRCACHE_FILE_PATH + " FROM "
                        + TEMP_FORMS_TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + TEMP_FORMS_TABLE_NAME);

                Timber.w("Successfully upgraded database from version "
                        + initialVersion + " to " + newVersion
                        + ", without destroying all the old data");
            }

            if (oldVersion < 7) {
                try {
                    db.execSQL("ALTER TABLE " + FORMS_TABLE_NAME + " ADD COLUMN " +
                            FormsColumns.TASKS_ONLY + " text;");
                    db.execSQL("update " + FORMS_TABLE_NAME + " set " +
                            FormsColumns.TASKS_ONLY + " = 'no';");
                } catch (Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to forms database version 7");
                    e.printStackTrace();
                }
            }

            if (oldVersion < 8) {
                try {
                    db.execSQL("ALTER TABLE " + FORMS_TABLE_NAME + " ADD COLUMN " +
                            FormsColumns.AUTO_SEND + " text;");
                    db.execSQL("ALTER TABLE " + FORMS_TABLE_NAME + " ADD COLUMN " +
                            FormsColumns.AUTO_DELETE + " text;");
                } catch (Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to forms database version 8");
                    e.printStackTrace();
                }
            }
            if (oldVersion < 9) {
                try {
                    db.execSQL("ALTER TABLE " + FORMS_TABLE_NAME + " ADD COLUMN " +
                            FormsColumns.LAST_DETECTED_FORM_VERSION_HASH + " text;");
                } catch (Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to forms database version 8");
                    e.printStackTrace();
                }
            }
        }
    }

    private DatabaseHelper mDbHelper;

    private DatabaseHelper getDbHelper() {
        // wrapper to test and reset/set the dbHelper based upon the attachment state of the device.
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            mDbHelper = null;
            return null;
        }

        if (mDbHelper != null) {
            return mDbHelper;
        }
        mDbHelper = new DatabaseHelper(DATABASE_NAME);      // smap
        return mDbHelper;
    }

    @Override
    public boolean onCreate() {

        if (!areStoragePermissionsGranted(getContext())) {
            Timber.i("Read and write permissions are required for this content provider to function.");
            return false;
        }

        // must be at the beginning of any activity that can be called from an external intent
        DatabaseHelper h = getDbHelper();
        return h != null;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        if (!areStoragePermissionsGranted(getContext())) {
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(FORMS_TABLE_NAME);
        qb.setProjectionMap(sFormsProjectionMap);
        qb.setStrict(true);

        String groupBy = null;
        switch (URI_MATCHER.match(uri)) {
            case FORMS:
                break;

            case FORM_ID:
                qb.appendWhere(FormsColumns._ID + "="
                        + uri.getPathSegments().get(1));
                break;

                // Only include the latest form that was downloaded with each form_id
                case NEWEST_FORMS_BY_FORM_ID:
                    Map<String, String> filteredProjectionMap = new HashMap<>(sFormsProjectionMap);
                    filteredProjectionMap.put(FormsColumns.DATE, FormsColumns.MAX_DATE);

                    qb.setProjectionMap(filteredProjectionMap);
                    groupBy = FormsColumns.JR_FORM_ID;
                    break;

            default:
                //throw new IllegalArgumentException("Unknown URI " + uri);     smap don't throw exception this prevents crash when launching from fill blank form
        }

        // Get the database and run the query
        SQLiteDatabase db = getDbHelper().getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null,
                null, sortOrder);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case FORMS:
            case NEWEST_FORMS_BY_FORM_ID:
                return FormsColumns.CONTENT_TYPE;

            case FORM_ID:
                return FormsColumns.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (URI_MATCHER.match(uri) != FORMS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }


        if (!areStoragePermissionsGranted(getContext())) {
            return null;
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        if (!values.containsKey(FormsColumns.FORM_FILE_PATH)) {
            throw new IllegalArgumentException(FormsColumns.FORM_FILE_PATH
                    + " must be specified.");
        }

        // Normalize the file path.
        // (don't trust the requester).
        String filePath = values.getAsString(FormsColumns.FORM_FILE_PATH);
        File form = new File(filePath);
        filePath = form.getAbsolutePath(); // normalized
        values.put(FormsColumns.FORM_FILE_PATH, filePath);

        Long now = System.currentTimeMillis();

        // Make sure that the necessary fields are all set
        if (!values.containsKey(FormsColumns.DATE)) {
            values.put(FormsColumns.DATE, now);
        }

            if (!values.containsKey(FormsColumns.DISPLAY_NAME)) {
                values.put(FormsColumns.DISPLAY_NAME, form.getName());
            }

        // don't let users put in a manual md5 hash
        if (values.containsKey(FormsColumns.MD5_HASH)) {
            values.remove(FormsColumns.MD5_HASH);
        }
        String md5 = FileUtils.getMd5Hash(form);
        values.put(FormsColumns.MD5_HASH, md5);

            if (!values.containsKey(FormsColumns.JRCACHE_FILE_PATH)) {
                String cachePath = Collect.CACHE_PATH + File.separator + md5
                        + ".formdef";
                values.put(FormsColumns.JRCACHE_FILE_PATH, cachePath);
            }
            if (!values.containsKey(FormsColumns.FORM_MEDIA_PATH)) {
                values.put(FormsColumns.FORM_MEDIA_PATH, FileUtils.constructMediaPath(filePath));
            }

        SQLiteDatabase db = getDbHelper().getWritableDatabase();

        // first try to see if a record with this filename already exists...
        String[] projection = {FormsColumns._ID, FormsColumns.FORM_FILE_PATH};
        String[] selectionArgs = {filePath};
        String selection = FormsColumns.FORM_FILE_PATH + "=?";
        Cursor c = null;
        try {
            c = db.query(FORMS_TABLE_NAME, projection, selection,
                    selectionArgs, null, null, null);
            if (c.getCount() > 0) {
                // already exists
                throw new SQLException("FAILED Insert into " + uri
                        + " -- row already exists for form definition file: "
                        + filePath);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        long rowId = db.insert(FORMS_TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI,
                    rowId);
            getContext().getContentResolver().notifyChange(formUri, null);
                getContext().getContentResolver().notifyChange(FormsProviderAPI.FormsColumns.CONTENT_NEWEST_FORMS_BY_FORMID_URI, null);
            return formUri;
        }

        throw new SQLException("Failed to insert into the forms database.");
    }

    private void deleteFileOrDir(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            if (file.isDirectory()) {
                // delete any media entries for files in this directory...
                int images = MediaUtils
                        .deleteImagesInFolderFromMediaProvider(file);
                int audio = MediaUtils
                        .deleteAudioInFolderFromMediaProvider(file);
                int video = MediaUtils
                        .deleteVideoInFolderFromMediaProvider(file);

                Timber.i("removed from content providers: %d image files, %d audio files, and %d"
                        + " video files.", images, audio, video);

                // delete all the containing files
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        // should make this recursive if we get worried about
                        // the media directory containing directories
                        Timber.i("attempting to delete file: %s", f.getAbsolutePath());
                        f.delete();
                    }
                }
            }
            file.delete();
            Timber.i("attempting to delete file: %s", file.getAbsolutePath());
        }
    }

    /**
     * This method removes the entry from the content provider, and also removes
     * any associated files. files: form.xml, [formmd5].formdef, formname-media
     * {directory}
     */
    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        if (!areStoragePermissionsGranted(getContext())) {
            return 0;
        }
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        int count;

            switch (URI_MATCHER.match(uri)) {
            case FORMS:
                Cursor del = null;
                try {
                    del = this.query(uri, null, where, whereArgs, null);
                        if (del != null && del.getCount() > 0) {
                        del.moveToFirst();
                        do {
                            deleteFileOrDir(del
                                    .getString(del
                                            .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                            String formFilePath = del.getString(del
                                    .getColumnIndex(FormsColumns.FORM_FILE_PATH));
                            deleteFileOrDir(formFilePath);
                            deleteFileOrDir(del.getString(del
                                    .getColumnIndex(FormsColumns.FORM_MEDIA_PATH)));
                        } while (del.moveToNext());
                    }
                } finally {
                    if (del != null) {
                        del.close();
                    }
                }
                count = db.delete(FORMS_TABLE_NAME, where, whereArgs);
                break;

            case FORM_ID:
                String formId = uri.getPathSegments().get(1);

                Cursor c = null;
                try {
                    c = this.query(uri, null, where, whereArgs, null);
                    // This should only ever return 1 record.
                        if (c != null && c.getCount() > 0) {
                        c.moveToFirst();
                        do {
                            deleteFileOrDir(c.getString(c
                                    .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                            String formFilePath = c.getString(c
                                    .getColumnIndex(FormsColumns.FORM_FILE_PATH));
                            deleteFileOrDir(formFilePath);
                            deleteFileOrDir(c.getString(c
                                    .getColumnIndex(FormsColumns.FORM_MEDIA_PATH)));

                            try {
                                // get rid of the old tables
                                ItemsetDbAdapter ida = new ItemsetDbAdapter();
                                ida.open();
                                ida.delete(c.getString(c
                                        .getColumnIndex(FormsColumns.FORM_MEDIA_PATH))
                                        + "/itemsets.csv");
                                ida.close();
                            } catch (Exception e) {
                                // if something else is accessing the provider this may not exist
                                // so catch it and move on.
                            }

                        } while (c.moveToNext());
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }

                count = db.delete(
                        FORMS_TABLE_NAME,
                        FormsColumns._ID
                                    + "=?"
                                + (!TextUtils.isEmpty(where) ? " AND (" + where
                                    + ')' : ""), prepareWhereArgs(whereArgs, formId));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        getContext().getContentResolver().notifyChange(FormsColumns.CONTENT_NEWEST_FORMS_BY_FORMID_URI, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where,
                      String[] whereArgs) {

        if (!areStoragePermissionsGranted(getContext())) {
            return 0;
        }
        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        int count = 0;
        switch (URI_MATCHER.match(uri)) {
            case FORMS:
                // don't let users manually update md5
                if (values.containsKey(FormsColumns.MD5_HASH)) {
                    values.remove(FormsColumns.MD5_HASH);
                }
                // if values contains path, then all filepaths and md5s will get
                // updated
                // this probably isn't a great thing to do.
                if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
                    String formFile = values
                            .getAsString(FormsColumns.FORM_FILE_PATH);
                    values.put(FormsColumns.MD5_HASH,
                            FileUtils.getMd5Hash(new File(formFile)));
                }

                Cursor c = null;
                try {
                    c = this.query(uri, null, where, whereArgs, null);

                    if (c != null && c.getCount() > 0) {
                        c.moveToPosition(-1);
                        while (c.moveToNext()) {
                            // before updating the paths, delete all the files
                            if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
                                String newFile = values
                                        .getAsString(FormsColumns.FORM_FILE_PATH);
                                String delFile = c
                                        .getString(c
                                                .getColumnIndex(FormsColumns.FORM_FILE_PATH));
                                    if (!newFile.equalsIgnoreCase(delFile)) {
                                    deleteFileOrDir(delFile);
                                }

                                // either way, delete the old cache because we'll
                                // calculate a new one.
                                deleteFileOrDir(c
                                        .getString(c
                                                .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                            }
                        }
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }

                count = db.update(FORMS_TABLE_NAME, values, where, whereArgs);
                break;

            case FORM_ID:
                String formId = uri.getPathSegments().get(1);
                // Whenever file paths are updated, delete the old files.

                Cursor update = null;
                try {
                    update = this.query(uri, null, where, whereArgs, null);

                    // This should only ever return 1 record.
                    if (update != null && update.getCount() > 0) {
                        update.moveToFirst();

                        // don't let users manually update md5
                        if (values.containsKey(FormsColumns.MD5_HASH)) {
                            values.remove(FormsColumns.MD5_HASH);
                        }

                        // the order here is important (jrcache needs to be before
                        // form file)
                        // because we update the jrcache file if there's a new form
                        // file
                        if (values.containsKey(FormsColumns.JRCACHE_FILE_PATH)) {
                            deleteFileOrDir(update
                                    .getString(update
                                            .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                        }

                        if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
                            String formFile = values
                                    .getAsString(FormsColumns.FORM_FILE_PATH);
                            String oldFile = update.getString(update
                                    .getColumnIndex(FormsColumns.FORM_FILE_PATH));

                            if (formFile == null || !formFile.equalsIgnoreCase(oldFile)) {
                                deleteFileOrDir(oldFile);
                            }

                            count = db.update(
                                    FORMS_TABLE_NAME,
                                    values,
                                    FormsColumns._ID
                                            + "=?"
                                        + (!TextUtils.isEmpty(where) ? " AND ("
                                            + where + ')' : ""), prepareWhereArgs(whereArgs, formId));
                    } else {
                        Timber.e("Attempting to update row that does not exist");
                    }
                } finally {
                    if (update != null) {
                        update.close();
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        getContext().getContentResolver().notifyChange(FormsProviderAPI.FormsColumns.CONTENT_NEWEST_FORMS_BY_FORMID_URI, null);
        return count;
    }

    @NonNull
    private String[] prepareWhereArgs(String[] whereArgs, String formId) {
        String[] newWhereArgs;
        if (whereArgs == null || whereArgs.length == 0) {
            newWhereArgs = new String[] {formId};
        } else {
            newWhereArgs = new String[(whereArgs.length + 1)];
            newWhereArgs[0] = formId;
            System.arraycopy(whereArgs, 0, newWhereArgs, 1, whereArgs.length);
        }
        return newWhereArgs;
    }

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(FormsProviderAPI.AUTHORITY, "forms", FORMS);
        URI_MATCHER.addURI(FormsProviderAPI.AUTHORITY, "forms/#", FORM_ID);
        // Only available for query and type
        URI_MATCHER.addURI(FormsProviderAPI.AUTHORITY, FormsColumns.CONTENT_NEWEST_FORMS_BY_FORMID_URI.getPath().replaceAll("^/+", ""), NEWEST_FORMS_BY_FORM_ID);

        sFormsProjectionMap = new HashMap<>();
        sFormsProjectionMap.put(FormsColumns._ID, FormsColumns._ID);
        sFormsProjectionMap.put(FormsColumns.DISPLAY_NAME, FormsColumns.DISPLAY_NAME);
        sFormsProjectionMap.put(FormsColumns.DESCRIPTION, FormsColumns.DESCRIPTION);
        sFormsProjectionMap.put(FormsColumns.JR_FORM_ID, FormsColumns.JR_FORM_ID);
        sFormsProjectionMap.put(FormsColumns.JR_VERSION, FormsColumns.JR_VERSION);
        sFormsProjectionMap.put(FormsColumns.SUBMISSION_URI, FormsColumns.SUBMISSION_URI);
        sFormsProjectionMap.put(FormsColumns.PROJECT, FormsColumns.PROJECT);                        // smap
        sFormsProjectionMap.put(FormsColumns.TASKS_ONLY, FormsColumns.TASKS_ONLY);                   // smap
        sFormsProjectionMap.put(FormsColumns.SOURCE, FormsColumns.SOURCE);                        // smap
        sFormsProjectionMap.put(FormsColumns.BASE64_RSA_PUBLIC_KEY, FormsColumns.BASE64_RSA_PUBLIC_KEY);
        sFormsProjectionMap.put(FormsColumns.MD5_HASH, FormsColumns.MD5_HASH);
        sFormsProjectionMap.put(FormsColumns.DATE, FormsColumns.DATE);
        sFormsProjectionMap.put(FormsColumns.FORM_MEDIA_PATH, FormsColumns.FORM_MEDIA_PATH);
        sFormsProjectionMap.put(FormsColumns.FORM_FILE_PATH, FormsColumns.FORM_FILE_PATH);
        sFormsProjectionMap.put(FormsColumns.JRCACHE_FILE_PATH, FormsColumns.JRCACHE_FILE_PATH);
        sFormsProjectionMap.put(FormsColumns.LANGUAGE, FormsColumns.LANGUAGE);
        sFormsProjectionMap.put(FormsColumns.AUTO_DELETE, FormsColumns.AUTO_DELETE);
        sFormsProjectionMap.put(FormsColumns.AUTO_SEND, FormsColumns.AUTO_SEND);
        sFormsProjectionMap.put(FormsColumns.LAST_DETECTED_FORM_VERSION_HASH, FormsColumns.LAST_DETECTED_FORM_VERSION_HASH);
    }
}
