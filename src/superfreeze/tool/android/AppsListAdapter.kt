/*
Copyright (c) 2015 axxapy
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


package superfreeze.tool.android

import android.app.usage.UsageStats
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.support.v7.widget.RecyclerView
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.jetbrains.annotations.Contract

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory


/**
 * This class is responsible for viewing the list of installed apps.
 */
class AppsListAdapter internal constructor(private val mActivity: MainActivity) : RecyclerView.Adapter<AppsListAdapter.ViewHolder>() {
	private val tFactory = ThreadFactory { r ->
		val t = Thread(r)
		t.isDaemon = true
		t
	}

	/**
	 * This list contains all user apps and the section headers.
	 */
	private val listOriginal = ArrayList<AbstractListItem>()

	/**
	 * This list contains the items that are shown to the user, including section headers.
	 */
	private val list = ArrayList<AbstractListItem>()

	private var listPendingFreeze = emptyList<PackageInfo>()

	private var executorServiceNames: ExecutorService? = null
	private val executorServiceIcons = Executors.newFixedThreadPool(3, tFactory)
	private val handler = Handler()
	private val packageManager: PackageManager = mActivity.packageManager

	private var namesToLoad = 0

	private val cacheAppName = Collections.synchronizedMap(LinkedHashMap<String, String>(10, 1.5f, true))
	private val cacheAppIcon = Collections.synchronizedMap(LinkedHashMap<String, Drawable>(10, 1.5f, true))

	var searchPattern: String = ""
		set(value) {
			field = value.toLowerCase()
			filterList()
		}


	internal inner class AppNameLoader(private val package_info: ListItemApp) : Runnable {

		override fun run() {
			cacheAppName[package_info.packageName] = package_info.applicationInfo.loadLabel(packageManager) as String
			handler.post {
				namesToLoad--
				if (namesToLoad == 0) {
					mActivity.hideProgressBar()
					executorServiceNames?.shutdown()
					executorServiceNames = null
				}
			}
		}
	}

	internal class GuiLoader(private val viewHolder: ViewHolderApp, private val listItem: AbstractListItem) : Runnable {

		override fun run() {
			listItem.loadNameAndIcon(viewHolder)
		}

	}

	override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
		return if (i == 0) {
			ViewHolderApp(
					LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false),
					viewGroup.context)
		} else {
			ViewHolderSectionHeader(
					LayoutInflater.from(viewGroup.context).inflate(R.layout.list_section_header, viewGroup, false))
		}
	}

	override fun onBindViewHolder(holder: ViewHolder, i: Int) {
		val item = list[i]

		holder.setName(item.text, searchPattern)

		holder.loadImage(item)
	}

	@Contract(pure = true)
	private fun getItem(pos: Int): AbstractListItem {
		return list[pos]
	}

	override fun getItemCount(): Int {
		return list.size
	}

	internal fun filterList() {
		list.clear()
		for (item in listOriginal) {

			if (item.isToBeShown()) {
				list.add(item)
			}
		}
		notifyDataSetChanged()
	}

	internal fun addItems(packages: List<PackageInfo>, usageStatsMap: Map<String, UsageStats>?) {
		val (listPendingFreeze, listNotPendingFreeze) = packages.partition {
			isPendingFreeze(it, usageStatsMap?.get(it.packageName))
		}

		addSectionHeader("PENDING FREEZE")
		for (info in listPendingFreeze) {
			addItem(info, usageStatsMap?.get(info.packageName))
		}

		addSectionHeader("OTHERS")
		for (info in listNotPendingFreeze) {
			addItem(info, usageStatsMap?.get(info.packageName))
		}

		this.listPendingFreeze = listPendingFreeze
	}

	private fun addItem(packageInfo: PackageInfo, usageStats: UsageStats?) {
		if (executorServiceNames == null) {
			executorServiceNames = Executors.newFixedThreadPool(3, tFactory)
		}
		val item = ListItemApp(packageInfo.packageName, usageStats)
		namesToLoad++
		executorServiceNames!!.submit(AppNameLoader(item))
		listOriginal.add(item)
		if (item.isToBeShown()) {
			list.add(item)
		}
		notifyDataSetChanged()
	}

	private fun addSectionHeader(title: String) {
		val sectionHeader = ListItemSectionHeader(title)
		list.add(sectionHeader)
		listOriginal.add(sectionHeader)
	}


	internal fun refresh() {
		for (app in listOriginal) {
			app.refresh()
		}
	}



	companion object {
		private const val TAG = "AppsListAdapter"
	}

	override fun getItemViewType(position: Int): Int {
		return getItem(position).type
	}




	abstract class ViewHolder(v: View) : RecyclerView.ViewHolder(v), OnClickListener {
		abstract fun setName(name: String, highlight: String?)
		abstract fun loadImage(item: AppsListAdapter.AbstractListItem)
	}

	inner class ViewHolderApp(v: View, private val context: Context) : ViewHolder(v) {
		private val txtAppName: TextView = v.findViewById(R.id.txtAppName)
		val imgIcon: ImageView = v.findViewById(R.id.imgIcon)

		init {
			v.setOnClickListener(this)
		}

		/**
		 * This method defines what is done when a list item (that is, an app) is clicked.
		 * @param v The clicked view.
		 */
		override fun onClick(v: View) {
			getItem(adapterPosition).freeze(context)
		}

		override fun setName(name: String, highlight: String?) {
			setAndHighlight(txtAppName, name, highlight)
		}

		private fun setAndHighlight(view: TextView, value: String, pattern: String?) {
			view.text = value
			if (pattern == null || pattern.isEmpty()) return // nothing to highlight

			val valueLower = value.toLowerCase()
			var offset = 0
			var index = valueLower.indexOf(pattern, offset)
			while (index >= 0 && offset < valueLower.length) {
				val span = SpannableString(view.text)
				span.setSpan(ForegroundColorSpan(Color.BLUE), index, index + pattern.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
				view.text = span
				offset += index + pattern.length
				index = valueLower.indexOf(pattern, offset)
			}
		}

		override fun loadImage(item: AbstractListItem) {
			imgIcon.setImageDrawable(cacheAppIcon[item.packageName])
			if (cacheAppIcon[item.packageName] == null) {
				executorServiceIcons.submit(GuiLoader(this, item))
			}
		}
	}

	class ViewHolderSectionHeader(private val v: View): ViewHolder(v) {
		override fun loadImage(item: AbstractListItem) {
		}
		override fun onClick(v: View?) {
		}

		override fun setName(name: String, highlight: String?) {
			v.findViewById<TextView>(R.id.textView).text = name
		}
	}


	abstract class AbstractListItem {
		abstract fun loadNameAndIcon(viewHolder: AppsListAdapter.ViewHolderApp)
		abstract fun freeze(context: Context)
		abstract fun refresh()
		abstract fun isToBeShown(): Boolean

		abstract val applicationInfo: ApplicationInfo?
		abstract val packageName: String?
		abstract val text: String
		abstract val type: Int
	}

	inner class ListItemApp(override val packageName: String, val usageStats: UsageStats?) : AbstractListItem() {
		override fun refresh() {
			_applicationInfo = null
		}

		override val type = 0
		override val applicationInfo: ApplicationInfo
			get() {
				//TODO test if 0 instead of GET_META_DATA is sufficient
				if (_applicationInfo == null)
					_applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
				return _applicationInfo!!
			}

		private var _applicationInfo: ApplicationInfo? = null

		override fun loadNameAndIcon(viewHolder: ViewHolderApp) {
			var first = true
			do {
				try {
					val appName = cacheAppName[packageName]
							?: applicationInfo.loadLabel(packageManager) as String

					val icon = applicationInfo.loadIcon(packageManager)
					cacheAppName[packageName] = appName
					cacheAppIcon[packageName] = icon
					handler.post {
						viewHolder.setName(appName, searchPattern)
						viewHolder.imgIcon.setImageDrawable(icon)
					}


				} catch (ex: OutOfMemoryError) {
					cacheAppIcon.clear()
					cacheAppName.clear()
					if (first) {
						first = false
						continue
					}
				}

				break
			} while (true)
		}

		override fun freeze(context: Context) {
			freezeApp(packageName, context)
		}

		override val text: String
			get() = cacheAppName[packageName] ?: packageName

		override fun isToBeShown(): Boolean {
			if (searchPattern.isEmpty()) {
				return true// empty search pattern: Show all apps
			} else if (cacheAppName[packageName]?.toLowerCase()?.contains(searchPattern) != false) {
				return true// search in application name
			}

			return false
		}
	}

	class ListItemSectionHeader(override val text: String) : AbstractListItem() {

		override val type = 1

		//These functions here do nothing:
		override fun refresh() {
		}
		override fun loadNameAndIcon(viewHolder: ViewHolderApp) {
		}
		override fun freeze(context: Context) {
		}

		override fun isToBeShown() = true

		override val packageName: String? get() = null
		override val applicationInfo: ApplicationInfo? get() = null

	}
}