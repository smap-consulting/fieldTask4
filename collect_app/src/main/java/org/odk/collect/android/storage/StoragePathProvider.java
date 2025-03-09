package org.odk.collect.android.storage;

import android.os.Environment;

import org.odk.collect.android.application.Collect;

import java.io.File;

import static org.odk.collect.utilities.PathUtils.getAbsoluteFilePath;
import static org.odk.collect.utilities.PathUtils.getRelativeFilePath;

public class StoragePathProvider {

    private StorageStateProvider storageStateProvider;

    public StoragePathProvider() {
        this(new StorageStateProvider());
    }

    public StoragePathProvider(StorageStateProvider storageStateProvider) {
        this.storageStateProvider = storageStateProvider;
    }

    public String[] getOdkDirPaths() {
        return getOdkDirPathsForScopedStorage();
    }

    private String[] getOdkDirPathsForScopedStorage() {
        return new String[]{
                getDirPath(StorageSubdirectory.FORMS),
                getDirPath(StorageSubdirectory.INSTANCES),
                getDirPath(StorageSubdirectory.CACHE),
                getDirPath(StorageSubdirectory.METADATA),
                getDirPath(StorageSubdirectory.LAYERS)
        };
    }

    public String getScopedStorageRootDirPath() {
        File scopedExternalFilesDirPath = Collect.getInstance().getExternalFilesDir(null);
        return scopedExternalFilesDirPath != null
                ? scopedExternalFilesDirPath.getAbsolutePath()
                : "";
    }

    public String getUnscopedStorageRootDirPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "fieldTask";  // smap
    }

    public String getUnscopedStorageDirPath(StorageSubdirectory subdirectory) {
        return getUnscopedStorageRootDirPath() + File.separator + subdirectory.getDirectoryName();
    }

    private String getScopedStorageDirPath(StorageSubdirectory subdirectory) {
        return getScopedStorageRootDirPath() + File.separator + subdirectory.getDirectoryName();
    }

    public String getDirPath(StorageSubdirectory subdirectory) {
        return getScopedStorageDirPath(subdirectory);
    }

    public String getStorageRootDirPath() {
        return getScopedStorageRootDirPath();
    }

    public String getCustomSplashScreenImagePath() {
        return getStorageRootDirPath() + File.separator + "customSplashScreenImage.jpg";
    }

    public String getTmpImageFilePath() {
        return getDirPath(StorageSubdirectory.CACHE) + File.separator + "tmp.jpg";
    }

    public String getTmpVideoFilePath() {
        return getDirPath(StorageSubdirectory.CACHE) + File.separator + "tmp.mp4";
    }

    public String getInstanceDbPath(String filePath) {
        return getDbPath(getDirPath(StorageSubdirectory.INSTANCES), filePath);
    }

    public String getAbsoluteInstanceFilePath(String filePath) {
        return getAbsoluteFilePath(getDirPath(StorageSubdirectory.INSTANCES), filePath);
    }

    public String getAbsoluteFormFilePath(String filePath) {
        return getAbsoluteFilePath(getDirPath(StorageSubdirectory.FORMS), filePath);
    }

    public String getFormDbPath(String filePath) {
        return getDbPath(getDirPath(StorageSubdirectory.FORMS), filePath);
    }

    public String getCacheDbPath(String filePath) {
        return getDbPath(getDirPath(StorageSubdirectory.CACHE), filePath);
    }

    public String getAbsoluteCacheFilePath(String filePath) {
        return getAbsoluteFilePath(getDirPath(StorageSubdirectory.CACHE), filePath);
    }

    private String getDbPath(String dirPath, String filePath) {
        String relativeFilePath;
        if (filePath.startsWith(dirPath)) {
            relativeFilePath = getRelativeFilePath(dirPath, filePath);
        } else {
            relativeFilePath = filePath;
        }

        return relativeFilePath;
    }

    @SuppressWarnings("PMD.DoNotHardCodeSDCard")
    public String getRelativeMapLayerPath(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("/sdcard/fieldTask/layers")) {    // smap
            return path.substring("/sdcard/fieldTask/layers".length() + 1);   // smap
        } else if (path.startsWith(getUnscopedStorageDirPath(StorageSubdirectory.LAYERS))) {
            return path.substring(getUnscopedStorageDirPath(StorageSubdirectory.LAYERS).length() + 1);
        } else if (path.startsWith(getScopedStorageDirPath(StorageSubdirectory.LAYERS))) {
            return path.substring(getScopedStorageDirPath(StorageSubdirectory.LAYERS).length() + 1);
        }
        return path;
    }

    public String getAbsoluteOfflineMapLayerPath(String path) {
        if (path == null) {
            return null;
        }
        return getDirPath(StorageSubdirectory.LAYERS) + File.separator + getRelativeMapLayerPath(path);
    }
}
