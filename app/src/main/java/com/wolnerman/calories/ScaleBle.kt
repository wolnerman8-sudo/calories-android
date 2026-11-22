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
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elinkthings.ailinksecretlib.AiLinkBleCheckUtil
import java.text.SimpleDateFormat
import java.util.*

/** Bibliothèque officielle AILinkSecretAndroid (elinkthings) : poignée de main et XOR A7. */
object AiLinkCrypto : AiLinkCryptoApi {
    @Volatile private var broken: String? = null
    val error: String? get() = broken
    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (t: Throwable) { broken = t.toString(); null }

    override fun handshakeStart(): ByteArray? = safe {
        AiLinkBleCheckUtil.sendHandshakeFormat(AiLinkBleCheckUtil.getRandomKey(16), AiLinkProtocol.SET_HANDSHAKE.toByte())
    }
    override fun handshakeAnswer(fromBle: ByteArray): ByteArray? = safe {
        val p = AiLinkBleCheckUtil.returnHandshakeDataFormat(fromBle) ?: return@safe null
        val enc = AiLinkBleCheckUtil.bleEncrypt(p) ?: return@safe null
        AiLinkBleCheckUtil.sendHandshakeFormat(enc, AiLinkProtocol.GET_HANDSHAKE.toByte())
    }
    override fun xorPayload(cid: Int, payload: ByteArray, mac: String): ByteArray? = safe {
        AiLinkBleCheckUtil.mcuEncrypt(byteArrayOf((cid shr 8).toByte(), cid.toByte()), payload, mac)
    }
}

data class FoundDevice(
    val address: String, val name: String?, val rssi: Int,
    val driver: ScaleDriverInfo?, val adv: AdvData, val rawHex: String,
) {
    val known: Boolean get() = driver != null
    val typeLabel: String get() = driver?.label ?: "Type inconnu"
}

enum class ScaleStatus { IDLE, SCANNING, CONNECTING, CONNECTED, ERROR }

/**
 * Gestion Bluetooth LE générique : scan, connexion, abonnements, file GATT. Le décodage est délégué au pilote.
 * Toutes les mises à jour d'état se font sur le thread principal.
 */
@SuppressLint("MissingPermission")
class ScaleManager(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    var status by mutableStateOf(ScaleStatus.IDLE); private set
    var message by mutableStateOf<String?>(null); private set
    var reading by mutableStateOf<WeightReading?>(null); private set
    var deviceName by mutableStateOf<String?>(null); private set
    var deviceMac by mutableStateOf<String?>(null); private set
    var driverLabel by mutableStateOf<String?>(null); private set
    var canTare by mutableStateOf(false); private set
    var gattServices by mutableStateOf<List<String>>(emptyList()); private set
    val found = mutableStateListOf<FoundDevice>()
    val log = mutableStateListOf<String>()

    private var gatt: BluetoothGatt? = null
    private var driver: ScaleDriver? = null
    private var driverInfo: ScaleDriverInfo? = null
    private var target: FoundDevice? = null
    private var scanning = false
    private var autoConnect = true
    private val ops = ArrayDeque<() -> Boolean>()
    private var opBusy = false
    private var lastValue: Double? = null
    private var lastChange = 0L
    private val loggedAdv = HashSet<String>()
    private val TOKEN = Any()
    private var appTared = false
    private var rejected = 0

    private val tf = SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE)
    fun addLog(s: String) = main.post {
        log.add("${tf.format(Date())}  $s")
        while (log.size > 500) log.removeAt(0)
    }

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true
    fun hasBle(): Boolean = ctx.packageManager.hasSystemFeature("android.hardware.bluetooth_le") && adapter != null

    private val session = object : DriverSession {
        override fun write(charUuid: String, data: ByteArray) {
            val g = gatt ?: return
            val ch = findChar(g, charUuid)
            if (ch == null) { addLog("Écriture impossible : caractéristique $charUuid absente"); return }
            addLog("→ ${ScaleDrivers.short(ch.uuid.toString())} : ${ScaleDecoder.hex(data)}")
            enqueue { write(g, ch, data) }
        }
        override fun log(s: String) { addLog(s) }
        override val cryptMac: String get() = target?.adv?.aiLink?.mac ?: target?.address ?: ""
        override val advMacLE: ByteArray? get() = target?.adv?.aiLink?.macLittleEndian
        override val address: String get() = target?.address ?: ""
    }

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
        val adv = AdvData(name, ScaleDecoder.advServices16(bytes), ScaleDecoder.parseAdv(bytes))
        val drv = ScaleDrivers.detect(adv)
        val d = FoundDevice(r.device.address, name, r.rssi, drv, adv, ScaleDecoder.hex(bytes?.let { trimAdv(it) }))
        main.post {
            if (!scanning) return@post
            val idx = found.indexOfFirst { it.address == d.address }
            if (idx >= 0) found[idx] = d else if (found.size < 80) found.add(d)
            val key = d.address + d.rawHex
            if ((drv != null || name != null) && loggedAdv.add(key) && loggedAdv.size < 300) {
                addLog("Annonce ${d.name ?: "?"} ${d.address} rssi=${d.rssi} type=${drv?.id ?: "inconnu"} services=${adv.services.joinToString(",")} " +
                    (adv.aiLink?.let { "CID=%04X VID=%04X PID=%04X MAC=%s ".format(it.cid, it.vid, it.pid, it.mac) } ?: "") + "data=${d.rawHex}")
                if (drv == ScaleDrivers.AILINK && adv.has("F0A0")) addLog("→ Balance AiLink « diffusion » (F0A0) : poids dans les annonces, format non décodé.")
            }
            // Connexion automatique uniquement pour une balance de cuisine reconnue avec certitude
            if (autoConnect && drv != null && drv.kitchen && drv.detect(adv) >= 85 && !adv.has("F0A0")) {
                autoConnect = false
                connect(d)
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
        message = "Recherche des balances… Allume ta balance (appuie sur une touche) et garde-la près du téléphone."
        addLog("Début du scan (Android ${Build.VERSION.SDK_INT}, Calories 1.5)")
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        } catch (t: Throwable) { scanning = false; fail("Impossible de lancer la recherche : ${t.message}"); return }
        main.postDelayed({
            if (scanning) {
                stopScan()
                if (status == ScaleStatus.SCANNING) {
                    status = ScaleStatus.IDLE
                    message = if (found.none { it.known }) "Aucune balance reconnue. Tu peux toucher un appareil de la liste pour l'essayer, ou rallumer la balance et relancer. Ferme aussi l'appli du fabricant (elle garde la balance connectée)." +
                        (if (Build.VERSION.SDK_INT < 31) " Vérifie que la localisation est activée." else "")
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
        target = d
        deviceName = d.name ?: "Appareil"; deviceMac = d.address
        driverInfo = d.driver; driverLabel = d.driver?.label ?: "Détection après connexion…"
        status = ScaleStatus.CONNECTING
        message = "Connexion à ${deviceName}…"
        reading = null; lastValue = null; canTare = false; appTared = false; rejected = 0
        addLog("Connexion à ${d.name} ${d.address} (pilote annonce : ${d.driver?.id ?: "aucun"})")
        gatt = dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        main.postDelayed({ if (status == ScaleStatus.CONNECTING) { addLog("Délai de connexion dépassé"); disconnect(true); fail("L'appareil ne répond pas. Rallume-le et réessaie.") } }, 20_000)
    }

    fun disconnect(silent: Boolean = false) {
        main.removeCallbacksAndMessages(TOKEN)
        ops.clear(); opBusy = false
        gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
        gatt = null; driver = null
        if (!silent) { status = ScaleStatus.IDLE; message = "Balance déconnectée."; addLog("Déconnexion demandée") }
    }

    fun close() { stopScan(); disconnect(silent = true) }

    private fun fail(msg: String) { status = ScaleStatus.ERROR; message = msg; addLog("Erreur : $msg") }

    fun tare() {
        val d = driver ?: return
        if (!d.tare(session)) addLog("Tare non disponible pour cette balance") else appTared = true
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

    private fun findChar(g: BluetoothGatt, u: String): BluetoothGattCharacteristic? {
        val want = ScaleDrivers.short(u)
        for (s in g.services) for (c in s.characteristics) if (ScaleDrivers.short(c.uuid.toString()) == want) return c
        return null
    }

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
        val v = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        return if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, v) == BluetoothStatusCodes.SUCCESS
        else { d.value = v; g.writeDescriptor(d) }
    }

    private fun canNotify(c: BluetoothGattCharacteristic) =
        c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

    private fun scheduleHeartbeat(g: BluetoothGatt, d: ScaleDriver) {
        val hb = d.heartbeat ?: return
        val ch = d.heartbeatChar ?: return
        if (d.heartbeatMs <= 0) return
        main.postAtTime({
            if (gatt === g && driver === d) { findChar(g, ch)?.let { c -> enqueue { write(g, c, hb) } }; scheduleHeartbeat(g, d) }
        }, TOKEN, SystemClock.uptimeMillis() + d.heartbeatMs)
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            addLog("État connexion : status=$st état=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && st == BluetoothGatt.GATT_SUCCESS) {
                main.post { message = "Connectée, lecture des services…" }
                main.postDelayed({ if (gatt === g) g.discoverServices() }, 300)
            } else {
                main.post {
                    if (gatt === g) {
                        runCatching { g.close() }; gatt = null; driver = null; ops.clear(); opBusy = false
                        main.removeCallbacksAndMessages(TOKEN)
                        val was = status
                        status = ScaleStatus.ERROR
                        message = if (was == ScaleStatus.CONNECTED) "La balance s'est déconnectée (mise en veille ?). Touche « Connecter une balance » pour reprendre."
                        else "Connexion impossible (code $st). Ferme l'appli du fabricant, rallume la balance et réessaie."
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            val list = g.services.map { s -> "${short(s.uuid)} [" + s.characteristics.joinToString(",") { short(it.uuid) + props(it.properties) } + "]" }
            addLog("Services : ${list.joinToString("  ")}")
            val svcIds = g.services.map { it.uuid.toString() }
            main.post {
                if (gatt !== g) return@post
                gattServices = list
                // Le pilote de l'annonce doit trouver son service ; sinon on redétecte d'après les services réels.
                var info = driverInfo
                val ids = svcIds.map { ScaleDrivers.short(it) }.toSet()
                if (info == null || info.serviceHints.none { it in ids }) {
                    val byGatt = ScaleDrivers.detectByServices(svcIds, deviceName)
                    if (byGatt != info) addLog("Pilote d'après les services : ${byGatt?.id ?: "inconnu"}")
                    info = byGatt
                }
                val inf = info ?: ScaleDrivers.UNKNOWN
                driverInfo = inf; driverLabel = inf.label
                val cid = target?.adv?.aiLink?.cid ?: 0
                val d = inf.create(AiLinkCrypto, cid)
                driver = d
                val chars = if (d.subscribe.isEmpty())
                    g.services.filter { short(it.uuid) !in setOf("1800", "1801") }.flatMap { it.characteristics }.filter { canNotify(it) }
                else d.subscribe.mapNotNull { u -> findChar(g, u)?.takeIf { canNotify(it) } ?: run { addLog("Caractéristique $u absente"); null } }
                chars.forEach { c -> enqueue { enableNotify(g, c) } }
                main.post { if (driver === d) d.onReady(session) }
                scheduleHeartbeat(g, d)
                status = ScaleStatus.CONNECTED
                canTare = inf != ScaleDrivers.UNKNOWN && inf != ScaleDrivers.SIG_WEIGHT && inf != ScaleDrivers.SIG_BODY
                message = if (inf == ScaleDrivers.UNKNOWN) "Connecté, mais ce modèle n'est pas encore pris en charge : ses données sont enregistrées dans le journal technique. Envoie-moi une capture."
                          else "Balance connectée (${inf.label}). Pose l'aliment sur le plateau."
                main.postAtTime({ if (reading == null && gatt === g && inf != ScaleDrivers.UNKNOWN) addLog("Aucun poids reçu après 6 s : appuie sur une touche de la balance ou pose un objet.") },
                    TOKEN, SystemClock.uptimeMillis() + 6000)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            addLog("Abonnement ${short(d.characteristic.uuid)} (status=$st)"); opDone()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            if (st != BluetoothGatt.GATT_SUCCESS) addLog("Écriture ${short(c.uuid)} status=$st")
            opDone()
        }

        @Deprecated("API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) onData(c, c.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = onData(c, value)
    }

    private fun onData(c: BluetoothGattCharacteristic, v: ByteArray) {
        val id = short(c.uuid)
        addLog("← $id : ${ScaleDecoder.hex(v)}")
        main.post {
            val d = driver ?: return@post
            val rs = runCatching { d.onNotify(c.uuid.toString(), v, session) }.onFailure { addLog("Erreur de décodage : $it") }.getOrDefault(emptyList())
            rs.forEach { r ->
                addLog("   = ${r.value} ${r.unitLabel}${if (r.stable) " stable" else ""}${r.extra?.let { " · $it" } ?: ""}")
                onReading(r)
            }
        }
    }

    /** Filtre de vraisemblance : rien au-delà de la capacité, pas de négatif sauf après une tare depuis l'appli. */
    private fun plausible(r: WeightReading): WeightReading? {
        val g = r.grams ?: return r
        val max = if (driverInfo?.kitchen == true) 10_500.0 else 300_000.0
        val why = when {
            kotlin.math.abs(g) > max -> "hors capacité"
            g < 0 && g > -3 -> return r.copy(value = 0.0, grams = 0.0)   // petit bruit autour de zéro
            g < 0 && !appTared -> "négatif sans tare depuis l'appli"
            else -> null
        }
        if (why == null) { rejected = 0; return r }
        addLog("   valeur rejetée (${r.value} ${r.unitLabel} : $why)")
        if (++rejected == 5) message = "La balance envoie des valeurs incohérentes. Active « Journal technique », copie-le et envoie-le-moi."
        return null
    }

    private fun onReading(raw: WeightReading) {
        val r = plausible(raw) ?: return
        val now = System.currentTimeMillis()
        if (lastValue != r.value) { lastValue = r.value; lastChange = now }
        // Si la balance n'envoie pas d'indicateur « stable », on le déduit : même valeur depuis 1,5 s.
        val stable = r.stable || (now - lastChange >= 1500 && reading?.value == r.value)
        reading = if (stable != r.stable) r.copy(stable = stable) else r
    }

    private fun short(u: UUID) = ScaleDrivers.short(u.toString())
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
