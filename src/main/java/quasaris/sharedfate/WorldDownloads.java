package quasaris.sharedfate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

public final class WorldDownloads {
    private static final Logger LOG = LoggerFactory.getLogger("SharedFate");
    private static final String INDEX = "index.txt";
    private static final String PREFIX = "/worlds/";
    private static HttpServer http;

    public record Entry(String file, String label) {}

    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("shared-fate-downloads");
    }

    public static String url() {
        return "http://" + SharedFateConfig.getPublicAddress() + ":" + SharedFateConfig.getDownloadPort() + "/worlds";
    }

    public static Component linkComponent(String url) {
        return Component.literal(url).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> start());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!entries().isEmpty()) {
                handler.getPlayer().sendSystemMessage(
                        Component.literal("Скачать сохранённые миры: ").append(linkComponent(url())), false);
            }
        });
    }

    private static void start() {
        if (http != null) {
            return;
        }
        try {
            http = HttpServer.create(new InetSocketAddress(SharedFateConfig.getDownloadPort()), 0);
            http.createContext("/worlds", WorldDownloads::handle);
            http.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "sharedfate-download");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            LOG.info("World download page: {}", url());
        } catch (IOException e) {
            LOG.warn("Could not start download page on port {}", SharedFateConfig.getDownloadPort(), e);
            http = null;
        }
    }

    private static void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    public static void archive(Path worldDir, String label) throws IOException {
        Files.createDirectories(dir());
        String name = "world-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
        Path zip = dir().resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> walk = Files.walk(worldDir)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String rel = worldDir.relativize(p).toString().replace('\\', '/');
                if (rel.equals("session.lock")) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
        Files.writeString(dir().resolve(INDEX), name + "\t" + clean(label) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        LOG.info("World archived for download: {}", name);
    }

    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        Path index = dir().resolve(INDEX);
        if (!Files.exists(index)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 2);
                if (parts.length == 2 && Files.exists(dir().resolve(parts[0]))) {
                    out.add(new Entry(parts[0], parts[1]));
                }
            }
        } catch (IOException ignored) {
        }
        Collections.reverse(out);
        return out;
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path.equals("/worlds") || path.equals("/worlds/")) {
                byte[] html = page().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, html.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(html);
                }
                return;
            }
            if (!path.startsWith(PREFIX)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            String name = path.substring(PREFIX.length());
            boolean listed = entries().stream().anyMatch(e -> e.file().equals(name));
            Path file = dir().resolve(name);
            if (!listed || !Files.isRegularFile(file)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
            ex.sendResponseHeaders(200, Files.size(file));
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(file, os);
            }
        } finally {
            ex.close();
        }
    }

    private static String page() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Миры сервера</title></head><body><ul>");
        for (Entry e : entries()) {
            sb.append("<li><a href=\"").append(PREFIX).append(e.file()).append("\">")
                    .append(escape(e.label())).append("</a></li>");
        }
        if (entries().isEmpty()) {
            sb.append("<li>Пока нет сохранённых миров</li>");
        }
        return sb.append("</ul></body></html>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private WorldDownloads() {}
}
