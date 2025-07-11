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

package org.odk.collect.android.formentry;


import java.io.Serializable;
import java.util.*;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.button.MaterialButton;

import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.IFormElement;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.NFCActivity;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.audio.AudioHelper;
import org.odk.collect.android.dao.helpers.ContentResolverHelper;
import org.odk.collect.android.exception.ExternalParamsException;
import org.odk.collect.android.exception.JavaRosaException;
import org.odk.collect.android.external.ExternalAppsUtils;
import org.odk.collect.android.formentry.media.AudioHelperFactory;
import org.odk.collect.android.formentry.media.PromptAutoplayer;
import org.odk.collect.android.formentry.questions.QuestionTextSizeHelper;
import org.odk.collect.android.javarosawrapper.FormController;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.listeners.WidgetValueChangedListener;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.preferences.PreferencesProvider;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.QuestionFontSizeUtils;
import org.odk.collect.android.utilities.QuestionMediaManager;
import org.odk.collect.android.utilities.ScreenContext;
import org.odk.collect.android.utilities.ThemeUtils;
import org.odk.collect.android.utilities.ToastUtils;
import org.odk.collect.android.widgets.AudioWidget;
import org.odk.collect.android.widgets.BarcodeWidget;
import org.odk.collect.android.widgets.ExStringWidget;
import org.odk.collect.android.widgets.GeoPointMapWidget;
import org.odk.collect.android.widgets.GeoPointWidget;
import org.odk.collect.android.widgets.ImageWidget;
import org.odk.collect.android.widgets.QuestionWidget;
import org.odk.collect.android.widgets.SmapFormWidget;
import org.odk.collect.android.widgets.VideoWidget;
import org.odk.collect.android.widgets.StringWidget;
import org.odk.collect.android.widgets.UrlWidget;
import org.odk.collect.android.widgets.WidgetFactory;
import org.odk.collect.android.widgets.interfaces.WidgetDataReceiver;
import org.odk.collect.android.widgets.utilities.AudioPlayer;
import org.odk.collect.android.widgets.utilities.ExternalAppRecordingRequester;
import org.odk.collect.android.widgets.utilities.InternalRecordingRequester;
import org.odk.collect.android.widgets.utilities.RecordingRequesterProvider;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;
import org.odk.collect.audioclips.PlaybackFailedException;
import org.odk.collect.audiorecorder.recording.AudioRecorder;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import timber.log.Timber;

import static org.odk.collect.android.injection.DaggerUtils.getComponent;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_EXTERNAL_APP_RECORDING;
import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes;

/**
 * Contains either one {@link QuestionWidget} if the current form element is a question or
 * multiple {@link QuestionWidget}s if the current form element is a group with the
 * {@code field-list} appearance.
 */
@SuppressLint("ViewConstructor")
public class ODKView extends FrameLayout implements OnLongClickListener, WidgetValueChangedListener {

    private final LinearLayout widgetsList;
    private final LinearLayout.LayoutParams layout;
    private final ArrayList<QuestionWidget> widgets;
    private final AudioHelper audioHelper;

    private WidgetValueChangedListener widgetValueChangedListener;

    private FormEntryPrompt nfcPrompt = null;           // smap
    private FormEntryPrompt barcodePrompt = null;       // smap
    private FormEntryPrompt exPrompt = null;            // smap
    private Button exGroupPrompt = null;                // smap
    private FormEntryPrompt formPrompt = null;          // smap
    private FormEntryPrompt geopointPrompt = null;      // smap
    private FormEntryPrompt imagePrompt = null;         // smap
    private FormEntryPrompt videoPrompt = null;         // smap
    private FormEntryPrompt audioPrompt = null;         // smap

    @Inject
    public AudioHelperFactory audioHelperFactory;

    //@Inject
    //public Analytics analytics;

    @Inject
    PreferencesProvider preferencesProvider;

    @Inject
    PermissionsProvider permissionsProvider;

    private final WidgetFactory widgetFactory;
    private final LifecycleOwner viewLifecycle;
    private final AudioRecorder audioRecorder;
    private final FormEntryViewModel formEntryViewModel;

    /**
     * Builds the view for a specified question or field-list of questions.
     *
     * @param context         the activity creating this view
     * @param questionPrompts the questions to be included in this view
     * @param groups          the group hierarchy that this question or field list is in
     * @param advancingPage   whether this view is being created after a forward swipe through the
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ODKView(ComponentActivity context, final FormEntryPrompt[] questionPrompts, FormEntryCaption[] groups, boolean advancingPage, boolean canUpdate, QuestionMediaManager questionMediaManager, WaitingForDataRegistry waitingForDataRegistry, AudioPlayer audioPlayer, AudioRecorder audioRecorder, FormEntryViewModel formEntryViewModel, InternalRecordingRequester internalRecordingRequester, ExternalAppRecordingRequester externalAppRecordingRequester) {  // smap add canUpdate
        super(context);
        viewLifecycle = ((ScreenContext) context).getViewLifecycle();
        this.audioRecorder = audioRecorder;
        this.formEntryViewModel = formEntryViewModel;

        getComponent(context).inject(this);
        this.audioHelper = audioHelperFactory.create(context);
        inflate(getContext(), R.layout.odk_view, this); // keep in an xml file to enable the vertical scrollbar

        // when the grouped fields are populated by an external app, this will get true.
        boolean readOnlyOverride = false;

        // smap set read only if this is a completed task
        if (!canUpdate) {
            readOnlyOverride = true;
        }
        // smap end

        // handle intent groups that are intended to receive multiple values from an external app
        if (groups != null && groups.length > 0) {
            // get the group we are showing -- it will be the last of the groups in the groups list
            final FormEntryCaption c = groups[groups.length - 1];
            final String intentString = c.getFormElement().getAdditionalAttribute(null, "intent");
            if (intentString != null && intentString.length() != 0) {
                readOnlyOverride = true;

                // Start smap - auto launch
                exGroupPrompt = addIntentLaunchButton(context, questionPrompts, c, intentString);   // smap get the button back
                String auto = c.getFormElement().getAdditionalAttribute(null, "auto");
                boolean autoLaunch = (auto != null && (auto.equals("yes") || auto.equals("true"))) ? true : false;
                if(!autoLaunch) {
                    exGroupPrompt = null;   // Don't auto launch then
                }
                // end smap
            }
        }

        this.widgetFactory = new WidgetFactory(
                context,
                readOnlyOverride,   // smap
                preferencesProvider.getGeneralSharedPreferences().getBoolean(KEY_EXTERNAL_APP_RECORDING, true),
                waitingForDataRegistry,
                questionMediaManager,
                audioPlayer,
                new RecordingRequesterProvider(
                        internalRecordingRequester,
                        externalAppRecordingRequester
                ),
                formEntryViewModel,
                audioRecorder,
                viewLifecycle
        );

        widgets = new ArrayList<>();
        widgetsList = findViewById(R.id.widgets);

        layout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        // display which group you are in as well as the question
        setGroupText(groups);

        for (FormEntryPrompt question : questionPrompts) {
            addWidgetForQuestion(question);
        }

        setupAudioErrors();
        autoplayIfNeeded(advancingPage);

        logAnalyticsForWidgets();
    }

    private void setupAudioErrors() {
        audioHelper.getError().observe(viewLifecycle, e -> {
            if (e instanceof PlaybackFailedException) {
                final PlaybackFailedException playbackFailedException = (PlaybackFailedException) e;
                Toast.makeText(
                        getContext(),
                        getContext().getString(playbackFailedException.getExceptionMsg() == 0 ? org.odk.collect.strings.R.string.file_missing : org.odk.collect.strings.R.string.file_invalid, playbackFailedException.getURI()),
                        Toast.LENGTH_SHORT
                ).show();

                audioHelper.errorDisplayed();
            }
        });
    }

    private void autoplayIfNeeded(boolean advancingPage) {

        // see if there is an autoplay option.
        // Only execute it during forward swipes through the form
        if (advancingPage) {    // smap remove check for only one widget should work off first widget
            FormEntryPrompt firstPrompt = widgets.get(0).getFormEntryPrompt();
            Boolean autoplayedAudio = autoplayAudio(firstPrompt);

            if (!autoplayedAudio) {
                boolean autoplayedVideo = autoplayVideo(firstPrompt);       // smap add auto played video

                // begin smap
                if(!autoplayedVideo) {
                    if (exGroupPrompt != null) {    // Smap - auto multi question external app
                        exGroupPrompt.performClick();
                    } else if (nfcPrompt != null) {    // Smap - auto get NFC
                        Intent i = new Intent(getContext(), NFCActivity.class);
                        Collect.getInstance().getFormController()
                                .setIndexWaitingForData(nfcPrompt.getIndex());
                        ((Activity) getContext()).startActivityForResult(i,
                                RequestCodes.NFC_CAPTURE);

                    } else if (barcodePrompt != null) {    // Smap - auto get barcode
                        BarcodeWidget bcWidget = (BarcodeWidget) widgets.get(0);
                        if (bcWidget != null) {
                            bcWidget.getBarcodeButton().performClick();
                        }
                    } else if (exPrompt != null) {    // Smap - auto external app
                        ExStringWidget exWidget = (ExStringWidget) widgets.get(0);
                        if (exWidget != null) {
                            exWidget.launchIntentButton.performClick();
                        }
                    } else if (formPrompt != null) {    // Smap - launch form
                        SmapFormWidget formWidget = (SmapFormWidget) widgets.get(0);
                        if (formWidget != null) {
                            formWidget.launchIntentButton.performClick();
                            formWidget.launchIntentButton.setVisibility(GONE);
                            formWidget.launching.setVisibility(VISIBLE);
                        }
                    } else if (geopointPrompt != null) {    // Smap - launch gps collection
                        if(widgets.get(0).getClass() == GeoPointWidget.class) {
                            GeoPointWidget geopointWidget = (GeoPointWidget) widgets.get(0);
                            if (geopointWidget != null) {
                                geopointWidget.binding.simpleButton.performClick();
                            }
                        } else if(widgets.get(0).getClass() == GeoPointMapWidget.class) {
                            GeoPointMapWidget geopointWidget = (GeoPointMapWidget) widgets.get(0);
                            if (geopointWidget != null) {
                                geopointWidget.binding.simpleButton.performClick();
                            }
                        }
                    } else if (imagePrompt != null) {    // Smap - launch image collection
                        ImageWidget imageWidget = (ImageWidget) widgets.get(0);
                        if (imageWidget != null) {
                            imageWidget.captureButton.performClick();
                        }
                    } else if (videoPrompt != null) {    // Smap - launch video collection
                        VideoWidget videoWidget = (VideoWidget) widgets.get(0);
                        if (videoWidget != null) {
                            videoWidget.captureButton.performClick();
                        }
                    } else if (audioPrompt != null) {    // Smap - launch audio collection
                        AudioWidget audioWidget = (AudioWidget) widgets.get(0);
                        if (audioWidget != null) {
                            audioWidget.binding.captureButton.performClick();   // Note this onlu auto starts capture of new audio
                        }
                    }
                }
                // end smap
            }

        }
    }

    private Boolean autoplayAudio(FormEntryPrompt firstPrompt) {
        PromptAutoplayer promptAutoplayer = new PromptAutoplayer(
                audioHelper,
                ReferenceManager.instance(),
                null,   // smap remove analytics
                Collect.getCurrentFormIdentifierHash()
        );

        return promptAutoplayer.autoplayIfNeeded(firstPrompt);
    }

    private boolean autoplayVideo(FormEntryPrompt prompt) {     // smap add boolean response
        final String autoplayOption = prompt.getFormElement().getAdditionalAttribute(null, "autoplay");
        boolean played = false;     // smap

        if (autoplayOption != null) {
            if (autoplayOption.equalsIgnoreCase("video")) {
                new Handler().postDelayed(() -> {
                    widgets.get(0).getAudioVideoImageTextLabel().playVideo();
                }, 150);
                played = true;    // smap
            }
        }
        return played;     // smap
    }

    /**
     * Creates a {@link QuestionWidget} for the given {@link FormEntryPrompt}, sets its listeners,
     * and adds it to the end of the view. If this widget is not the first one, add a divider above
     * it.
     */
    private void addWidgetForQuestion(FormEntryPrompt question) {

        nfcPrompt = null;           // smap - Set these to null for this widget.  Used later for auto launch
        barcodePrompt = null;       // smap
        exPrompt = null;            // smap
        formPrompt = null;          // smap
        geopointPrompt = null;      // smap
        imagePrompt = null;         // smap
        videoPrompt = null;         // smap
        audioPrompt = null;         // smap

        QuestionWidget qw = configureWidgetForQuestion(question);

        widgets.add(qw);

        if (widgets.size() > 1) {
            widgetsList.addView(getDividerView());
        }
        widgetsList.addView(qw, layout);

        // Start smap
        String appearance = question.getAppearanceHint();
        if (appearance == null) appearance = "";
        appearance = appearance.toLowerCase(Locale.ENGLISH);

        if(widgets.size() == 1) {
            // Auto get NFC if first question, and not already obtained a code
            if (question.getDataType() == Constants.DATATYPE_BARCODE) {
                String s = question.getAnswerText();    // Make sure an NFC / barcode code has not alredy been retrieved
                if (s == null) {
                    if (appearance.contains("read_nfc")) {
                        nfcPrompt = question;
                    }
                }
            }

            // Other types auto launch only if the auto parameter is set
            String auto = widgets.get(
                    0).getFormEntryPrompt().getFormElement().getAdditionalAttribute(null, "auto");
            String form_identifier = widgets.get(
                    0).getFormEntryPrompt().getFormElement().getAdditionalAttribute(null, "form_identifier");

            boolean autoLaunch = (auto != null && (auto.equals("yes") || auto.equals("true"))) ? true : false;
            boolean isNew = (appearance.contains("new")) ? true : false;
            String s = question.getAnswerText();    // Make sure a value has not already been retrieved

            // If a response has already ben received then clear the group response button so that it is not automatically activated
            if(s != null) {
                exGroupPrompt = null;
            }

            // Auto get External Launch if first question, and not already obtained a value
            if(nfcPrompt == null && autoLaunch && s == null) {
                if (appearance.trim().startsWith("ex:") &&
                        (question.getDataType() == Constants.DATATYPE_INTEGER ||
                                question.getDataType() == Constants.DATATYPE_DECIMAL ||
                                question.getDataType() == Constants.DATATYPE_TEXT)) {

                    exPrompt = question;

                } else if (form_identifier != null && question.getDataType() == Constants.DATATYPE_TEXT) {
                    formPrompt = question;
                } else if (question.getDataType() == Constants.DATATYPE_GEOPOINT) {
                    geopointPrompt = question;
                } else if (question.getDataType() == Constants.DATATYPE_BARCODE) {
                    barcodePrompt = question;
                } else if (isNew && question.getControlType() == Constants.CONTROL_VIDEO_CAPTURE) {
                    videoPrompt = question;
                } else if (isNew && question.getControlType() == Constants.CONTROL_AUDIO_CAPTURE) {
                    audioPrompt = question;
                } else if (isNew && question.getControlType() == Constants.CONTROL_IMAGE_CHOOSE) {
                    imagePrompt = question;
                }
            }
        }
        // End Smap
    }

    /**
     * Creates a {@link QuestionWidget} for the given {@link FormEntryPrompt}, sets its listeners,
     * and adds it to the view at the specified {@code index}. If this widget is not the first one,
     * add a divider above it. If the specified {@code index} is beyond the end of the widget list,
     * add it to the end.
     */
    public void addWidgetForQuestion(FormEntryPrompt question, int index) {
        if (index > widgets.size() - 1) {
            addWidgetForQuestion(question);
            return;
        }

        QuestionWidget qw = configureWidgetForQuestion(question);

        widgets.add(index, qw);

        int indexAccountingForDividers = index * 2;
        if (index > 0) {
            widgetsList.addView(getDividerView(), indexAccountingForDividers - 1);
        }

        widgetsList.addView(qw, indexAccountingForDividers, layout);
    }

    /**
     * Creates and configures a {@link QuestionWidget} for the given {@link FormEntryPrompt}.
     * <p>
     * Note: if the given question is of an unsupported type, a text widget will be created.
     */
    private QuestionWidget configureWidgetForQuestion(FormEntryPrompt question) {
        QuestionWidget qw = widgetFactory.createWidgetFromPrompt(question, permissionsProvider);
        qw.setOnLongClickListener(this);
        qw.setValueChangedListener(this);

        return qw;
    }

    private View getDividerView() {
        View divider = new View(getContext());
        divider.setBackgroundResource(new ThemeUtils(getContext()).getDivider());
        divider.setMinimumHeight(3);

        return divider;
    }

    /**
     * @return a HashMap of answers entered by the user for this set of widgets
     */
    public HashMap<FormIndex, IAnswerData> getAnswers() {
        HashMap<FormIndex, IAnswerData> answers = new LinkedHashMap<>();
        for (QuestionWidget q : widgets) {
            /*
             * The FormEntryPrompt has the FormIndex, which is where the answer gets stored. The
             * QuestionWidget has the answer the user has entered.
             */
            FormEntryPrompt p = q.getFormEntryPrompt();
            answers.put(p.getIndex(), q.getAnswer());
        }

        return answers;
    }

    /**
     * Add a TextView containing the hierarchy of groups to which the question belongs.
     */
    private void setGroupText(FormEntryCaption[] groups) {
        String path = getGroupsPath(groups);

        if (!path.isEmpty()) {
            TextView tv = findViewById(R.id.group_text);
            tv.setText(path);

            QuestionTextSizeHelper textSizeHelper = new QuestionTextSizeHelper();
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeHelper.getSubtitle1());

            tv.setVisibility(VISIBLE);
        }
    }

    /**
     * @see #getGroupsPath(FormEntryCaption[], boolean)
     */
    @NonNull
    public static String getGroupsPath(FormEntryCaption[] groups) {
        return getGroupsPath(groups, false);
    }

    /**
     * Builds a string representing the 'path' of the list of groups.
     * Each level is separated by `>`.
     * <p>
     * Some views (e.g. the repeat picker) may want to hide the multiplicity of the last item,
     * i.e. show `Friends` instead of `Friends > 1`.
     */
    @NonNull
    public static String getGroupsPath(FormEntryCaption[] groups, boolean hideLastMultiplicity) {
        if (groups == null) {
            return "";
        }

        List<String> segments = new ArrayList<>();
        int index = 1;
        for (FormEntryCaption group : groups) {
            String text = group.getLongText();

            if (text != null) {
                segments.add(text);

                boolean isMultiplicityAllowed = !(hideLastMultiplicity && index == groups.length);
                if (group.repeats() && isMultiplicityAllowed) {
                    segments.add(Integer.toString(group.getMultiplicity() + 1));
                }
            }

            index++;
        }

        return TextUtils.join(" > ", segments);
    }

    /**
     * Adds a button to launch an intent if the group displayed by this view is an intent group.
     * An intent group launches an intent and receives multiple values from the launched app.
     */
    private Button addIntentLaunchButton(Context context, FormEntryPrompt[] questionPrompts,        // smap return button
                                       FormEntryCaption c, String intentString) {
        final String buttonText;
        final String errorString;
        String v = c.getSpecialFormQuestionText("buttonText");
        buttonText = (v != null) ? v : context.getString(org.odk.collect.strings.R.string.launch_app);
        v = c.getSpecialFormQuestionText("noAppErrorString");
        errorString = (v != null) ? v : context.getString(org.odk.collect.strings.R.string.no_app);

        // set button formatting
        MaterialButton launchIntentButton = findViewById(R.id.launchIntentButton);
        launchIntentButton.setText(buttonText);
        launchIntentButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, QuestionFontSizeUtils.getQuestionFontSize() + 2);
        launchIntentButton.setVisibility(VISIBLE);
        launchIntentButton.setOnClickListener(view -> {
            String intentName = ExternalAppsUtils.extractIntentName(intentString);
            Map<String, String> parameters = ExternalAppsUtils.extractParameters(intentString);

            Intent i = new Intent(intentName);
            if (i.resolveActivity(Collect.getInstance().getPackageManager()) == null) {
                Intent launchIntent = Collect.getInstance().getPackageManager().getLaunchIntentForPackage(intentName);

                if (launchIntent != null) {
                    // Make sure FLAG_ACTIVITY_NEW_TASK is not set because it doesn't work with startActivityForResult
                    launchIntent.setFlags(0);
                    i = launchIntent;
                }
            }

            try {
                ExternalAppsUtils.populateParameters(i, parameters,
                        c.getIndex().getReference());

                for (FormEntryPrompt p : questionPrompts) {
                    IFormElement formElement = p.getFormElement();
                    if (formElement instanceof QuestionDef) {
                        TreeReference reference =
                                (TreeReference) formElement.getBind().getReference();
                        IAnswerData answerValue = p.getAnswerValue();
                        Object value =
                                answerValue == null ? null : answerValue.getValue();
                        switch (p.getDataType()) {
                            case Constants.DATATYPE_TEXT:
                            case Constants.DATATYPE_INTEGER:
                            case Constants.DATATYPE_DECIMAL:
                            case Constants.DATATYPE_BINARY:
                                i.putExtra(reference.getNameLast(),
                                        (Serializable) value);
                                break;
                        }
                    }
                }

                ((Activity) getContext()).startActivityForResult(i, RequestCodes.EX_GROUP_CAPTURE);
            } catch (ExternalParamsException e) {
                Timber.e(e, "ExternalParamsException");

                ToastUtils.showShortToast(e.getMessage());
            } catch (ActivityNotFoundException e) {
                Timber.d(e, "ActivityNotFoundExcept");

                ToastUtils.showShortToast(errorString);
            }
        });

        return launchIntentButton;      // smap
    }

    public void setFocus(Context context) {
        if (!widgets.isEmpty()) {
            widgets.get(0).setFocus(context);
        }
    }

    /**
     * Returns true if any part of the question widget is currently on the screen or false otherwise.
     */
    public boolean isDisplayed(QuestionWidget qw) {
        Rect scrollBounds = new Rect();
        findViewById(R.id.odk_view_container).getHitRect(scrollBounds);
        return qw.getLocalVisibleRect(scrollBounds);
    }

    public void scrollTo(@Nullable QuestionWidget qw) {
        if (qw != null && widgets.contains(qw)) {
            findViewById(R.id.odk_view_container).scrollTo(0, qw.getTop());
        }
    }

    /**
     * Saves answers for the widgets in this view. Called when the widgets are in an intent group.
     */
    public void setDataForFields(Bundle bundle) throws JavaRosaException {
        FormController formController = Collect.getInstance().getFormController();
        if (formController == null) {
            return;
        }

        if (bundle != null) {
            Set<String> keys = bundle.keySet();
            for (String key : keys) {
                Object answer = bundle.get(key);
                if (answer == null) {
                    continue;
                }
                for (QuestionWidget questionWidget : widgets) {
                    FormEntryPrompt prompt = questionWidget.getFormEntryPrompt();
                    TreeReference treeReference =
                            (TreeReference) prompt.getFormElement().getBind().getReference();

                    if (treeReference.getNameLast().equals(key)) {
                        switch (prompt.getDataType()) {
                            case Constants.DATATYPE_TEXT:
                                formController.saveAnswer(prompt.getIndex(),
                                        ExternalAppsUtils.asStringData(answer));
                                ((StringWidget) questionWidget).setDisplayValueFromModel();
                                questionWidget.showAnswerContainer();
                                break;
                            case Constants.DATATYPE_INTEGER:
                                formController.saveAnswer(prompt.getIndex(),
                                        ExternalAppsUtils.asIntegerData(answer));
                                ((StringWidget) questionWidget).setDisplayValueFromModel();
                                questionWidget.showAnswerContainer();
                                break;
                            case Constants.DATATYPE_DECIMAL:
                                formController.saveAnswer(prompt.getIndex(),
                                        ExternalAppsUtils.asDecimalData(answer));
                                ((StringWidget) questionWidget).setDisplayValueFromModel();
                                questionWidget.showAnswerContainer();
                                break;
                            case Constants.DATATYPE_BINARY:
                                try {
                                    Uri uri;
                                    if (answer instanceof Uri) {
                                        uri = (Uri) answer;
                                    } else if (answer instanceof String) {
                                        uri = Uri.parse(bundle.getString(key));
                                    } else {
                                        throw new RuntimeException("The value for " + key + " must be a URI but it is " + answer);
                                    }

                                    File destFile = FileUtils.createDestinationMediaFile(formController.getInstanceFile().getParent(), ContentResolverHelper.getFileExtensionFromUri(uri));
                                    //TODO might be better to use QuestionMediaManager in the future
                                    FileUtils.saveAnswerFileFromUri(uri, destFile, getContext());
                                    ((WidgetDataReceiver) questionWidget).setData(destFile);

                                    questionWidget.showAnswerContainer();

                                } catch (Exception | Error e) {
                                    Timber.w(e);
                                }
                                break;
                            default:
                                throw new RuntimeException(
                                        getContext().getString( org.odk.collect.strings.R.string.ext_assign_value_error,
                                                treeReference.toString(false)));
                        }
                        break;
                    }
                }
            }
        }
    }

    public boolean suppressFlingGesture(MotionEvent e1, MotionEvent e2, float velocityX,
                                        float velocityY) {
        for (QuestionWidget q : widgets) {
            if (q.suppressFlingGesture(e1, e2, velocityX, velocityY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if the answer was cleared, false otherwise.
     */
    public boolean clearAnswer() {
        // If there's only one widget, clear the answer.
        // If there are more, then force a long-press to clear the answer.
        if (widgets.size() == 1 && !widgets.get(0).getFormEntryPrompt().isReadOnly()) {
            widgets.get(0).clearAnswer();
            return true;
        } else {
            return false;
        }
    }

    public ArrayList<QuestionWidget> getWidgets() {
        return widgets;
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        for (int i = 0; i < widgets.size(); i++) {
            QuestionWidget qw = widgets.get(i);
            qw.setOnFocusChangeListener(l);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        return false;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        for (QuestionWidget qw : widgets) {
            qw.cancelLongPress();
        }
    }

    /**
     * Highlights the question at the given {@link FormIndex} in red for 2.5 seconds, scrolls the
     * view to display that question at the top and gives it focus.
     */
    public void highlightWidget(FormIndex formIndex) {
        QuestionWidget qw = getQuestionWidget(formIndex);

        if (qw != null) {
            // postDelayed is needed because otherwise scrolling may not work as expected in case when
            // answers are validated during form finalization.
            new Handler().postDelayed(() -> {
                qw.setFocus(getContext());
                scrollTo(qw);

                ValueAnimator va = new ValueAnimator();
                va.setIntValues(getResources().getColor(R.color.red_500), getDrawingCacheBackgroundColor());
                va.setEvaluator(new ArgbEvaluator());
                va.addUpdateListener(valueAnimator -> qw.setBackgroundColor((int) valueAnimator.getAnimatedValue()));
                va.setDuration(2500);
                va.start();
            }, 100);
        }
    }

    private QuestionWidget getQuestionWidget(FormIndex formIndex) {
        for (QuestionWidget qw : widgets) {
            if (formIndex.equals(qw.getFormEntryPrompt().getIndex())) {
                return qw;
            }
        }
        return null;
    }

    /**
     * Removes the widget and corresponding divider at a particular index.
     */
    public void removeWidgetAt(int index) {
        int indexAccountingForDividers = index * 2;

        // There may be a first TextView to display the group path. See addGroupText(FormEntryCaption[])
        if (widgetsList.getChildCount() > 0 && widgetsList.getChildAt(0) instanceof TextView) {
            indexAccountingForDividers += 1;
        }
        widgetsList.removeViewAt(indexAccountingForDividers);

        if (index > 0) {
            widgetsList.removeViewAt(indexAccountingForDividers - 1);
        }

        widgets.remove(index);
    }

    public void setWidgetValueChangedListener(WidgetValueChangedListener listener) {
        widgetValueChangedListener = listener;
    }

    public void widgetValueChanged(QuestionWidget changedWidget) {
        if (audioRecorder.isRecording()) {
            formEntryViewModel.logFormEvent(AnalyticsEvents.ANSWER_WHILE_RECORDING);
        }

        if (widgetValueChangedListener != null) {
            widgetValueChangedListener.widgetValueChanged(changedWidget);
        }

    }

    private void logAnalyticsForWidgets() {
        for (QuestionWidget widget : widgets) {
            if (widget instanceof UrlWidget) {
                formEntryViewModel.logFormEvent(AnalyticsEvents.URL_QUESTION);
            }
        }
    }
}
