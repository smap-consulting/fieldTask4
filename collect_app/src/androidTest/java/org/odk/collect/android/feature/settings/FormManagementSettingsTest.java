package org.odk.collect.android.feature.settings;

import android.Manifest;

import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.MainMenuActivity;
import org.odk.collect.android.support.NotificationDrawerRule;
import org.odk.collect.android.support.pages.FormManagementPage;
import org.odk.collect.android.support.pages.GeneralSettingsPage;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class FormManagementSettingsTest {

    private final TestDependencies testDependencies = new TestDependencies();
    private final NotificationDrawerRule notificationDrawer = new NotificationDrawerRule();

    public IntentsTestRule<MainMenuActivity> rule = new IntentsTestRule<>(MainMenuActivity.class);

    @Rule
    public RuleChain copyFormChain = TestRuleChain.chain(testDependencies)
            .around(GrantPermissionRule.grant(Manifest.permission.GET_ACCOUNTS))
            .around(notificationDrawer)
            .around(rule);

    @Test
    public void whenMatchExactlyEnabled_changingAutomaticUpdateFrequency_changesTaskFrequency() {
        List<TestScheduler.DeferredTask> deferredTasks = testDependencies.scheduler.getDeferredTasks();
        assertThat(deferredTasks, is(empty()));

        FormManagementPage page = new MainMenuPage(rule).assertOnPage()
                .clickOnMenu()
                .clickGeneralSettings()
                .clickFormManagement()
                .clickUpdateForms()
                .clickOption(R.string.match_exactly);

        deferredTasks = testDependencies.scheduler.getDeferredTasks();
        assertThat(deferredTasks.size(), is(1));
        String matchExactlyTag = deferredTasks.get(0).getTag();

        page.clickAutomaticUpdateFrequency()
                .clickOption(R.string.every_one_hour);

        deferredTasks = testDependencies.scheduler.getDeferredTasks();
        assertThat(deferredTasks.size(), is(1));
        assertThat(deferredTasks.get(0).getTag(), is(matchExactlyTag));
        assertThat(deferredTasks.get(0).getRepeatPeriod(), is(1000L * 60 * 60));
    }

    @Test
    public void whenPreviouslyDownloadedOnlyEnabled_changingAutomaticUpdateFrequency_changesTaskFrequency() {
        List<TestScheduler.DeferredTask> deferredTasks = testDependencies.scheduler.getDeferredTasks();
        assertThat(deferredTasks, is(empty()));

        FormManagementPage page = new MainMenuPage(rule).assertOnPage()
                .clickOnMenu()
                .clickGeneralSettings()
                .clickFormManagement()
                .clickUpdateForms()
                .clickOption(R.string.previously_downloaded_only);

        deferredTasks = testDependencies.scheduler.getDeferredTasks();
        assertThat(deferredTasks.size(), is(1));
        String previouslyDownloadedTag = deferredTasks.get(0).getTag();

        page.clickAutomaticUpdateFrequency()
                .clickOption(R.string.every_one_hour);

        deferredTasks = testDependencies.scheduler.getDeferredTasks();
        assertThat(deferredTasks.size(), is(1));
        assertThat(deferredTasks.get(0).getTag(), is(previouslyDownloadedTag));
        assertThat(deferredTasks.get(0).getRepeatPeriod(), is(1000L * 60 * 60));
    }

    @Test
    public void whenPreviouslyDownloadedOnlyEnabled_checkingAutoDownload_downloadsUpdatedForms() throws Exception {
        FormManagementPage page = new MainMenuPage(rule).assertOnPage()
                .setServer(testDependencies.server.getURL())
                .clickOnMenu()
                .clickGeneralSettings()
                .clickFormManagement()
                .clickUpdateForms()
                .clickOption(R.string.previously_downloaded_only)
                .clickOnString(R.string.automatic_download);

        FormLoadingUtils.copyFormToStorage("one-question.xml");
        testDependencies.server.addForm("One Question Updated", "one_question", "2", "one-question-updated.xml");
        testDependencies.scheduler.runDeferredTasks();

        page.pressBack(new GeneralSettingsPage(rule))
                .pressBack(new MainMenuPage(rule))
                .clickFillBlankForm()
                .assertText("One Question Updated");

        notificationDrawer.open()
                .assertAndDismissNotification("ODK Collect", "ODK auto-download results", "Success");
    }

    @Test
    public void whenGoogleDriveUsingAsServer_disablesPrefsAndOnlyAllowsManualUpdates() {
        testDependencies.googleAccountPicker.setDeviceAccount("steph@curry.basket");

        new MainMenuPage(rule).assertOnPage()
                .enablePreviouslyDownloadedOnlyUpdates() // Enabled a different mode before setting up Google
                .setGoogleAccount("steph@curry.basket")
                .clickOnMenu()
                .clickGeneralSettings()
                .clickFormManagement()
                .assertDisabled(R.string.form_update_mode_title)
                .assertDisabled(R.string.form_update_frequency_title)
                .assertDisabled(R.string.automatic_download)
                .assertText(R.string.manual);

        assertThat(testDependencies.scheduler.getDeferredTasks().size(), is(0));
    }
}
