package com.wolnerman.calories

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elinkthings.ailinksecretlib.AiLinkBleCheckUtil
import java.text.SimpleDateFormat
import java.util.*

/** Accès à la bibliothèque officielle AILinkSecretAndroid (MIT, elinkthings) pour la poignée de main et le XOR A7. */
object AiLinkCrypto {
    @Volatile private var broken: String? = null
    val error: String? get() = broken

    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (t: Throwable) { broken = t.toString(); null }

    fun handshakeStart(): ByteArray? = safe {
        AiLinkBleCheckUtil.sendHandshakeFormat(AiLinkBleCheckUtil.getRandomKey(16), AiLinkProtocol.SET_HANDSHAKE.toByte())
    }

    /** Réponse à la demande de poignée de main envoyée par le module BLE (A6 type 0x23). */
    fun handshakeAnswer(fromBle: ByteArray): ByteArray? = safe {
        val p = AiLinkBleCheckUtil.returnHandshakeDataFormat(fromBle) ?: return@safe null
        val enc = AiLinkBleCheckUtil.bleEncrypt(p) ?: return@safe null
        AiLinkBleCheckUtil.sendHandshakeFormat(enc, AiLinkProtocol.GET_HANDSHAKE.toByte())
    }

    /** Chiffrement/déchiffrement (symétrique) d'un payload A7. mac = "AA:BB:CC:DD:EE:FF". */
    fun xorPayload(cid: Int, payload: ByteArray, mac: String): ByteArray? = safe {
        AiLinkBleCheckUtil.mcuEncrypt(byteArrayOf((cid shr 8).toByte(), cid.toByte()), payload, mac)
    }
}

data class FoundDevice(
    val address: String, val name: String?, val rssi: Int, val aiLink: Boolean,
    val adv: AiLinkAdv?, val services: List<String>, val rawHex: String,
)

enum class ScaleStatus { IDLE, SCANNING, CONNECTING, CONNECTED, ERROR }

/**
 * Gestion Bluetooth LE de la balance AiLink. Toutes les mises à jour d'état se font sur le thread principal.
 * Les autorisations sont demandées par l'interface avant d'appeler [startScan] / [connect].
 */
@SuppressLint("MissingPermission")
class ScaleManager(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    var status by mutableStateOf(ScaleStatus.IDLE); private set
    var message by mutableStateOf<String?>(null); private set
    var reading by mutableStateOf<ScaleReading?>(null); private set
    var deviceName by mutableStateOf<String?>(null); private set
    var deviceMac by mutableStateOf<String?>(null); private set
    var gattServices by mutableStateOf<List<String>>(emptyList()); private set
    val found = mutableStateListOf<FoundDevice>()
    val log = mutableStateListOf<String>()

    private var gatt: BluetoothGatt? = null
    private var cryptMac: String? = null
    private var cid: Int = AiLinkProtocol.CID_NUTRITION
    private var scanning = false
    private var autoConnect = true
    private val ops = ArrayDeque<() -> Boolean>()
    private var opBusy = false
    private var lastValue: Double? = null
    private var lastChange = 0L
    private val loggedAdv = HashSet<String>()

    private val tf = SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE)
    fun addLog(s: String) = main.post {
        log.add("${tf.format(Date())}  $s")
        while (log.size > 400) log.removeAt(0)
    }

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true
    fun hasBle(): Boolean = ctx.packageManager.hasSystemFeature("android.hardware.bluetooth_le") && adapter != null

    // ---------- Scan ----------
    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onResult(it) }
        override fun onScanFailed(errorCode: Int) {
            addLog("Échec du scan, code $errorCode")
            main.post { scanning = false; status = ScaleStatus.ERROR; message = "La recherche Bluetooth a échoué (code $errorCode). Réessaie." }
        }
    }

    private fun onResult(r: ScanResult) {
        val bytes = r.scanRecord?.bytes
        val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull()
        val ai = ScaleDecoder.looksLikeAiLink(name, bytes)
        val adv = ScaleDecoder.parseAdv(bytes)
        val services = ScaleDecoder.advServices16(bytes)
        val trimmed = bytes?.let { trimAdv(it) }
        val d = FoundDevice(r.device.address, name, r.rssi, ai, adv, services, ScaleDecoder.hex(trimmed))
        main.post {
            if (!scanning) return@post
            val idx = found.indexOfFirst { it.address == d.address }
            if (idx >= 0) found[idx] = d else if (ai || name != null) found.add(d)
            if (ai) {
                val key = d.address + d.rawHex
                if (loggedAdv.add(key) && loggedAdv.size < 200) {
                    addLog("Annonce ${d.name ?: "?"} ${d.address} rssi=${d.rssi} services=${services.joinToString(",")} " +
                        (adv?.let { "CID=%04X VID=%04X PID=%04X MAC=%s ".format(it.cid, it.vid, it.pid, it.mac) } ?: "") + "data=${d.rawHex}")
                    if ("F0A0" in services) addLog("→ Balance de type « diffusion » (F0A0) : les poids sont dans les annonces (format non décodé, envoie-moi ce journal).")
                }
                // Connexion automatique au premier appareil qui s'annonce clairement comme AiLink (FFE0 ou en-tête « In »)
                if (autoConnect && (adv != null || "FFE0" in services) && "F0A0" !in services) {
                    autoConnect = false
                    connect(d)
                }
            }
        }
    }

    private fun trimAdv(b: ByteArray): ByteArray {
        var i = 0
        while (i < b.size) { val l = b[i].toInt() and 0xFF; if (l == 0) break; i += 1 + l }
        return b.copyOf(minOf(i, b.size))
    }

    fun startScan(auto: Boolean = true) {
        val a = adapter
        if (a == null || !hasBle()) { fail("Ce téléphone n'a pas de Bluetooth basse consommation."); return }
        if (!a.isEnabled) { fail("Le Bluetooth est désactivé. Active-le puis réessaie."); return }
        val scanner = a.bluetoothLeScanner ?: run { fail("Bluetooth indisponible pour le moment. Réessaie."); return }
        disconnect(silent = true)
        found.clear(); loggedAdv.clear()
        autoConnect = auto
        scanning = true
        status = ScaleStatus.SCANNING
        message = "Recherche de la balance… Allume-la (appuie sur une touche) et garde-la près du téléphone."
        addLog("Début du scan (Android ${Build.VERSION.SDK_INT})")
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        } catch (t: Throwable) { scanning = false; fail("Impossible de lancer la recherche : ${t.message}"); return }
        main.postDelayed({
            if (scanning) {
                stopScan()
                if (status == ScaleStatus.SCANNING) {
                    status = ScaleStatus.IDLE
                    message = if (found.none { it.aiLink }) "Aucune balance trouvée. Allume-la, ferme l'appli AiLink (elle garde la balance connectée), puis réessaie." +
                        (if (Build.VERSION.SDK_INT < 31) " Vérifie aussi que la localisation est activée." else "")
                    else "Touche ta balance dans la liste pour la connecter."
                }
            }
        }, 15_000)
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCb) }
        addLog("Fin du scan (${found.size} appareil(s))")
    }

    // ---------- Connexion ----------
    fun connect(d: FoundDevice) {
        stopScan()
        disconnect(silent = true)
        val dev = runCatching { adapter?.getRemoteDevice(d.address) }.getOrNull() ?: run { fail("Appareil introuvable."); return }
        deviceName = d.name ?: "Balance"; deviceMac = d.address
        cryptMac = d.adv?.mac ?: d.address
        cid = d.adv?.cid?.takeIf { it != 0 } ?: AiLinkProtocol.CID_NUTRITION
        status = ScaleStatus.CONNECTING
        message = "Connexion à ${deviceName}…"
        reading = null; lastValue = null
        addLog("Connexion à ${d.name} ${d.address} (MAC chiffrement=$cryptMac, CID=%04X)".format(cid))
        gatt = if (Build.VERSION.SDK_INT >= 23) dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE) else dev.connectGatt(ctx, false, gattCb)
        main.postDelayed({ if (status == ScaleStatus.CONNECTING) { addLog("Délai de connexion dépassé"); disconnect(true); fail("La balance ne répond pas. Rallume-la et réessaie.") } }, 20_000)
    }

    fun disconnect(silent: Boolean = false) {
        main.removeCallbacksAndMessages(HS_TOKEN)
        ops.clear(); opBusy = false
        gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
        gatt = null
        if (!silent) { status = ScaleStatus.IDLE; message = "Balance déconnectée."; addLog("Déconnexion demandée") }
    }

    fun close() { stopScan(); disconnect(silent = true) }

    private fun fail(msg: String) { status = ScaleStatus.ERROR; message = msg; addLog("Erreur : $msg") }

    /** Remise à zéro / tare (A7 type 0x04). */
    fun tare() {
        val g = gatt ?: return
        val ch = g.getService(UUID.fromString(AiLinkProtocol.SERVICE))?.getCharacteristic(UUID.fromString(AiLinkProtocol.CHAR_WRITE)) ?: run {
            addLog("Tare impossible : caractéristique FFE1 absente"); return
        }
        val plain = byteArrayOf(AiLinkProtocol.TYPE_ZERO.toByte(), 0x01)
        val enc = AiLinkCrypto.xorPayload(cid, plain.copyOf(), cryptMac ?: "") ?: plain
        val frame = ScaleDecoder.buildA7(cid, enc)
        addLog("→ Tare FFE1 : ${ScaleDecoder.hex(frame)}")
        enqueue { write(g, ch, frame) }
    }

    // ---------- File d'opérations GATT ----------
    private fun enqueue(op: () -> Boolean) = main.post { ops.addLast(op); next() }
    private fun next() {
        if (opBusy) return
        while (ops.isNotEmpty()) {
            val op = ops.removeFirst()
            if (runCatching { op() }.getOrDefault(false)) { opBusy = true; return }
        }
    }
    private fun opDone() = main.post { opBusy = false; next() }

    @Suppress("DEPRECATION")
    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, v: ByteArray): Boolean {
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(ch, v, type) == BluetoothStatusCodes.SUCCESS
        else { ch.writeType = type; ch.value = v; g.writeCharacteristic(ch) }
    }

    @Suppress("DEPRECATION")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(ch, true)
        val d = ch.getDescriptor(UUID.fromString(AiLinkProtocol.CCCD)) ?: return false
        val v = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, v) == BluetoothStatusCodes.SUCCESS
        else { d.value = v; g.writeDescriptor(d) }
    }

    private val HS_TOKEN = Any()

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            addLog("État connexion : status=$st état=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && st == BluetoothGatt.GATT_SUCCESS) {
                main.post { message = "Connectée, lecture des services…" }
                main.postDelayed({ if (gatt === g) g.discoverServices() }, 300)
            } else {
                main.post {
                    if (gatt === g) {
                        runCatching { g.close() }; gatt = null; ops.clear(); opBusy = false
                        val was = status
                        status = ScaleStatus.ERROR
                        message = if (was == ScaleStatus.CONNECTED) "La balance s'est déconnectée (mise en veille ?). Touche « Connecter la balance » pour reprendre."
                        else "Connexion impossible (code $st). Ferme l'appli AiLink, rallume la balance et réessaie."
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            val list = g.services.map { s -> "${short(s.uuid)} [" + s.characteristics.joinToString(",") { short(it.uuid) + props(it.properties) } + "]" }
            addLog("Services : ${list.joinToString("  ")}")
            main.post { gattServices = list }
            val svc = g.getService(UUID.fromString(AiLinkProtocol.SERVICE))
            if (svc == null) {
                main.post { fail("Ce n'est pas une balance AiLink compatible (service FFE0 absent). Envoie-moi une capture du journal.") }
                return
            }
            val notify = svc.getCharacteristic(UUID.fromString(AiLinkProtocol.CHAR_NOTIFY))
            val wn = svc.getCharacteristic(UUID.fromString(AiLinkProtocol.CHAR_WRITE_NOTIFY))
            notify?.let { c -> enqueue { enableNotify(g, c) } }
            wn?.let { c -> enqueue { enableNotify(g, c) } }
            if (wn != null) {
                enqueue {
                    val hs = AiLinkCrypto.handshakeStart()
                    if (hs == null) { addLog("Bibliothèque de chiffrement indisponible : ${AiLinkCrypto.error}"); false }
                    else { addLog("→ Poignée de main FFE3 : ${ScaleDecoder.hex(hs)}"); write(g, wn, hs) }
                }
            }
            main.post {
                status = ScaleStatus.CONNECTED
                message = "Balance connectée. Pose l'aliment sur le plateau."
            }
            main.postAtTime({ if (reading == null && gatt === g) addLog("Aucun poids reçu après 6 s : appuie sur une touche de la balance ou pose un objet.") },
                HS_TOKEN, android.os.SystemClock.uptimeMillis() + 6000)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            addLog("Notifications ${short(d.characteristic.uuid)} activées (status=$st)"); opDone()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            addLog("Écriture ${short(c.uuid)} status=$st"); opDone()
        }

        @Deprecated("API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) onData(g, c, c.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onData(g, c, value)
        }
    }

    private fun onData(g: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray) {
        val id = short(c.uuid)
        val a6 = ScaleDecoder.a6Type(v)
        if (a6 != null) {
            addLog("← $id A6 type=%02X : %s".format(a6, ScaleDecoder.hex(v)))
            if (a6 == AiLinkProtocol.SET_HANDSHAKE) {
                val ans = AiLinkCrypto.handshakeAnswer(v)
                val wn = g.getService(UUID.fromString(AiLinkProtocol.SERVICE))?.getCharacteristic(UUID.fromString(AiLinkProtocol.CHAR_WRITE_NOTIFY))
                if (ans != null && wn != null) { addLog("→ Réponse poignée de main : ${ScaleDecoder.hex(ans)}"); enqueue { write(g, wn, ans) } }
                else addLog("Impossible de répondre à la poignée de main : ${AiLinkCrypto.error}")
            } else if (a6 == AiLinkProtocol.GET_HANDSHAKE) addLog("Poignée de main acceptée par la balance")
            return
        }
        val results = ScaleDecoder.decodeNotification(v) { cidIn, p -> AiLinkCrypto.xorPayload(cidIn, p, cryptMac ?: "") }
        if (results.isEmpty()) { addLog("← $id (non reconnu) : ${ScaleDecoder.hex(v)}"); return }
        for ((f, r) in results) {
            val clear = if (f.checksumOk) AiLinkCrypto.xorPayload(f.cid, f.payload.copyOf(), cryptMac ?: "") else null
            addLog("← $id A7 CID=%04X somme=%s brut=%s clair=%s → %s".format(f.cid, if (f.checksumOk) "ok" else "FAUX",
                ScaleDecoder.hex(v), ScaleDecoder.hex(clear), r?.let { "${it.value} ${it.unitLabel}${if (it.stable) " stable" else ""}" } ?: "pas un poids"))
            if (r != null) main.post { onReading(r) }
        }
    }

    private fun onReading(r: ScaleReading) {
        val now = System.currentTimeMillis()
        if (lastValue != r.value) { lastValue = r.value; lastChange = now }
        // Si la balance n'envoie pas l'indicateur « stable », on le déduit : même valeur depuis 1,5 s.
        val stable = r.stable || (now - lastChange >= 1500 && reading?.value == r.value)
        reading = if (stable != r.stable) r.copy(stable = stable) else r
        if (status != ScaleStatus.CONNECTED) status = ScaleStatus.CONNECTED
    }

    private fun short(u: UUID): String {
        val s = u.toString()
        return if (s.endsWith("-0000-1000-8000-00805f9b34fb") && s.startsWith("0000")) s.substring(4, 8).uppercase(Locale.ROOT) else s
    }
    private fun props(p: Int): String {
        val l = mutableListOf<String>()
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) l += "r"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) l += "w"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) l += "wn"
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) l += "n"
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) l += "i"
        return "(" + l.joinToString("") + ")"
    }

    companion object {
        @Volatile private var inst: ScaleManager? = null
        fun get(ctx: Context): ScaleManager = inst ?: synchronized(this) { inst ?: ScaleManager(ctx.applicationContext).also { inst = it } }
        fun shutdown() { inst?.close() }
    }
}
