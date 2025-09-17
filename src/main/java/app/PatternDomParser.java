package app;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;

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

      if (!"Pattern".equals(root.getTagName())) {
        throw new IllegalArgumentException("Root element must be <Pattern>, got <" + root.getTagName() + ">");
      }

      PatternModel model = new PatternModel();
      if (root.hasAttribute("name")) model.name = root.getAttribute("name").trim();

      // Variables
      NodeList variablesBlocks = root.getElementsByTagName("Variables");
      if (variablesBlocks.getLength() > 0) {
        Element variablesEl = (Element) variablesBlocks.item(0);
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
      NodeList invBlocks = root.getElementsByTagName("Invariants");
      if (invBlocks.getLength() > 0) {
        Element invEl = (Element) invBlocks.item(0);
        NodeList invs = invEl.getElementsByTagName("Invariant");
        for (int i = 0; i < invs.getLength(); i++) {
          Element e = (Element) invs.item(i);
          PatternModel.Invariant inv = new PatternModel.Invariant();
          inv.expression = attrOr(e, "expression", textOr(e, "TRUE"));
          model.invariants.add(inv);
        }
      }

      // Events
      NodeList eventsBlocks = root.getElementsByTagName("Events");
      if (eventsBlocks.getLength() > 0) {
        Element eventsEl = (Element) eventsBlocks.item(0);
        NodeList events = eventsEl.getElementsByTagName("Event");
        for (int i = 0; i < events.getLength(); i++) {
          Element evtEl = (Element) events.item(i);
          PatternModel.Event evt = new PatternModel.Event();
          evt.name = attrOr(evtEl, "name", "event" + (i + 1));

          // Parameters
          NodeList paramsBlocks = evtEl.getElementsByTagName("Parameters");
          if (paramsBlocks.getLength() > 0) {
            Element paramsEl = (Element) paramsBlocks.item(0);
            NodeList ps = paramsEl.getElementsByTagName("Param");
            for (int j = 0; j < ps.getLength(); j++) {
              Element pEl = (Element) ps.item(j);
              PatternModel.Param p = new PatternModel.Param();
              p.name = attrOr(pEl, "name", "p" + (j + 1));
              p.type = attrOr(pEl, "type", "UNSPECIFIED");
              evt.params.add(p);
            }
          }

          // Guards
          NodeList guardsBlocks = evtEl.getElementsByTagName("Guards");
          if (guardsBlocks.getLength() > 0) {
            Element guardsEl = (Element) guardsBlocks.item(0);
            NodeList gs = guardsEl.getElementsByTagName("Guard");
            for (int j = 0; j < gs.getLength(); j++) {
              Element gEl = (Element) gs.item(j);
              PatternModel.Guard g = new PatternModel.Guard();
              g.expr = attrOr(gEl, "expression", textOr(gEl, "TRUE"));
              evt.guards.add(g);
            }
          }

          // Actions
          NodeList actionsBlocks = evtEl.getElementsByTagName("Actions");
          if (actionsBlocks.getLength() > 0) {
            Element actionsEl = (Element) actionsBlocks.item(0);
            NodeList as = actionsEl.getElementsByTagName("Action");
            for (int j = 0; j < as.getLength(); j++) {
              Element aEl = (Element) as.item(j);
              PatternModel.Action a = new PatternModel.Action();
              a.assignment = attrOr(aEl, "value", textOr(aEl, "skip"));
              evt.actions.add(a);
            }
          }

          model.events.add(evt);
        }
      }

      return model;
    }
  }

  // helpers
  private static String attrOr(Element e, String name, String def) {
    return e.hasAttribute(name) ? e.getAttribute(name).trim() : def;
  }
  private static String textOr(Element e, String def) {
    String t = e.getTextContent();
    if (t == null) return def;
    t = t.trim();
    return t.isEmpty() ? def : t;
  }
}