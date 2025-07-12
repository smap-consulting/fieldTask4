package org.odk.collect.android.regression;

import android.Manifest;

import androidx.test.rule.GrantPermissionRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.support.pages.AboutPage;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.odk.collect.android.support.matchers.DrawableMatcher.withImageDrawable;
import static org.odk.collect.android.support.matchers.RecyclerViewMatcher.withRecyclerView;

//Issue NODK-234
@RunWith(AndroidJUnit4.class)
public class AboutPageTest {

    public CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain ruleChain = RuleChain
            .outerRule(GrantPermissionRule.grant(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE))
            .around(rule);

    @Test
    public void when_rotateScreenOnAboutPage_should_notCrash() {
        //TestCase1
        new MainMenuPage(rule)
                .clickOnMenu()
                .clickAbout()
                .rotateToLandscape(new AboutPage(rule))
                .assertOnPage()
                .scrollToOpenSourceLibrariesLicenses();
    }

    @Test
    public void when_openAboutPage_should_iconsBeVisible() {
        //TestCase2
        new MainMenuPage(rule)
                .clickOnMenu()
                .clickAbout()
                .assertOnPage();

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(0, R.id.title))
                .check(matches(withText(R.string.odk_website)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(0, R.id.summary))
                .check(matches(withText(R.string.odk_website_summary)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(0, R.id.imageView))
                .check(matches(withImageDrawable(R.drawable.ic_website)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(1, R.id.title))
                .check(matches(withText(R.string.odk_forum)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(1, R.id.summary))
                .check(matches(withText(R.string.odk_forum_summary)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(1, R.id.imageView))
                .check(matches(withImageDrawable(R.drawable.ic_forum)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(2, R.id.title))
                .check(matches(withText(R.string.tell_your_friends)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(2, R.id.summary))
                .check(matches(withText(R.string.tell_your_friends_msg)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(2, R.id.imageView))
                .check(matches(withImageDrawable(R.drawable.ic_share)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(3, R.id.title))
                .check(matches(withText(R.string.leave_a_review)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(3, R.id.summary))
                .check(matches(withText(R.string.leave_a_review_msg)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(3, R.id.imageView))
                .check(matches(withImageDrawable(R.drawable.ic_review_rate)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(4, R.id.title))
                .check(matches(withText(R.string.all_open_source_licenses)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(4, R.id.summary))
                .check(matches(withText(R.string.all_open_source_licenses_msg)));

        onView(withRecyclerView(R.id.recyclerView)
                .atPositionOnView(4, R.id.imageView))
                .check(matches(withImageDrawable(R.drawable.ic_stars)));
    }

    @Test
    public void when_OpenSourcesLibrariesLicenses_should_openSourceLicensesTitleBeDisplayed() {
        //TestCase3
        new MainMenuPage(rule)
                .clickOnMenu()
                .clickAbout()
                .clickOnOpenSourceLibrariesLicenses();
    }
}
