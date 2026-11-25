package dev.privalo.radiantengine.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.text.Lang;
import dev.privalo.radiantengine.text.MiniLegacy;
import dev.privalo.radiantengine.util.EntityCompat;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Asynchronous update checker for GitHub Releases and SpigotMC (through the Spiget API).
 *
 * <p>Every network call runs on the HttpClient executor with a 5 second timeout. Any failure (no internet, private repo,
 * 404, rate limit, malformed JSON...) is ignored silently: nothing is printed and the server keeps running.</p>
 */
public final class UpdateChecker implements Listener {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final String ADMIN_PERMISSION = "radiantengine.admin";
    private static final String DEFAULT_REPO = "TTVPrivalo21/RadiantEngine";
    private static final String DEFAULT_AUTHOR_URL = "https://www.spigotmc.org/resources/authors/thekillerv2.1619961/";

    /** Result of a successful check. */
    public record Release(String version, String url) {
    }

    private final RadiantEngine plugin;
    private final String currentVersion;
    private HttpClient client;
    private volatile Release available;

    public UpdateChecker(RadiantEngine plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /** Newer release found by the last check, or {@code null}. */
    public Release available() {
        return available;
    }

    /** Starts an asynchronous check when {@code update-checker.enabled} is true. Never blocks and never throws. */
    public void checkAsync() {
        try {
            FileConfiguration config = plugin.getConfig();
            if (!config.getBoolean("update-checker.enabled", true)) {
                return;
            }
            String source = config.getString("update-checker.source", "github").trim().toLowerCase(Locale.ROOT);
            CompletableFuture<Release> future = switch (source) {
                case "spigot", "spigotmc", "spiget" -> fetchSpigot(config);
                default -> fetchGithub(config);
            };
            if (future == null) {
                return;
            }
            future.thenAccept(release -> {
                if (release == null || !isNewer(release.version(), currentVersion)) {
                    return;
                }
                if (!plugin.isEnabled()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    available = release;
                    announceConsole(release);
                });
            }).exceptionally(error -> null);
        } catch (Throwable ignored) {
            // the update check must never affect the plugin
        }
    }

    /** Stops in-flight requests (called on disable). */
    public void shutdown() {
        HttpClient current = client;
        client = null;
        if (current != null) {
            try {
                current.shutdownNow();
            } catch (Throwable ignored) {
                // nothing else to release
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Release release = available;
        if (release == null || !plugin.getConfig().getBoolean("update-checker.notify-admins-on-join", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                notifyPlayer(player, release);
            }
        }, 40L);
    }

    // ── sources ──

    private CompletableFuture<Release> fetchGithub(FileConfiguration config) {
        String repo = config.getString("update-checker.github-repo", DEFAULT_REPO).trim();
        if (!REPO.matcher(repo).matches()) {
            return null;
        }
        String fallbackUrl = "https://github.com/" + repo + "/releases/latest";
        return get("https://api.github.com/repos/" + repo + "/releases/latest", "application/vnd.github+json")
                .thenApply(body -> {
                    JsonObject json = parseObject(body);
                    if (json == null) {
                        return null;
                    }
                    String tag = string(json, "tag_name");
                    if (tag == null) {
                        return null;
                    }
                    String url = string(json, "html_url");
                    return new Release(normalize(tag), safeUrl(url, fallbackUrl));
                });
    }

    private CompletableFuture<Release> fetchSpigot(FileConfiguration config) {
        int resourceId = config.getInt("update-checker.spigot-resource-id", 0);
        if (resourceId <= 0) {
            return null;
        }
        String authorUrl = safeUrl(config.getString("update-checker.spigot-author-url", DEFAULT_AUTHOR_URL), DEFAULT_AUTHOR_URL);
        String resourceUrl = "https://www.spigotmc.org/resources/" + resourceId + "/";
        return get("https://api.spiget.org/v2/resources/" + resourceId + "/versions/latest", "application/json")
                .thenApply(body -> {
                    JsonObject json = parseObject(body);
                    String name = json == null ? null : string(json, "name");
                    if (name == null) {
                        return null;
                    }
                    return new Release(normalize(name), safeUrl(resourceUrl, authorUrl));
                });
    }

    private CompletableFuture<String> get(String url, String accept) {
        HttpClient http = client();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "RadiantEngine/" + currentVersion + " (UpdateChecker)")
                .header("Accept", accept)
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 ? response.body() : null)
                .exceptionally(error -> null);
    }

    private HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            current = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            client = current;
        }
        return current;
    }

    // ── output ──

    private void announceConsole(Release release) {
        Lang lang = plugin.getLang();
        CommandSender console = Bukkit.getConsoleSender();
        for (String line : lang.list("update-console")) {
            String withPrefix = line.replace("{prefix}", lang.prefix());
            Lang.sendRaw(console, Lang.apply(withPrefix, "current", currentVersion,
                    "latest", release.version(), "url", release.url()));
        }
    }

    private void notifyPlayer(Player player, Release release) {
        Lang lang = plugin.getLang();
        lang.send(player, "update-join", "current", currentVersion, "latest", release.version());
        String label = lang.format("update-join-link", "url", release.url());
        if (EntityCompat.isPaper()) {
            String hover = lang.format("update-join-hover", "url", release.url());
            lang.sendText(player, "<click:open_url:'" + release.url() + "'><hover:show_text:'" + hover.replace("'", "\\'")
                    + "'>" + label + "</hover></click>");
            return;
        }
        try {
            List<BaseComponent> parts = new ArrayList<>(List.of(TextComponent.fromLegacyText(MiniLegacy.toLegacy(lang.prefix() + label))));
            ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, release.url());
            for (BaseComponent part : parts) {
                part.setClickEvent(click);
            }
            player.spigot().sendMessage(parts.toArray(new BaseComponent[0]));
        } catch (Throwable error) {
            lang.sendText(player, label + " <gray>" + Lang.escape(release.url()));
        }
    }

    // ── helpers ──

    private static JsonObject parseObject(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    /** Only plain http(s) URLs without characters that could break the MiniMessage click tag. */
    static String safeUrl(String url, String fallback) {
        if (url == null) {
            return fallback;
        }
        String trimmed = url.trim();
        if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://"))
                || trimmed.chars().anyMatch(c -> c <= ' ' || c == '\'' || c == '"' || c == '<' || c == '>' || c == '\\')) {
            return fallback;
        }
        return trimmed;
    }

    /** Removes a leading "v"/"V" (and surrounding spaces) from a tag such as {@code v1.2.0}. */
    public static String normalize(String version) {
        String out = version == null ? "" : version.trim();
        if (out.length() > 1 && (out.charAt(0) == 'v' || out.charAt(0) == 'V') && Character.isDigit(out.charAt(1))) {
            out = out.substring(1);
        }
        return out;
    }

    /**
     * Semantic comparison: numeric parts are compared one by one (missing parts count as 0); when they are equal, a
     * release without suffix ({@code 1.2.0}) is newer than a pre-release of the same numbers ({@code 1.2.0-SNAPSHOT}).
     *
     * @return true when {@code remote} is strictly newer than {@code current}
     */
    public static boolean isNewer(String remote, String current) {
        return compare(normalize(remote), normalize(current)) > 0;
    }

    public static int compare(String a, String b) {
        int[] na = numbers(a);
        int[] nb = numbers(b);
        int length = Math.max(na.length, nb.length);
        for (int i = 0; i < length; i++) {
            int x = i < na.length ? na[i] : 0;
            int y = i < nb.length ? nb[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        boolean preA = hasSuffix(a);
        boolean preB = hasSuffix(b);
        if (preA != preB) {
            return preA ? -1 : 1;
        }
        return 0;
    }

    private static String core(String version) {
        int cut = version.length();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (c == '-' || c == '+' || c == ' ') {
                cut = i;
                break;
            }
        }
        return version.substring(0, cut);
    }

    private static boolean hasSuffix(String version) {
        String core = core(version);
        if (core.length() < version.length() && version.charAt(core.length()) != '+') {
            return true;
        }
        for (String part : core.split("\\.")) {
            if (!part.isEmpty() && !Character.isDigit(part.charAt(part.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private static int[] numbers(String version) {
        String[] parts = core(version).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int end = 0;
            while (end < part.length() && Character.isDigit(part.charAt(end))) {
                end++;
            }
            try {
                out[i] = end == 0 ? 0 : Integer.parseInt(part.substring(0, Math.min(end, 9)));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
