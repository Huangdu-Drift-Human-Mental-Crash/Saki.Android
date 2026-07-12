package org.hdhmc.saki.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.hdhmc.saki.data.remote.subsonic.SubsonicAuth
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.ServerEndpoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Singleton
class EndpointSelector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val bestEndpoints = ConcurrentHashMap<Long, Long>()
    private val forcedEndpoints = ConcurrentHashMap<Long, Long>()
    private val lastProbeResults = ConcurrentHashMap<Long, List<EndpointProbeResult>>()
    private val serverConfigs = ConcurrentHashMap<Long, ServerConfig>()
    private val activeProbes = ConcurrentHashMap<Long, ActiveProbe>()
    private val probeGenerations = ConcurrentHashMap<Long, Long>()
    private val probingServers = ConcurrentHashMap<Long, Boolean>()
    private val latestEndpointEvents = ConcurrentHashMap<EndpointKey, EndpointReachabilityEvent>()
    private val endpointEventVersion = AtomicLong(0L)
    private val offlineRecoveryJobs = ConcurrentHashMap<Long, Job>()
    private val endpointStateLocks = ConcurrentHashMap<Long, Any>()

    private val _probeVersion = MutableStateFlow(0L)
    val probeVersion: StateFlow<Long> = _probeVersion.asStateFlow()

    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(1500, TimeUnit.MILLISECONDS)
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()
    }

    data class EndpointProbeResult(
        val endpoint: ServerEndpoint,
        val latencyMs: Long?,
        val reachable: Boolean,
    )

    private data class ActiveProbe(
        val generation: Long,
        val job: Job,
        val firstReachable: CompletableDeferred<ServerEndpoint?>,
    )

    private data class EndpointKey(
        val serverId: Long,
        val endpointId: Long,
    )

    private var networkCallbackRegistered = false

    fun start() {
        if (networkCallbackRegistered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onNetworkChanged()
            override fun onLost(network: Network) = onNetworkChanged()
        })
        networkCallbackRegistered = true
    }

    fun registerServer(server: ServerConfig) {
        serverConfigs[server.id] = server
    }

    fun unregisterServer(serverId: Long) {
        activeProbes.remove(serverId)?.let { activeProbe ->
            activeProbe.job.cancel()
            activeProbe.firstReachable.complete(null)
        }
        probeGenerations.remove(serverId)
        probingServers.remove(serverId)
        serverConfigs.remove(serverId)
        bestEndpoints.remove(serverId)
        forcedEndpoints.remove(serverId)
        lastProbeResults.remove(serverId)
        latestEndpointEvents.keys.removeAll { it.serverId == serverId }
        offlineRecoveryJobs.remove(serverId)?.cancel()
        endpointStateLocks.remove(serverId)
        _probeVersion.update { it + 1 }
    }

    private var reprobeJob: kotlinx.coroutines.Job? = null

    private fun onNetworkChanged() {
        forcedEndpoints.clear()
        cancelOfflineRecoveryJobs()
        reprobeJob?.cancel()
        reprobeJob = scope.launch {
            serverConfigs.forEach { (serverId, server) -> probe(serverId, server) }
        }
    }

    /**
     * Probe endpoints: returns as soon as the first reachable endpoint is found.
     * Remaining probes continue in background to find the optimal endpoint.
     * Cancels any previous in-flight probe for the same server.
     */
    suspend fun probe(serverId: Long, server: ServerConfig): ServerEndpoint? {
        val firstReachable = synchronized(endpointStateLock(serverId)) {
            activeProbes.remove(serverId)?.let { activeProbe ->
                activeProbe.job.cancel()
                activeProbe.firstReachable.complete(null)
            }
            serverConfigs[serverId] = server
            val endpoints = server.endpoints.sortedBy(ServerEndpoint::order)
            val generation = nextProbeGeneration(serverId)
            val probeStartEventVersion = endpointEventVersion.get()
            probingServers[serverId] = true
            _probeVersion.update { it + 1 }
            if (endpoints.isEmpty()) {
                lastProbeResults.remove(serverId)
                bestEndpoints.remove(serverId)
                probingServers.remove(serverId)
                _probeVersion.update { it + 1 }
                return null
            }

            val firstReachable = CompletableDeferred<ServerEndpoint?>()
            val probeResults = ConcurrentHashMap<Long, Long>()
            val completedEndpointIds = ConcurrentHashMap.newKeySet<Long>()
            var probeFinished = false

            val parentJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val jobs = endpoints.map { endpoint ->
                        launch {
                            val latency = pingEndpoint(endpoint, server)
                            if (!isCurrentProbe(serverId, generation)) return@launch
                            completedEndpointIds += endpoint.id
                            if (latency != null) {
                                probeResults[endpoint.id] = latency
                                recordReachableEndpoint(serverId, endpoint, latency)
                                firstReachable.complete(endpoint)
                            }
                            publishProbeResults(
                                serverId = serverId,
                                endpoints = endpoints,
                                reachableLatencies = probeResults,
                                completedEndpointIds = completedEndpointIds,
                                probeStartEventVersion = probeStartEventVersion,
                                final = false,
                            )
                        }
                    }
                    jobs.forEach { it.join() }
                    if (!isCurrentProbe(serverId, generation)) return@launch
                    publishProbeResults(
                        serverId = serverId,
                        endpoints = endpoints,
                        reachableLatencies = probeResults,
                        completedEndpointIds = completedEndpointIds,
                        probeStartEventVersion = probeStartEventVersion,
                        final = true,
                    )
                    clearProbeInProgress(serverId, generation)
                    if (isOfflineDegraded(serverId)) {
                        scheduleOfflineRecovery(serverId)
                    }
                    probeFinished = true
                    firstReachable.complete(null)
                } finally {
                    if (!probeFinished && isCurrentProbe(serverId, generation)) {
                        clearProbeInProgress(serverId, generation)
                    }
                }
            }

            activeProbes[serverId] = ActiveProbe(
                generation = generation,
                job = parentJob,
                firstReachable = firstReachable,
            )
            parentJob.start()
            firstReachable
        }

        return firstReachable.await()
    }

    fun sortedEndpoints(serverId: Long, endpoints: List<ServerEndpoint>): List<ServerEndpoint> {
        val bestId = bestEndpoints[serverId]
        val failedIds = lastProbeResults[serverId]
            .orEmpty()
            .filterNot { result -> result.reachable }
            .map { it.endpoint.id }
            .toSet()
        val ordered = endpoints.sortedBy { endpoint -> if (endpoint.id in failedIds) 1 else 0 }
        val best = bestId?.let { id -> ordered.find { it.id == id } }
        return if (best == null) {
            ordered
        } else {
            listOf(best) + ordered.filter { it.id != best.id }
        }
    }

    fun invalidate(serverId: Long, failedEndpointId: Long) {
        synchronized(endpointStateLock(serverId)) {
            forcedEndpoints.remove(serverId, failedEndpointId)
            val removedActiveEndpoint = bestEndpoints.remove(serverId, failedEndpointId)
            val endpoint = serverConfigs[serverId]?.endpoints?.firstOrNull { it.id == failedEndpointId }
                ?: return
            recordEndpointEvent(serverId, failedEndpointId, reachable = false)
            upsertProbeResult(serverId, EndpointProbeResult(endpoint, latencyMs = null, reachable = false))
            if (removedActiveEndpoint && forcedEndpoints[serverId] == null) {
                selectBestKnownReachableEndpointLocked(serverId)
            }
        }
        _probeVersion.update { it + 1 }
        if (isOfflineDegraded(serverId)) {
            scheduleOfflineRecovery(serverId)
        }
    }

    fun getActiveEndpointId(serverId: Long): Long? = bestEndpoints[serverId]

    fun getActiveEndpoint(serverId: Long): ServerEndpoint? {
        val activeEndpointId = getActiveEndpointId(serverId) ?: return null
        return serverConfigs[serverId]?.endpoints?.firstOrNull { it.id == activeEndpointId }
    }

    fun getLastProbeResults(serverId: Long): List<EndpointProbeResult> =
        lastProbeResults[serverId] ?: emptyList()

    fun isProbeInProgress(serverId: Long): Boolean = probingServers[serverId] == true

    fun hasCompletedProbe(serverId: Long): Boolean {
        val endpointIds = serverConfigs[serverId]?.endpoints?.map { it.id }?.toSet().orEmpty()
        if (endpointIds.isEmpty()) return false
        val resultIds = lastProbeResults[serverId].orEmpty().map { it.endpoint.id }.toSet()
        return resultIds.containsAll(endpointIds)
    }

    fun isOfflineDegraded(serverId: Long): Boolean {
        if (bestEndpoints[serverId] != null) return false
        val results = lastProbeResults[serverId].orEmpty()
        return hasCompletedProbe(serverId) && results.none { it.reachable }
    }

    fun recordSuccess(serverId: Long, endpoint: ServerEndpoint, latencyMs: Long? = null) {
        synchronized(endpointStateLock(serverId)) {
            serverConfigs[serverId] ?: return
            recordReachableEndpoint(serverId, endpoint, latencyMs)
            activeProbes[serverId]?.firstReachable?.complete(endpoint)
            offlineRecoveryJobs.remove(serverId)?.cancel()
        }
        _probeVersion.update { it + 1 }
    }

    fun forceEndpoint(serverId: Long, endpointId: Long) {
        synchronized(endpointStateLock(serverId)) {
            forcedEndpoints[serverId] = endpointId
            bestEndpoints[serverId] = endpointId
            offlineRecoveryJobs.remove(serverId)?.cancel()
        }
        _probeVersion.update { it + 1 }
    }

    fun clearForce(serverId: Long) {
        synchronized(endpointStateLock(serverId)) {
            forcedEndpoints.remove(serverId)
            bestEndpoints.remove(serverId)
            selectBestKnownReachableEndpointLocked(serverId)
        }
        _probeVersion.update { it + 1 }
        if (isOfflineDegraded(serverId)) {
            scheduleOfflineRecovery(serverId)
        }
    }

    fun isForced(serverId: Long): Boolean = forcedEndpoints.containsKey(serverId)

    fun findServerByHostPort(host: String, port: Int): Long? {
        var matchedServerId: Long? = null
        for ((serverId, config) in serverConfigs) {
            for (ep in config.endpoints) {
                val url = ep.baseUrl.trimEnd('/').toHttpUrlOrNull() ?: continue
                if (url.host == host && url.port == port) {
                    if (matchedServerId == null) {
                        matchedServerId = serverId
                    } else if (matchedServerId != serverId) {
                        return null // ambiguous
                    }
                }
            }
        }
        return matchedServerId
    }

    /** Returns the first endpoint by order for the given server (used as canonical base for Coil URLs). */
    fun getCanonicalEndpoint(serverId: Long): ServerEndpoint? {
        return serverConfigs[serverId]?.endpoints
            ?.sortedBy(ServerEndpoint::order)
            ?.firstOrNull()
    }

    private fun nextProbeGeneration(serverId: Long): Long {
        return probeGenerations.merge(serverId, 1L) { current, increment -> current + increment } ?: 1L
    }

    private fun isCurrentProbe(serverId: Long, generation: Long): Boolean {
        return probeGenerations[serverId] == generation
    }

    private fun clearProbeInProgress(serverId: Long, generation: Long) {
        probingServers.remove(serverId)
        activeProbes.computeIfPresent(serverId) { _, activeProbe ->
            activeProbe.takeUnless { it.generation == generation }
        }
        _probeVersion.update { it + 1 }
    }

    private fun recordReachableEndpoint(
        serverId: Long,
        endpoint: ServerEndpoint,
        latencyMs: Long?,
    ) {
        synchronized(endpointStateLock(serverId)) {
            recordEndpointEvent(serverId, endpoint.id, reachable = true)
            val previous = lastProbeResults[serverId]
                .orEmpty()
                .firstOrNull { it.endpoint.id == endpoint.id }
            upsertProbeResult(
                serverId,
                EndpointProbeResult(
                    endpoint = endpoint,
                    latencyMs = latencyMs ?: previous?.latencyMs,
                    reachable = true,
                ),
            )
            if (forcedEndpoints[serverId] == null) {
                val currentBestId = bestEndpoints[serverId]
                val currentBestLatency = lastProbeResults[serverId]
                    .orEmpty()
                    .firstOrNull { it.endpoint.id == currentBestId }
                    ?.latencyMs
                if (
                    currentBestId == null ||
                    latencyMs == null ||
                    currentBestLatency == null ||
                    latencyMs < currentBestLatency
                ) {
                    bestEndpoints[serverId] = endpoint.id
                }
            }
        }
    }

    private fun selectBestKnownReachableEndpointLocked(serverId: Long) {
        check(Thread.holdsLock(endpointStateLock(serverId)))
        if (forcedEndpoints[serverId] != null) return
        val bestEndpointId = bestReachableEndpointId(lastProbeResults[serverId].orEmpty())
        if (bestEndpointId != null) {
            bestEndpoints[serverId] = bestEndpointId
        } else {
            bestEndpoints.remove(serverId)
        }
    }

    private fun publishProbeResults(
        serverId: Long,
        endpoints: List<ServerEndpoint>,
        reachableLatencies: Map<Long, Long>,
        completedEndpointIds: Set<Long>,
        probeStartEventVersion: Long,
        final: Boolean,
    ) {
        synchronized(endpointStateLock(serverId)) {
            val previousById = lastProbeResults[serverId].orEmpty().associateBy { it.endpoint.id }
            lastProbeResults[serverId] = endpoints.mapNotNull { endpoint ->
                val latency = reachableLatencies[endpoint.id]
                val latestEvent = latestEndpointEvents[EndpointKey(serverId, endpoint.id)]
                when (
                    resolveEndpointReachability(
                        hasProbeLatency = latency != null,
                        latestEvent = latestEvent,
                        probeStartEventVersion = probeStartEventVersion,
                        probeCompleted = final || endpoint.id in completedEndpointIds,
                        previousReachable = previousById[endpoint.id]?.reachable,
                    )
                ) {
                    true -> EndpointProbeResult(
                        endpoint = endpoint,
                        latencyMs = latency ?: previousById[endpoint.id]?.latencyMs,
                        reachable = true,
                    )
                    false -> EndpointProbeResult(endpoint, latencyMs = null, reachable = false)
                    null -> previousById[endpoint.id]
                }
            }
            if (final) {
                selectBestKnownReachableEndpointLocked(serverId)
            }
        }
        _probeVersion.update { it + 1 }
    }

    private fun upsertProbeResult(serverId: Long, result: EndpointProbeResult) {
        synchronized(endpointStateLock(serverId)) {
            val server = serverConfigs[serverId]
            val previousById = lastProbeResults[serverId]
                .orEmpty()
                .associateBy { it.endpoint.id }
                .toMutableMap()
            previousById[result.endpoint.id] = result
            val orderedEndpoints = server?.endpoints?.sortedBy(ServerEndpoint::order).orEmpty()
            lastProbeResults[serverId] = if (orderedEndpoints.isEmpty()) {
                previousById.values.toList()
            } else {
                orderedEndpoints.mapNotNull { endpoint -> previousById[endpoint.id] }
            }
        }
    }

    private fun recordEndpointEvent(serverId: Long, endpointId: Long, reachable: Boolean) {
        latestEndpointEvents[EndpointKey(serverId, endpointId)] = EndpointReachabilityEvent(
            version = endpointEventVersion.incrementAndGet(),
            reachable = reachable,
        )
    }

    private fun endpointStateLock(serverId: Long): Any =
        endpointStateLocks.computeIfAbsent(serverId) { Any() }

    private fun scheduleOfflineRecovery(serverId: Long) {
        offlineRecoveryJobs.compute(serverId) { _, existing ->
            if (existing?.isActive == true) {
                existing
            } else {
                scope.launch {
                    try {
                        var attempt = 0
                        while (isOfflineDegraded(serverId)) {
                            delay(endpointOfflineRecoveryDelayMs(attempt))
                            val server = serverConfigs[serverId] ?: break
                            if (!isOfflineDegraded(serverId)) break
                            probe(serverId, server)
                            attempt += 1
                        }
                    } finally {
                        val currentJob = kotlin.coroutines.coroutineContext[Job]
                        if (currentJob != null) {
                            offlineRecoveryJobs.remove(serverId, currentJob)
                        }
                    }
                }
            }
        }
    }

    private fun cancelOfflineRecoveryJobs() {
        offlineRecoveryJobs.forEach { (serverId, job) ->
            if (offlineRecoveryJobs.remove(serverId, job)) {
                job.cancel()
            }
        }
    }

    private suspend fun pingEndpoint(endpoint: ServerEndpoint, server: ServerConfig): Long? {
        val authQuery = SubsonicAuth.baseQuery(server)
        val urlBuilder = endpoint.baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("rest/ping.view")
            ?.addQueryParameter("f", "json") ?: return null
        authQuery.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val request = Request.Builder().url(urlBuilder.build()).get().build()
        val start = System.nanoTime()
        return suspendCancellableCoroutine { cont ->
            val call = probeClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    val ms = (System.nanoTime() - start) / 1_000_000
                    if (cont.isActive) cont.resume(if (response.isSuccessful) ms else null)
                }
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }
}

internal fun bestReachableEndpointId(
    results: List<EndpointSelector.EndpointProbeResult>,
): Long? = results
    .asSequence()
    .filter { result -> result.reachable }
    .minByOrNull { result -> result.latencyMs ?: Long.MAX_VALUE }
    ?.endpoint
    ?.id

internal data class EndpointReachabilityEvent(
    val version: Long,
    val reachable: Boolean,
)

internal fun resolveEndpointReachability(
    hasProbeLatency: Boolean,
    latestEvent: EndpointReachabilityEvent?,
    probeStartEventVersion: Long,
    probeCompleted: Boolean,
    previousReachable: Boolean?,
): Boolean? = when {
    latestEvent != null && latestEvent.version > probeStartEventVersion -> latestEvent.reachable
    hasProbeLatency -> true
    probeCompleted -> false
    else -> previousReachable
}

private val EndpointOfflineRecoveryDelaysMs = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L)

internal fun endpointOfflineRecoveryDelayMs(attempt: Int): Long =
    EndpointOfflineRecoveryDelaysMs[attempt.coerceIn(0, EndpointOfflineRecoveryDelaysMs.lastIndex)]
