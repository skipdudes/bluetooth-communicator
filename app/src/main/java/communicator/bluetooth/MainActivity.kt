package communicator.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var targetDevice: BluetoothDevice? = null
    private var activeSocket: BluetoothSocket? = null

    companion object {
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        //val MY_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        const val TAG = "BluetoothApp"
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

        findViewById<Button>(R.id.button_server).setOnClickListener { startServer() }
        findViewById<Button>(R.id.button_client).setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@setOnClickListener
            }

            startClient()
        }

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

    @SuppressLint("MissingPermission")
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

    private fun startServer() {
        if (hasPermissions()) {
            Toast.makeText(this, "Starting server...", Toast.LENGTH_SHORT).show()
            AcceptThread().start()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startClient() {
        if (targetDevice == null) {
            Toast.makeText(this, "Select device from the list!", Toast.LENGTH_SHORT).show()
            return
        }
        if (hasPermissions()) {
            Toast.makeText(this, "Connecting with ${targetDevice?.name}...", Toast.LENGTH_SHORT).show()
            ConnectThread(targetDevice!!).start()
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        activeSocket = socket
        runOnUiThread {
            Toast.makeText(this, "CONNECTED!", Toast.LENGTH_LONG).show()
            // todo: run thread
        }
    }

    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? by lazy {
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord("CommunicatorBT", MY_UUID)
        }

        override fun run() {
            var socket: BluetoothSocket? = null

            while (true) {
                try {
                    socket = serverSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    break
                }

                if (socket != null) {
                    manageMyConnectedSocket(socket)
                    serverSocket?.close()
                    break
                }
            }
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            try {
                socket?.connect()

                socket?.let { manageMyConnectedSocket(it) }

            } catch (e: IOException) {
                Log.e(TAG, "Could not connect", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection error", Toast.LENGTH_SHORT).show()
                }
                try {
                    socket?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not close the client socket", e)
                }
                return
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }
}