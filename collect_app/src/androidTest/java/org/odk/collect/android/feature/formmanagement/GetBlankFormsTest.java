package org.odk.collect.android.feature.formmanagement;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.support.pages.GetBlankFormPage;

@RunWith(AndroidJUnit4.class)
public class GetBlankFormsTest {

    public CollectTestRule rule = new CollectTestRule();

    final TestDependencies testDependencies = new TestDependencies();

    @Rule
    public RuleChain copyFormChain = TestRuleChain.chain(testDependencies)
            .around(rule);

    @Test
    public void whenThereIsAnAuthenticationErrorFetchingFormList_allowsUserToReenterCredentials() {
        testDependencies.server.setCredentials("Draymond", "Green");
        testDependencies.server.addForm("One Question", "one-question", "1", "one-question.xml");

        rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .clickGetBlankFormWithAuthenticationError()
                .fillUsername("Draymond")
                .fillPassword("Green")
                .clickOK(new GetBlankFormPage(rule))
                .assertText("One Question");
    }

    @Test
    public void whenThereIsAnErrorFetchingFormList_showsError() {
        testDependencies.server.alwaysReturnError();

        rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .clickGetBlankFormWithError()
                .assertText(R.string.load_remote_form_error)
                .clickOK(new GetBlankFormPage(rule));
    }

    @Test
    public void whenThereIsAnErrorFetchingForms_showsError() {
        testDependencies.server.addForm("One Question", "one-question", "1", "one-question.xml");
        testDependencies.server.errorOnFetchingForms();

        rule.mainMenu()
                .setServer(testDependencies.server.getURL())
                .clickGetBlankForm()
                .clickGetSelected()
                .assertText("One Question (Version:: 1 ID: one-question) - Failure")
                .clickOK(new GetBlankFormPage(rule));
    }
}
