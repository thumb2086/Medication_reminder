package com.example.medicationreminderapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.medicationreminderapp.databinding.FragmentWifiConfigBinding

class WiFiConfigFragment : Fragment() {

    private var _binding: FragmentWifiConfigBinding? = null
    private val binding get() = _binding!!

    private lateinit var bluetoothLeManager: BluetoothLeManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothLeManager = (activity as MainActivity).bluetoothLeManager

        loadSsidHistory()
        setupValidation()

        binding.sendWifiCredentialsButton.setOnClickListener {
            val ssid = binding.ssidInput.text.toString()
            val password = binding.passwordInput.text.toString()

            if (validateInput(ssid, password)) {
                if (bluetoothLeManager.isConnected()) {
                    showConnectingUI()
                    bluetoothLeManager.sendWifiCredentials(ssid, password)
                    saveSsid(ssid)
                } else {
                    Toast.makeText(requireContext(), R.string.ble_status_disconnected, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun onWifiStatusUpdate(status: Int) {
        when (status) {
            1 -> showSuccessUI()
            else -> showFailureUI()
        }
    }

    private fun showConnectingUI() {
        binding.statusLayout.visibility = View.VISIBLE
        binding.statusProgressBar.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.wifi_status_connecting)
        binding.sendWifiCredentialsButton.isEnabled = false
    }

    private fun showSuccessUI() {
        binding.statusProgressBar.visibility = View.GONE
        binding.statusText.text = getString(R.string.wifi_status_connected)
        binding.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
        binding.sendWifiCredentialsButton.isEnabled = true
    }

    private fun showFailureUI() {
        binding.statusProgressBar.visibility = View.GONE
        binding.statusText.text = getString(R.string.wifi_status_failed)
        binding.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_red))
        binding.sendWifiCredentialsButton.isEnabled = true
    }

    private fun setupValidation() {
        binding.ssidInput.doAfterTextChanged {
            binding.ssidLayout.error = null
        }
        binding.passwordInput.doAfterTextChanged {
            binding.passwordLayout.error = null
        }
    }

    private fun validateInput(ssid: String, password: String): Boolean {
        var isValid = true
        if (ssid.isBlank()) {
            binding.ssidLayout.error = getString(R.string.fill_all_fields)
            isValid = false
        }
        if (password.isBlank()) {
            binding.passwordLayout.error = getString(R.string.fill_all_fields)
            isValid = false
        }
        return isValid
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.wifi_settings)
    }

    override fun onPause() {
        super.onPause()
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
    }

    private fun loadSsidHistory() {
        val sharedPref = activity?.getSharedPreferences("wifi_history", Context.MODE_PRIVATE) ?: return
        val ssidHistory = sharedPref.getStringSet("ssid_set", emptySet()) ?: emptySet()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ssidHistory.toList())
        binding.ssidInput.setAdapter(adapter)
    }

    private fun saveSsid(ssid: String) {
        val sharedPref = activity?.getSharedPreferences("wifi_history", Context.MODE_PRIVATE) ?: return
        val ssidHistory = sharedPref.getStringSet("ssid_set", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        ssidHistory.add(ssid)
        sharedPref.edit {
            putStringSet("ssid_set", ssidHistory)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
