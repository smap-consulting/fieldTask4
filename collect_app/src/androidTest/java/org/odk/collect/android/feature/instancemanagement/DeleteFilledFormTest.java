package org.odk.collect.android.feature.instancemanagement;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.support.CopyFormRule;

@RunWith(AndroidJUnit4.class)
public class DeleteFilledFormTest {

    public final CollectTestRule rule = new CollectTestRule();

    @Rule
    public final RuleChain chain = TestRuleChain.chain()
            .around(new CopyFormRule("one-question.xml"))
            .around(rule);

    @Test
    public void deletingAForm_removesFormFromFinalizedForms() {
        rule.mainMenu()
                .startBlankForm("One Question")
                .answerQuestion("what is your age", "30")
                .swipeToEndScreen()
                .clickSaveAndExit()

                .clickDeleteSavedForm()
                .clickForm("One Question")
                .clickDeleteSelected(1)
                .clickDeleteForms()
                .pressBack(new MainMenuPage(rule))
                .assertNumberOfFinalizedForms(0);
    }
}
