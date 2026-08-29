package org.beobma.classWarPlugin.updater

import com.google.gson.Gson
import com.google.gson.JsonParseException
import org.beobma.classWarPlugin.ClassWarPlugin
import org.bukkit.plugin.InvalidDescriptionException
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.scheduler.BukkitTask
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.JarFile
import java.util.logging.Level
import java.util.regex.PatternSyntaxException

class GitHubReleaseUpdater(private val plugin: ClassWarPlugin) {
    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val checkInProgress = AtomicBoolean(false)
    private var scheduledTask: BukkitTask? = null

    fun start() {
        stop()
        val settings = runCatching { Settings.from(plugin) }
            .getOrElse { error ->
                plugin.logger.log(Level.WARNING, "[ClassWar] 자동 업데이트 설정을 읽을 수 없습니다.", error)
                return
            }
        if (!settings.enabled) {
            plugin.loggerInfo("자동 업데이트가 비활성화되어 있습니다.")
            return
        }

        val firstDelay = secondsToTicks(settings.startupDelaySeconds.toLong())
        val period = hoursToTicks(settings.checkIntervalHours)
        scheduledTask = if (period > 0L) {
            plugin.server.scheduler.runTaskTimerAsynchronously(
                plugin,
                Runnable { runAutomaticCheck(settings) },
                firstDelay,
                period,
            )
        } else {
            plugin.server.scheduler.runTaskLaterAsynchronously(
                plugin,
                Runnable { runAutomaticCheck(settings) },
                firstDelay,
            )
        }
    }

    fun stop() {
        scheduledTask?.cancel()
        scheduledTask = null
    }

    /** Runs an update check asynchronously and invokes [onComplete] back on the server thread. */
    fun checkNow(onComplete: (UpdateResult) -> Unit) {
        val settings = runCatching { Settings.from(plugin) }
            .getOrElse { error ->
                onComplete(UpdateResult.Failed("자동 업데이트 설정이 올바르지 않습니다: ${error.message}"))
                return
            }
        if (!checkInProgress.compareAndSet(false, true)) {
            onComplete(UpdateResult.InProgress)
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = try {
                checkSafely(settings)
            } finally {
                checkInProgress.set(false)
            }
            if (plugin.isEnabled) {
                plugin.server.scheduler.runTask(plugin, Runnable { onComplete(result) })
            }
        })
    }

    private fun runAutomaticCheck(settings: Settings) {
        if (!checkInProgress.compareAndSet(false, true)) return
        try {
            when (val result = checkSafely(settings)) {
                is UpdateResult.Downloaded -> plugin.loggerInfo(
                    "${result.currentVersion} -> ${result.latestVersion} 업데이트를 내려받았습니다. " +
                        "서버를 재시작하면 적용됩니다: ${result.target}",
                )

                is UpdateResult.PendingRestart -> plugin.loggerInfo(
                    "${result.latestVersion} 업데이트가 이미 준비되어 있습니다. 서버를 재시작하면 적용됩니다.",
                )

                is UpdateResult.CurrentIsNewer -> plugin.loggerInfo(
                    "설치 버전(${result.currentVersion})이 최신 공개 릴리스(${result.latestVersion})보다 높습니다.",
                )

                is UpdateResult.Failed -> plugin.logger.warning("[ClassWar] 자동 업데이트 확인 실패: ${result.reason}")
                UpdateResult.InProgress, is UpdateResult.UpToDate -> Unit
            }
        } finally {
            checkInProgress.set(false)
        }
    }

    private fun checkSafely(settings: Settings): UpdateResult = try {
        checkForUpdate(settings)
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        UpdateResult.Failed("업데이트 확인 작업이 중단되었습니다.")
    } catch (error: UpdateException) {
        UpdateResult.Failed(error.message ?: "업데이트를 처리할 수 없습니다.")
    } catch (error: Exception) {
        plugin.logger.log(Level.WARNING, "[ClassWar] GitHub 자동 업데이트 처리 중 오류가 발생했습니다.", error)
        UpdateResult.Failed(error.message ?: error.javaClass.simpleName)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun checkForUpdate(settings: Settings): UpdateResult {
        val currentVersionText = plugin.pluginMeta.version
        val currentVersion = ReleaseVersion.parse(currentVersionText)
            ?: return UpdateResult.Failed("현재 플러그인 버전 '$currentVersionText'을 비교할 수 없습니다.")
        val release = fetchLatestRelease(settings.repository)
        val latestVersion = ReleaseVersion.parse(release.tagName)
            ?: return UpdateResult.Failed("최신 릴리스 태그 '${release.tagName}'을 비교할 수 없습니다.")

        val comparison = currentVersion.compareTo(latestVersion)
        if (comparison == 0) return UpdateResult.UpToDate(currentVersionText)
        if (comparison > 0) return UpdateResult.CurrentIsNewer(currentVersionText, release.tagName)

        val asset = selectAsset(release, settings.assetPattern)
        if (asset.size <= 0L) throw UpdateException("릴리스 파일 크기 정보가 올바르지 않습니다.")
        if (asset.size > settings.maxDownloadBytes) {
            throw UpdateException(
                "릴리스 파일이 허용 크기(${settings.maxDownloadBytes / MEBIBYTE} MiB)를 초과합니다.",
            )
        }

        val updateDirectory = plugin.server.updateFolderFile.toPath()
        Files.createDirectories(updateDirectory)
        val target = updateDirectory.resolve(currentJarFileName())
        if (isMatchingPluginJar(target, release.tagName)) {
            return UpdateResult.PendingRestart(release.tagName, target)
        }

        downloadAndVerify(asset, release.tagName, target, settings.maxDownloadBytes)
        return UpdateResult.Downloaded(currentVersionText, release.tagName, target, release.pageUrl)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchLatestRelease(repository: String): Release {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/$repository/releases/latest"))
            .timeout(API_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", "ClassWarPlugin/${plugin.pluginMeta.version}")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        when (response.statusCode()) {
            200 -> Unit
            404 -> throw UpdateException("저장소에 공개된 정식 릴리스가 없습니다.")
            403, 429 -> throw UpdateException("GitHub API 요청 한도에 도달했거나 접근이 거부되었습니다.")
            else -> throw UpdateException("GitHub API가 HTTP ${response.statusCode()} 상태를 반환했습니다.")
        }

        val payload = try {
            gson.fromJson(response.body(), GitHubReleasePayload::class.java)
        } catch (error: JsonParseException) {
            throw UpdateException("GitHub 릴리스 응답을 해석할 수 없습니다.", error)
        }
        if (payload.draft || payload.prerelease) {
            throw UpdateException("GitHub가 정식 릴리스가 아닌 항목을 반환했습니다.")
        }
        val tagName = payload.tagName?.takeIf { it.isNotBlank() }
            ?: throw UpdateException("GitHub 릴리스에 태그가 없습니다.")
        val pageUrl = payload.pageUrl?.takeIf { it.startsWith("https://github.com/") }
            ?: "https://github.com/$repository/releases/latest"
        return Release(tagName, pageUrl, payload.assets.orEmpty())
    }

    private fun selectAsset(release: Release, pattern: Regex): GitHubAssetPayload {
        val candidates = release.assets.filter { asset ->
            asset.state.equals("uploaded", ignoreCase = true) &&
                asset.name?.let(pattern::matches) == true &&
                !asset.name.contains("sources", ignoreCase = true) &&
                !asset.name.contains("javadoc", ignoreCase = true)
        }
        if (candidates.isEmpty()) {
            throw UpdateException("릴리스 ${release.tagName}에서 설정과 일치하는 실행 JAR을 찾지 못했습니다.")
        }
        if (candidates.size == 1) return candidates.single()

        val preferred = candidates.filter { asset ->
            val name = asset.name.orEmpty()
            name.contains("-all", ignoreCase = true) ||
                name.contains("-shadow", ignoreCase = true)
        }
        if (preferred.size == 1) return preferred.single()
        throw UpdateException(
            "릴리스에 일치하는 JAR이 여러 개라 선택할 수 없습니다: " +
                candidates.joinToString { it.name.orEmpty() },
        )
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadAndVerify(
        asset: GitHubAssetPayload,
        releaseVersion: String,
        target: Path,
        maxDownloadBytes: Long,
    ) {
        val downloadUrl = asset.downloadUrl
            ?.let(::validatedGitHubDownloadUri)
            ?: throw UpdateException("릴리스 파일 다운로드 주소가 없습니다.")
        val request = HttpRequest.newBuilder()
            .uri(downloadUrl)
            .timeout(DOWNLOAD_TIMEOUT)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "ClassWarPlugin/${plugin.pluginMeta.version}")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw UpdateException("릴리스 다운로드가 HTTP ${response.statusCode()} 상태로 실패했습니다.")
        }

        val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        if (contentLength > maxDownloadBytes) {
            response.body().close()
            throw UpdateException("다운로드 파일이 설정된 최대 크기를 초과합니다.")
        }

        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".${plugin.name}-", ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            response.body().use { input ->
                Files.newOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > maxDownloadBytes) {
                            throw UpdateException("다운로드 파일이 설정된 최대 크기를 초과합니다.")
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (downloaded != asset.size) {
                throw UpdateException("다운로드 크기가 GitHub 릴리스 정보와 일치하지 않습니다.")
            }
            verifyDigest(asset.digest, digest.digest())
            verifyPluginJar(temporary, releaseVersion)
            moveAtomically(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validatedGitHubDownloadUri(value: String): URI {
        val uri = try {
            URI.create(value)
        } catch (error: IllegalArgumentException) {
            throw UpdateException("릴리스 파일 다운로드 주소가 올바르지 않습니다.", error)
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || !uri.host.equals("github.com", ignoreCase = true)) {
            throw UpdateException("신뢰할 수 없는 릴리스 파일 다운로드 주소입니다.")
        }
        return uri
    }

    private fun verifyDigest(expectedDigest: String?, actualDigest: ByteArray) {
        if (expectedDigest.isNullOrBlank()) return
        val parts = expectedDigest.split(':', limit = 2)
        if (parts.size != 2 || !parts[0].equals("sha256", ignoreCase = true)) {
            throw UpdateException("지원하지 않는 릴리스 다이제스트 형식입니다.")
        }
        val expected = parts[1].lowercase(Locale.ROOT)
        val actual = actualDigest.joinToString("") { byte -> "%02x".format(byte) }
        if (expected != actual) throw UpdateException("다운로드한 JAR의 SHA-256이 GitHub 릴리스와 다릅니다.")
    }

    private fun isMatchingPluginJar(path: Path, releaseVersion: String): Boolean =
        Files.isRegularFile(path) && runCatching {
            verifyPluginJar(path, releaseVersion)
            true
        }.getOrDefault(false)

    private fun verifyPluginJar(path: Path, releaseVersion: String) {
        val description = try {
            JarFile(path.toFile(), true).use { archive ->
                val entry = archive.getJarEntry("plugin.yml")
                    ?: throw UpdateException("다운로드한 JAR에 plugin.yml이 없습니다.")
                val pluginDescription = archive.getInputStream(entry).use(::PluginDescriptionFile)
                val requiredEntries = listOf(
                    "${pluginDescription.main.replace('.', '/')}.class",
                    "org/beobma/classWarPlugin/libs/kotlin/jvm/internal/Intrinsics.class",
                    "org/beobma/classWarPlugin/libs/gson/Gson.class",
                )
                val missingEntries = requiredEntries.filter { archive.getJarEntry(it) == null }
                if (missingEntries.isNotEmpty()) {
                    throw UpdateException("다운로드한 JAR에 필수 런타임 클래스가 없습니다.")
                }
                pluginDescription
            }
        } catch (error: InvalidDescriptionException) {
            throw UpdateException("다운로드한 JAR의 plugin.yml이 올바르지 않습니다.", error)
        } catch (error: SecurityException) {
            throw UpdateException("다운로드한 JAR의 서명 검증에 실패했습니다.", error)
        }

        if (description.name != plugin.pluginMeta.name || description.main != plugin.pluginMeta.mainClass) {
            throw UpdateException("다운로드한 JAR이 ${plugin.pluginMeta.name} 플러그인이 아닙니다.")
        }
        val jarVersion = ReleaseVersion.parse(description.version)
            ?: throw UpdateException("다운로드한 JAR의 버전 '${description.version}'을 비교할 수 없습니다.")
        val tagVersion = ReleaseVersion.parse(releaseVersion)
            ?: throw UpdateException("릴리스 태그 '$releaseVersion'을 비교할 수 없습니다.")
        if (jarVersion.compareTo(tagVersion) != 0) {
            throw UpdateException(
                "다운로드한 JAR 버전(${description.version})이 릴리스 태그($releaseVersion)와 다릅니다.",
            )
        }
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun currentJarFileName(): String {
        val source = plugin.javaClass.protectionDomain.codeSource?.location
        val sourceFile = source?.takeIf { it.protocol == "file" }
            ?.let { runCatching { Path.of(it.toURI()).fileName?.toString() }.getOrNull() }
        return sourceFile?.takeIf { it.endsWith(".jar", ignoreCase = true) } ?: "${plugin.name}.jar"
    }

    sealed interface UpdateResult {
        data class UpToDate(val currentVersion: String) : UpdateResult
        data class CurrentIsNewer(val currentVersion: String, val latestVersion: String) : UpdateResult
        data class PendingRestart(val latestVersion: String, val target: Path) : UpdateResult
        data class Downloaded(
            val currentVersion: String,
            val latestVersion: String,
            val target: Path,
            val releasePage: String,
        ) : UpdateResult

        data class Failed(val reason: String) : UpdateResult
        data object InProgress : UpdateResult
    }

    private data class Settings(
        val enabled: Boolean,
        val repository: String,
        val assetPattern: Regex,
        val startupDelaySeconds: Int,
        val checkIntervalHours: Double,
        val maxDownloadBytes: Long,
    ) {
        companion object {
            private val REPOSITORY_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

            fun from(plugin: ClassWarPlugin): Settings {
                val config = plugin.config
                val repository = config.getString("auto-update.repository", DEFAULT_REPOSITORY)
                    ?.trim()
                    .orEmpty()
                require(REPOSITORY_PATTERN.matches(repository)) {
                    "auto-update.repository는 '소유자/저장소' 형식이어야 합니다."
                }
                val patternText = config.getString("auto-update.asset-pattern", DEFAULT_ASSET_PATTERN)
                    ?: DEFAULT_ASSET_PATTERN
                val assetPattern = try {
                    Regex(patternText)
                } catch (error: PatternSyntaxException) {
                    throw IllegalArgumentException("auto-update.asset-pattern이 올바른 정규식이 아닙니다.", error)
                }
                val maximumMiB = config.getLong("auto-update.max-download-size-mib", 64L).coerceIn(1L, 1_024L)
                return Settings(
                    enabled = config.getBoolean("auto-update.enabled", true),
                    repository = repository,
                    assetPattern = assetPattern,
                    startupDelaySeconds = config.getInt("auto-update.startup-delay-seconds", 10).coerceAtLeast(0),
                    checkIntervalHours = config.getDouble("auto-update.check-interval-hours", 6.0).coerceAtLeast(0.0),
                    maxDownloadBytes = Math.multiplyExact(maximumMiB, MEBIBYTE),
                )
            }
        }
    }

    private data class Release(
        val tagName: String,
        val pageUrl: String,
        val assets: List<GitHubAssetPayload>,
    )

    private data class GitHubReleasePayload(
        @Suppress("PropertyName") val tag_name: String? = null,
        @Suppress("PropertyName") val html_url: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAssetPayload>? = null,
    ) {
        val tagName: String? get() = tag_name
        val pageUrl: String? get() = html_url
    }

    private data class GitHubAssetPayload(
        val name: String? = null,
        val state: String? = null,
        val size: Long = -1L,
        val digest: String? = null,
        @Suppress("PropertyName") val browser_download_url: String? = null,
    ) {
        val downloadUrl: String? get() = browser_download_url
    }

    private class UpdateException(message: String, cause: Throwable? = null) : IOException(message, cause)

    companion object {
        private const val DEFAULT_REPOSITORY = "BEOBMA/ClassWarPlugin"
        private const val DEFAULT_ASSET_PATTERN = "(?i)^ClassWarPlugin(?:-[0-9A-Za-z._-]+)?\\.jar$"
        private const val GITHUB_API_VERSION = "2026-03-10"
        private const val MEBIBYTE = 1_048_576L
        private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
        private val API_TIMEOUT = Duration.ofSeconds(20)
        private val DOWNLOAD_TIMEOUT = Duration.ofMinutes(2)

        private fun secondsToTicks(seconds: Long): Long = seconds.coerceAtMost(Long.MAX_VALUE / 20L) * 20L

        private fun hoursToTicks(hours: Double): Long {
            if (!hours.isFinite() || hours <= 0.0) return 0L
            return (hours * 60.0 * 60.0 * 20.0).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
        }
    }
}
