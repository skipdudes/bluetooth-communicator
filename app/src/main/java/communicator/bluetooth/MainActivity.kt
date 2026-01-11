package communicator.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var targetDevice: BluetoothDevice? = null

    private var connectedThread: ConnectedThread? = null
    private lateinit var chatAdapter: ArrayAdapter<String>
    private val chatMessages = ArrayList<String>()

    private lateinit var statusText: TextView
    private lateinit var messageInput: EditText
    private lateinit var checkBoxSound: CheckBox

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

        statusText = findViewById(R.id.status_text)
        messageInput = findViewById(R.id.message_input)
        checkBoxSound = findViewById(R.id.checkbox_sound)
        val chatListView: ListView = findViewById(R.id.chat_list_view)

        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatMessages)
        chatListView.adapter = chatAdapter

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        findViewById<Button>(R.id.button_server).setOnClickListener { startServer() }
        findViewById<Button>(R.id.button_client).setOnClickListener { startClient() }

        findViewById<Button>(R.id.button_send).setOnClickListener {
            val msg = messageInput.text.toString()
            if (msg.isNotEmpty() && connectedThread != null) {
                connectedThread?.write(msg.toByteArray())
                addMessageToChat("Me: $msg")
                messageInput.text.clear()
            } else {
                Toast.makeText(this, "Not connected or empty message", Toast.LENGTH_SHORT).show()
            }
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "This device has no Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        checkPermissionsAndEnableBluetooth()
    }

    private fun addMessageToChat(message: String) {
        runOnUiThread {
            chatMessages.add(message)
            chatAdapter.notifyDataSetChanged()
        }
    }

    private fun updateStatus(status: String) {
        runOnUiThread { statusText.text = "Status: $status" }
    }

    private fun playNotification() {
        if (!checkBoxSound.isChecked) return

        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        } catch (e: Exception) { e.printStackTrace() }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
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

    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
        updateStatus("CONNECTED")
    }

    private fun startServer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        updateStatus("Waiting for connection...")
        AcceptThread().start()
    }

    private fun startClient() {
        if (targetDevice == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        updateStatus("Connecting with ${targetDevice?.name}...")
        ConnectThread(targetDevice!!).start()
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
                } catch (e: IOException) { break }
                if (socket != null) {
                    manageMyConnectedSocket(socket)
                    serverSocket?.close()
                    break
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy { device.createRfcommSocketToServiceRecord(MY_UUID) }
        override fun run() {
            bluetoothAdapter?.cancelDiscovery()
            try {
                socket?.connect()
                socket?.let { manageMyConnectedSocket(it) }
            } catch (e: IOException) {
                updateStatus("Connection error")
                try { socket?.close() } catch (e2: IOException) {}
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        private val buffer: ByteArray = ByteArray(1024)

        override fun run() {
            var bytes: Int

            while (true) {
                try {
                    bytes = inputStream.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)

                    addMessageToChat("They: $incomingMessage")

                    runOnUiThread { playNotification() }

                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    updateStatus("Disconnected")
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                outputStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
                updateStatus("Sending error")
            }
        }

        fun cancel() {
            try { socket.close() } catch (e: IOException) {}
        }
    }
}