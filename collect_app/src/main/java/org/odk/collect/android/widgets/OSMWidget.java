package org.odk.collect.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.osm.OSMTag;
import org.javarosa.core.model.osm.OSMTagItem;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.formentry.questions.WidgetViewUtils;
import org.odk.collect.android.javarosawrapper.FormController;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.widgets.interfaces.WidgetDataReceiver;
import org.odk.collect.android.widgets.interfaces.ButtonClickListener;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;

import java.util.ArrayList;
import java.util.List;

import static org.odk.collect.android.formentry.questions.WidgetViewUtils.createSimpleButton;
import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes;

/**
 * Widget that allows the user to launch OpenMapKit to get an OSM Feature with a
 * predetermined set of tags that are edited in the application.
 *
 * @author Nicholas Hallahan nhallahan@spatialdev.com
 */
@SuppressLint("ViewConstructor")
public class OSMWidget extends QuestionWidget implements WidgetDataReceiver, ButtonClickListener {

    // button colors
    private static final int OSM_GREEN = Color.rgb(126, 188, 111);
    private static final int OSM_BLUE = Color.rgb(112, 146, 255);

    final Button launchOpenMapKitButton;
    private final String instanceDirectory;
    private final TextView errorTextView;
    private final TextView osmFileNameHeaderTextView;
    final TextView osmFileNameTextView;

    private final List<OSMTag> osmRequiredTags;
    private final String instanceId;
    private final int formId;
    private final String formFileName;
    private final WaitingForDataRegistry waitingForDataRegistry;
    private String osmFileName;

    public OSMWidget(Context context, QuestionDetails questionDetails, WaitingForDataRegistry waitingForDataRegistry) {
        super(context, questionDetails);
        this.waitingForDataRegistry = waitingForDataRegistry;

        FormController formController = Collect.getInstance().getFormController();

        formFileName = FileUtils.getFormBasenameFromMediaFolder(formController.getMediaFolder());

        instanceDirectory = formController.getInstanceFile().getParent();
        instanceId = formController.getSubmissionMetadata().instanceId;
        formId = formController.getFormDef().getID();

        errorTextView = new TextView(context);
        errorTextView.setId(View.generateViewId());
        errorTextView.setText(org.odk.collect.strings.R.string.invalid_osm_data);

        // Determine the tags required
        osmRequiredTags = questionDetails.getPrompt().getQuestion().getOsmTags();

        // If an OSM File has already been saved, get the name.
        osmFileName = questionDetails.getPrompt().getAnswerText();

        // Setup Launch OpenMapKit Button
        launchOpenMapKitButton = createSimpleButton(getContext(), R.id.simple_button, getFormEntryPrompt().isReadOnly(), getAnswerFontSize(), this);

        // Button Styling
        if (osmFileName != null) {
            launchOpenMapKitButton.setBackgroundColor(OSM_BLUE);
        } else {
            launchOpenMapKitButton.setBackgroundColor(OSM_GREEN);
        }
        launchOpenMapKitButton.setTextColor(Color.WHITE); // White text
        if (osmFileName != null) {
            launchOpenMapKitButton.setText(getContext().getString(org.odk.collect.strings.R.string.recapture_osm));
        } else {
            launchOpenMapKitButton.setText(getContext().getString(org.odk.collect.strings.R.string.capture_osm));
        }

        osmFileNameHeaderTextView = new TextView(context);
        osmFileNameHeaderTextView.setId(View.generateViewId());
        osmFileNameHeaderTextView.setTextSize(20);
        osmFileNameHeaderTextView.setTypeface(null, Typeface.BOLD);
        osmFileNameHeaderTextView.setPadding(10, 0, 0, 10);
        osmFileNameHeaderTextView.setText(org.odk.collect.strings.R.string.edited_osm_file);

        // text view showing the resulting OSM file name
        osmFileNameTextView = new TextView(context);
        osmFileNameTextView.setId(View.generateViewId());
        osmFileNameTextView.setTextSize(18);
        osmFileNameTextView.setTypeface(null, Typeface.ITALIC);
        if (osmFileName != null) {
            osmFileNameTextView.setText(osmFileName);
        } else {
            osmFileNameHeaderTextView.setVisibility(View.GONE);
        }
        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(35, 30, 30, 35);
        osmFileNameTextView.setLayoutParams(params);

        // finish complex layout
        LinearLayout answerLayout = new LinearLayout(getContext());
        answerLayout.setOrientation(LinearLayout.VERTICAL);
        answerLayout.addView(launchOpenMapKitButton);
        answerLayout.addView(errorTextView);
        answerLayout.addView(osmFileNameHeaderTextView);
        answerLayout.addView(osmFileNameTextView);
        addAnswerView(answerLayout, WidgetViewUtils.getStandardMargin(context));

        errorTextView.setVisibility(View.GONE);
    }

    private void launchOpenMapKit() {
        try {
            //launch with intent that sends plain text
            Intent launchIntent = new Intent(Intent.ACTION_SEND);
            launchIntent.setType("text/plain");

            //send form id
            launchIntent.putExtra("FORM_ID", String.valueOf(formId));

            //send instance id
            launchIntent.putExtra("INSTANCE_ID", instanceId);

            //send instance directory
            launchIntent.putExtra("INSTANCE_DIR", instanceDirectory);

            //send form file name
            launchIntent.putExtra("FORM_FILE_NAME", formFileName);

            //send OSM file name if there was a previous edit
            if (osmFileName != null) {
                launchIntent.putExtra("OSM_EDIT_FILE_NAME", osmFileName);
            }

            //send encode tag data structure to intent
            writeOsmRequiredTagsToExtras(launchIntent);

            try {
                waitingForDataRegistry.waitForData(getFormEntryPrompt().getIndex());
                ((Activity) getContext()).startActivityForResult(launchIntent, RequestCodes.OSM_CAPTURE);
            } catch (ActivityNotFoundException e) {
                waitingForDataRegistry.cancelWaitingForData();
                errorTextView.setVisibility(View.VISIBLE);
            }

        } catch (Exception ex) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(org.odk.collect.strings.R.string.alert);
            builder.setMessage(org.odk.collect.strings.R.string.install_openmapkit);
            DialogInterface.OnClickListener okClickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    //TODO: launch to app store?
                }
            };

            builder.setPositiveButton("Ok", okClickListener);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    @Override
    public void setData(Object answer) {
        // show file name of saved osm data
        osmFileName = (String) answer;
        osmFileNameTextView.setText(osmFileName);
        osmFileNameHeaderTextView.setVisibility(View.VISIBLE);
        osmFileNameTextView.setVisibility(View.VISIBLE);

        widgetValueChanged();
    }

    @Override
    public IAnswerData getAnswer() {
        String s = osmFileNameTextView.getText().toString();

        return !s.isEmpty()
                ? new StringData(s)
                : null;
    }

    @Override
    public void clearAnswer() {
        osmFileNameTextView.setText(null);
        widgetValueChanged();
    }

    @Override
    public void onButtonClick(int buttonId) {
        launchOpenMapKitButton.setBackgroundColor(OSM_BLUE);
        errorTextView.setVisibility(View.GONE);
        launchOpenMapKit();
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        osmFileNameTextView.setOnLongClickListener(l);
        launchOpenMapKitButton.setOnLongClickListener(l);
    }

    /**
     * See: https://github.com/AmericanRedCross/openmapkit/wiki/ODK-Collect-Tag-Intent-Extras
     */
    private void writeOsmRequiredTagsToExtras(Intent intent) {
        ArrayList<String> tagKeys = new ArrayList<>();
        for (OSMTag tag : osmRequiredTags) {
            tagKeys.add(tag.key);
            if (tag.label != null) {
                intent.putExtra("TAG_LABEL." + tag.key, tag.label);
            }
            ArrayList<String> tagValues = new ArrayList<>();
            if (tag.items != null) {
                for (OSMTagItem item : tag.items) {
                    tagValues.add(item.value);
                    if (item.label != null) {
                        intent.putExtra("TAG_VALUE_LABEL." + tag.key + "." + item.value,
                                item.label);
                    }
                }
            }
            intent.putStringArrayListExtra("TAG_VALUES." + tag.key, tagValues);
        }
        intent.putStringArrayListExtra("TAG_KEYS", tagKeys);
    }
}
