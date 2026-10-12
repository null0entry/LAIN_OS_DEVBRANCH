package dev.lain.os

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.lain.os.bootstrap.AvailabilityState
import dev.lain.os.bootstrap.BootstrapState
import dev.lain.os.bootstrap.BootstrapViewModel
import dev.lain.os.databinding.ActivityBootstrapBinding

class BootstrapActivity : AppCompatActivity() {
    private lateinit var ui: ActivityBootstrapBinding
    private val model: BootstrapViewModel by viewModels()
    private var navigationStarted = false
    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // A result is a signal to rescan, never a persisted grant assertion.
            model.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (model.isComplete()) {
            openWorkbench()
            return
        }
        ui = ActivityBootstrapBinding.inflate(layoutInflater)
        setContentView(ui.root)
        ViewCompat.setOnApplyWindowInsetsListener(ui.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ui.bootstrapDemo.setOnClickListener { finishSetup(useDemo = true) }
        ui.bootstrapFinish.setOnClickListener { finishSetup(useDemo = false) }
        ui.bootstrapConfigurePlanner.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CONFIGURE_PLANNER, true))
        }
        ui.bootstrapMicrophone.setOnClickListener {
            model.microphonePermission()?.let(microphonePermission::launch)
        }
        model.state.observe(this, ::render)
    }

    override fun onResume() {
        super.onResume()
        if (::ui.isInitialized) model.refresh()
    }

    private fun render(state: BootstrapState) {
        if (state.complete) {
            openWorkbench()
            return
        }
        ui.bootstrapCapabilities.text = state.capabilities.joinToString("\n") {
            "${it.capability}: ${it.state.name}"
        }
        ui.bootstrapDemo.isEnabled = !state.finishing
        ui.bootstrapFinish.isEnabled = !state.finishing
        ui.bootstrapConfigurePlanner.isEnabled = !state.finishing
        ui.bootstrapMicrophone.isEnabled = !state.finishing && state.capabilities.any {
            it.capability == "voice.input" && it.state == AvailabilityState.NEEDS_PERMISSION
        }
        ui.bootstrapPlanner.text = when {
            state.plannerLoading -> getString(R.string.bootstrap_planner_loading)
            state.plannerMode == "demo" -> getString(R.string.bootstrap_planner_demo)
            state.plannerMode == null -> getString(R.string.bootstrap_planner_unavailable)
            else -> getString(R.string.bootstrap_planner_configured, state.plannerName)
        }
        ui.bootstrapError.visibility = if (state.error) View.VISIBLE else View.GONE
        ui.bootstrapProgress.visibility = if (state.finishing) View.VISIBLE else View.GONE
    }

    private fun finishSetup(useDemo: Boolean) {
        model.finish(useDemo)
    }

    private fun openWorkbench() {
        if (navigationStarted) return
        navigationStarted = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
