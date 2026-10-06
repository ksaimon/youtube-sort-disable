package com.focustube.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class MainActivity : AppCompatActivity() {
    private var showMainAfterSettings = false
    private var backAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val action = backAction
                if (action != null) {
                    backAction = null
                    action()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (Prefs.isDisclosureAccepted(this)) showMainScreen() else showDisclosureScreen()
    }

    override fun onResume() {
        super.onResume()
        if (showMainAfterSettings) {
            showMainAfterSettings = false
            showMainScreen()
        }
    }

    private fun showDisclosureScreen() {
        val content = verticalContent()
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.disclosure_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
            setTextColor(getColor(R.color.text_primary))
        })
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.disclosure_intro)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(getColor(R.color.text_secondary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })

        val disclosure = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.card_radius)
            cardElevation = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.disclosure_stroke_width)
            setCardBackgroundColor(getColor(R.color.color_surface))
            strokeColor = getColor(R.color.brand_primary)
            addView(MaterialTextView(context).apply {
                text = getString(R.string.accessibility_disclosure)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setTextColor(getColor(R.color.text_primary))
                setPadding(resources.getDimensionPixelSize(R.dimen.spacing_medium))
            })
        }
        content.addView(disclosure, margin(top = resources.getDimensionPixelSize(R.dimen.spacing_medium)))
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.disclosure_consent_note)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.text_secondary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_medium))
        })
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.privacy_button)
            setOnClickListener { showPrivacyScreen(fromDisclosure = true) }
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(MaterialButton(this@MainActivity).apply {
                text = getString(R.string.no_thanks)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small)
            })
            addView(MaterialButton(this@MainActivity).apply {
                text = getString(R.string.agree)
                setOnClickListener {
                    Prefs.acceptDisclosure(this@MainActivity)
                    showMainAfterSettings = true
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(actions, margin(top = resources.getDimensionPixelSize(R.dimen.spacing_large)))
        showScrollableScreen(getString(R.string.disclosure_screen_title), content)
    }

    private fun showMainScreen() {
        val spacing = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        val content = verticalContent()
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.welcome_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
        })
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.welcome_body)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(getColor(R.color.text_secondary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small), bottom = spacing)
        })

        val controlsCard = sectionCard()
        val controls = verticalContent()
        controls.addView(sectionTitle(getString(R.string.control_title)))
        controls.addView(createSwitch(
            getString(R.string.master_switch),
            getString(R.string.master_summary),
            Prefs.MASTER_ENABLED,
        ))
        controls.addView(MaterialButton(this).apply {
            text = getString(R.string.pause_ten_minutes)
            setOnClickListener {
                Prefs.pauseForTenMinutes(this@MainActivity)
                Toast.makeText(this@MainActivity, R.string.paused_confirmation, Toast.LENGTH_SHORT).show()
            }
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })
        controlsCard.addView(controls)
        content.addView(controlsCard, margin(bottom = spacing))

        val setupCard = sectionCard()
        val setup = verticalContent()
        setup.addView(sectionTitle(getString(R.string.setup_title)))
        setup.addView(MaterialTextView(this).apply {
            text = getString(R.string.setup_body)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.text_secondary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })
        setup.addView(MaterialButton(this).apply {
            text = getString(R.string.accessibility_settings)
            setOnClickListener { openAccessibilitySettings(showMainOnReturn = false) }
            layoutParams = margin(top = spacing)
        })
        setupCard.addView(setup)
        content.addView(setupCard, margin(bottom = spacing))

        val optionsCard = sectionCard()
        val options = verticalContent()
        options.addView(sectionTitle(getString(R.string.focus_title)))
        options.addView(createSwitch(
            getString(R.string.block_shorts),
            getString(R.string.block_shorts_summary),
            Prefs.BLOCK_SHORTS_PLAYER,
        ))
        options.addView(divider())
        options.addView(createSwitch(
            getString(R.string.hide_shorts),
            getString(R.string.hide_shorts_summary),
            Prefs.HIDE_SHORTS_UI,
        ))
        options.addView(divider())
        options.addView(createSwitch(
            getString(R.string.hide_feed),
            getString(R.string.hide_feed_summary),
            Prefs.HIDE_FEED,
        ))
        options.addView(feedModePicker())
        options.addView(divider())
        options.addView(createSwitch(
            getString(R.string.hide_shorts_other_pages),
            getString(R.string.hide_shorts_other_pages_summary),
            Prefs.HIDE_SHORTS_OTHER_PAGES,
            defaultValue = false,
        ))
        optionsCard.addView(options)
        content.addView(optionsCard, margin(bottom = spacing))

        val scheduleCard = sectionCard()
        val schedule = verticalContent()
        schedule.addView(sectionTitle(getString(R.string.schedule_title)))
        schedule.addView(MaterialTextView(this).apply {
            text = getString(R.string.schedule_summary)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.text_secondary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })
        schedule.addView(createSwitch(
            getString(R.string.schedule_switch),
            getString(R.string.schedule_switch_summary),
            Prefs.SCHEDULE_ENABLED,
            defaultValue = false,
        ))
        val timeButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(scheduleTimeButton(getString(R.string.schedule_start), Prefs.SCHEDULE_START_MINUTE),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small) })
            addView(scheduleTimeButton(getString(R.string.schedule_end), Prefs.SCHEDULE_END_MINUTE),
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        schedule.addView(timeButtons, margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small)))
        scheduleCard.addView(schedule)
        content.addView(scheduleCard, margin(bottom = spacing))

        val infoCard = sectionCard()
        val info = verticalContent()
        info.addView(sectionTitle(getString(R.string.info_title)))
        info.addView(MaterialButton(this).apply {
            text = getString(R.string.privacy_button)
            setOnClickListener { showPrivacyScreen() }
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })
        info.addView(MaterialButton(this).apply {
            text = getString(R.string.disable_uninstall_button)
            setOnClickListener { showDisableHelpScreen() }
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })
        infoCard.addView(info)
        content.addView(infoCard)

        showScrollableScreen(getString(R.string.app_name), content)
    }

    private fun showPrivacyScreen(fromDisclosure: Boolean = false) {
        val content = verticalContent()
        content.addView(sectionTitle(getString(R.string.privacy_title)))
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.accessibility_disclosure)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(getColor(R.color.text_primary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_medium))
        })
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.privacy_details)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.text_secondary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_medium))
        })
        if (!fromDisclosure) {
            content.addView(MaterialButton(this).apply {
                text = getString(R.string.disable_uninstall_button)
                setOnClickListener { showDisableHelpScreen(returnToPrivacy = true) }
                layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_medium))
            })
        }
        showScrollableScreen(getString(R.string.privacy_title), content) {
            if (fromDisclosure) showDisclosureScreen() else showMainScreen()
        }
    }

    private fun showDisableHelpScreen(returnToPrivacy: Boolean = false) {
        val content = verticalContent()
        content.addView(sectionTitle(getString(R.string.disable_title)))
        content.addView(MaterialTextView(this).apply {
            text = getString(R.string.disable_instructions)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(getColor(R.color.text_primary))
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_medium))
        })
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.open_accessibility_settings)
            setOnClickListener { openAccessibilitySettings(showMainOnReturn = false) }
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_medium))
        })
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.open_app_info)
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_small))
        })
        showScrollableScreen(getString(R.string.disable_title), content) {
            if (returnToPrivacy) showPrivacyScreen() else showMainScreen()
        }
    }

    private fun openAccessibilitySettings(showMainOnReturn: Boolean) {
        showMainAfterSettings = showMainOnReturn
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun showScrollableScreen(title: String, content: View, onBack: (() -> Unit)? = null) {
        backAction = onBack
        val spacing = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        val toolbar = MaterialToolbar(this).apply {
            this.title = title
            setTitleTextColor(getColor(R.color.text_primary))
            setBackgroundColor(getColor(R.color.color_surface))
            elevation = resources.getDimension(R.dimen.toolbar_elevation)
            if (onBack != null) {
                setNavigationIcon(R.drawable.ic_arrow_back)
                setNavigationContentDescription(R.string.back)
                setNavigationOnClickListener {
                    val action = backAction
                    backAction = null
                    action?.invoke()
                }
            }
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.color_background))
            clipToPadding = false
            addView(toolbar, LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(R.dimen.toolbar_height)))
            addView(ScrollView(this@MainActivity).apply {
                clipToPadding = false
                setPadding(0, spacing, 0, spacing)
                addView(content, ViewGroup.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(page)
        ViewCompat.requestApplyInsets(page)
    }

    private fun createSwitch(
        title: String,
        summary: String,
        key: String,
        defaultValue: Boolean = true,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing_medium), 0,
            resources.getDimensionPixelSize(R.dimen.spacing_medium))
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(MaterialTextView(context).apply {
                text = title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                setTextColor(getColor(R.color.text_primary))
            })
            addView(MaterialTextView(context).apply {
                text = summary
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(getColor(R.color.text_secondary))
                layoutParams = margin(top = resources.getDimensionPixelSize(R.dimen.spacing_xsmall))
            })
        }
        addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        addView(MaterialSwitch(context).apply {
            isChecked = Prefs.get(this@MainActivity, key, defaultValue)
            contentDescription = title
            setOnCheckedChangeListener { _, checked -> Prefs.set(this@MainActivity, key, checked) }
        }, LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.spacing_small)
        })
    }

    private fun feedModePicker(): View = RadioGroup(this).apply {
        orientation = RadioGroup.VERTICAL
        val coverId = View.generateViewId()
        val subscriptionsId = View.generateViewId()
        addView(MaterialRadioButton(context).apply {
            id = coverId
            text = getString(R.string.cover_feed_option)
        })
        addView(MaterialRadioButton(context).apply {
            id = subscriptionsId
            text = getString(R.string.subscriptions_option)
        })
        val currentMode = Prefs.getString(this@MainActivity, Prefs.HOME_FEED_MODE, Prefs.HOME_FEED_COVER)
        check(if (currentMode == Prefs.HOME_FEED_SUBSCRIPTIONS) subscriptionsId else coverId)
        setOnCheckedChangeListener { _, checkedId ->
            Prefs.setString(
                this@MainActivity,
                Prefs.HOME_FEED_MODE,
                if (checkedId == subscriptionsId) Prefs.HOME_FEED_SUBSCRIPTIONS else Prefs.HOME_FEED_COVER,
            )
        }
    }

    private fun scheduleTimeButton(label: String, preferenceKey: String) = MaterialButton(this).apply {
        fun updateLabel() {
            val minute = Prefs.getInt(
                this@MainActivity,
                preferenceKey,
                if (preferenceKey == Prefs.SCHEDULE_START_MINUTE) 9 * 60 else 17 * 60,
            )
            text = getString(R.string.schedule_time_button, label, formatTime(minute))
        }
        updateLabel()
        setOnClickListener {
            val minute = Prefs.getInt(
                this@MainActivity,
                preferenceKey,
                if (preferenceKey == Prefs.SCHEDULE_START_MINUTE) 9 * 60 else 17 * 60,
            )
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(this@MainActivity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(minute / 60)
                .setMinute(minute % 60)
                .setTitleText(label)
                .build()
            picker.addOnPositiveButtonClickListener {
                Prefs.setInt(this@MainActivity, preferenceKey, picker.hour * 60 + picker.minute)
                updateLabel()
            }
            picker.show(supportFragmentManager, "schedule-$preferenceKey")
        }
    }

    private fun verticalContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(resources.getDimensionPixelSize(R.dimen.spacing_medium))
    }

    private fun sectionCard() = MaterialCardView(this).apply {
        radius = resources.getDimension(R.dimen.card_radius)
        cardElevation = 0f
        strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_width)
        setCardBackgroundColor(getColor(R.color.color_surface))
        strokeColor = getColor(R.color.color_outline)
    }

    private fun sectionTitle(textValue: String) = MaterialTextView(this).apply {
        text = textValue
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
        setTextColor(getColor(R.color.text_primary))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.color_outline))
        layoutParams = LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(R.dimen.divider_height))
    }

    private fun margin(
        start: Int = 0,
        top: Int = 0,
        bottom: Int = 0,
    ) = LinearLayout.LayoutParams(-1, -2).apply {
        marginStart = start
        topMargin = top
        bottomMargin = bottom
    }

    private fun formatTime(totalMinutes: Int): String = String.format(
        java.util.Locale.getDefault(), "%02d:%02d", totalMinutes / 60, totalMinutes % 60,
    )
}
