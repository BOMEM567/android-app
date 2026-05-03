package cn.microfluidics.pumpcontroller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final UUID UART_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID UART_RX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID UART_TX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int REQ_PERMISSIONS = 42;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothLeScanner scanner;
    private boolean scanning;

    private TextView stateBadge;
    private TextView deviceText;
    private TextView stateText;
    private TextView progressText;
    private TextView batteryText;
    private TextView modeText;
    private TextView logText;
    private ProgressBar progressBar;
    private EditText volumeInput;
    private EditText minutesInput;
    private EditText serverUrlInput;
    private EditText deviceIdInput;
    private EditText tokenInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNeededPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    private void buildUi() {
        int primary = Color.rgb(20, 121, 102);
        int accent = Color.rgb(36, 106, 147);
        int warning = Color.rgb(161, 106, 21);
        int danger = Color.rgb(185, 61, 52);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(232, 241, 238));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(12));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView eyebrow = label("微流控灌流系统");
        titleBox.addView(eyebrow);
        TextView title = text("培养液控制", 24, Color.rgb(16, 35, 31), true);
        titleBox.addView(title);

        stateBadge = pill("待机", primary);
        header.addView(stateBadge);
        root.addView(header);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), 0, dp(14), dp(14));
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout panel = panel();
        content.addView(panel);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(12));
        LinearLayout deviceBox = new LinearLayout(this);
        deviceBox.setOrientation(LinearLayout.VERTICAL);
        deviceBox.addView(label("当前设备"));
        deviceText = text("pump-001", 18, Color.rgb(16, 35, 31), true);
        deviceBox.addView(deviceText);
        row.addView(deviceBox, new LinearLayout.LayoutParams(0, -2, 1));
        Button connect = button("连接蓝牙", accent);
        connect.setOnClickListener((view) -> connectBle());
        row.addView(connect);
        panel.addView(row);

        progressText = text("0%", 42, Color.rgb(16, 35, 31), true);
        progressText.setGravity(Gravity.CENTER);
        panel.addView(progressText, new LinearLayout.LayoutParams(-1, -2));
        TextView progressLabel = label("运行进度");
        progressLabel.setGravity(Gravity.CENTER);
        panel.addView(progressLabel);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(12));
        progressParams.setMargins(0, dp(14), 0, dp(14));
        panel.addView(progressBar, progressParams);

        GridLayout stats = new GridLayout(this);
        stats.setColumnCount(3);
        stateText = text("idle", 17, Color.rgb(16, 35, 31), true);
        batteryText = text("-- V", 17, Color.rgb(16, 35, 31), true);
        modeText = text("蓝牙", 17, Color.rgb(16, 35, 31), true);
        stats.addView(metric("状态", stateText));
        stats.addView(metric("电池", batteryText));
        stats.addView(metric("模式", modeText));
        panel.addView(stats);

        LinearLayout taskPanel = panel();
        content.addView(taskPanel);
        taskPanel.addView(heading("本次任务"));
        GridLayout inputs = new GridLayout(this);
        inputs.setColumnCount(2);
        volumeInput = input("20", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        minutesInput = input("60", InputType.TYPE_CLASS_NUMBER);
        inputs.addView(inputGroup("目标体积 mL", volumeInput));
        inputs.addView(inputGroup("运行时长 min", minutesInput));
        taskPanel.addView(inputs);

        GridLayout localButtons = new GridLayout(this);
        localButtons.setColumnCount(2);
        localButtons.addView(action("开始灌流", primary, () -> sendBle("START")));
        localButtons.addView(action("停止", danger, () -> sendBle("STOP")));
        localButtons.addView(action("暂停", warning, () -> sendBle("PAUSE")));
        localButtons.addView(action("继续", accent, () -> sendBle("RESUME")));
        taskPanel.addView(localButtons);

        LinearLayout remotePanel = panel();
        content.addView(remotePanel);
        remotePanel.addView(heading("远程监视"));
        serverUrlInput = input("http://127.0.0.1:8080", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        deviceIdInput = input("pump-001", InputType.TYPE_CLASS_TEXT);
        tokenInput = input("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        remotePanel.addView(inputGroup("服务端地址", serverUrlInput));
        remotePanel.addView(inputGroup("设备编号", deviceIdInput));
        remotePanel.addView(inputGroup("控制口令", tokenInput));
        GridLayout remoteButtons = new GridLayout(this);
        remoteButtons.setColumnCount(2);
        remoteButtons.addView(action("刷新状态", accent, this::refreshRemote));
        remoteButtons.addView(action("远程开始", primary, () -> remoteCommand("START")));
        remoteButtons.addView(action("远程暂停", warning, () -> remoteCommand("PAUSE")));
        remoteButtons.addView(action("远程继续", accent, () -> remoteCommand("RESUME")));
        remoteButtons.addView(action("远程停止", danger, () -> remoteCommand("STOP")));
        remotePanel.addView(remoteButtons);

        LinearLayout logPanel = panel();
        content.addView(logPanel);
        logPanel.addView(heading("运行日志"));
        logText = text("", 12, Color.rgb(220, 235, 231), false);
        logText.setPadding(dp(12), dp(10), dp(12), dp(10));
        logText.setBackgroundColor(Color.rgb(15, 28, 26));
        logPanel.addView(logText, new LinearLayout.LayoutParams(-1, dp(170)));

        setContentView(root);
    }

    private void connectBle() {
        if (!hasBluetoothPermissions()) {
            requestNeededPermissions();
            appendLog("请先授予蓝牙权限");
            return;
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            appendLog("蓝牙未开启");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            appendLog("无法启动 BLE 扫描");
            return;
        }
        appendLog("正在扫描灌流控制器...");
        scanning = true;
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UART_SERVICE)).build());
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(filters, settings, scanCallback);
        mainHandler.postDelayed(this::stopScan, 12000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            stopScan();
            BluetoothDevice device = result.getDevice();
            appendLog("发现设备，正在连接...");
            bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        }

        @Override
        public void onScanFailed(int errorCode) {
            appendLog("BLE 扫描失败: " + errorCode);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendLog("BLE 已连接，正在发现服务");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                rxCharacteristic = null;
                appendLog("BLE 已断开");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(UART_SERVICE);
            if (service == null) {
                appendLog("未找到 UART 服务");
                return;
            }
            rxCharacteristic = service.getCharacteristic(UART_RX);
            BluetoothGattCharacteristic tx = service.getCharacteristic(UART_TX);
            if (rxCharacteristic == null || tx == null) {
                appendLog("UART 特征不完整");
                return;
            }
            gatt.setCharacteristicNotification(tx, true);
            BluetoothGattDescriptor descriptor = tx.getDescriptor(CCCD);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
            setMode("蓝牙");
            appendLog("BLE 通道就绪");
            sendBle("STATUS");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleBleMessage(characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            handleBleMessage(value);
        }
    };

    private void sendBle(String command) {
        if (rxCharacteristic == null || bluetoothGatt == null) {
            appendLog("BLE 未连接");
            return;
        }
        String payloadText = commandText(command);
        byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 33) {
            bluetoothGatt.writeCharacteristic(rxCharacteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            rxCharacteristic.setValue(payload);
            bluetoothGatt.writeCharacteristic(rxCharacteristic);
        }
        appendLog("BLE -> " + payloadText);
    }

    private void handleBleMessage(byte[] value) {
        String text = new String(value, StandardCharsets.UTF_8);
        appendLog("BLE <- " + text);
        try {
            updateStatus(new JSONObject(text));
        } catch (Exception ignored) {
        }
    }

    private void refreshRemote() {
        runHttp(() -> {
            String base = trimSlash(serverUrlInput.getText().toString());
            String id = deviceIdInput.getText().toString().trim();
            HttpURLConnection conn = openConnection(base + "/api/status?device_id=" + id, "GET");
            String body = readResponse(conn);
            appendLog("HTTP " + conn.getResponseCode() + " " + body);
            updateStatus(new JSONObject(body));
            setMode("远程");
        });
    }

    private void remoteCommand(String command) {
        runHttp(() -> {
            String base = trimSlash(serverUrlInput.getText().toString());
            JSONObject params = new JSONObject()
                    .put("volume_ml", number(volumeInput, 20))
                    .put("duration_min", (int) number(minutesInput, 60));
            JSONObject body = new JSONObject()
                    .put("device_id", deviceIdInput.getText().toString().trim())
                    .put("command", command)
                    .put("params", params);
            HttpURLConnection conn = openConnection(base + "/api/command", "POST");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            appendLog("HTTP " + conn.getResponseCode() + " " + readResponse(conn));
            setMode("远程");
        });
    }

    private HttpURLConnection openConnection(String urlText, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        String token = tokenInput.getText().toString().trim();
        if (!token.isEmpty()) {
            conn.setRequestProperty("X-Pump-Token", token);
        }
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private void runHttp(HttpTask task) {
        new Thread(() -> {
            try {
                task.run();
            } catch (Exception error) {
                appendLog(error.getMessage());
            }
        }).start();
    }

    private void updateStatus(JSONObject data) {
        runOnUiThread(() -> {
            String deviceId = data.optString("device_id", deviceText.getText().toString());
            String state = data.optString("state", stateText.getText().toString());
            int percent = Math.max(0, Math.min(100, (int) Math.round(data.optDouble("progress", 0) * 100)));
            double battery = data.optDouble("battery_v", 0);
            deviceText.setText(deviceId);
            stateText.setText(state);
            stateBadge.setText(stateLabel(state));
            progressText.setText(String.format(Locale.US, "%d%%", percent));
            progressBar.setProgress(percent);
            batteryText.setText(battery > 0 ? String.format(Locale.US, "%.2f V", battery) : "-- V");
        });
    }

    private String commandText(String command) {
        if ("START".equals(command)) {
            return String.format(Locale.US, "START %.1f %d", number(volumeInput, 20), (int) number(minutesInput, 60));
        }
        return command;
    }

    private void stopScan() {
        if (scanning && scanner != null && hasBluetoothPermissions()) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        requestPermissions(permissions.toArray(new String[0]), REQ_PERMISSIONS);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackgroundColor(Color.rgb(251, 253, 251));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        panel.setLayoutParams(params);
        return panel;
    }

    private TextView heading(String value) {
        return text(value, 17, Color.rgb(16, 35, 31), true);
    }

    private TextView label(String value) {
        return text(value, 12, Color.rgb(104, 121, 115), true);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView pill(String value, int color) {
        TextView view = text(value, 13, color, true);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        return view;
    }

    private EditText input(String value, int type) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setInputType(type);
        input.setPadding(dp(10), 0, dp(10), 0);
        return input;
    }

    private LinearLayout inputGroup(String label, EditText input) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, dp(8), dp(8), dp(8));
        group.addView(label(label));
        group.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
        return group;
    }

    private LinearLayout metric(String label, TextView value) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(10), dp(10), dp(10), dp(10));
        group.addView(label(label));
        group.addView(value);
        return group;
    }

    private Button action(String label, int color, Runnable handler) {
        Button button = button(label, color);
        button.setOnClickListener((view) -> handler.run());
        return button;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundColor(color);
        button.setMinHeight(dp(48));
        return button;
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            if (logText == null) return;
            String line = String.format(Locale.CHINA, "[%tT] %s\n", System.currentTimeMillis(), message);
            logText.setText(line + logText.getText());
        });
    }

    private void setMode(String value) {
        runOnUiThread(() -> modeText.setText(value));
    }

    private String stateLabel(String state) {
        if ("running".equals(state)) return "运行中";
        if ("paused".equals(state)) return "已暂停";
        if ("finished".equals(state)) return "已完成";
        if ("error".equals(state)) return "异常";
        return "待机";
    }

    private double number(EditText input, double fallback) {
        try {
            return Double.parseDouble(input.getText().toString().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String trimSlash(String value) {
        return value.trim().replaceAll("/+$", "");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface HttpTask {
        void run() throws Exception;
    }
}
