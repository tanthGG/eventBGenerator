package app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encodes domain-specific rules describing how individual pattern events should be
 * combined into composite events when multiple patterns are selected together.
 */
public final class PatternCombinationEngine {

  private static final List<Rule> RULES = List.of(
      rule(
          "creating_Pkt",
          ref("PPacket", "creating_Pkt"),
          ref("PNDBuffer", "record_ndBuff")),
      rule(
          "start_tx",
          ref("PSend", "start_tx"),
          ref("PNDBuffer", "remove_ndBuff"),
          ref("PPacket", "set_pktFwdr")),
      rule(
          "receive",
          ref("PReceive", "receive"),
          ref("PSend", "remove_ctlNeighbours")),
      rule(
          "fwdr_receive_pkts",
          ref("PReceive", "fwdr_receive_pkts"),
          ref("PNDBuffer", "record_ndBuff")),
      rule(
          "dest_recv_pkts",
          ref("PReceive", "dest_recv_pkts"),
          ref("PDestBuffer", "record_destBuff")),
      rule(
          "finish_tx_pkts",
          ref("PSend", "finish_tx_pkts"),
          ref("PNDBuffer", "is_In_Range_ndBuff")),
      rule(
          "final_tx_pkts",
          ref("PSend", "final_tx_pkts"),
          ref("PNDBuffer", "isNot_In_Range_ndBuff"))
  );

  /**
   * Applies the combination rules to the supplied list of events. When a rule matches,
   * the referenced events are merged into a new composite event that replaces the
   * originals. If a rule cannot be fulfilled (e.g. because one of its constituent
   * patterns is missing) it is ignored gracefully.
   */
  public List<PatternModel.Event> apply(List<PatternModel.Event> events) {
    if (events == null || events.isEmpty()) {
      return events == null ? List.of() : events;
    }

    Map<EventKey, PatternModel.Event> lookup = new LinkedHashMap<>();
    for (PatternModel.Event event : events) {
      EventKey key = key(event);
      if (key != null) {
        lookup.putIfAbsent(key, event);
      }
    }

    Set<EventKey> consumed = new LinkedHashSet<>();
    List<PatternModel.Event> composed = new ArrayList<>();

    for (Rule rule : RULES) {
      List<PatternModel.Event> matches = new ArrayList<>();
      for (EventRef ref : rule.refs()) {
        PatternModel.Event evt = lookup.get(ref.key());
        if (evt == null) {
          matches = null;
          break;
        }
        matches.add(evt);
      }
      if (matches == null || matches.isEmpty()) {
        continue;
      }
      composed.add(merge(rule.outputName(), matches));
      for (PatternModel.Event evt : matches) {
        EventKey k = key(evt);
        if (k != null) consumed.add(k);
      }
    }

    for (PatternModel.Event evt : events) {
      EventKey k = key(evt);
      if (k == null || !consumed.contains(k)) {
        composed.add(evt);
      }
    }

    return composed;
  }

  private PatternModel.Event merge(String outputName, List<PatternModel.Event> sources) {
    PatternModel.Event merged = new PatternModel.Event();
    merged.name = outputName != null && !outputName.isBlank()
        ? outputName
        : sources.get(0).name;

    merged.sourcePattern = sources.stream()
        .map(e -> e.sourcePattern == null ? "" : e.sourcePattern.trim())
        .filter(s -> !s.isEmpty())
        .distinct()
        .collect(Collectors.joining("+"));
    if (merged.sourcePattern == null || merged.sourcePattern.isEmpty()) {
      merged.sourcePattern = "Composite";
    }

    LinkedHashMap<String, PatternModel.Param> params = new LinkedHashMap<>();
    for (PatternModel.Event evt : sources) {
      for (PatternModel.Param param : evt.params) {
        if (param == null || param.name == null) continue;
        String name = param.name.trim();
        if (name.isEmpty()) continue;
        PatternModel.Param existing = params.get(name);
        String type = param.type != null ? param.type.trim() : null;
        if (existing == null) {
          PatternModel.Param copy = new PatternModel.Param();
          copy.name = name;
          copy.type = type;
          params.put(name, copy);
        } else if (type != null && !type.isEmpty()) {
          existing.type = reconcileType(existing.type, type);
        }
      }
    }
    merged.params.addAll(params.values());

    LinkedHashSet<String> guardExprs = new LinkedHashSet<>();
    for (PatternModel.Event evt : sources) {
      for (PatternModel.Guard guard : evt.guards) {
        if (guard == null || guard.expr == null) continue;
        String expr = guard.expr.trim();
        if (expr.isEmpty() || !guardExprs.add(expr)) continue;
        PatternModel.Guard copy = new PatternModel.Guard();
        copy.expr = expr;
        merged.guards.add(copy);
      }
    }

    LinkedHashSet<String> actionExprs = new LinkedHashSet<>();
    for (PatternModel.Event evt : sources) {
      for (PatternModel.Action action : evt.actions) {
        if (action == null || action.assignment == null) continue;
        String assignment = action.assignment.trim();
        if (assignment.isEmpty() || !actionExprs.add(assignment)) continue;
        PatternModel.Action copy = new PatternModel.Action();
        copy.assignment = assignment;
        merged.actions.add(copy);
      }
    }

    return merged;
  }

  private static EventKey key(PatternModel.Event event) {
    if (event == null) return null;
    String pattern = normalize(event.sourcePattern);
    String name = normalize(event.name);
    if (pattern == null || name == null) return null;
    return new EventKey(pattern, name);
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.toLowerCase(Locale.ROOT);
  }

  private static Rule rule(String name, EventRef... refs) {
    return new Rule(name, List.of(refs));
  }

  private static EventRef ref(String pattern, String event) {
    return new EventRef(pattern, event);
  }

  private static String reconcileType(String current, String incoming) {
    String candidate = incoming == null ? null : incoming.trim();
    if (candidate == null || candidate.isEmpty()) {
      return current;
    }
    String existing = current == null ? null : current.trim();
    if (existing == null || existing.isEmpty()) {
      return candidate;
    }
    if (Objects.equals(existing, candidate)) {
      return existing;
    }

    String normExisting = existing.replaceAll("\\s+", "");
    String normCandidate = candidate.replaceAll("\\s+", "");

    if (normCandidate.equalsIgnoreCase(normExisting)) {
      return existing;
    }
    if (normCandidate.contains(normExisting)) {
      return candidate;
    }
    if (normExisting.contains(normCandidate)) {
      return existing;
    }

    // Prefer the more descriptive type (heuristic: longer textual representation).
    return candidate.length() >= existing.length() ? candidate : existing;
  }

  private record EventKey(String pattern, String event) {}

  private record EventRef(String pattern, String event) {
    private EventKey key() {
      return new EventKey(
          Objects.requireNonNull(pattern).trim().toLowerCase(Locale.ROOT),
          Objects.requireNonNull(event).trim().toLowerCase(Locale.ROOT));
    }
  }

  private record Rule(String outputName, List<EventRef> refs) {}
}
