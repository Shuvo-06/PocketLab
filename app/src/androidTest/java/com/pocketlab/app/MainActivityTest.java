package com.pocketlab.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Smoke test: the app starts, shows its four tabs, and every tab opens. Runs on a device or emulator. */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void startsOnTheSensorDashboardWithAllTabsVisible() {
        onView(withId(R.id.summary)).check(matches(isDisplayed()));
        onView(withText(R.string.nav_dashboard)).check(matches(isDisplayed()));
        onView(withText(R.string.nav_recorder)).check(matches(isDisplayed()));
        onView(withText(R.string.nav_experiments)).check(matches(isDisplayed()));
        onView(withText(R.string.nav_history)).check(matches(isDisplayed()));
    }

    @Test
    public void everyTabOpens() {
        onView(withId(R.id.recorderFragment)).perform(click());
        // Either the recorder controls or the "no sensors" message is shown, depending on the device.
        onView(anyOf(allOf(withId(R.id.content), isDisplayed()), allOf(withId(R.id.empty_text), isDisplayed())))
                .check(matches(isDisplayed()));

        onView(withId(R.id.experimentsFragment)).perform(click());
        onView(withId(R.id.experiment_list)).check(matches(isDisplayed()));

        onView(withId(R.id.historyFragment)).perform(click());
        onView(withId(R.id.search_edit)).check(matches(isDisplayed()));

        onView(withId(R.id.dashboardFragment)).perform(click());
        onView(withId(R.id.summary)).check(matches(isDisplayed()));
    }
}
