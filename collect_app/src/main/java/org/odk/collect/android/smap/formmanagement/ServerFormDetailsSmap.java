/*
 * Copyright (C) 2011 University of Washington
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

package org.odk.collect.android.smap.formmanagement;

import org.odk.collect.android.forms.ManifestFile;

import java.io.Serializable;

public class ServerFormDetailsSmap implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String formName;
    private final String downloadUrl;
    private final String formID;
    private final String formVersion;
    private final String hash;
    private final boolean isNotOnDevice;
    private final boolean isUpdated;
    private final String manifestUrl;       // smap
    private boolean isFormNotDownloaded;    // smap
    private boolean tasks_only;             // smap
    private String formPath;                // smap
    private String project;                 // smap

    private final ManifestFile manifest;

    public ServerFormDetailsSmap(String formName, String downloadUrl, String formID,
                                 String formVersion, String hash,
                                 boolean isNotOnDevice, boolean isUpdated, ManifestFile manifest,
                                 String manifestUrl,    // smap
                                 boolean isFormNotDownloaded,
                                 boolean tasks_only,
                                 String formPath,
                                 String project) {   // smap add formNotDownloaded, tasks_only, formPath, project

        this.formName = formName;
        this.downloadUrl = downloadUrl;
        this.formID = formID;
        this.formVersion = formVersion;
        this.hash = hash;
        this.isNotOnDevice = isNotOnDevice;
        this.isUpdated = isUpdated;
        this.manifestUrl = manifestUrl;     // smap
        this.isFormNotDownloaded = isFormNotDownloaded;   // smap
        this.tasks_only = tasks_only;   // smap
        this.formPath = formPath;       // smap
        this.project = project;       // smap
        this.manifest = manifest;
    }

    public String getFormName() {
        return formName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getFormId() {
        return formID;
    }

    public String getFormVersion() {
        return formVersion;
    }

    public String getManifestUrl() {
        return manifestUrl;
    }           // smap

    public boolean getTasksOnly() {
        return tasks_only;
    }           // smap

    public boolean isFormNotDownloaded() {
        return isFormNotDownloaded;
    }           // smap

    public String getFormPath() {
        return formPath;
    }           // smap

    public String getProject() {
        return project;
    }           // smap

    public String getHash() {
        return hash;
    }

    public boolean isNotOnDevice() {
        return isNotOnDevice;
    }

    public boolean isUpdated() {
        return isUpdated;
    }

    public ManifestFile getManifest() {
        return manifest;
    }
}