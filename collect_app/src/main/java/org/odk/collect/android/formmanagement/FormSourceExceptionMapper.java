package org.odk.collect.android.formmanagement;

import android.content.Context;

import org.odk.collect.android.R;
import org.odk.collect.android.forms.FormSourceException;

import static org.odk.collect.android.forms.FormSourceException.ParseError;
import static org.odk.collect.android.forms.FormSourceException.SecurityError;
import static org.odk.collect.android.forms.FormSourceException.ServerError;
import static org.odk.collect.android.forms.FormSourceException.Unreachable;
import static org.odk.collect.android.utilities.TranslationHandler.getString;

public class FormSourceExceptionMapper {

    private final Context context;

    public FormSourceExceptionMapper(Context context) {
        this.context = context;
    }

    public String getMessage(FormSourceException exception) {
        if (exception instanceof Unreachable) {
            return getString(context, org.odk.collect.strings.R.string.unreachable_error, ((Unreachable) exception).getServerUrl()) + " " + getString(context, org.odk.collect.strings.R.string.report_to_project_lead);
        } else if (exception instanceof SecurityError) {
            return getString(context, org.odk.collect.strings.R.string.security_error, ((SecurityError) exception).getServerUrl()) + " " + getString(context, org.odk.collect.strings.R.string.report_to_project_lead);
        } else if (exception instanceof ServerError) {
            return getString(context, org.odk.collect.strings.R.string.server_error, ((ServerError) exception).getServerUrl(), ((ServerError) exception).getStatusCode()) + " " + getString(context, org.odk.collect.strings.R.string.report_to_project_lead);
        } else if (exception instanceof ParseError) {
            return getString(context, org.odk.collect.strings.R.string.invalid_response, ((ParseError) exception).getServerUrl()) + " " + getString(context, org.odk.collect.strings.R.string.report_to_project_lead);
        } else {
            return getString(context, org.odk.collect.strings.R.string.report_to_project_lead);
        }
    }
}
