package app;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/** DOM parser for Pattern XML → PatternModel. */
public class PatternDomParser {

  public PatternModel parse(Path xmlPath) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setIgnoringComments(true);
    f.setCoalescing(true);

    DocumentBuilder b = f.newDocumentBuilder();
    try (InputStream in = new FileInputStream(xmlPath.toFile())) {
      Document doc = b.parse(in);
      Element root = doc.getDocumentElement();
      if (root == null) throw new IllegalArgumentException("Empty pattern document");

      String tag = root.getTagName();
      if ("PatternBundle".equals(tag)) {
        return parseBundle(root);
      } else if ("Pattern".equals(tag)) {
        return parseLegacyPattern(root);
      }

      throw new IllegalArgumentException("Unsupported root element <" + tag + ">");
    }
  }

  private PatternModel parseBundle(Element bundleEl) {
    Map<String, PatternModel.Context> contextsByName = new HashMap<>();
    Element patternEl = null;

    NodeList children = bundleEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (!(n instanceof Element)) continue;
      Element el = (Element) n;
      switch (el.getTagName()) {
        case "Context" -> {
          String name = attrOr(el, "name", "Context");
          contextsByName.put(name, parseContext(el));
        }
        case "Pattern" -> {
          if (patternEl == null) patternEl = el;
        }
        default -> {
          // ignore other elements
        }
      }
    }

    if (patternEl == null) {
      throw new IllegalArgumentException("Pattern bundle must contain a <Pattern>");
    }

    PatternModel model = new PatternModel();
    model.name = attrOr(patternEl, "name", model.name);

    // Attach referenced context if present
    Element ctxRefEl = child(patternEl, "ContextRef");
    if (ctxRefEl != null) {
      String refName = attr(ctxRefEl, "name");
      PatternModel.Context referenced = contextsByName.get(refName);
      if (referenced != null) {
        model.context = copyContext(referenced);
      }
    }

    // If no context matched but there is exactly one context definition, use it by default.
    if (model.context == null && contextsByName.size() == 1) {
      model.context = copyContext(contextsByName.values().iterator().next());
    }

    // Variables
    Element variablesEl = child(patternEl, "Variables");
    if (variablesEl != null) {
      NodeList vars = variablesEl.getChildNodes();
      for (int i = 0; i < vars.getLength(); i++) {
        Node n = vars.item(i);
        if (n instanceof Element && "Variable".equals(((Element) n).getTagName())) {
          Element vEl = (Element) n;
          PatternModel.Variable v = new PatternModel.Variable();
          v.name = attrOr(vEl, "name", "v" + (model.variables.size() + 1));
          v.type = attrOr(vEl, "type", "UNSPECIFIED");
          model.variables.add(v);
        }
      }
    }

    // Invariants
    Element invsEl = child(patternEl, "Invariants");
    if (invsEl != null) {
      NodeList invNodes = invsEl.getChildNodes();
      for (int i = 0; i < invNodes.getLength(); i++) {
        Node n = invNodes.item(i);
        if (n instanceof Element && "Invariant".equals(((Element) n).getTagName())) {
          Element invEl = (Element) n;
          PatternModel.Invariant inv = new PatternModel.Invariant();
          inv.expression = attrOr(invEl, "expression", textOr(invEl, "TRUE"));
          model.invariants.add(inv);
        }
      }
    }

    // Initialisation
    Element initEl = child(patternEl, "Initialisation");
    if (initEl != null) {
      PatternModel.Event initEvt = new PatternModel.Event();
      initEvt.name = "Initialisation";
      initEvt.sourcePattern = model.name;
      parseActions(initEl, initEvt.actions);
      model.events.add(initEvt);
    }

    // Events
    Element eventsEl = child(patternEl, "Events");
    if (eventsEl != null) {
      NodeList eventNodes = eventsEl.getChildNodes();
      for (int i = 0; i < eventNodes.getLength(); i++) {
        Node n = eventNodes.item(i);
        if (!(n instanceof Element) || !"Event".equals(((Element) n).getTagName())) continue;
        Element eventEl = (Element) n;
        PatternModel.Event evt = new PatternModel.Event();
        evt.name = attrOr(eventEl, "name", "event" + (model.events.size() + 1));
        evt.sourcePattern = model.name;

        Element paramsEl = child(eventEl, "Parameters");
        if (paramsEl != null) {
          NodeList params = paramsEl.getChildNodes();
          for (int j = 0; j < params.getLength(); j++) {
            Node pn = params.item(j);
            if (!(pn instanceof Element) || !"Param".equals(((Element) pn).getTagName())) continue;
            Element pEl = (Element) pn;
            PatternModel.Param p = new PatternModel.Param();
            p.name = attrOr(pEl, "name", "p" + (evt.params.size() + 1));
            String typeAttr = attr(pEl, "type");
            p.type = (typeAttr != null && !typeAttr.isEmpty()) ? typeAttr : null;
            evt.params.add(p);
          }
        }

        Element guardsEl = child(eventEl, "Guards");
        if (guardsEl != null) {
          NodeList guardNodes = guardsEl.getChildNodes();
          for (int j = 0; j < guardNodes.getLength(); j++) {
            Node gn = guardNodes.item(j);
            if (!(gn instanceof Element) || !"Guard".equals(((Element) gn).getTagName())) continue;
            Element gEl = (Element) gn;
            PatternModel.Guard g = new PatternModel.Guard();
            g.expr = attrOr(gEl, "expression", textOr(gEl, "TRUE"));
            evt.guards.add(g);
          }
        }

        Element actionsEl = child(eventEl, "Actions");
        if (actionsEl != null) {
          parseActions(actionsEl, evt.actions);
        }

        model.events.add(evt);
      }
    }

    // Ensure there is at least an initialisation event
    if (model.events.stream().noneMatch(e -> "Initialisation".equalsIgnoreCase(e.name))) {
      PatternModel.Event initEvt = new PatternModel.Event();
      initEvt.name = "Initialisation";
      initEvt.sourcePattern = model.name;
      model.events.add(0, initEvt);
    }

    return model;
  }

  private PatternModel parseLegacyPattern(Element root) {
    PatternModel model = new PatternModel();
    if (root.hasAttribute("name")) model.name = root.getAttribute("name").trim();

    // Context (optional, legacy inline)
    Element contextEl = child(root, "Context");
    if (contextEl != null) {
      model.context = parseContext(contextEl);
    }

    // Variables
    Element variablesEl = child(root, "Variables");
    if (variablesEl != null) {
      NodeList vars = variablesEl.getElementsByTagName("Variable");
      for (int i = 0; i < vars.getLength(); i++) {
        Element vEl = (Element) vars.item(i);
        PatternModel.Variable v = new PatternModel.Variable();
        v.name = attrOr(vEl, "name", "v" + (i + 1));
        v.type = attrOr(vEl, "type", "UNSPECIFIED");
        model.variables.add(v);
      }
    }

    // Invariants
    Element invEl = child(root, "Invariants");
    if (invEl != null) {
      NodeList invs = invEl.getElementsByTagName("Invariant");
      for (int i = 0; i < invs.getLength(); i++) {
        Element e = (Element) invs.item(i);
        PatternModel.Invariant inv = new PatternModel.Invariant();
        inv.expression = attrOr(e, "expression", textOr(e, "TRUE"));
        model.invariants.add(inv);
      }
    }

    // Events (legacy format)
    Element eventsEl = child(root, "Events");
    if (eventsEl != null) {
      NodeList events = eventsEl.getElementsByTagName("Event");
      for (int i = 0; i < events.getLength(); i++) {
        Element evtEl = (Element) events.item(i);
        PatternModel.Event evt = new PatternModel.Event();
        evt.name = attrOr(evtEl, "name", "event" + (i + 1));
        evt.sourcePattern = model.name;

        Element paramsEl = child(evtEl, "Parameters");
        if (paramsEl != null) {
          NodeList ps = paramsEl.getElementsByTagName("Param");
          for (int j = 0; j < ps.getLength(); j++) {
            Element pEl = (Element) ps.item(j);
            PatternModel.Param p = new PatternModel.Param();
            p.name = attrOr(pEl, "name", "p" + (j + 1));
            String typeAttr = attr(pEl, "type");
            p.type = (typeAttr != null && !typeAttr.isEmpty()) ? typeAttr : null;
            evt.params.add(p);
          }
        }

        Element guardsEl = child(evtEl, "Guards");
        if (guardsEl != null) {
          NodeList gs = guardsEl.getElementsByTagName("Guard");
          for (int j = 0; j < gs.getLength(); j++) {
            Element gEl = (Element) gs.item(j);
            PatternModel.Guard g = new PatternModel.Guard();
            g.expr = attrOr(gEl, "expression", textOr(gEl, "TRUE"));
            evt.guards.add(g);
          }
        }

        Element actionsEl = child(evtEl, "Actions");
        if (actionsEl != null) {
          parseActions(actionsEl, evt.actions);
        }

        model.events.add(evt);
      }
    }

    return model;
  }

  private PatternModel.Context parseContext(Element ctxEl) {
    PatternModel.Context ctx = new PatternModel.Context();

    Element setsEl = child(ctxEl, "Sets");
    if (setsEl != null) {
      NodeList setNodes = setsEl.getElementsByTagName("Set");
      for (int i = 0; i < setNodes.getLength(); i++) {
        Element s = (Element) setNodes.item(i);
        ctx.sets.add(attrOr(s, "name", "SET" + (i + 1)));
      }
    }
    if (ctx.sets.isEmpty()) {
      NodeList nodes = ctxEl.getChildNodes();
      for (int i = 0; i < nodes.getLength(); i++) {
        Node n = nodes.item(i);
        if (n instanceof Element && "Set".equals(((Element) n).getTagName())) {
          ctx.sets.add(attrOr((Element) n, "name", "SET" + (ctx.sets.size() + 1)));
        }
      }
    }

    Element constsEl = child(ctxEl, "Constants");
    if (constsEl != null) {
      NodeList constNodes = constsEl.getElementsByTagName("Constant");
      for (int i = 0; i < constNodes.getLength(); i++) {
        Element c = (Element) constNodes.item(i);
        ctx.constants.add(attrOr(c, "name", "CONST" + (i + 1)));
      }
    }
    if (ctx.constants.isEmpty()) {
      NodeList nodes = ctxEl.getChildNodes();
      for (int i = 0; i < nodes.getLength(); i++) {
        Node n = nodes.item(i);
        if (n instanceof Element && "Constant".equals(((Element) n).getTagName())) {
          ctx.constants.add(attrOr((Element) n, "name", "CONST" + (ctx.constants.size() + 1)));
        }
      }
    }

    Element axiomsEl = child(ctxEl, "Axioms");
    if (axiomsEl != null) {
      NodeList axNodes = axiomsEl.getElementsByTagName("Axiom");
      for (int i = 0; i < axNodes.getLength(); i++) {
        Element ax = (Element) axNodes.item(i);
        ctx.axioms.add(attrOr(ax, "expression", textOr(ax, "TRUE")));
      }
    }
    if (ctx.axioms.isEmpty()) {
      NodeList nodes = ctxEl.getChildNodes();
      for (int i = 0; i < nodes.getLength(); i++) {
        Node n = nodes.item(i);
        if (n instanceof Element && "Axiom".equals(((Element) n).getTagName())) {
          ctx.axioms.add(attrOr((Element) n, "expression", textOr((Element) n, "TRUE")));
        }
      }
    }

    return ctx;
  }

  private void parseActions(Element parent, List<PatternModel.Action> target) {
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node n = nodes.item(i);
      if (!(n instanceof Element) || !"Action".equals(((Element) n).getTagName())) continue;
      Element aEl = (Element) n;
      PatternModel.Action a = new PatternModel.Action();

      String singleVar = attr(aEl, "var");
      String singleValueAttr = attr(aEl, "value");
      String multiVars = attr(aEl, "vars");
      String multiValues = attr(aEl, "values");
      String text = textOr(aEl, "");

      String assignment = null;

      if (multiVars != null && !multiVars.isBlank()) {
        String lhs = multiVars.trim();
        String rhs = (multiValues != null && !multiValues.isBlank()) ? multiValues : (text.isBlank() ? null : text);
        if (!lhs.isEmpty() && rhs != null && !rhs.isBlank()) {
          assignment = lhs + " ≔ " + rhs;
        }
      } else if (singleVar != null && !singleVar.isBlank()) {
        String varName = singleVar.trim();
        if (!varName.isEmpty() && !"skip".equalsIgnoreCase(varName)) {
          String rhs = (singleValueAttr != null && !singleValueAttr.isBlank()) ? singleValueAttr : text;
          if (rhs == null || rhs.isBlank()) rhs = "skip";
          assignment = varName + " ≔ " + rhs;
        } else {
          String rhs = (singleValueAttr != null && !singleValueAttr.isBlank()) ? singleValueAttr : text;
          if (rhs != null && !rhs.isBlank() && !rhs.trim().equalsIgnoreCase("skip")) {
            assignment = rhs;
          }
        }
      } else {
        String rhs = (singleValueAttr != null && !singleValueAttr.isBlank()) ? singleValueAttr : text;
        if (rhs != null && !rhs.isBlank()) {
          assignment = rhs;
        }
      }

      if (assignment == null || assignment.trim().equalsIgnoreCase("skip")) continue;

      a.assignment = assignment;
      target.add(a);
    }
  }

  private Element child(Element parent, String tag) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n instanceof Element && tag.equals(((Element) n).getTagName())) {
        return (Element) n;
      }
    }
    return null;
  }

  private PatternModel.Context copyContext(PatternModel.Context src) {
    PatternModel.Context copy = new PatternModel.Context();
    copy.sets.addAll(src.sets);
    copy.constants.addAll(src.constants);
    copy.axioms.addAll(src.axioms);
    return copy;
  }

  private static String attrOr(Element e, String name, String def) {
    String v = attr(e, name);
    return (v == null || v.isBlank()) ? def : v;
  }

  private static String attr(Element e, String name) {
    return e.hasAttribute(name) ? e.getAttribute(name).trim() : null;
  }

  private static String textOr(Element e, String def) {
    String t = e.getTextContent();
    if (t == null) return def;
    t = t.trim();
    return t.isEmpty() ? def : t;
  }
}
