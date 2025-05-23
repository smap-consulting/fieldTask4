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

import org.odk.collect.android.database.FormDatabaseMigrator;
import org.odk.collect.android.database.FormsDatabaseHelper;
import org.odk.collect.android.fastexternalitemset.ItemsetDbAdapter;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.storage.StorageInitializer;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageSubdirectory;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.utilities.Clock;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import timber.log.Timber;

import static org.odk.collect.android.database.DatabaseConstants.FORMS_TABLE_NAME;
import static org.odk.collect.utilities.PathUtils.getAbsoluteFilePath;

public class FormsProvider extends ContentProvider {
    private static HashMap<String, String> sFormsProjectionMap;

    private static final int FORMS = 1;
    private static final int FORM_ID = 2;
    // Forms unique by ID, keeping only the latest one downloaded
    private static final int NEWEST_FORMS_BY_FORM_ID = 3;

    private static final UriMatcher URI_MATCHER;

    private static FormsDatabaseHelper dbHelper;

    @Inject
    PermissionsProvider permissionsProvider;

    @Inject
    Clock clock;

    private synchronized FormsDatabaseHelper getDbHelper() {
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
        dbHelper = new FormsDatabaseHelper(new FormDatabaseMigrator(), new StoragePathProvider());
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
        FormsDatabaseHelper h = getDbHelper();
        return h != null;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(FORMS_TABLE_NAME);
        qb.setProjectionMap(sFormsProjectionMap);
        qb.setStrict(true);

        Cursor c = null;
        String groupBy = null;
        FormsDatabaseHelper formsDatabaseHelper = getDbHelper();
        if (formsDatabaseHelper != null) {
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
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }
            c = qb.query(formsDatabaseHelper.getReadableDatabase(), projection, selection, selectionArgs, groupBy, null, sortOrder);

            // Tell the cursor what uri to watch, so it knows when its source data changes
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }

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
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            return null;
        }

        // Validate the requested uri
        if (URI_MATCHER.match(uri) != FORMS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        FormsDatabaseHelper formsDatabaseHelper = getDbHelper();
        if (formsDatabaseHelper != null) {
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
            StoragePathProvider storagePathProvider = new StoragePathProvider();
            String filePath = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), values.getAsString(FormsColumns.FORM_FILE_PATH));
            File form = new File(filePath);
            filePath = form.getAbsolutePath(); // normalized
                values.put(FormsColumns.FORM_FILE_PATH, storagePathProvider.getFormDbPath(filePath));

            Long now = clock.getCurrentTime();

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
                values.put(FormsColumns.JRCACHE_FILE_PATH, storagePathProvider.getCacheDbPath(md5 + ".formdef"));
            }
            if (!values.containsKey(FormsColumns.FORM_MEDIA_PATH)) {
                values.put(FormsColumns.FORM_MEDIA_PATH, storagePathProvider.getFormDbPath(FileUtils.constructMediaPath(filePath)));
            }

            SQLiteDatabase db = formsDatabaseHelper.getWritableDatabase();

            // first try to see if a record with this filename already exists...
            String[] projection = {FormsColumns._ID, FormsColumns.FORM_FILE_PATH};
                String[] selectionArgs = {storagePathProvider.getFormDbPath(filePath)};
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

            long rowId = db.insertOrThrow(FORMS_TABLE_NAME, null, values);
            if (rowId > 0) {
                Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI,
                        rowId);
                getContext().getContentResolver().notifyChange(formUri, null);
                    getContext().getContentResolver().notifyChange(FormsColumns.CONTENT_NEWEST_FORMS_BY_FORMID_URI, null);
                return formUri;
            } else {        // smap - additional debug information
                throw new SQLException("Failed to insert into the forms database - rowId zero. Table: " + FORMS_TABLE_NAME + " Values:" + values.toString());
            }
        } else {    // smap additional debug information
            throw new SQLException("Failed to insert into the forms database - FormDatabaseHelper not found.");
        }

        //throw new SQLException("Failed to insert into the forms database.");  // smap - not used
    }

    private void deleteFileOrDir(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            if (file.isDirectory()) {
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
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            return 0;
        }

        StoragePathProvider storagePathProvider = new StoragePathProvider();
        int count = 0;
        FormsDatabaseHelper formsDatabaseHelper = getDbHelper();
        if (formsDatabaseHelper != null) {
            SQLiteDatabase db = formsDatabaseHelper.getWritableDatabase();

            switch (URI_MATCHER.match(uri)) {
            case FORMS:
                Cursor del = null;
                try {
                    del = this.query(uri, null, where, whereArgs, null);
                    if (del != null && del.getCount() > 0) {
                        del.moveToFirst();
                        do {
                            deleteFileOrDir(storagePathProvider.getAbsoluteCacheFilePath(del
                                    .getString(del
                                            .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH))));
                            String formFilePath = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), del.getString(del
                                    .getColumnIndex(FormsColumns.FORM_FILE_PATH)));
                            deleteFileOrDir(formFilePath);
                            deleteFileOrDir(getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), del.getString(del
                                    .getColumnIndex(FormsColumns.FORM_MEDIA_PATH))));
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
                                deleteFileOrDir(storagePathProvider.getAbsoluteCacheFilePath(c.getString(c
                                        .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH))));
                                String formFilePath = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), c.getString(c
                                        .getColumnIndex(FormsColumns.FORM_FILE_PATH)));
                                deleteFileOrDir(formFilePath);
                                deleteFileOrDir(getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), c.getString(c
                                        .getColumnIndex(FormsColumns.FORM_MEDIA_PATH))));

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
        }

        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        deferDaggerInit();
        if (!permissionsProvider.areStoragePermissionsGranted()) {
            return 0;
        }

        StoragePathProvider storagePathProvider = new StoragePathProvider();

        int count = 0;
        FormsDatabaseHelper formsDatabaseHelper = getDbHelper();
        if (formsDatabaseHelper != null) {
            SQLiteDatabase db = formsDatabaseHelper.getWritableDatabase();
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
                        String formFile = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), values
                                .getAsString(FormsColumns.FORM_FILE_PATH));
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
                                    String newFile = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), values
                                            .getAsString(FormsColumns.FORM_FILE_PATH));
                                    String delFile = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), c
                                            .getString(c
                                                        .getColumnIndex(FormsColumns.FORM_FILE_PATH)));
                                        if (!newFile.equalsIgnoreCase(delFile)) {
                                        deleteFileOrDir(delFile);
                                    }

                                    // either way, delete the old cache because we'll
                                    // calculate a new one.
                                        deleteFileOrDir(storagePathProvider.getAbsoluteCacheFilePath(c
                                            .getString(c
                                                        .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH))));
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
                                    deleteFileOrDir(storagePathProvider.getAbsoluteCacheFilePath(update
                                        .getString(update
                                                    .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH))));
                            }

                            if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
                                String formFile = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), values
                                        .getAsString(FormsColumns.FORM_FILE_PATH));
                                String oldFile = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.FORMS), update.getString(update
                                        .getColumnIndex(FormsColumns.FORM_FILE_PATH)));

                                if (formFile == null || !formFile.equalsIgnoreCase(oldFile)) {
                                    deleteFileOrDir(oldFile);
                                }

                                    // we're updating our file, so update the md5
                                    // and get rid of the cache (doesn't harm anything)
                                    deleteFileOrDir(storagePathProvider.getAbsoluteCacheFilePath(update
                                            .getString(update
                                                    .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH))));
                                    String newMd5 = FileUtils
                                            .getMd5Hash(new File(formFile));
                                    values.put(FormsColumns.MD5_HASH, newMd5);
                                    values.put(FormsColumns.JRCACHE_FILE_PATH,
                                            storagePathProvider.getCacheDbPath(newMd5 + ".formdef"));
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
            getContext().getContentResolver().notifyChange(FormsColumns.CONTENT_NEWEST_FORMS_BY_FORMID_URI, null);
        }

        return count;
    }

    @NonNull
    private String[] prepareWhereArgs(String[] whereArgs, String formId) {
        String[] newWhereArgs;
        if (whereArgs == null || whereArgs.length == 0) {
            newWhereArgs = new String[] {formId};
        } else {
            newWhereArgs = new String[whereArgs.length + 1];
            newWhereArgs[0] = formId;
            System.arraycopy(whereArgs, 0, newWhereArgs, 1, whereArgs.length);
        }
        return newWhereArgs;
    }

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(FormsProviderAPI.AUTHORITY, FormsColumns.CONTENT_URI.getPath(), FORMS);
        URI_MATCHER.addURI(FormsProviderAPI.AUTHORITY, FormsColumns.CONTENT_URI.getPath() + "/#", FORM_ID);
        // Only available for query and type
        URI_MATCHER.addURI(FormsProviderAPI.AUTHORITY, FormsColumns.CONTENT_NEWEST_FORMS_BY_FORMID_URI.getPath(), NEWEST_FORMS_BY_FORM_ID);

        sFormsProjectionMap = new HashMap<>();
        sFormsProjectionMap.put(FormsColumns._ID, FormsColumns._ID);
        sFormsProjectionMap.put(FormsColumns.DISPLAY_NAME, FormsColumns.DISPLAY_NAME);
        sFormsProjectionMap.put(FormsColumns.DESCRIPTION, FormsColumns.DESCRIPTION);
        sFormsProjectionMap.put(FormsColumns.JR_FORM_ID, FormsColumns.JR_FORM_ID);
        sFormsProjectionMap.put(FormsColumns.JR_VERSION, FormsColumns.JR_VERSION);
        sFormsProjectionMap.put(FormsColumns.SUBMISSION_URI, FormsColumns.SUBMISSION_URI);
        sFormsProjectionMap.put(FormsColumns.PROJECT, FormsColumns.PROJECT);                              // smap
        sFormsProjectionMap.put(FormsColumns.TASKS_ONLY, FormsColumns.TASKS_ONLY);                        // smap
        sFormsProjectionMap.put(FormsColumns.READ_ONLY, FormsColumns.READ_ONLY);                        // smap
        sFormsProjectionMap.put(FormsColumns.SEARCH_LOCAL_DATA, FormsColumns.SEARCH_LOCAL_DATA);          // smap
        sFormsProjectionMap.put(FormsColumns.SOURCE, FormsColumns.SOURCE);                                // smap
        sFormsProjectionMap.put(FormsColumns.BASE64_RSA_PUBLIC_KEY, FormsColumns.BASE64_RSA_PUBLIC_KEY);
        sFormsProjectionMap.put(FormsColumns.MD5_HASH, FormsColumns.MD5_HASH);
        sFormsProjectionMap.put(FormsColumns.DATE, FormsColumns.DATE);
        sFormsProjectionMap.put(FormsColumns.FORM_MEDIA_PATH, FormsColumns.FORM_MEDIA_PATH);
        sFormsProjectionMap.put(FormsColumns.FORM_FILE_PATH, FormsColumns.FORM_FILE_PATH);
        sFormsProjectionMap.put(FormsColumns.JRCACHE_FILE_PATH, FormsColumns.JRCACHE_FILE_PATH);
        sFormsProjectionMap.put(FormsColumns.LANGUAGE, FormsColumns.LANGUAGE);
        sFormsProjectionMap.put(FormsColumns.AUTO_DELETE, FormsColumns.AUTO_DELETE);
        sFormsProjectionMap.put(FormsColumns.AUTO_SEND, FormsColumns.AUTO_SEND);
        //sFormsProjectionMap.put(FormsColumns.GEOMETRY_XPATH, FormsColumns.GEOMETRY_XPATH);   // smap
        sFormsProjectionMap.put(FormsColumns.DELETED_DATE, FormsColumns.DELETED_DATE);
    }
}
