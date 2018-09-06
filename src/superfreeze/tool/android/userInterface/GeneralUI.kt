/*
Copyright (c) 2018 Hocceruser

This file is part of SuperFreeze.

SuperFreeze is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SuperFreeze is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SuperFreeze.  If not, see <http://www.gnu.org/licenses/>.
*/

package superfreeze.tool.android.userInterface

import android.Manifest
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.widget.Toast
import superfreeze.tool.android.R

internal fun requestUsageStatsPermission(context: Context, doAfterwards: () -> Unit) {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
		return
	}

	if (!usageStatsPermissionGranted(context)) {

		AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog)
				.setTitle("UsageStats access")
				.setMessage("If you enable UsageStats access, SuperFreeze can:\n - see which apps have been awoken since last freeze\n - freeze only apps you did not use for some time.")
				.setPositiveButton("Enable") { _, _ ->
					showUsageStatsSettings(context)
					MainActivity.doOnResume {

						if (!usageStatsPermissionGranted(context)) {
							toast(context, "You did not enable usagestats access.", Toast.LENGTH_SHORT)
						}
						doAfterwards()

						//Do not execute again
						false
					}
				}
				.setNeutralButton("Not now") { _, _ ->
					//directly load running applications
					doAfterwards()
				}
				//TODO add negative button "never"
				.setIcon(R.mipmap.ic_launcher)
				.setCancelable(false)
				.show()
	} else {
		//directly load running applications
		doAfterwards()
	}

}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
private fun showUsageStatsSettings(context: Context) {
	val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
	context.startActivity(intent)
	toast(context, "Please select SuperFreeze, then enable access", Toast.LENGTH_LONG)
}

private fun usageStatsPermissionGranted(context: Context): Boolean {

	//On earlier versions there are no usage stats
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
		return false
	}

	val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

	val mode = appOpsManager.checkOpNoThrow(
			AppOpsManager.OPSTR_GET_USAGE_STATS,
			Process.myUid(),
			context.packageName)

	return if (mode == AppOpsManager.MODE_DEFAULT) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
		} else {
			false//TODO check if this assumption is right: At Lollipop, mode will be AppOpsManager.MODE_ALLOWED if it was allowed
		}
	} else {
		mode == AppOpsManager.MODE_ALLOWED
	}


}

private fun toast(context: Context, s: String, duration: Int) {
	Toast.makeText(context, s, duration).show()
}