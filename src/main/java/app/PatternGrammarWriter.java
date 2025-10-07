package app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes the XML grammar (BNF) alongside generated Event-B artifacts. */
public class PatternGrammarWriter {
  private static final String RESOURCE_PATH = "/patternGrammar.bnf";
  private static final String OUTPUT_DIR = "bnf";
  private static final String OUTPUT_FILE = "PatternBundleGrammar.bnf";

  public void write(Path projectDir) throws IOException {
    Path targetDir = projectDir.resolve(OUTPUT_DIR);
    Files.createDirectories(targetDir);
    Path targetFile = targetDir.resolve(OUTPUT_FILE);

    try (InputStream in = PatternGrammarWriter.class.getResourceAsStream(RESOURCE_PATH)) {
      if (in == null) {
        throw new IOException("Missing grammar resource: " + RESOURCE_PATH);
      }
      Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
