package org.odk.collect.android.formentry;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.odk.collect.android.R;
import org.odk.collect.android.utilities.FormNameUtils;

public class FormEndView extends FrameLayout {

    private final Listener listener;
    private final String formTitle;
    private final String defaultInstanceName;
    private final boolean readOnly;

    public FormEndView(Context context, String formTitle, String defaultInstanceName,
                       boolean readOnly, boolean instanceComplete, Listener listener) {
        super(context);
        this.formTitle = formTitle;
        this.readOnly = readOnly;
        this.defaultInstanceName = defaultInstanceName;
        this.listener = listener;
        init(context, instanceComplete);
    }

    private void init(Context context, boolean instanceComplete) {
        inflate(context, R.layout.form_entry_end, this);

        ((TextView) findViewById(R.id.description)).setText(context.getString(org.odk.collect.strings.R.string.save_enter_data_description, formTitle));

        EditText saveAs = findViewById(R.id.save_name);
        Button saveButton = findViewById(R.id.save_exit_button);

        // disallow carriage returns in the name
        InputFilter returnFilter = (source, start, end, dest, dstart, dend) -> FormNameUtils.normalizeFormName(source.toString().substring(start, end), true);
        saveAs.setFilters(new InputFilter[]{returnFilter});

        saveAs.setText(defaultInstanceName);
        saveAs.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                listener.onSaveAsChanged(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        final CheckBox markAsFinalized = findViewById(R.id.mark_finished);
        markAsFinalized.setChecked(instanceComplete);

        if(!readOnly) {
            // Note even instances that cannot be updated have to be saved as the comments are saved
            saveButton.setOnClickListener(v -> {
                listener.onSaveClicked(markAsFinalized.isChecked());
            });
        } else {
            // Readonly do not save
            saveButton.setText(org.odk.collect.strings.R.string.exit);
            saveButton.setOnClickListener(v -> {
                listener.onExitClicked();
            });
        }


    }

    public interface Listener {
        void onSaveAsChanged(String string);

        void onSaveClicked(boolean markAsFinalized);

        void onExitClicked();
    }
}
