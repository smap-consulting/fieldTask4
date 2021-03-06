
package org.odk.collect.android.database;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageSubdirectory;
import org.odk.collect.android.utilities.SQLiteUtils;

import java.io.File;
import timber.log.Timber;
import static android.provider.BaseColumns._ID;


/**
 * This class helps open, create, and upgrade the database file.
 */
public class SmapReferenceDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "reference.db";
    public static final String TABLE_NAME = "reference";

    static final int DATABASE_VERSION = 1;
    private static boolean isDatabaseBeingMigrated;

    public static final String REF_SOURCE = "source";
    public static final String REF_SURVEY = "survey";
    public static final String REF_REFERENCE_SURVEY = "reference_survey";
    public static final String REF_DATA_TABLE_NAME = "name";
    public static final String REF_COLUMN_NAMES = "columns";
    public static final String REF_UPDATED_TIME = "updated_time";


    public SmapReferenceDatabaseHelper() {
        super(new DatabaseContext(new StoragePathProvider().getDirPath(StorageSubdirectory.METADATA)), DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static String getDatabasePath() {
        return new StoragePathProvider().getDirPath(StorageSubdirectory.METADATA) + File.separator + DATABASE_NAME;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createLatestVersion(db);
    }

    /**
     * Upgrades the database.
     *
     * When a new migration is added, a corresponding test case should be added to
     * InstancesDatabaseHelperTest by copying a real database into assets.
     */
    @SuppressWarnings({"checkstyle:FallThrough"})
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            Timber.i("Upgrading database from version %d to %d", oldVersion, newVersion);

            if(oldVersion < DATABASE_VERSION) {   // smap
                upgradeToLatestVersion(db);
            }

            Timber.i("Upgrading database from version %d to %d completed with success.", oldVersion, newVersion);
            isDatabaseBeingMigrated = false;
        } catch (SQLException e) {
            isDatabaseBeingMigrated = false;
            throw e;
        }
    }

    private static void createLatestVersion(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + _ID + " integer primary key, "
                + REF_SOURCE + " text not null, "
                + REF_SURVEY + " text not null, "
                + REF_REFERENCE_SURVEY + " text not null, "
                + REF_DATA_TABLE_NAME + " text not null, "
                + REF_COLUMN_NAMES + " text not null, "
                + REF_UPDATED_TIME + " long not null "
                + ");");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            isDatabaseBeingMigrated = false;
        } catch (SQLException e) {
            isDatabaseBeingMigrated = false;
            throw e;
        }
    }

    private void upgradeToLatestVersion(SQLiteDatabase db) {
        //SQLiteUtils.addColumn(db, FORMS_TABLE_NAME, AUTO_SEND, "text");
    }

    public static void databaseMigrationStarted() {
        isDatabaseBeingMigrated = true;
    }

    public static boolean isDatabaseBeingMigrated() {
        return isDatabaseBeingMigrated;
    }

    public static boolean databaseNeedsUpgrade() {
        boolean isDatabaseHelperOutOfDate = false;
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(SmapReferenceDatabaseHelper.getDatabasePath(), null, SQLiteDatabase.OPEN_READONLY);
            isDatabaseHelperOutOfDate = DATABASE_VERSION != db.getVersion();
            db.close();
        } catch (SQLException e) {
            Timber.i(e);
        }
        return isDatabaseHelperOutOfDate;
    }

    public static void recreateDatabase() {

        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(FormsDatabaseHelper.getDatabasePath(), null, SQLiteDatabase.OPEN_READWRITE);
            SQLiteUtils.dropTable(db, TABLE_NAME);
            createLatestVersion(db);
            db.close();
        } catch (SQLException e) {
            Timber.i(e);
        }
    }
}
