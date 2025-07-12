package org.odk.collect.android.feature.formmanagement;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.support.CopyFormRule;
import org.odk.collect.android.support.NotificationDrawerRule;
import org.odk.collect.android.support.pages.FillBlankFormPage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class MatchExactlyTest {

    final CollectTestRule rule = new CollectTestRule();
    final TestDependencies testDependencies = new TestDependencies();
    final NotificationDrawerRule notificationDrawerRule = new NotificationDrawerRule();

    @Rule
    public RuleChain copyFormChain = TestRuleChain.chain(testDependencies)
            .around(notificationDrawerRule)
            .around(new CopyFormRule("one-question.xml"))
            .around(new CopyFormRule("one-question-repeat.xml"))
            .around(rule);

    @Test
    public void whenMatchExactlyEnabled_clickingFillBlankForm_andClickingRefresh_getsLatestFormsFromServer() {
        FillBlankFormPage page = rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .enableMatchExactly()
                .clickFillBlankForm()
                .assertText("One Question")
                .assertText("One Question Repeat");

        testDependencies.server.addForm("One Question Updated", "one_question", "2", "one-question-updated.xml");
        testDependencies.server.addForm("Two Question", "two_question", "1", "two-question.xml");

        page.clickRefresh()
                .assertText("Two Question") // Check new form downloaded
                .assertText("One Question Updated") // Check updated form updated
                .assertTextDoesNotExist("One Question Repeat"); // Check deleted form deleted
    }

    @Test
    public void whenMatchExactlyEnabled_clickingFillBlankForm_andClickingRefresh_whenThereIsAnError_showsNotification_andClickingNotification_returnsToFillBlankForms() throws Exception {
        testDependencies.server.alwaysReturnError();

        rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .enableMatchExactly()
                .clickFillBlankForm()
                .clickRefreshWithError();

        notificationDrawerRule
                .open()
                .clickNotification(
                        "ODK Collect",
                        "Form update failed",
                        "Fill Blank Form",
                        new FillBlankFormPage(rule)
                ).pressBack(new MainMenuPage(rule)); // Check we return to Fill Blank Form, not open a new one
    }

    @Test
    public void whenMatchExactlyEnabled_clickingFillBlankForm_andClickingRefresh_whenThereIsAnAuthenticationError_promptsForCredentials() throws Exception {
        testDependencies.server.addForm("One Question Updated", "one_question", "2", "one-question-updated.xml");
        testDependencies.server.setCredentials("Klay", "Thompson");

        rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .enableMatchExactly()
                .clickFillBlankForm()
                .clickRefreshWithAuthError()
                .fillUsername("Klay")
                .fillPassword("Thompson")
                .clickOK(new FillBlankFormPage(rule))
                .clickRefresh()
                .assertText("One Question Updated");
    }

    @Test
    public void whenMatchExactlyEnabled_getsLatestFormsFromServer_automaticallyAndRepeatedly() throws Exception {
        MainMenuPage page = rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .enableMatchExactly();

        testDependencies.server.addForm("One Question Updated", "one_question", "2", "one-question-updated.xml");
        testDependencies.server.addForm("Two Question", "two_question", "1", "two-question.xml");
        testDependencies.scheduler.runDeferredTasks();

        page = page.clickFillBlankForm()
                .assertText("Two Question")
                .assertText("One Question Updated")
                .assertTextDoesNotExist("One Question Repeat")
                .pressBack(new MainMenuPage(rule));

        testDependencies.server.removeForm("Two Question");
        testDependencies.scheduler.runDeferredTasks();

        page.assertOnPage()
                .clickFillBlankForm()
                .assertText("One Question Updated")
                .assertTextDoesNotExist("Two Question");
    }

    @Test
    public void whenMatchExactlyEnabled_hidesGetBlankFormsAndDeleteBlankForms() {
        rule.mainMenu()
                .enableMatchExactly()
                .assertTextNotDisplayed(R.string.get_forms)
                .clickDeleteSavedForm()
                .assertTextDoesNotExist(R.string.forms);
    }

    @Test
    public void whenMatchExactlyDisabled_stopsSyncingAutomatically() {
        rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .enableMatchExactly()
                .enableManualUpdates();

        assertThat(testDependencies.scheduler.getDeferredTasks(), is(empty()));
    }

}
