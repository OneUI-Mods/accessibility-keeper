package dev.ryuya.accessibilitykeeper

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var enabled: MaterialSwitch
    private lateinit var modes: RadioGroup
    private lateinit var apps: LinearLayout
    private val appSwitches = mutableMapOf<String, MaterialSwitch>()
    private val refresh = { runOnUiThread { show() } }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(resource: Int, style: Int) =
        TextView(this).apply {
            setText(resource)
            setTextAppearance(style)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(24), dp(20), dp(24))
            }
        content.addView(
            text(
                R.string.app_name,
                com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall,
            ),
        )
        content.addView(
            text(
                    R.string.summary,
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                )
                .apply {
                    setPadding(0, dp(8), 0, dp(16))
                },
        )
        status =
            text(
                R.string.status_waiting,
                com.google.android.material.R.style.TextAppearance_Material3_LabelLarge,
            )
        content.addView(status)
        val card =
            MaterialCardView(this).apply {
                radius = dp(24).toFloat()
                setContentPadding(dp(20), dp(12), dp(20), dp(16))
                layoutParams =
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { topMargin = dp(16) }
            }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        enabled =
            MaterialSwitch(this).apply {
                setText(R.string.keep_permission)
                minHeight = dp(56)
                setOnCheckedChangeListener { _, checked ->
                    ModuleApplication.settings
                        ?.edit()
                        ?.putBoolean(KeeperHooks.KEY_ENABLED, checked)
                        ?.apply()
                    modes.isEnabled = checked
                    for (index in 0 until modes.childCount) modes.getChildAt(index).isEnabled =
                        checked
                }
            }
        inner.addView(enabled)
        inner.addView(
            text(
                    R.string.after_force_stop,
                    com.google.android.material.R.style.TextAppearance_Material3_TitleSmall,
                )
                .apply {
                    setPadding(0, dp(12), 0, dp(4))
                },
        )
        modes =
            RadioGroup(this).apply {
                addView(
                    RadioButton(context).apply {
                        id = R.id.mode_wait
                        setText(R.string.mode_wait)
                    },
                )
                addView(
                    RadioButton(context).apply {
                        id = R.id.mode_reconnect
                        setText(R.string.mode_reconnect)
                    },
                )
                setOnCheckedChangeListener { _, checked ->
                    ModuleApplication.settings
                        ?.edit()
                        ?.putBoolean(KeeperHooks.KEY_RECONNECT, checked == R.id.mode_reconnect)
                        ?.apply()
                }
            }
        inner.addView(modes)
        inner.addView(
            text(
                    R.string.mode_note,
                    com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                )
                .apply {
                    setPadding(0, dp(8), 0, 0)
                },
        )
        card.addView(inner)
        content.addView(card)
        content.addView(
            text(
                    R.string.apps_title,
                    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium,
                )
                .apply {
                    setPadding(0, dp(24), 0, dp(4))
                },
        )
        content.addView(
            text(
                R.string.apps_note,
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
            ),
        )
        apps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(apps)
        content.addView(
            MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                setText(R.string.licenses)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { showLicenses() }
            },
        )
        val scroll = ScrollView(this).apply { addView(content) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        ModuleApplication.listeners += refresh
        show()
    }

    override fun onStop() {
        ModuleApplication.listeners -= refresh
        super.onStop()
    }

    private fun show() {
        val settings = ModuleApplication.settings
        status.setText(if (settings == null) R.string.status_waiting else R.string.status_ready)
        val on = settings?.getBoolean(KeeperHooks.KEY_ENABLED, true) ?: true
        enabled.isEnabled = settings != null
        enabled.isChecked = on
        modes.check(
            if (settings?.getBoolean(KeeperHooks.KEY_RECONNECT, false) == true) R.id.mode_reconnect
            else R.id.mode_wait,
        )
        for (index in 0 until modes.childCount) modes.getChildAt(index).isEnabled =
            settings != null && on
        modes.visibility = View.VISIBLE
    }

    private fun showLicenses() {
        val catalog =
            org.json.JSONArray(assets.open("legal/libraries.json").bufferedReader().readText())
        val names = (0 until catalog.length()).map { catalog.getJSONObject(it) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.licenses)
            .setItems(
                names
                    .map {
                        "${it.getString("title")} ${it.getString("version")} (${it.getString("license")})"
                    }
                    .toTypedArray(),
            ) { _, which ->
                val item = names[which]
                val body = assets.open(item.getString("asset")).bufferedReader().readText()
                MaterialAlertDialogBuilder(this)
                    .setTitle(item.getString("title"))
                    .setMessage(
                        item.optString("notice").let { if (it.isEmpty()) body else "$it\n\n$body" },
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
