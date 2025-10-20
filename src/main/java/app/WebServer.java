package app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Simple embedded HTTP server providing a UI for composing pattern bundles. */
public class WebServer {

  private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]+)\"");

  private final GenerationService generationService;
  private final Path projectRoot;
  private final Path nodeStructureDir;

  public WebServer(Path projectRoot, GenerationService generationService) {
    this.projectRoot = projectRoot;
    this.generationService = generationService;
    this.nodeStructureDir = projectRoot.resolve("node_Structure");
  }

  public void start(int port) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", new StaticHandler("web/index.html", "text/html"));
    server.createContext("/static/app.js", new StaticHandler("web/app.js", "text/javascript"));
    server.createContext("/static/style.css", new StaticHandler("web/style.css", "text/css"));
    server.createContext("/api/patterns", this::handleListPatterns);
    server.createContext("/api/generate", this::handleGenerate);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
  }

  private void handleListPatterns(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      send(exchange, 405, "Method Not Allowed", "text/plain");
      return;
    }
    if (!Files.isDirectory(nodeStructureDir)) {
      send(exchange, 200, "[]", "application/json");
      return;
    }
    var builder = new StringBuilder();
    builder.append("[");
    boolean first = true;
    try (var stream = Files.list(nodeStructureDir)) {
      for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList()) {
        if (!first) builder.append(",");
        builder.append("\"").append(path.getFileName().toString()).append("\"");
        first = false;
      }
    }
    builder.append("]");
    send(exchange, 200, builder.toString(), "application/json");
  }

  private void handleGenerate(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      send(exchange, 405, "Method Not Allowed", "text/plain");
      return;
    }
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    List<String> fileNames = extractQuotedValues(body);
    if (fileNames.isEmpty()) {
      send(exchange, 400, "No patterns selected", "text/plain");
      return;
    }

    List<Path> patternPaths = new ArrayList<>();
    for (String fileName : fileNames) {
      Path path = nodeStructureDir.resolve(fileName).normalize();
      if (!path.startsWith(nodeStructureDir) || !Files.exists(path)) {
        send(exchange, 404, "Pattern not found: " + fileName, "text/plain");
        return;
      }
      patternPaths.add(path);
    }

    EventBIR ir;
    try {
      ir = generationService.compose(patternPaths);
    } catch (Exception e) {
      send(exchange, 500, "Failed to generate: " + e.getMessage(), "text/plain");
      return;
    }

    byte[] zipData = toZip(ir);
    exchange.getResponseHeaders().set("Content-Type", "application/zip");
    exchange.getResponseHeaders().set("Content-Disposition",
        "attachment; filename=\"eventb-pattern.zip\"");
    exchange.sendResponseHeaders(200, zipData.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(zipData);
    }
  }

  private byte[] toZip(EventBIR ir) throws IOException {
    byte[] ctxBytes = ir.ctxText().getBytes(StandardCharsets.UTF_8);
    byte[] machineBytes = ir.machineText().getBytes(StandardCharsets.UTF_8);
    try (var baos = new java.io.ByteArrayOutputStream();
         var zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("Generated_Composite_C0.ctx"));
      zip.write(ctxBytes);
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("Generated_Composite_M0.bcm"));
      zip.write(machineBytes);
      zip.closeEntry();
      zip.finish();
      return baos.toByteArray();
    }
  }

  private List<String> extractQuotedValues(String body) {
    List<String> values = new ArrayList<>();
    int start = body.indexOf('[');
    int end = body.indexOf(']', start >= 0 ? start : 0);
    if (start < 0 || end < 0 || end <= start) {
      return values;
    }
    String inside = body.substring(start + 1, end);
    Matcher matcher = QUOTED_VALUE.matcher(inside);
    while (matcher.find()) {
      String value = matcher.group(1).trim();
      if (!value.isEmpty()) values.add(value);
    }
    return values;
  }

  private void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static class StaticHandler implements HttpHandler {
    private final String resourcePath;
    private final String contentType;

    StaticHandler(String resourcePath, String contentType) {
      this.resourcePath = resourcePath;
      this.contentType = contentType;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
        if (in == null) {
          exchange.sendResponseHeaders(404, -1);
          return;
        }
        byte[] data = in.readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
          os.write(data);
        }
      }
    }
  }
}
