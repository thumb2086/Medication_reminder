package com.example.medicationreminderapp

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        // Set background color to match the theme
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        view.setBackgroundColor(typedValue.data)

        bluetoothLeManager = (activity as MainActivity).bluetoothLeManager

        loadSsidHistory()

        binding.sendWifiCredentialsButton.setOnClickListener {
            val ssid = binding.ssidInput.text.toString()
            val password = binding.passwordInput.text.toString()

            if (ssid.isNotBlank() && password.isNotBlank()) {
                if (bluetoothLeManager.isConnected()) {
                    bluetoothLeManager.sendWifiCredentials(ssid, password)
                    saveSsid(ssid)
                    Toast.makeText(requireContext(), "Wi-Fi credentials sent", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Bluetooth not connected", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter SSID and password", Toast.LENGTH_SHORT).show()
            }
        }
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
        with(sharedPref.edit()) {
            putStringSet("ssid_set", ssidHistory)
            apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
