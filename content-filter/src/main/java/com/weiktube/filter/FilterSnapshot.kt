package com.weiktube.filter

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class NetworkRule(val id: String, val host: String, val pathPrefix: String)
data class ScriptletRule(val name: String, val args: List<String>, val ruleId: String)

/** Immutable, deliberately restricted data format. Downloaded packages never contain executable code. */
data class FilterSnapshot(
    val version: Int,
    val networkRules: List<NetworkRule>,
    val scriptlets: List<ScriptletRule>,
    val cosmeticSelectors: List<String>,
) {
    fun toJson(): JSONObject = JSONObject().put("schemaVersion", 1).put("version", version)
        .put("networkRules", JSONArray().also { array -> networkRules.forEach {
            array.put(JSONObject().put("id", it.id).put("host", it.host).put("pathPrefix", it.pathPrefix))
        } })
        .put("scriptlets", JSONArray().also { array -> scriptlets.forEach {
            array.put(JSONObject().put("name", it.name).put("args", JSONArray(it.args)).put("ruleId", it.ruleId))
        } })
        .put("cosmeticSelectors", JSONArray(cosmeticSelectors))

    companion object {
        const val MAX_BYTES = 1024 * 1024
        val SCRIPTLETS = setOf("set-constant", "json-prune", "json-prune-fetch-response", "json-prune-xhr-response")
        private val adDomains = setOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com")
        private val youtubeAdPaths = setOf("/pagead/", "/api/stats/ads")
        private val identifier = Regex("[a-zA-Z0-9_.-]{1,80}")
        private val pruneFields = setOf("playerResponse.adPlacements", "playerResponse.playerAds", "playerResponse.adSlots", "adPlacements", "playerAds", "adSlots")
        // This reviewed matcher is declarative configuration, not an arbitrary remotely supplied regex.
        private const val requestMatcher = "url:/^(?:https:\\/\\/(?:m\\.|www\\.)?youtube\\.com)?\\/(?:youtubei\\/v\\d+\\/(?:player|next|browse|get_watch)(?:\\?|$)|watch\\?|playlist\\?|get_watch\\?)/"
        // Keep this in sync with filter-runtime.js: unsupported selectors must reject the update.
        private val supportedSelectors = setOf("ytd", "ytm").flatMap { prefix ->
            listOf("ad-slot", "display-ad", "in-feed-ad-layout", "promoted-video", "promoted-sparkles-web",
                "promoted-sparkles-text-search", "search-pyv").map { "$prefix-$it-renderer" }
        }.toSet()

        fun parse(bytes: ByteArray): FilterSnapshot {
            require(bytes.size in 1..MAX_BYTES) { "FILTER_SIZE" }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            keys(json, setOf("schemaVersion", "version", "networkRules", "scriptlets", "cosmeticSelectors"))
            require(json.get("schemaVersion") == 1) { "FILTER_SCHEMA" }
            val rawVersion = json.get("version")
            require(rawVersion is Int || rawVersion is Long) { "FILTER_VERSION" }
            val version = (rawVersion as Number).toLong()
            require(version in 1..Int.MAX_VALUE.toLong()) { "FILTER_VERSION" }
            val network = json.getJSONArray("networkRules")
            require(network.length() <= 128) { "FILTER_RULE_LIMIT" }
            val seen = mutableSetOf<String>()
            val networkRules = (0 until network.length()).map { i ->
                val rule = network.getJSONObject(i)
                keys(rule, setOf("id", "host", "pathPrefix"))
                val id = rule.getString("id"); val host = rule.getString("host"); val path = rule.getString("pathPrefix")
                require(identifier.matches(id) && seen.add(id)) { "FILTER_RULE_ID" }
                require((host in adDomains && path.isEmpty()) || (host == "youtube.com" && path in youtubeAdPaths)) { "FILTER_NETWORK_SCOPE" }
                NetworkRule(id, host, path)
            }
            val scripts = json.getJSONArray("scriptlets")
            require(scripts.length() in 1..64) { "FILTER_RULE_LIMIT" }
            val scriptlets = (0 until scripts.length()).map { i ->
                val rule = scripts.getJSONObject(i)
                keys(rule, setOf("name", "args", "ruleId"))
                val name = rule.getString("name"); val id = rule.getString("ruleId")
                require(name in SCRIPTLETS && identifier.matches(id) && seen.add(id)) { "FILTER_SCRIPTLET" }
                val raw = rule.getJSONArray("args")
                require(raw.length() in 1..8) { "FILTER_ARGUMENTS" }
                val args = (0 until raw.length()).map { j ->
                    raw.getString(j).also { require(it.length <= 4096 && '\u0000' !in it) { "FILTER_ARGUMENTS" } }
                }
                if (name == "set-constant") require(args.size == 2 && args[1] == "undefined" &&
                    args[0] in setOf("ytInitialPlayerResponse.adPlacements", "ytInitialPlayerResponse.adSlots", "ytInitialPlayerResponse.playerAds", "playerResponse.adPlacements", "playerResponse.adSlots")) { "FILTER_CONSTANT" }
                else {
                    require(args[0].split(' ').let { fields -> fields.isNotEmpty() && fields.all { it in pruneFields } }) { "FILTER_PRUNE_SCOPE" }
                    if (name == "json-prune") require(args.size == 1) { "FILTER_ARGUMENTS" }
                    else require(args.size == 3 && args[1].isEmpty() && args[2] == requestMatcher) { "FILTER_REQUEST_SCOPE" }
                }
                ScriptletRule(name, args, id)
            }
            val cosmetic = json.getJSONArray("cosmeticSelectors")
            require(cosmetic.length() <= 64) { "FILTER_RULE_LIMIT" }
            val selectors = (0 until cosmetic.length()).map { i -> cosmetic.getString(i).also {
                require(it in supportedSelectors) { "FILTER_SELECTOR" }
            } }.distinct()
            return FilterSnapshot(version.toInt(), networkRules.toList(), scriptlets.toList(), selectors)
        }

        private fun keys(json: JSONObject, expected: Set<String>) {
            require(json.keys().asSequence().toSet() == expected) { "FILTER_FIELDS" }
        }
    }
}

object FilterNetwork {
    private val origins = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val accountPath = Regex("^/(?:signin|login|logout|account)(?:[/?_]|$)|^/(?:o/oauth|ServiceLogin)", RegexOption.IGNORE_CASE)
    fun host(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443)) null
        else uri.host?.lowercase()?.trimEnd('.')
    }.getOrNull()
    fun matches(host: String, domains: Set<String>): Boolean = domains.any { domain ->
        val normalized = domain.lowercase().trimEnd('.')
        normalized.isNotEmpty() && (host == normalized || host.endsWith(".$normalized"))
    }
    fun youtubePage(url: String): Boolean = host(url) in origins
    fun enabledFor(url: String, enabled: Boolean, exceptions: Set<String>): Boolean =
        enabled && youtubePage(url) && host(url)?.let { !matches(it, exceptions) } == true &&
            runCatching { !accountPath.containsMatchIn(URI(url).path.orEmpty()) }.getOrDefault(false)

    fun blocked(snapshot: FilterSnapshot, url: String, topLevelUrl: String, mainFrame: Boolean,
                enabled: Boolean = true, exceptions: Set<String> = emptySet()): Boolean {
        if (mainFrame || !enabledFor(topLevelUrl, enabled, exceptions)) return false
        val host = host(url) ?: return false
        if (matches(host, exceptions)) return false
        val path = runCatching { URI(url).rawPath.orEmpty() }.getOrDefault("")
        return snapshot.networkRules.any { rule ->
            matches(host, setOf(rule.host)) && (rule.pathPrefix.isEmpty() || path.startsWith(rule.pathPrefix))
        }
    }
}
