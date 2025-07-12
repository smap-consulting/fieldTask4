/*
 * Copyright 2019 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.feature.formentry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.odk.collect.android.R;
import org.odk.collect.android.support.pages.AdminSettingsPage;
import org.odk.collect.android.support.pages.GeneralSettingsPage;

public class NavigationButtonsTest {

    CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain copyFormChain = TestRuleChain.chain()
            .around(rule);

    @Test //TestCase14
    public void showsAndHidesButtonsCorrectlyOnEachScreen() {
        rule.mainMenu()
                .copyForm("two-question.xml")

                .startBlankForm("Two Question")
                .assertQuestion("What is your name?")
                .assertTextNotDisplayed(R.string.form_backward)

                .clickForwardButton()
                .assertQuestion("What is your age?")
                .clickBackwardButton()
                .assertQuestion("What is your name?")
                .clickForwardButton()
                .assertQuestion("What is your age?")

                .clickForwardButtonToEndScreen()
                .assertText(R.string.form_backward)
                .assertTextNotDisplayed(R.string.form_forward);
    }

    @Test
    public void whenNavigatingBackwardsIsDisabled_showsAndHidesButtonsCorrectlyOnEachScreen() {
        rule.mainMenu()
                .clickOnMenu()
                .clickAdminSettings()
                .clickFormEntrySettings()
                .clickMovingBackwards()
                .clickOnString(R.string.yes)
                .pressBack(new AdminSettingsPage(rule))
                .pressBack(new MainMenuPage(rule))

                .copyForm("two-question.xml")

                .startBlankForm("Two Question")
                .assertQuestion("What is your name?")
                .assertTextNotDisplayed(R.string.form_backward)

                .clickForwardButton()
                .assertQuestion("What is your age?")
                .assertTextNotDisplayed(R.string.form_backward)

                .clickForwardButtonToEndScreen()
                .assertTextNotDisplayed(R.string.form_backward)
                .assertTextNotDisplayed(R.string.form_forward);
    }

    @Test
    public void whenButtonsDisabled_buttonsNotShown() {
        rule.mainMenu()
                .clickOnMenu()
                .clickGeneralSettings()
                .clickOnUserInterface()
                .clickNavigation()
                .clickSwipes()
                .pressBack(new GeneralSettingsPage(rule))
                .pressBack(new MainMenuPage(rule))
                .copyForm("two-question.xml")

                .startBlankForm("Two Question")
                .assertTextNotDisplayed(R.string.form_backward)
                .assertTextNotDisplayed(R.string.form_forward)

                .swipeToNextQuestion("What is your age?")
                .assertTextNotDisplayed(R.string.form_backward)
                .assertTextNotDisplayed(R.string.form_forward)

                .swipeToEndScreen()
                .assertTextNotDisplayed(R.string.form_backward)
                .assertTextNotDisplayed(R.string.form_forward);
    }
}
