package com.lumi.dockeditor

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumi.dockeditor.databinding.ActivityEditPinnedBinding
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import kotlin.concurrent.thread

class EditPinnedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditPinnedBinding
    private lateinit var adapter: AppListAdapter
    private lateinit var appList: MutableList<AppInfo>
    private lateinit var itemTouchHelper: ItemTouchHelper

    companion object {
        private const val TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml"
        private const val MAX_APPS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPinnedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Retrieve the app list from intent
        val list = intent.getParcelableArrayListExtra<AppInfo>("appList")
        if (list == null) {
            appList = mutableListOf()
            Toast.makeText(this, "Error: Could not load app list.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        appList = list.toMutableList()

        setupRecyclerView()
        setupButtons()
        updateAddButtonState()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            appList,
            dragListener = object : AppListAdapter.OnStartDragListener {
                override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
                    itemTouchHelper.startDrag(viewHolder)
                }
            },
            clickListener = object : AppListAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) = onAppClick(position)
            },
            removeListener = object : AppListAdapter.OnItemRemoveListener {
                override fun onItemRemove(position: Int) = onAppRemove(position)
            }
        )
    
        binding.appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@EditPinnedActivity)
            adapter = this@EditPinnedActivity.adapter
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                onAppReorder(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.appsRecyclerView)
    }

    private fun setupButtons() {
        binding.saveButton.setOnClickListener { saveChanges() }
        binding.addAppButton.setOnClickListener {
            if (appList.size < MAX_APPS) {
                showAppSelectionDialog()
            } else {
                Toast.makeText(this, "Maximum of $MAX_APPS apps allowed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveChanges() {
        thread {
            try {
                val newAppsArray = JSONArray()
                for (app in appList) {
                    val appObj = JSONObject(app.originalJsonString).apply {
                        put("packageName", app.packageName)
                        val appPanelData = optJSONObject("appPanelData") ?: JSONObject()
                        
                        if (app.componentName.isNotEmpty()) {
                            appPanelData.put("componentName", app.componentName)
                        } else {
                            appPanelData.remove("componentName")
                        }
                        
                        put("appPanelData", appPanelData)
                        remove("activity")
                    }
                    newAppsArray.put(appObj)
                }

                // Manually build string to preserve specific spacing/formatting required by the target app
                val jsonBuilder = StringBuilder("[")
                for (i in 0 until newAppsArray.length()) {
                    val appString = newAppsArray.getJSONObject(i).toString()
                    jsonBuilder.append(appString)

                    if (i < newAppsArray.length() - 1) {
                        if (i == newAppsArray.length() - 2) {
                            jsonBuilder.append(" ,")
                        } else {
                            jsonBuilder.append(", ")
                        }
                    }
                }
                jsonBuilder.append("]")

                val encodedJson = jsonBuilder.toString()
                    .replace("\\/", "/") 
                    .replace("\"", "&quot;")

                val newXmlContent = """
                    <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                    <map>
                        <string name="aui_bar_apps_pinned">$encodedJson</string>
                    	<string name="aui_bar_apps_history">[]</string>
                    </map>
                """.trimIndent()

                val success = RootShell.writeFileContent(TARGET_FILE, newXmlContent)

                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Changes saved successfully!\nRestart Oculus system to see changes.", Toast.LENGTH_LONG).show()
                        binding.saveButton.isEnabled = false
                        finish()
                    } else {
                        Toast.makeText(this, "Save failed: check log for details.", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onAppReorder(from: Int, to: Int) {
        if (from != to) {
            Collections.swap(appList, from, to)
            adapter.notifyItemMoved(from, to)
            binding.saveButton.isEnabled = true
        }
    }

    private fun onAppClick(position: Int) {
        showAppSelectionDialog(position)
    }

    private fun onAppRemove(position: Int) {
        appList.removeAt(position)
        adapter.notifyItemRemoved(position)
        adapter.notifyItemRangeChanged(position, appList.size)
        updateAddButtonState()
        binding.saveButton.isEnabled = true
    }

    private fun updateAddButtonState() {
        binding.addAppButton.apply {
            isEnabled = appList.size < MAX_APPS
            text = if (appList.size >= MAX_APPS) {
                "Max Apps Reached"
            } else {
                "Add App (${appList.size}/$MAX_APPS)"
            }
        }
    }

    private fun showAppSelectionDialog(editPosition: Int = -1) {
        val dialog = AppSelectionDialog(this, object : AppSelectionDialog.OnAppSelectedListener {
            override fun onAppSelected(app: InstalledAppInfo) {
                showActivitySelectionDialog(app, editPosition)
            }
        })
        dialog.show()
    }

    private fun showActivitySelectionDialog(selectedApp: InstalledAppInfo, editPosition: Int) {
        val dialog = ActivitySelectionDialog(this, selectedApp, object : ActivitySelectionDialog.OnActivitySelectedListener {
            override fun onActivitySelected(activity: ActivityInfo) {
                val newAppInfo = AppInfo(
                    selectedApp.packageName,
                    "APP",
                    "ANDROID_6DOF",
                    activity.name
                )

                if (editPosition == -1) {
                    if (appList.size < MAX_APPS) {
                        appList.add(newAppInfo)
                        adapter.notifyItemInserted(appList.size - 1)
                        updateAddButtonState()
                    }
                } else {
                    val existingApp = appList[editPosition]
                    existingApp.packageName = newAppInfo.packageName
                    existingApp.activity = newAppInfo.activity
                    existingApp.componentName = newAppInfo.componentName
                    existingApp.originalJsonString = newAppInfo.originalJsonString

                    adapter.notifyItemChanged(editPosition)
                }
                binding.saveButton.isEnabled = true
            }
        })
        dialog.show()
    }
}