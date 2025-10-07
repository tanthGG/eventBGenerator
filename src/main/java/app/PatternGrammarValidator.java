package app;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates Pattern Bundle XML documents against the BNF structure. */
public class PatternGrammarValidator {

  private static final String NAME_PATTERN_SRC = "[A-Za-z_:][A-Za-z0-9_\\-.:]*";
  private static final Pattern NAME_PATTERN = Pattern.compile(NAME_PATTERN_SRC);
  private static final Pattern NAME_LIST_PATTERN =
      Pattern.compile("\\s*" + NAME_PATTERN_SRC + "(\\s*,\\s*" + NAME_PATTERN_SRC + ")*\\s*");

  public void validateBundle(Element root) {
    requireTag(root, "PatternBundle");
    String bundleName = requireAttr(root, "name", "PatternBundle name");
    requireName(bundleName, "PatternBundle name");

    List<Element> contextElements = new ArrayList<>();
    Element patternElement = null;

    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (!(node instanceof Element el)) continue;
      String tag = el.getTagName();
      switch (tag) {
        case "Context" -> contextElements.add(el);
        case "Pattern" -> {
          if (patternElement != null) {
            throw new IllegalArgumentException("PatternBundle must contain exactly one <Pattern>");
          }
          patternElement = el;
        }
        default -> throw new IllegalArgumentException(
            "Unexpected element <" + tag + "> in <PatternBundle>");
      }
    }

    if (patternElement == null) {
      throw new IllegalArgumentException("PatternBundle is missing a <Pattern> element");
    }

    Set<String> contextNames = new HashSet<>();
    for (Element ctx : contextElements) {
      String name = validateContext(ctx);
      if (!contextNames.add(name)) {
        throw new IllegalArgumentException("Duplicate context name '" + name + "'");
      }
    }

    validatePattern(patternElement, contextNames);
  }

  private String validateContext(Element ctxEl) {
    requireTag(ctxEl, "Context");
    String ctxName = requireAttr(ctxEl, "name", "Context name");
    requireName(ctxName, "Context name");

    NodeList children = ctxEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (!(node instanceof Element el)) continue;
      switch (el.getTagName()) {
        case "Sets" -> validateNamedElements(el, "Set", false);
        case "Constants" -> validateNamedElements(el, "Constant", false);
        case "Axioms" -> validateExpressionElements(el, "Axiom", false);
        default -> throw new IllegalArgumentException(
            "Unexpected element <" + el.getTagName() + "> in <Context>");
      }
    }
    return ctxName;
  }

  private void validatePattern(Element patternEl, Set<String> contextNames) {
    requireTag(patternEl, "Pattern");

    String patternName = requireAttr(patternEl, "name", "Pattern name");
    requireName(patternName, "Pattern name");
    String patternType = requireAttr(patternEl, "type", "Pattern type");
    requireNonBlank(patternType, "Pattern type");

    boolean contextRefFound = false;
    boolean variablesSeen = false;
    boolean invariantsSeen = false;
    boolean initSeen = false;
    boolean eventsSeen = false;

    NodeList children = patternEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (!(node instanceof Element el)) continue;
      switch (el.getTagName()) {
        case "ContextRef" -> {
          if (contextRefFound) {
            throw new IllegalArgumentException(
                "Pattern must not contain multiple <ContextRef> elements");
          }
          contextRefFound = true;
          String name = requireAttr(el, "name", "ContextRef name");
          requireName(name, "ContextRef name");
          if (!contextNames.isEmpty() && !contextNames.contains(name)) {
            throw new IllegalArgumentException(
                "ContextRef references unknown context '" + name + "'");
          }
        }
        case "Variables" -> {
          if (variablesSeen) {
            throw new IllegalArgumentException("Duplicate <Variables> section");
          }
          variablesSeen = true;
          validateVariables(el);
        }
        case "Invariants" -> {
          if (invariantsSeen) {
            throw new IllegalArgumentException("Duplicate <Invariants> section");
          }
          invariantsSeen = true;
          validateExpressionElements(el, "Invariant", false);
        }
        case "Initialisation" -> {
          if (initSeen) {
            throw new IllegalArgumentException("Duplicate <Initialisation> section");
          }
          initSeen = true;
          validateActions(el, true);
        }
        case "Events" -> {
          if (eventsSeen) {
            throw new IllegalArgumentException("Duplicate <Events> section");
          }
          eventsSeen = true;
          validateEvents(el);
        }
        default -> throw new IllegalArgumentException(
            "Unexpected element <" + el.getTagName() + "> in <Pattern>");
      }
    }

    if (!contextNames.isEmpty() && !contextRefFound) {
      throw new IllegalArgumentException("Pattern must declare a <ContextRef>");
    }
  }

  private void validateVariables(Element variablesEl) {
    NodeList vars = variablesEl.getChildNodes();
    for (int i = 0; i < vars.getLength(); i++) {
      Node node = vars.item(i);
      if (!(node instanceof Element el)) continue;
      requireTag(el, "Variable");
      String name = requireAttr(el, "name", "Variable name");
      requireName(name, "Variable name");
      String type = requireAttr(el, "type", "Variable type");
      requireNonBlank(type, "Variable type");
    }
  }

  private void validateEvents(Element eventsEl) {
    NodeList events = eventsEl.getChildNodes();
    int count = 0;
    for (int i = 0; i < events.getLength(); i++) {
      Node node = events.item(i);
      if (!(node instanceof Element el)) continue;
      requireTag(el, "Event");
      validateEvent(el);
      count++;
    }
    if (count == 0) {
      throw new IllegalArgumentException("<Events> must contain at least one <Event>");
    }
  }

  private void validateEvent(Element eventEl) {
    String name = requireAttr(eventEl, "name", "Event name");
    requireName(name, "Event name");

    boolean paramsSeen = false;
    boolean guardsSeen = false;
    boolean actionsSeen = false;

    NodeList children = eventEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (!(node instanceof Element el)) continue;
      switch (el.getTagName()) {
        case "Parameters" -> {
          if (paramsSeen) {
            throw new IllegalArgumentException(
                "Event '" + name + "' has duplicate <Parameters> sections");
          }
          paramsSeen = true;
          validateParameters(el, name);
        }
        case "Guards" -> {
          if (guardsSeen) {
            throw new IllegalArgumentException(
                "Event '" + name + "' has duplicate <Guards> sections");
          }
          guardsSeen = true;
          validateExpressionElements(el, "Guard", true);
        }
        case "Actions" -> {
          if (actionsSeen) {
            throw new IllegalArgumentException(
                "Event '" + name + "' has duplicate <Actions> sections");
          }
          actionsSeen = true;
          validateActions(el, true);
        }
        default -> throw new IllegalArgumentException(
            "Unexpected element <" + el.getTagName() + "> in <Event>");
      }
    }

    if (!actionsSeen) {
      throw new IllegalArgumentException(
          "Event '" + name + "' must contain an <Actions> section");
    }
  }

  private void validateParameters(Element paramsEl, String eventName) {
    NodeList params = paramsEl.getChildNodes();
    int count = 0;
    for (int i = 0; i < params.getLength(); i++) {
      Node node = params.item(i);
      if (!(node instanceof Element el)) continue;
      requireTag(el, "Param");
      String paramName = requireAttr(el, "name", "Param name");
      requireName(paramName, "Param name");
      String type = attrOrNull(el, "type");
      if (type != null && !type.isBlank()) {
        requireNonBlank(type, "Param type");
      }
      count++;
    }
    if (count == 0) {
      throw new IllegalArgumentException(
          "Event '" + eventName + "' has an empty <Parameters> section");
    }
  }

  private void validateNamedElements(Element parent, String expectedTag, boolean requireOne) {
    NodeList nodes = parent.getChildNodes();
    int count = 0;
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element el)) continue;
      requireTag(el, expectedTag);
      String name = requireAttr(el, "name", expectedTag + " name");
      requireName(name, expectedTag + " name");
      count++;
    }
    if (requireOne && count == 0) {
      throw new IllegalArgumentException(
          "<" + parent.getTagName() + "> must contain at least one <" + expectedTag + ">");
    }
  }

  private void validateExpressionElements(Element parent, String expectedTag, boolean requireOne) {
    NodeList nodes = parent.getChildNodes();
    int count = 0;
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element el)) continue;
      requireTag(el, expectedTag);
      String expr = requireAttr(el, "expression", expectedTag + " expression");
      requireNonBlank(expr, expectedTag + " expression");
      count++;
    }
    if (requireOne && count == 0) {
      throw new IllegalArgumentException(
          "<" + parent.getTagName() + "> must contain at least one <" + expectedTag + ">");
    }
  }

  private void validateActions(Element parent, boolean requireActions) {
    NodeList nodes = parent.getChildNodes();
    int count = 0;
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element el)) continue;
      requireTag(el, "Action");
      validateAction(el);
      count++;
    }
    if (requireActions && count == 0) {
      throw new IllegalArgumentException(
          "<" + parent.getTagName() + "> must contain at least one <Action>");
    }
  }

  private void validateAction(Element actionEl) {
    String singleVar = actionEl.hasAttribute("var") ? actionEl.getAttribute("var").trim() : null;
    String singleValue = actionEl.hasAttribute("value") ? actionEl.getAttribute("value").trim() : null;
    String vars = actionEl.hasAttribute("vars") ? actionEl.getAttribute("vars").trim() : null;
    String values = actionEl.hasAttribute("values") ? actionEl.getAttribute("values").trim() : null;

    boolean singleAssignment = singleVar != null && !singleVar.isEmpty();
    boolean multiAssignment = vars != null && !vars.isEmpty();

    if (singleAssignment && multiAssignment) {
      throw new IllegalArgumentException("Action cannot mix 'var' with 'vars'");
    }
    if (multiAssignment) {
      if (values == null || values.isEmpty()) {
        throw new IllegalArgumentException(
            "Action with 'vars' must include matching 'values'");
      }
      if (!NAME_LIST_PATTERN.matcher(vars).matches()) {
        throw new IllegalArgumentException(
            "Action 'vars' must be a comma separated list of names");
      }
    } else if (values != null && !values.isEmpty()) {
      throw new IllegalArgumentException(
          "Action with 'values' must be accompanied by 'vars'");
    }

    if (singleAssignment) {
      if (!"skip".equalsIgnoreCase(singleVar) && (singleValue == null || singleValue.isEmpty())) {
        throw new IllegalArgumentException("Action with 'var' must include a non-empty 'value'");
      }
    } else if (!multiAssignment) {
      throw new IllegalArgumentException("Action must specify either 'var' or 'vars'");
    }
  }

  private String attrOrNull(Element element, String name) {
    return element.hasAttribute(name) ? element.getAttribute(name).trim() : null;
  }

  private void requireTag(Element element, String expected) {
    if (!expected.equals(element.getTagName())) {
      throw new IllegalArgumentException(
          "Expected <" + expected + "> but found <" + element.getTagName() + ">");
    }
  }

  private String requireAttr(Element element, String name, String description) {
    if (!element.hasAttribute(name)) {
      throw new IllegalArgumentException(
          description + " is required on <" + element.getTagName() + ">");
    }
    String value = element.getAttribute(name).trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(
          description + " must not be blank on <" + element.getTagName() + ">");
    }
    return value;
  }

  private void requireName(String value, String description) {
    if (!NAME_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          description + " contains an invalid identifier: '" + value + "'");
    }
  }

  private void requireNonBlank(String value, String description) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(description + " must not be blank");
    }
  }
}
