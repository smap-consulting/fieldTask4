package org.odk.collect.android.feature.formentry;

import android.Manifest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.support.CopyFormRule;
import org.odk.collect.android.support.pages.SaveOrIgnoreDialog;

@RunWith(AndroidJUnit4.class)
public class QuittingFormTest {

    public CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain copyFormChain = RuleChain
            .outerRule(GrantPermissionRule.grant(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE
            ))
            .around(new ResetStateRule())
            .around(new CopyFormRule("two-question.xml"))
            .around(rule);

    @Test
    public void partiallyFillingForm_andPressingBack_andClickingSaveChanges_savesCurrentAnswers() {
        rule.mainMenu()
                .startBlankForm("Two Question")
                .answerQuestion("What is your name?", "Reuben")
                .swipeToNextQuestion()
                .answerQuestion("What is your age?", "10")
                .pressBack(new SaveOrIgnoreDialog<>("Two Question", new MainMenuPage(rule), rule))
                .clickSaveChanges()
                .clickEditSavedForm()
                .clickOnForm("Two Question")
                .assertText("Reuben") // Previous answers are saved
                .assertText("10"); // Current screen answers are saved
    }

    @Test
    public void partiallyFillingForm_andPressingBack_andClickingIgnoreChanges_doesNotSaveForm() {
        rule.mainMenu()
                .startBlankForm("Two Question")
                .answerQuestion("What is your name?", "Reuben")
                .pressBack(new SaveOrIgnoreDialog<>("Two Question", new MainMenuPage(rule), rule))
                .clickIgnoreChanges()
                .assertNumberOfEditableForms(0);
    }
}
