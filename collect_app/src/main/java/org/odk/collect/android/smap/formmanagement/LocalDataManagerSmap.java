package org.odk.collect.android.smap.formmanagement;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.kxml2.io.KXmlParser;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.dao.SmapReferencesDao;
import org.odk.collect.android.external.ExternalDataUtil;
import org.odk.collect.android.external.ExternalSQLiteOpenHelper;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.smap.local.LocalSQLiteOpenHelperSmap;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageSubdirectory;
import org.odk.collect.android.taskModel.LinkedInstance;
import org.odk.collect.android.taskModel.LinkedSurvey;
import org.odk.collect.android.taskModel.ReferenceSurvey;
import org.odk.collect.android.tasks.FormLoaderTask;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import timber.log.Timber;

import static org.odk.collect.utilities.PathUtils.getAbsoluteFilePath;

public class LocalDataManagerSmap {

    FormLoaderTask formLoaderTask;

    public LocalDataManagerSmap(FormLoaderTask formLoaderTask) {
        this.formLoaderTask = formLoaderTask;
    }

    private class FormData {
        String name;
        ContentValues values = new ContentValues();
        HashMap<String, ArrayList<FormData>> subForms = new HashMap<> ();
    }

    public void loadLocalData(String surveyIdent, File formMediaDir) {

        StoragePathProvider storagePathProvider = new StoragePathProvider();
        SmapReferencesDao refDao = new SmapReferencesDao();
        Map<String, String> columnNamesCache = new HashMap<>();

        try {
            // 1. Get the hashmap of surveys referenced by the loading survey
            HashMap<String, LinkedSurvey> surveys = refDao.getLinkedSurveys(surveyIdent);

            // 2. Get the links to surveys whose data is referenced - from the references table
            if(surveys != null && surveys.size() > 0) {
                HashMap<String, ArrayList<ContentValues>> dataSets = new HashMap<> ();
                ArrayList<LinkedInstance> instances = getLinkedInstances(surveys);

                // 3. Process each instance
                if(instances.size() > 0) {
                    for (LinkedInstance li : instances) {

                        // 3. Convert contents of instance into a record
                        ArrayList<ContentValues> data = dataSets.get(li.survey.tableName);
                        if (data == null) {
                            data = new ArrayList<>();
                            dataSets.put(li.survey.tableName, data);
                        }

                        // Accumulate data in a FormData structure
                        FormData fd = new FormData();
                        FormData currentForm = fd;
                        currentForm.name = "main";
                        Stack<FormData> formDataStack = new Stack<>(); formDataStack.push(fd);

                        String absPath = getAbsoluteFilePath(storagePathProvider.getDirPath(StorageSubdirectory.INSTANCES), li.instanceFilePath);
                        XmlPullParser parser = new KXmlParser();
                        parser.setInput(new InputStreamReader(new FileInputStream(absPath), StandardCharsets.UTF_8));

                        String tag;
                        parser.nextToken();
                        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                            switch (parser.getEventType()) {
                                case XmlPullParser.START_TAG:
                                    tag = parser.getName();

                                    parser.next();
                                    if (parser.getEventType() == XmlPullParser.TEXT) {
                                        String value = parser.getText();
                                        Timber.i("#####################: " + tag + " : " + value);
                                        if(li.survey.columns.contains(tag)) {
                                            String safeColumnName = ExternalDataUtil.toSafeColumnName(tag, columnNamesCache);
                                            currentForm.values.put(safeColumnName, value);
                                        }
                                    } else if (parser.getEventType() == XmlPullParser.START_TAG) {
                                        Timber.i("#####################: Sub Form: " + tag);
                                        if(!tag.equals("main")) {   // Top level form main already has a form definition which is an entry point to the graph
                                            ArrayList<FormData> subFormArray = currentForm.subForms.get(tag);
                                            if (subFormArray == null) {
                                                subFormArray = new ArrayList<>();
                                                currentForm.subForms.put(tag, subFormArray);
                                            }
                                            FormData subFormData = new FormData();
                                            subFormArray.add(subFormData);
                                            currentForm = subFormData;
                                            currentForm.name = tag;
                                        }
                                    }
                                    break;
                                case XmlPullParser.END_TAG:
                                    tag = parser.getName();
                                    if(tag.equals(currentForm.name) && !formDataStack.empty()) {
                                        currentForm = formDataStack.pop();
                                        Timber.i("#####################: End Sub Form: " + tag);
                                    }



                                default:
                            }

                            parser.next();
                        }

                        // Convert FormData structure into records
                        ContentValues values = new ContentValues();
                        data.add(values);
                    }

                    // 4. Write instance records to the database table
                    for (String tableName: dataSets.keySet()) {

                        File dbFile = new File(formMediaDir.getAbsolutePath(), tableName + ".db");
                        if (!dbFile.exists()) {
                            FirebaseCrashlytics.getInstance().log("LocalCSV: csv table does not exist: " + dbFile.getAbsolutePath());
                        }
                        LocalSQLiteOpenHelperSmap localSQLiteOpenHelper = new LocalSQLiteOpenHelperSmap(dbFile);
                        localSQLiteOpenHelper.append(dataSets.get(tableName), formLoaderTask);
                    }
                }

            }
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(e);
        }


    }

    /*
     * Get the instances of the linked surveys
     */
    private ArrayList<LinkedInstance> getLinkedInstances(HashMap<String, LinkedSurvey> surveys) {
        ArrayList<LinkedInstance> instances = new ArrayList<>();

        InstancesDao instancesDao = new InstancesDao();
        try (Cursor cursor = instancesDao.getFinalizedDateOrderInstancesCursor()) {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    String surveyName =  cursor.getString(cursor.getColumnIndex(InstanceProviderAPI.InstanceColumns.JR_FORM_ID));
                    LinkedInstance li = new LinkedInstance();
                    li.survey = surveys.get(surveyName);
                    if(li.survey != null) {
                        // Need to process this survey
                        li.instanceFilePath = cursor.getString(cursor.getColumnIndex(InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH));
                        instances.add(li);
                        Timber.i("xxxxxxxxxxxxxxxxxxx: " + li.instanceFilePath);
                    } else {
                        Timber.i("xxxxxxxxxxxxxxxxxx: Survey " + surveyName + " is not referenced");
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return instances;
    }
}