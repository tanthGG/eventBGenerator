package app;

import java.util.Map;

/** Provides canonical instantiated names for known patterns. */
public final class PatternNames {
  private static final Map<String, String> INSTANTIATED_NAMES = Map.of(
      "PPacket", "MPacket",
      "PSend", "MSend",
      "PReceive", "MReceive",
      "PNDBuffer", "MWaitingBuffer",
      "PDestBuffer", "MDestBuffer");

  private PatternNames() {}

  public static String instantiate(String original) {
    if (original == null) return null;
    String trimmed = original.trim();
    if (trimmed.isEmpty()) return trimmed;
    return INSTANTIATED_NAMES.getOrDefault(trimmed, trimmed);
  }
}
