package org.odk.collect.android.feature.formentry;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SaveAsTest {

    public final CollectTestRule rule = new CollectTestRule();

    @Rule
    public final RuleChain chain = TestRuleChain.chain()
            .around(rule);

    @Test
    public void fillingFormNameAtEndOfForm_savesInstanceWithName() {
        rule.mainMenu()
                .copyForm("one-question.xml")
                .startBlankForm("One Question")
                .swipeToEndScreen()
                .fillInFormName("My Favourite Form")
                .clickSaveAndExit()
                .clickSendFinalizedForm(1)
                .assertText("My Favourite Form");
    }
}
