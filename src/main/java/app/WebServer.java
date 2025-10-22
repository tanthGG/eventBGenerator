package app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Simple embedded HTTP server providing a UI for composing pattern bundles. */
public class WebServer {

  private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]+)\"");
  private static final Pattern PROJECT_NAME =
      Pattern.compile("\"projectName\"\\s*:\\s*\"([^\"]+)\"");

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
    GenerateRequest request = parseGenerateRequest(body);
    List<List<String>> refinements = request.refinements();
    if (refinements.isEmpty()) {
      send(exchange, 400, "No refinements provided", "text/plain");
      return;
    }

    Path workspace = generationService.workspaceRoot();
    if (workspace == null) {
      send(exchange, 500, "Workspace not configured", "text/plain");
      return;
    }

    String projectName = sanitizeProjectName(request.projectName());
    if (projectName.isBlank()) {
      projectName = defaultProjectName();
    }

    List<Path> generatedFilePaths = new ArrayList<>();
    List<String> fileSummaries = new ArrayList<>();
    int refinementIndex = 1;

    for (List<String> fileNames : refinements) {
      if (fileNames == null || fileNames.isEmpty()) {
        send(exchange, 400, "Each refinement must include at least one pattern", "text/plain");
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
        ir = generationService.compose(patternPaths, refinementIndex);
      } catch (Exception e) {
        send(exchange, 500, "Failed to generate: " + e.getMessage(), "text/plain");
        return;
      }

      Path machineDir;
      try {
        machineDir = generationService.writeToProject(projectName, ir);
      } catch (IOException e) {
        send(exchange, 500, "Failed to write files: " + e.getMessage(), "text/plain");
        return;
      }

      Path ctxPath = machineDir.resolve(ir.ctxName() + ".ctx");
      Path machPath = machineDir.resolve(ir.machName() + ".bcm");
      generatedFilePaths.add(ctxPath);
      generatedFilePaths.add(machPath);
      fileSummaries.add(relativizeForResponse(workspace, ctxPath));
      fileSummaries.add(relativizeForResponse(workspace, machPath));

      refinementIndex++;
    }

    Path projectDir = workspace.resolve(projectName);
    String projectPath = relativizeForResponse(projectRoot, projectDir);
    if (projectPath.isEmpty()) {
      projectPath = relativizeForResponse(workspace, projectDir);
    }
    byte[] archive;
    try {
      archive = zipGeneratedFiles(projectName, generatedFilePaths, workspace);
    } catch (IOException e) {
      send(exchange, 500, "Failed to assemble download: " + e.getMessage(), "text/plain");
      return;
    }

    String downloadName = projectName.isBlank() ? "eventb-artifacts.zip" : projectName + ".zip";
    String safeProjectPath = sanitizeHeaderValue(projectPath);
    String safeProjectName = sanitizeHeaderValue(projectName);
    String filesHeader = fileSummaries.stream()
        .map(this::sanitizeHeaderValue)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining(";"));

    exchange.getResponseHeaders().set("Content-Type", "application/zip");
    exchange.getResponseHeaders().set("Content-Disposition",
        "attachment; filename=\"" + downloadName + "\"");
    if (!safeProjectPath.isEmpty()) {
      exchange.getResponseHeaders().set("X-Project-Path", safeProjectPath);
    }
    if (!safeProjectName.isEmpty()) {
      exchange.getResponseHeaders().set("X-Project-Name", safeProjectName);
    }
    if (!filesHeader.isEmpty()) {
      exchange.getResponseHeaders().set("X-Generated-Files", filesHeader);
    }

    exchange.sendResponseHeaders(200, archive.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(archive);
    }
  }

  private String defaultProjectName() {
    return "web-session-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .format(LocalDateTime.now());
  }

  private String relativizeForResponse(Path root, Path target) {
    if (target == null) return "";
    Path absoluteTarget = target.toAbsolutePath().normalize();
    if (root != null) {
      Path absoluteRoot = root.toAbsolutePath().normalize();
      try {
        if (absoluteTarget.startsWith(absoluteRoot)) {
          return absoluteRoot.relativize(absoluteTarget).toString().replace('\\', '/');
        }
      } catch (IllegalArgumentException ignored) {
        // fall through to absolute representation
      }
    }
    return absoluteTarget.toString().replace('\\', '/');
  }

  private byte[] zipGeneratedFiles(String projectName, List<Path> generatedFiles, Path workspace)
      throws IOException {
    String root = projectName.isBlank() ? "eventb-artifacts" : projectName;
    root = root.replaceAll("[/\\\\]+", "-");
    if (root.isBlank()) root = "eventb-artifacts";
    if (!root.endsWith("/")) root = root + "/";

    Path absWorkspace = workspace != null ? workspace.toAbsolutePath().normalize() : null;

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
      Set<String> addedDirs = new HashSet<>();
      zip.putNextEntry(new ZipEntry(root));
      zip.closeEntry();
      addedDirs.add(root);

      for (Path file : generatedFiles) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
          continue;
        }
        String relative = normalized.getFileName().toString();
        if (absWorkspace != null && normalized.startsWith(absWorkspace)) {
          relative = absWorkspace.relativize(normalized).toString().replace('\\', '/');
        }
        String entryName = root + relative;
        ensureDirectoryEntries(zip, addedDirs, entryName);
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = Files.newInputStream(normalized)) {
          in.transferTo(zip);
        }
        zip.closeEntry();
      }

      zip.finish();
      return baos.toByteArray();
    }
  }

  private void ensureDirectoryEntries(ZipOutputStream zip, Set<String> addedDirs, String entryName)
      throws IOException {
    int index = entryName.lastIndexOf('/');
    while (index > 0) {
      String dir = entryName.substring(0, index + 1);
      if (addedDirs.add(dir)) {
        zip.putNextEntry(new ZipEntry(dir));
        zip.closeEntry();
      }
      index = dir.lastIndexOf('/', dir.length() - 2);
    }
  }

  private String sanitizeHeaderValue(String value) {
    if (value == null) return "";
    String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
    return sanitized;
  }

  private String sanitizeProjectName(String candidate) {
    if (candidate == null) return "";
    String normalized = candidate.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
    normalized = normalized.replaceAll("-{2,}", "-");
    normalized = normalized.replaceAll("(^-+|-+$)", "");
    return normalized;
  }

  private GenerateRequest parseGenerateRequest(String body) {
    String projectName = null;
    Matcher nameMatcher = PROJECT_NAME.matcher(body);
    if (nameMatcher.find()) {
      projectName = nameMatcher.group(1).trim();
    }
    List<List<String>> refinements = parseRefinementGroups(body);
    return new GenerateRequest(projectName, refinements);
  }

  private List<List<String>> parseRefinementGroups(String body) {
    List<List<String>> refinements = new ArrayList<>();
    int keyIndex = body.indexOf("\"refinements\"");
    if (keyIndex < 0) return refinements;
    int arrayStart = body.indexOf('[', keyIndex);
    if (arrayStart < 0) return refinements;
    int arrayEnd = findMatchingBracket(body, arrayStart);
    if (arrayEnd < 0) return refinements;
    String inner = body.substring(arrayStart + 1, arrayEnd);
    int pos = 0;
    while (pos < inner.length()) {
      int open = inner.indexOf('[', pos);
      if (open < 0) break;
      int close = findMatchingBracket(inner, open);
      if (close < 0) break;
      String segment = inner.substring(open + 1, close);
      List<String> patterns = new ArrayList<>();
      Matcher matcher = QUOTED_VALUE.matcher(segment);
      while (matcher.find()) {
        String value = matcher.group(1).trim();
        if (!value.isEmpty()) patterns.add(value);
      }
      refinements.add(patterns);
      pos = close + 1;
    }
    return refinements;
  }

  private static int findMatchingBracket(String text, int start) {
    int depth = 0;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '[') {
        depth++;
      } else if (c == ']') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
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

  private record GenerateRequest(String projectName, List<List<String>> refinements) {}
}
