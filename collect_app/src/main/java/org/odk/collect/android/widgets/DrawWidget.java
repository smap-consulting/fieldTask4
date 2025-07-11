/*
 * Copyright (C) 2012 University of Washington
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

package org.odk.collect.android.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.DrawActivity;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.formentry.questions.WidgetViewUtils;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.android.utilities.QuestionMediaManager;
import org.odk.collect.android.widgets.interfaces.ButtonClickListener;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;

import static org.odk.collect.android.formentry.questions.WidgetViewUtils.createSimpleButton;
import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes;

/**
 * Free drawing widget.
 *
 * @author BehrAtherton@gmail.com
 */
@SuppressLint("ViewConstructor")
public class DrawWidget extends BaseImageWidget implements ButtonClickListener {

    Button drawButton;

    public DrawWidget(Context context, QuestionDetails prompt, QuestionMediaManager questionMediaManager, WaitingForDataRegistry waitingForDataRegistry) {
        super(context, prompt, questionMediaManager, waitingForDataRegistry, new MediaUtils());
        imageClickHandler = new DrawImageClickHandler(DrawActivity.OPTION_DRAW, RequestCodes.DRAW_IMAGE, org.odk.collect.strings.R.string.draw_image);
        setUpLayout();
        addCurrentImageToLayout();
        addAnswerView(answerLayout, WidgetViewUtils.getStandardMargin(context));
    }

    @Override
    protected void setUpLayout() {
        super.setUpLayout();
        drawButton = createSimpleButton(getContext(), questionDetails.isReadOnly(), getContext().getString(org.odk.collect.strings.R.string.draw_image), getAnswerFontSize(), this);

        answerLayout.addView(drawButton);
        answerLayout.addView(errorTextView);

        errorTextView.setVisibility(View.GONE);
    }

    @Override
    public Intent addExtrasToIntent(Intent intent) {
        return intent;
    }

    @Override
    protected boolean doesSupportDefaultValues() {
        return true;
    }

    @Override
    public void clearAnswer() {
        super.clearAnswer();
        // reset buttons
        drawButton.setText(getContext().getString(org.odk.collect.strings.R.string.draw_image));
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        drawButton.setOnLongClickListener(l);
        super.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        drawButton.cancelLongPress();
    }

    @Override
    public void onButtonClick(int buttonId) {
        imageClickHandler.clickImage("drawButton");
    }
}
