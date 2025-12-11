package app;

import java.util.Map;

/** Provides canonical instantiated names for known patterns. */
public final class PatternNames {
  private static final Map<String, String> INSTANTIATED_NAMES = Map.ofEntries(
      Map.entry("PPacket", "MPacket"),
      Map.entry("IPacket", "MPacket"),
      Map.entry("PSend", "MSend"),
      Map.entry("ISend", "MSend"),
      Map.entry("PReceive", "MReceive"),
      Map.entry("IReceive", "MReceive"),
      Map.entry("PNDBuffer", "MNDBuffer"),
      Map.entry("INDBuffer", "MNDBuffer"),
      Map.entry("PDestBuffer", "MDestBuffer"),
      Map.entry("IDestBuffer", "MDestBuffer"),
      Map.entry("PSensingUnit", "MSensingUnit"),
      Map.entry("ISensingUnit", "MSensingUnit"));

  private PatternNames() {}

  public static String instantiate(String original) {
    if (original == null) return null;
    String trimmed = original.trim();
    if (trimmed.isEmpty()) return trimmed;
    return INSTANTIATED_NAMES.getOrDefault(trimmed, trimmed);
  }
}
