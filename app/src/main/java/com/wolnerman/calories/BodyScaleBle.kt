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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

/** v2.5 : pesées enregistrées (50 dernières) et réglages de la balance pèse-personne. */
class BodyScaleStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("body_scale", Context.MODE_PRIVATE)

    var autoSave: Boolean
        get() = p.getBoolean("auto", true)
        set(v) { p.edit().putBoolean("auto", v).apply() }
    var deviceAddress: String?
        get() = p.getString("dev", null)
        set(v) { p.edit().putString("dev", v).apply() }
    var deviceKind: String?
        get() = p.getString("kind", null)
        set(v) { p.edit().putString("kind", v).apply() }

    fun list(): List<WeighIn> = runCatching {
        val a = JSONArray(p.getString("log", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> WeighIn(o.getLong("t"), o.getDouble("kg"), o.optString("p"), o.optString("hc"),
            o.optDouble("fat").takeIf { !it.isNaN() }, o.optDouble("mus").takeIf { !it.isNaN() }) } }
    }.getOrDefault(emptyList())

    private fun save(l: List<WeighIn>) {
        val a = JSONArray()
        l.sortedByDescending { it.timeMs }.take(50).forEach { w ->
            val o = JSONObject().put("t", w.timeMs).put("kg", w.kg).put("p", w.protocol).put("hc", w.health)
            w.fatPct?.let { o.put("fat", it) }; w.musclePct?.let { o.put("mus", it) }
            a.put(o)
        }
        p.edit().putString("log", a.toString()).apply()
    }

    /** Ajoute la pesée ; false si doublon (même poids à moins de 2 minutes). */
    fun add(w: WeighIn): Boolean {
        val l = list()
        if (WeighInRules.isDuplicate(w.kg, w.timeMs, l)) return false
        save(l + w); return true
    }

    fun setHealth(timeMs: Long, status: String) = save(list().map { if (it.timeMs == timeMs) it.copy(health = status) else it })
}

/**
 * v2.5 : balance pèse-personne, indépendante de la balance de cuisine (ScaleManager) : son propre scan,
 * sa propre connexion GATT et son propre journal. Les balances AiLink ne sont jamais revendiquées.
 */
@SuppressLint("MissingPermission")
class BodyScaleManager private constructor(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val adapter: BluetoothAdapter? get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    val store = BodyScaleStore(ctx)

    data class Found(val address: String, val name: String?, val rssi: Int, val kind: BodyKind, val raw: String)

    var status by mutableStateOf(ScaleStatus.IDLE); private set
    var message by mutableStateOf<String?>(null); private set
    var live by mutableStateOf<BodyReading?>(null); private set
    var deviceLabel by mutableStateOf<String?>(null); private set
    var saveMsg by mutableStateOf<String?>(null); private set
    var needsHealthPermission by mutableStateOf(false); private set
    var lastSaved by mutableStateOf<WeighIn?>(null); private set
    var history by mutableStateOf(store.list()); private set
    val found = mutableStateListOf<Found>()
    val log = mutableStateListOf<String>()

    private var scanning = false
    /** Journal technique actif : on liste et journalise aussi les appareils inconnus (sauf balances de cuisine AiLink). */
    var logAll = false
    private var target: Found? = null
    private var gatt: BluetoothGatt? = null
    private var chipsea = ChipseaGatt()
    private var stability = BodyStability()
    private val session = WeighInSession()
    private val loggedRaw = HashSet<String>()
    private val ops = ArrayDeque<() -> Boolean>()
    private var opBusy = false
    private var lastFrameAt = 0L
    private val TOKEN = Any()

    private val tf = SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE)
    fun addLog(s: String) = main.post { log.add("${tf.format(Date())}  $s"); while (log.size > 600) log.removeAt(0) }

    fun hasBle() = ctx.packageManager.hasSystemFeature("android.hardware.bluetooth_le") && adapter != null
    fun isBluetoothOn() = adapter?.isEnabled == true

    // ---------------------------------------------------------------- Scan
    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onResult(it) }
        override fun onScanFailed(errorCode: Int) {
            addLog("Échec du scan, code $errorCode")
            main.post { scanning = false; fail("La recherche Bluetooth a échoué (code $errorCode). Réessaie dans quelques secondes.") }
        }
    }

    private fun trimAdv(b: ByteArray): ByteArray {
        var i = 0
        while (i < b.size) { val l = b[i].toInt() and 0xFF; if (l == 0) break; i += 1 + l }
        return b.copyOf(minOf(i, b.size))
    }

    private fun onResult(r: ScanResult) {
        try {
            val bytes = r.scanRecord?.bytes
            val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull()
            val ms = BleAdv.manufacturer(bytes)
            val services = ScaleDecoder.advServices16(bytes)
            val aiLink = ScaleDecoder.parseAdv(bytes) != null
            val kind = BodyScaleDetect.detect(name, r.device.address, services, ms, aiLink)
                ?: if (logAll && !aiLink && (name != null || ms.isNotEmpty())) BodyKind.CANDIDATE else return
            val raw = ScaleDecoder.hex(bytes?.let { trimAdv(it) })
            val f = Found(r.device.address, name, r.rssi, kind, raw)
            main.post { onFound(f, ms) }
        } catch (t: Throwable) {
            addLog("Annonce illisible ignorée : $t")
        }
    }

    private fun onFound(f: Found, ms: List<ManufData>) {
        if (!scanning) return
        val i = found.indexOfFirst { it.address == f.address }
        if (i >= 0) found[i] = f else if (found.size < 40) found.add(f)
        val tgt = target
        // Journal : chaque annonce différente d'une balance possible (pour analyse si non reconnue)
        if (loggedRaw.add(f.address + f.raw) && loggedRaw.size < 400)
            addLog("Annonce ${f.name ?: "?"} ${f.address} rssi=${f.rssi} type=${f.kind.name} fabricant=${ms.joinToString(" ")} data=${f.raw}")
        if (tgt == null) {
            val saved = store.deviceAddress
            val auto = when {
                saved != null -> f.address == saved
                else -> f.kind == BodyKind.OKOK_ADV || f.kind == BodyKind.QN_ADV || f.kind == BodyKind.CHIPSEA_GATT
            }
            if (auto) choose(f)
            return
        }
        if (tgt.address != f.address || tgt.kind.gatt) return
        if (f.kind == BodyKind.CANDIDATE) return
        val r = runCatching { if (f.kind == BodyKind.QN_ADV) ms.firstOrNull { QnAdv.isAabb(it) }?.let { QnAdv.parse(it.data) } else OkokAdv.parse(ms) }
            .onFailure { addLog("Erreur de décodage : $it") }.getOrNull()
        if (r == null) { addLog("   trame non décodée (format inconnu) : ${ms.joinToString(" ")}"); return }
        onReading(r)
    }

    fun startScan() {
        val a = adapter
        if (a == null || !hasBle()) { fail("Ce téléphone n'a pas de Bluetooth basse consommation."); return }
        if (!a.isEnabled) { fail("Le Bluetooth est désactivé. Active-le puis réessaie."); return }
        val scanner = a.bluetoothLeScanner ?: run { fail("Bluetooth indisponible pour le moment. Réessaie."); return }
        stop(silent = true)
        found.clear(); loggedRaw.clear(); target = null; live = null; deviceLabel = null
        stability = BodyStability(); chipsea = ChipseaGatt()
        scanning = true
        status = ScaleStatus.SCANNING
        message = "Recherche… Monte sur la balance (ou tapote-la pour l'allumer). Ferme l'appli OKOK pendant la pesée."
        addLog("Début du scan balance pèse-personne (Android ${Build.VERSION.SDK_INT}, Calories 2.5, appareil mémorisé : ${store.deviceAddress ?: "aucun"})")
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        } catch (t: Throwable) { scanning = false; fail("Impossible de lancer la recherche : ${t.message}"); return }
        // Les balances « annonce » n'émettent que pendant la pesée : on écoute 3 minutes.
        main.postDelayed({
            if (scanning && (target == null || target?.kind?.gatt == false)) {
                stopScan()
                status = ScaleStatus.IDLE
                message = if (found.isEmpty()) "Aucune balance trouvée. Monte sur la balance puis relance la recherche. Si elle n'est pas reconnue, active le journal technique et envoie-le."
                          else "Écoute terminée. Touche « Rechercher » avant ta prochaine pesée."
            }
        }, 180_000)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCb) }
        addLog("Fin du scan (${found.size} appareil(s))")
    }

    /** Choisit un appareil de la liste : écoute de ses annonces, ou connexion GATT. */
    fun choose(f: Found) {
        target = f
        deviceLabel = "${f.name ?: "Balance"} · ${f.kind.label}"
        addLog("Appareil choisi : ${f.name} ${f.address} (${f.kind.name})")
        if (f.kind != BodyKind.CANDIDATE) { store.deviceAddress = f.address; store.deviceKind = f.kind.name }
        when {
            f.kind.gatt -> connect(f)
            f.kind == BodyKind.CANDIDATE -> { status = ScaleStatus.CONNECTED; message = "Ce modèle n'est pas encore reconnu : ses annonces sont dans le journal technique. Monte sur la balance, puis copie le journal et envoie-le." }
            else -> { status = ScaleStatus.CONNECTED; message = "Balance trouvée. Monte dessus et attends « Stable »." }
        }
    }

    fun forgetDevice() { store.deviceAddress = null; store.deviceKind = null; addLog("Appareil oublié") }

    fun stop(silent: Boolean = false) {
        stopScan()
        main.removeCallbacksAndMessages(TOKEN)
        ops.clear(); opBusy = false
        gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
        gatt = null
        if (!silent) { status = ScaleStatus.IDLE; message = "Arrêté."; addLog("Arrêt demandé") }
    }

    private fun fail(m: String) { status = ScaleStatus.ERROR; message = m; addLog("Erreur : $m") }

    // ---------------------------------------------------------------- GATT (Chipsea-BLE, norme SIG)
    private fun connect(f: Found) {
        stopScan()
        val dev = runCatching { adapter?.getRemoteDevice(f.address) }.getOrNull() ?: run { fail("Appareil introuvable."); return }
        status = ScaleStatus.CONNECTING
        message = "Connexion à ${f.name ?: f.address}… (ferme l'appli OKOK, elle garde la balance occupée)"
        addLog("Connexion GATT à ${f.name} ${f.address}")
        gatt = dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        main.postAtTime({ if (status == ScaleStatus.CONNECTING) { addLog("Délai de connexion dépassé"); stop(true); fail("La balance ne répond pas. Monte dessus pour la réveiller, ferme l'appli OKOK et réessaie.") } },
            TOKEN, android.os.SystemClock.uptimeMillis() + 20_000)
    }

    private fun enqueue(op: () -> Boolean) = main.post { ops.addLast(op); next() }
    private fun next() {
        if (opBusy) return
        while (ops.isNotEmpty()) { val op = ops.removeFirst(); if (runCatching { op() }.getOrDefault(false)) { opBusy = true; return } }
    }
    private fun opDone() = main.post { opBusy = false; next() }
    private fun short(u: UUID) = ScaleDrivers.short(u.toString())
    private fun findChar(g: BluetoothGatt, s16: String): BluetoothGattCharacteristic? {
        for (s in g.services) for (c in s.characteristics) if (short(c.uuid) == s16) return c
        return null
    }
    private fun canNotify(c: BluetoothGattCharacteristic) =
        c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

    @Suppress("DEPRECATION")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(ch, true)
        val d = ch.getDescriptor(UUID.fromString(AiLinkProtocol.CCCD)) ?: return false
        val v = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        return if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, v) == BluetoothStatusCodes.SUCCESS else { d.value = v; g.writeDescriptor(d) }
    }

    @Suppress("DEPRECATION")
    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, v: ByteArray): Boolean {
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(ch, v, type) == BluetoothStatusCodes.SUCCESS
        else { ch.writeType = type; ch.value = v; g.writeCharacteristic(ch) }
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            addLog("État connexion : status=$st état=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && st == BluetoothGatt.GATT_SUCCESS) {
                main.postDelayed({ if (gatt === g) g.discoverServices() }, 300)
            } else main.post {
                if (gatt !== g) return@post
                runCatching { g.close() }; gatt = null; ops.clear(); opBusy = false
                val was = status
                status = ScaleStatus.IDLE
                message = if (was == ScaleStatus.CONNECTED) "La balance s'est déconnectée (mise en veille). Touche « Rechercher » avant la prochaine pesée."
                          else "Connexion impossible (code $st). Ferme l'appli OKOK, monte sur la balance et réessaie."
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            val list = g.services.map { s -> "${short(s.uuid)} [" + s.characteristics.joinToString(",") { short(it.uuid) } + "]" }
            addLog("Services : ${list.joinToString("  ")}")
            main.post {
                if (gatt !== g) return@post
                val kind = target?.kind
                val wanted = when (kind) {
                    BodyKind.CHIPSEA_GATT -> listOf(ChipseaGatt.NOTIFY)
                    BodyKind.SIG_WEIGHT -> listOf("2A9D")
                    BodyKind.SIG_BODY -> listOf("2A9C", "2A9D")
                    else -> emptyList()
                }
                var chars = wanted.mapNotNull { findChar(g, it)?.takeIf { c -> canNotify(c) } }
                if (chars.isEmpty()) {
                    addLog("Caractéristiques attendues absentes (${wanted.joinToString()}) : abonnement à toutes, journal seulement")
                    chars = g.services.filter { short(it.uuid) !in setOf("1800", "1801") }.flatMap { it.characteristics }.filter { canNotify(it) }
                }
                chars.forEach { c -> enqueue { enableNotify(g, c) } }
                if (kind == BodyKind.CHIPSEA_GATT) findChar(g, ChipseaGatt.WRITE)?.let { w ->
                    val pr = Storage(ctx).loadProfile()
                    val sync = ChipseaGatt.userSync(1, pr.male != false, (pr.age ?: 40).coerceIn(10, 99),
                        (pr.heightCm ?: 170.0).toInt().coerceIn(90, 250), Date())
                    addLog("→ FFF2 synchro utilisateur/heure : ${ScaleDecoder.hex(sync)}")
                    enqueue { write(g, w, sync) }
                }
                status = ScaleStatus.CONNECTED
                message = "Balance connectée. Monte dessus et attends « Stable »."
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) { addLog("Abonnement ${short(d.characteristic.uuid)} (status=$st)"); opDone() }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) { if (st != 0) addLog("Écriture ${short(c.uuid)} status=$st"); opDone() }

        @Deprecated("API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { if (Build.VERSION.SDK_INT < 33) onData(c, c.value ?: return) }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = onData(c, value)
    }

    private fun onData(c: BluetoothGattCharacteristic, v: ByteArray) {
        val id = short(c.uuid)
        addLog("← $id : ${ScaleDecoder.hex(v)}")
        main.post {
            val r = runCatching {
                when {
                    id == "2A9D" || id == "2A9C" -> BodySig.decode(id, v)
                    target?.kind == BodyKind.CHIPSEA_GATT -> chipsea.feed(v)
                    else -> null
                }
            }.onFailure { addLog("Erreur de décodage : $it") }.getOrNull()
            if (r == null) addLog("   trame non décodée") else onReading(r)
        }
    }

    // ---------------------------------------------------------------- Mesures
    private fun onReading(raw: BodyReading) {
        val now = System.currentTimeMillis()
        lastFrameAt = now
        val r = stability.feed(raw, now)
        val stable = r?.stable == true
        addLog("   = ${raw.kg} kg${if (raw.stable == true) " stable" else ""} (${raw.protocol})${raw.extra?.let { " · $it" } ?: ""}")
        if (r != null) live = r else if (raw.kg < BodyPlausible.MIN_KG) live = null
        val toSave = session.onFrame(raw.kg, stable, now)
        if (toSave != null && store.autoSave) record(r ?: raw, auto = true)
    }

    /** Enregistre une pesée (bouton ou automatique) puis l'écrit dans Santé Connect. */
    fun record(r: BodyReading, auto: Boolean = false) {
        val now = System.currentTimeMillis()
        val (fat, mus) = compositionOf(r)
        val w = WeighIn(now, BodyUnits.round2(r.kg), r.protocol, fatPct = fat, musclePct = mus)
        val protocol = r.protocol
        session.markSaved()
        if (!store.add(w)) { saveMsg = "Déjà enregistré : ${WeighInRules.fmtKg(w.kg)} (même poids il y a moins de 2 min)."; return }
        history = store.list(); lastSaved = w
        addLog("Pesée enregistrée : ${w.kg} kg${if (auto) " (automatique)" else ""}")
        saveMsg = "✓ ${WeighInRules.fmtKg(w.kg)} enregistré${if (auto) " automatiquement" else ""}. Santé Connect…"
        io.launch {
            val res = HealthSync.writeWeight(ctx, w.kg, Instant.ofEpochMilli(w.timeMs), deviceLabel ?: protocol)
            val (status, msg) = res.fold(
                onSuccess = { if (it == HealthSync.WeightWrite.WRITTEN) "ok" to "✓ ${WeighInRules.fmtKg(w.kg)} enregistré et écrit dans Santé Connect."
                              else "dup" to "✓ ${WeighInRules.fmtKg(w.kg)} enregistré (déjà présent dans Santé Connect)." },
                onFailure = { e ->
                    needsHealthPermission = (e as? HealthSync.WeightError)?.needsPermission == true
                    "err" to "✓ ${WeighInRules.fmtKg(w.kg)} enregistré dans Calories, mais pas dans Santé Connect : ${e.message ?: e.javaClass.simpleName}"
                })
            if (status != "err") needsHealthPermission = false
            addLog("Santé Connect : $msg")
            store.setHealth(w.timeMs, status); history = store.list(); saveMsg = msg
        }
    }

    /** Réessaie d'écrire dans Santé Connect les pesées qui ont échoué (après avoir accordé l'autorisation). */
    fun retryHealth() {
        val pending = store.list().filter { it.health == "err" || it.health.isEmpty() }
        if (pending.isEmpty()) return
        io.launch {
            var ok = 0; var last: String? = null
            for (w in pending) {
                HealthSync.writeWeight(ctx, w.kg, Instant.ofEpochMilli(w.timeMs), w.protocol).fold(
                    onSuccess = { store.setHealth(w.timeMs, if (it == HealthSync.WeightWrite.WRITTEN) "ok" else "dup"); ok++ },
                    onFailure = { e -> last = e.message; needsHealthPermission = (e as? HealthSync.WeightError)?.needsPermission == true })
            }
            if (ok > 0) needsHealthPermission = false
            history = store.list()
            saveMsg = if (last == null) "Santé Connect : $ok pesée(s) envoyée(s)." else "Santé Connect : $ok envoyée(s), erreur : $last"
        }
    }

    companion object {
        @Volatile private var inst: BodyScaleManager? = null
        fun get(ctx: Context): BodyScaleManager = inst ?: synchronized(this) { inst ?: BodyScaleManager(ctx.applicationContext).also { inst = it } }
        fun shutdown() { inst?.stop(silent = true) }
    }
}
