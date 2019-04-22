/*
Copyright (c) 2018 Hocuri

This file is part of SuperFreezZ.

SuperFreezZ is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SuperFreezZ is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SuperFreezZ.  If not, see <http://www.gnu.org/licenses/>.
*/


package superfreeze.tool.android.userInterface;

import android.os.Bundle;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import androidx.fragment.app.Fragment;
import superfreeze.tool.android.BuildConfig;
import superfreeze.tool.android.IntroFragment;
import superfreeze.tool.android.IntroModesFragment;
import superfreeze.tool.android.R;
import superfreeze.tool.android.database.DatabaseKt;

/**
 * Shows the intro slides on the very first startup
 */
public final class IntroActivity extends AppIntro {

	public static final String SHOW_ACCESSIBILITY_SERVICE_CHOOSER = BuildConfig.APPLICATION_ID + "SHOW_ACCESSIBILITY_SERVICE_CHOOSER";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().hide();

		String action = getIntent().getAction();
		if (SHOW_ACCESSIBILITY_SERVICE_CHOOSER.equals(action)) {

			addSlide(new AccessibilityServiceChooserFragment());

		} else {

			addSlide(new IntroFragment());
			addSlide(new IntroModesFragment());
			addSlide(new AccessibilityServiceChooserFragment());
		}

		//The 3D animation has a early 2000 feel to it. Therefore I did not find it visually fitting
		//setDepthAnimation();
		showSkipButton(false);
	}

	@Override
	public void onDonePressed(Fragment currentFragment) {
		super.onDonePressed(currentFragment);
		done();
	}

	public void done() {
		DatabaseKt.firstLaunchCompleted(getApplicationContext());
		finish();
	}

}
