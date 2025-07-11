package org.odk.collect.android.formmanagement;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.R;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.forms.Form;
import org.odk.collect.android.forms.FormSource;
import org.odk.collect.android.forms.FormSourceException;
import org.odk.collect.android.forms.FormsRepository;
import org.odk.collect.android.forms.MediaFile;
import org.odk.collect.android.listeners.FormDownloaderListener;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.FormNameUtils;
import org.odk.collect.android.utilities.Validator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import timber.log.Timber;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.odk.collect.android.analytics.AnalyticsEvents.DOWNLOAD_SAME_FORMID_VERSION_DIFFERENT_HASH;
import static org.odk.collect.utilities.PathUtils.getAbsoluteFilePath;

public class ServerFormDownloader implements FormDownloader {

    private final FormsRepository formsRepository;
    private final FormSource formSource;
    private final File cacheDir;
    private final String formsDirPath;
    private final FormMetadataParser formMetadataParser;

    private final Analytics analytics;

    public ServerFormDownloader(FormSource formSource, FormsRepository formsRepository, File cacheDir, String formsDirPath, FormMetadataParser formMetadataParser, Analytics analytics) {
        this.formSource = formSource;
        this.cacheDir = cacheDir;
        this.formsDirPath = formsDirPath;
        this.formsRepository = formsRepository;
        this.formMetadataParser = formMetadataParser;

        this.analytics = analytics;
    }

    @Override
    public void downloadForm(ServerFormDetails form, @Nullable ProgressReporter progressReporter, @Nullable Supplier<Boolean> isCancelled) throws FormDownloadException, InterruptedException {
        Form formOnDevice;
        if (form.getHash() != null) {
            formOnDevice = formsRepository.getOneByMd5Hash(getMd5HashWithoutPrefix(form.getHash()));
        } else {
            /*
             This allows us to support non open rosa servers and open rosa servers that don't
             return hashes. Can be removed when we drop support for these.
             */
            formOnDevice = formsRepository.getLatestByFormIdAndVersion(form.getFormId(), form.getFormVersion());
        }

        if (formOnDevice != null) {
            if (formOnDevice.isDeleted()) {
                formsRepository.restore(formOnDevice.getId());
            }
        } else {
            List<Form> allSameFormIdVersion = formsRepository.getAllByFormIdAndVersion(form.getFormId(), form.getFormVersion());
            if (!allSameFormIdVersion.isEmpty() && !form.getDownloadUrl().contains("/draft.xml")) {
                String formIdentifier = form.getFormName() + " " + form.getFormId();
                String formIdHash = FileUtils.getMd5Hash(new ByteArrayInputStream(formIdentifier.getBytes()));
                analytics.logFormEvent(DOWNLOAD_SAME_FORMID_VERSION_DIFFERENT_HASH, formIdHash);
            }
        }

        File tempDir = new File(cacheDir, "download-" + UUID.randomUUID().toString());
        tempDir.mkdirs();

        try {
            FormDownloaderListener stateListener = new ProgressReporterAndSupplierStateListener(progressReporter, isCancelled);
            boolean result = processOneForm(form, stateListener, tempDir, formsDirPath, formMetadataParser);

            if (!result) {
                throw new FormDownloadException();
            }
        } finally {
            try {
                deleteDirectory(tempDir);
            } catch (IOException ignored) {
                // ignored
            }
        }
    }

    public boolean processOneForm(ServerFormDetails fd, FormDownloaderListener stateListener, File tempDir, String formsDirPath, FormMetadataParser formMetadataParser) throws InterruptedException {
        boolean success = true;

        // use a temporary media path until everything is ok.
        String tempMediaPath = new File(tempDir, "media").getAbsolutePath();
        FileResult fileResult = null;

        try {
            // get the xml file
            // if we've downloaded a duplicate, this gives us the file
            fileResult = downloadXform(fd.getFormName(), fd.getDownloadUrl(), stateListener, tempDir, formsDirPath);

            // download media files if there are any
            if (fd.getManifest() != null && !fd.getManifest().getMediaFiles().isEmpty()) {
                downloadMediaFiles(tempMediaPath, stateListener, fd.getManifest().getMediaFiles(), tempDir, fileResult.file.getName());
            }
        } catch (InterruptedException e) {
            Timber.i(e);
            cleanUp(fileResult, tempMediaPath);
            throw e;
        } catch (FormSourceException | IOException e) {
            return false;
        }

        if (stateListener != null && stateListener.isTaskCancelled()) {
            cleanUp(fileResult, tempMediaPath);
            fileResult = null;
        }

        if (fileResult == null) {
            return false;
        }

        Map<String, String> parsedFields = null;
        if (fileResult.isNew) {
            try {
                final long start = System.currentTimeMillis();
                Timber.i("Parsing document %s", fileResult.file.getAbsolutePath());

                parsedFields = formMetadataParser
                        .parse(fileResult.file, new File(tempMediaPath));

                Timber.i("Parse finished in %.3f seconds.", (System.currentTimeMillis() - start) / 1000F);
            } catch (RuntimeException e) {
                return false;
            }
        }

        boolean installed = false;

        if ((stateListener == null || !stateListener.isTaskCancelled()) && success) {
            if (!fileResult.isNew || isSubmissionOk(parsedFields)) {
                installed = installEverything(tempMediaPath, fileResult, parsedFields, formsDirPath);
            } else {
                success = false;
            }
        }

        if (!installed) {
            success = false;
            cleanUp(fileResult, tempMediaPath);
        }

        return success;
    }

    private boolean isSubmissionOk(Map<String, String> parsedFields) {
        String submission = parsedFields.get(FileUtils.SUBMISSIONURI);
        return submission == null || Validator.isUrlValid(submission);
    }

    boolean installEverything(String tempMediaPath, FileResult fileResult, Map<String, String> parsedFields, String formsDirPath) {
        FormResult formResult;

        File formFile;

        if (fileResult.isNew()) {
            // Copy form to forms dir
            formFile = new File(formsDirPath, fileResult.file.getName());
            FileUtils.copyFile(fileResult.file, formFile);
        } else {
            formFile = fileResult.file;
        }

        // Save form in database
        formResult = findOrCreateForm(formFile, parsedFields);

        // move the media files in the media folder
        if (tempMediaPath != null) {
            File formMediaDir = new File(getAbsoluteFilePath(formsDirPath, formResult.form.getFormMediaPath()));

            try {
                moveMediaFiles(tempMediaPath, formMediaDir);
            } catch (IOException e) {
                Timber.e(e);

                if (formResult.isNew() && fileResult.isNew()) {
                    // this means we should delete the entire form together with the metadata
                    formsRepository.delete(formResult.form.getId());
                }

                return false;
            }
        }

        return true;
    }

    private void cleanUp(FileResult fileResult, String tempMediaPath) {
        if (fileResult == null) {
            Timber.d("The user cancelled (or an exception happened) the download of a form at the very beginning.");
        } else {
            String md5Hash = FileUtils.getMd5Hash(fileResult.file);
            if (md5Hash != null) {
                formsRepository.deleteByMd5Hash(md5Hash);
            }
            FileUtils.deleteAndReport(fileResult.getFile());
        }

        if (tempMediaPath != null) {
            FileUtils.purgeMediaPath(tempMediaPath);
        }
    }

    private FormResult findOrCreateForm(File formFile, Map<String, String> formInfo) {
        final String formFilePath = formFile.getAbsolutePath();
        String mediaPath = FileUtils.constructMediaPath(formFilePath);

        Form existingForm = formsRepository.getOneByPath(formFile.getAbsolutePath());

        if (existingForm == null) {
            Form newForm = saveNewForm(formInfo, formFile, mediaPath);
            return new FormResult(newForm, true);
        } else {
            return new FormResult(existingForm, false);
        }
    }

    private Form saveNewForm(Map<String, String> formInfo, File formFile, String mediaPath) {
        Form form = new Form.Builder()
                .formFilePath(formFile.getAbsolutePath())
                .formMediaPath(mediaPath)
                .displayName(formInfo.get(FileUtils.TITLE))
                .jrVersion(formInfo.get(FileUtils.VERSION))
                .jrFormId(formInfo.get(FileUtils.FORMID))
                .submissionUri(formInfo.get(FileUtils.SUBMISSIONURI))
                .base64RSAPublicKey(formInfo.get(FileUtils.BASE64_RSA_PUBLIC_KEY))
                .autoDelete(formInfo.get(FileUtils.AUTO_DELETE))
                .autoSend(formInfo.get(FileUtils.AUTO_SEND))
                //.geometryXpath(formInfo.get(FileUtils.GEOMETRY_XPATH))  // smap
                .build();

        return formsRepository.save(form);
    }

    /**
     * Takes the formName and the URL and attempts to download the specified file. Returns a file
     * object representing the downloaded file.
     */
    FileResult downloadXform(String formName, String url, FormDownloaderListener stateListener, File tempDir, String formsDirPath) throws FormSourceException, IOException, InterruptedException {
        InputStream xform = formSource.fetchForm(url, true);        // smap

        String fileName = getFormFileName(formName, formsDirPath);
        File tempFormFile = new File(tempDir + File.separator + fileName);
        writeFile(xform, tempFormFile, tempDir, stateListener);

        // we've downloaded the file, and we may have renamed it
        // make sure it's not the same as a file we already have
        Form form = formsRepository.getOneByMd5Hash(FileUtils.getMd5Hash(tempFormFile));
        if (form != null) {
            // delete the file we just downloaded, because it's a duplicate
            FileUtils.deleteAndReport(tempFormFile);

            // set the file returned to the file we already had
            String existingPath = getAbsoluteFilePath(formsDirPath, form.getFormFilePath());
            return new FileResult(new File(existingPath), false);
        } else {
            return new FileResult(tempFormFile, true);
        }
    }

    /**
     * Common routine to take a downloaded document save the contents in the file
     * 'file'. Shared by media file download and form file download.
     * <p>
     * SurveyCTO: The file is saved into a temp folder and is moved to the final place if everything
     * is okay, so that garbage is not left over on cancel.
     */
    private void writeFile(InputStream inputStream, File destinationFile, File tempDir, FormDownloaderListener stateListener)
            throws IOException, InterruptedException {

        File tempFile = File.createTempFile(
                destinationFile.getName(),
                ".tempDownload",
                tempDir
        );

        // WiFi network connections can be renegotiated during a large form download sequence.
        // This will cause intermittent download failures.  Silently retry once after each
        // failure.  Only if there are two consecutive failures do we abort.
        boolean success = false;
        int attemptCount = 0;
        final int MAX_ATTEMPT_COUNT = 2;
        while (!success && ++attemptCount <= MAX_ATTEMPT_COUNT) {
            // write connection to file
            InputStream is = null;
            OutputStream os = null;

            try {
                is = inputStream;
                os = new FileOutputStream(tempFile);

                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0 && (stateListener == null || !stateListener.isTaskCancelled())) {
                    os.write(buf, 0, len);
                }
                os.flush();
                success = true;

            } catch (Exception e) {
                Timber.e(e.toString());
                // silently retry unless this is the last attempt,
                // in which case we rethrow the exception.

                FileUtils.deleteAndReport(tempFile);

                if (attemptCount == MAX_ATTEMPT_COUNT) {
                    throw e;
                }
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
                if (is != null) {
                    try {
                        // ensure stream is consumed...
                        final long count = 1024L;
                        while (is.skip(count) == count) {
                            // skipping to the end of the http entity
                        }
                    } catch (Exception e) {
                        // no-op
                    }
                    try {
                        is.close();
                    } catch (Exception e) {
                        Timber.w(e);
                    }
                }
            }

            if (stateListener != null && stateListener.isTaskCancelled()) {
                FileUtils.deleteAndReport(tempFile);
                throw new InterruptedException();
            }
        }

        Timber.d("Completed downloading of %s. It will be moved to the proper path...", tempFile.getAbsolutePath());

        FileUtils.deleteAndReport(destinationFile);

        String errorMessage = FileUtils.copyFile(tempFile, destinationFile);

        if (destinationFile.exists()) {
            Timber.d("Copied %s over %s", tempFile.getAbsolutePath(), destinationFile.getAbsolutePath());
            FileUtils.deleteAndReport(tempFile);
        } else {
            String msg = Collect.getInstance().getString(org.odk.collect.strings.R.string.fs_file_copy_error,
                    tempFile.getAbsolutePath(), destinationFile.getAbsolutePath(), errorMessage);
            throw new RuntimeException(msg);
        }
    }

    private void downloadMediaFiles(String tempMediaPath, FormDownloaderListener stateListener, List<MediaFile> files, File tempDir, String formFileName) throws FormSourceException, IOException, InterruptedException {
        File tempMediaDir = new File(tempMediaPath);
        tempMediaDir.mkdir();

        for (int i = 0; i < files.size(); i++) {
            if (stateListener != null) {
                stateListener.progressUpdate("", String.valueOf(i + 1), "");
            }

            MediaFile toDownload = files.get(i);

            File tempMediaFile = new File(tempMediaDir, toDownload.getFilename());
            String finalMediaPath = FileUtils.constructMediaPath(formsDirPath + File.separator + formFileName);
            File finalMediaFile = new File(finalMediaPath, toDownload.getFilename());

            if (!finalMediaFile.exists()) {
                InputStream mediaFile = formSource.fetchMediaFile(toDownload.getDownloadUrl(), true);   // smap
                writeFile(mediaFile, tempMediaFile, tempDir, stateListener);
            } else {
                String currentFileHash = FileUtils.getMd5Hash(finalMediaFile);
                String downloadFileHash = getMd5HashWithoutPrefix(toDownload.getHash());

                if (currentFileHash != null && downloadFileHash != null && !currentFileHash.contentEquals(downloadFileHash)) {
                    // if the hashes match, it's the same file
                    // otherwise delete our current one and replace it with the new one
                    FileUtils.deleteAndReport(finalMediaFile);
                    InputStream mediaFile = formSource.fetchMediaFile(toDownload.getDownloadUrl(), true);       // smap
                    writeFile(mediaFile, tempMediaFile, tempDir, stateListener);
                } else {
                    // exists, and the hash is the same
                    // no need to download it again
                    Timber.i("Skipping media file fetch -- file hashes identical: %s", finalMediaFile.getAbsolutePath());
                }
            }
        }
    }

    @NotNull
    private static String getFormFileName(String formName, String formsDirPath) {
        String formattedFormName = FormNameUtils.formatFilenameFromFormName("project", formName);   // smap add dummy project
        String fileName = formattedFormName + ".xml";
        int i = 2;
        while (new File(formsDirPath + File.separator + fileName).exists()) {
            fileName = formattedFormName + "_" + i + ".xml";
            i++;
        }
        return fileName;
    }

    public static String getMd5HashWithoutPrefix(String hash) {
        return hash == null || hash.isEmpty() ? null : hash.substring("md5:".length());
    }

    public static void moveMediaFiles(String tempMediaPath, File formMediaPath) throws IOException {
        File tempMediaFolder = new File(tempMediaPath);
        File[] mediaFiles = tempMediaFolder.listFiles();

        if (mediaFiles != null && mediaFiles.length != 0) {
            for (File mediaFile : mediaFiles) {
                org.apache.commons.io.FileUtils.moveFileToDirectory(mediaFile, formMediaPath, true);
            }
        }
    }

    private static class FormResult {

        private final Form form;
        private final boolean isNew;

        private FormResult(Form form, boolean isNew) {
            this.form = form;
            this.isNew = isNew;
        }

        private boolean isNew() {
            return isNew;
        }

        public Form getForm() {
            return form;
        }
    }

    private static class FileResult {

        private final File file;
        private final boolean isNew;

        FileResult(File file, boolean isNew) {
            this.file = file;
            this.isNew = isNew;
        }

        private File getFile() {
            return file;
        }

        private boolean isNew() {
            return isNew;
        }
    }

    private static class ProgressReporterAndSupplierStateListener implements FormDownloaderListener {
        private final ProgressReporter progressReporter;
        private final Supplier<Boolean> isCancelled;

        ProgressReporterAndSupplierStateListener(ProgressReporter progressReporter, Supplier<Boolean> isCancelled) {
            this.progressReporter = progressReporter;
            this.isCancelled = isCancelled;
        }

        @Override
        public void progressUpdate(String currentFile, String progress, String total) {
            if (progressReporter != null) {
                progressReporter.onDownloadingMediaFile(Integer.parseInt(progress));
            }
        }

        @Override
        public boolean isTaskCancelled() {
            if (isCancelled != null) {
                return isCancelled.get();
            } else {
                return false;
            }
        }
    }
}
