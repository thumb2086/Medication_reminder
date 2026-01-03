package com.example.medicationreminderapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.medicationreminderapp.databinding.FragmentFirmwareUpdateBinding

class FirmwareUpdateFragment : Fragment() {

    private var _binding: FragmentFirmwareUpdateBinding? = null
    private val binding get() = _binding!!

    private var selectedFirmwareUri: Uri? = null

    private val selectFirmwareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFirmwareUri = uri
                binding.firmwareFileName.text = uri.lastPathSegment
                binding.startUpdateButton.isEnabled = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirmwareUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.selectFirmwareButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*" // Allow all file types for now, will filter for .bin
            }
            selectFirmwareLauncher.launch(intent)
        }

        binding.startUpdateButton.setOnClickListener {
            selectedFirmwareUri?.let {
                // TODO: Implement firmware update logic
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
