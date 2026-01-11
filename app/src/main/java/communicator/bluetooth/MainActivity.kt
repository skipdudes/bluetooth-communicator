package communicator.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var targetDevice: BluetoothDevice? = null

    companion object {
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        //val MY_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    private val requestEnableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth turned on", Toast.LENGTH_SHORT).show()
            showPairedDevices()
        } else {
            Toast.makeText(this, "Cancelled turning on Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    private val bluetoothPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        if (isGranted) {
            enableBluetooth()
        } else {
            Toast.makeText(this, "No permission to use Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "This device has no Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        checkPermissionsAndEnableBluetooth()
    }

    private fun checkPermissionsAndEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissionRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            } else {
                enableBluetooth()
            }
        } else {
            enableBluetooth()
        }
    }

    private fun enableBluetooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBluetooth.launch(enableBtIntent)
        } else {
            showPairedDevices()
        }
    }

    private fun showPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }

        val pairedDevices = bluetoothAdapter?.bondedDevices
        val pairedDeviceList = ArrayList<String>()

        if (!pairedDevices.isNullOrEmpty()) {
            for (device in pairedDevices) {
                pairedDeviceList.add("${device.name} [${device.address}]")
            }
        } else {
            pairedDeviceList.add("No paired devices")
        }

        val pairedDevicesSpinner: Spinner = findViewById(R.id.device_spinner)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pairedDeviceList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        pairedDevicesSpinner.adapter = adapter

        pairedDevicesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (pairedDevices.isNullOrEmpty()) return

                val deviceDesc = parent.getItemAtPosition(position).toString()
                try {
                    val mac = deviceDesc.substring(deviceDesc.indexOf('[') + 1, deviceDesc.indexOf(']'))

                    for (device: BluetoothDevice in pairedDevices) {
                        if (device.address == mac) {
                            targetDevice = device
                            break
                        }
                    }
                } catch (e: Exception) {

                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

        }
    }
}