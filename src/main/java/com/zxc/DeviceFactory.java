package com.zxc;

import com.arcadedb.database.Database;
import com.arcadedb.graph.Edge;
import com.arcadedb.graph.MutableEdge;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.query.sql.executor.ResultSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceFactory {

    private final Database db;

    public DeviceFactory(Database db) {
        this.db = db;
    }

    public MutableVertex registerTemplate(String name, String type, String displayName) {
        return registerTemplate(name, type, displayName, null);
    }

    public MutableVertex registerTemplate(String name, String type, String displayName, Map<String, Object> extraProps) {
        MutableVertex template = db.newVertex("EquipmentTemplate")
                .set("name", name)
                .set("type", type)
                .set("displayName", displayName);
        if (extraProps != null) {
            extraProps.forEach(template::set);
        }
        return template.save();
    }

    public MutableVertex addSlot(MutableVertex equipmentTemplate, String slotName, String slotType, int slotIndex) {
        return addSlot(equipmentTemplate, slotName, slotType, slotIndex, 0.0, 0.0);
    }

    public MutableVertex addSlot(MutableVertex equipmentTemplate, String slotName, String slotType, int slotIndex, double x, double y) {
        MutableVertex slot = db.newVertex("SlotTemplate")
                .set("name", slotName)
                .set("slotType", slotType)
                .set("slotIndex", slotIndex)
                .set("x", x)
                .set("y", y)
                .save();
        equipmentTemplate.newEdge("HAS_SLOT_TEMPLATE", slot).save();
        return slot;
    }

    public MutableVertex addPort(MutableVertex equipmentTemplate, String portName, String direction, String connectorType) {
        return addPort(equipmentTemplate, portName, direction, connectorType, null, null, 0.0, 0.0);
    }

    public MutableVertex addPort(MutableVertex equipmentTemplate, String portName, String direction, String connectorType, double x, double y) {
        return addPort(equipmentTemplate, portName, direction, connectorType, null, null, x, y);
    }

    public MutableVertex addPort(MutableVertex equipmentTemplate, String portName, String direction, String connectorType,
                                 String gender, String signalType, double x, double y) {
        return addPort(equipmentTemplate, portName, direction, connectorType, gender, signalType, null, x, y);
    }

    public MutableVertex addPort(MutableVertex equipmentTemplate, String portName, String direction, String connectorType,
                                 String gender, String signalType, String panelFace, double x, double y) {
        MutableVertex port = db.newVertex("PortTemplate")
                .set("name", portName)
                .set("direction", direction)
                .set("connectorType", connectorType)
                .set("x", x)
                .set("y", y);
        if (gender != null) {
            port.set("gender", gender);
        }
        if (signalType != null) {
            port.set("signalType", signalType);
        }
        if (panelFace != null) {
            port.set("panelFace", panelFace);
        }
        port.save();
        equipmentTemplate.newEdge("HAS_PORT_TEMPLATE", port).save();
        return port;
    }

    public MutableVertex instantiateEquipment(String templateName, String instanceName, String displayName) {
        return instantiateEquipment(templateName, instanceName, displayName, null, null, null);
    }

    public MutableVertex instantiateEquipment(String templateName, String instanceName, String displayName,
                                             Vertex parentLocationOrChassis, String slotName) {
        return instantiateEquipment(templateName, instanceName, displayName, parentLocationOrChassis, slotName, null);
    }

    public MutableVertex instantiateEquipment(String templateName, String instanceName, String displayName,
                                             Vertex parentLocationOrChassis, String slotName,
                                             Map<String, Object> edgeProperties) {
        // 1. Query the EquipmentTemplate using parameterized query
        ResultSet rs = db.query("cypher", "MATCH (t:EquipmentTemplate {name: $name}) RETURN t", Map.of("name", templateName));
        if (!rs.hasNext()) {
            throw new IllegalArgumentException("EquipmentTemplate not found: " + templateName);
        }
        Vertex template = rs.next().getVertex().orElseThrow(
                () -> new IllegalStateException("Failed to resolve vertex for template: " + templateName)
        );

        // 2. Spawn concrete Equipment vertex
        MutableVertex equipment = db.newVertex("Equipment")
                .set("name", instanceName)
                .set("displayName", displayName != null ? displayName : instanceName)
                .set("model", templateName);
        if (template.has("type")) {
            equipment.set("type", template.get("type"));
        }
        equipment.save();

        // 3. Attach INSTANCE_OF edge from Equipment to template
        equipment.newEdge("INSTANCE_OF", template).save();

        // 4. Inspect parent vertex type, validate slot if applicable, and connect relationship
        if (parentLocationOrChassis != null) {
            Vertex parent = parentLocationOrChassis.asVertex();
            if ("Space".equals(parent.getTypeName()) || parent.getType().instanceOf("Location")) {
                equipment.newEdge("LOCATED_IN", parentLocationOrChassis.asVertex()).save();
            } else if ("Equipment".equals(parent.getTypeName()) || parent.getType().instanceOf("Equipment")) {
                if (slotName != null) {
                    validateSlot(parent, slotName);
                }
                MutableEdge edge = parentLocationOrChassis.asVertex().modify().newEdge("HAS_PART", equipment);
                if (slotName != null) {
                    edge.set("slot", slotName);
                }
                if (edgeProperties != null) {
                    edgeProperties.forEach(edge::set);
                }
                edge.save();
            }
        }

        // 5. Iterate HAS_PORT_TEMPLATE and spawn concrete ConnectionPoints
        Map<String, MutableVertex> spawnedPorts = new HashMap<>();
        Map<String, Vertex> portTemplates = new HashMap<>();

        for (Edge edge : template.getEdges(Vertex.DIRECTION.OUT, "HAS_PORT_TEMPLATE")) {
            Vertex pt = edge.getInVertex();
            String portName = pt.getString("name");
            String cpName = instanceName + "_" + portName;

            MutableVertex cp = db.newVertex("ConnectionPoint")
                    .set("name", cpName)
                    .set("portName", portName);
            if (pt.has("direction")) {
                cp.set("direction", pt.get("direction"));
            }
            if (pt.has("connectorType")) {
                cp.set("connectorType", pt.get("connectorType"));
            }
            if (pt.has("gender")) {
                cp.set("gender", pt.get("gender"));
            }
            if (pt.has("signalType")) {
                cp.set("signalType", pt.get("signalType"));
            }
            if (pt.has("panelFace")) {
                cp.set("panelFace", pt.get("panelFace"));
            }
            if (pt.has("x")) {
                cp.set("x", pt.get("x"));
            }
            if (pt.has("y")) {
                cp.set("y", pt.get("y"));
            }
            cp.save();

            // Link Equipment to concrete ConnectionPoint
            equipment.newEdge("HAS_CONNECTION_POINT", cp).save();
            // Link concrete ConnectionPoint to its PortTemplate
            cp.newEdge("INSTANCE_OF", pt).save();

            spawnedPorts.put(portName, cp);
            portTemplates.put(portName, pt);
        }

        // 6. Propagate internal port-to-port signal continuity (CONNECTS_TO)
        for (Map.Entry<String, Vertex> entry : portTemplates.entrySet()) {
            Vertex pt = entry.getValue();
            MutableVertex cpSource = spawnedPorts.get(entry.getKey());
            for (Edge connEdge : pt.getEdges(Vertex.DIRECTION.OUT, "CONNECTS_TO")) {
                Vertex targetPt = connEdge.getInVertex();
                String targetPortName = targetPt.getString("name");
                MutableVertex cpTarget = spawnedPorts.get(targetPortName);
                if (cpTarget != null) {
                    cpSource.newEdge("CONNECTS_TO", cpTarget).save();
                }
            }
        }

        return equipment;
    }

    private void validateSlot(Vertex parent, String slotName) {
        ResultSet rs = db.query("cypher",
                "MATCH (parent:Equipment {name: $parentName})-[:INSTANCE_OF]->(t:EquipmentTemplate)-[:HAS_SLOT_TEMPLATE]->(slot:SlotTemplate) " +
                "RETURN slot.name AS slotName", Map.of("parentName", parent.getString("name")));
        boolean found = false;
        List<String> availableSlots = new ArrayList<>();
        while (rs.hasNext()) {
            String s = rs.next().getProperty("slotName");
            availableSlots.add(s);
            if (slotName.equals(s)) {
                found = true;
            }
        }
        if (!availableSlots.isEmpty() && !found) {
            throw new IllegalArgumentException("Slot '" + slotName + "' is invalid for parent equipment '" +
                    parent.getString("name") + "'. Available slots: " + availableSlots);
        }
    }

    public static MutableVertex createCable(
            Database db,
            String cableTag,
            String displayName,
            String partNumber,
            String mediaType,
            String color,
            String wiringStandard,
            double lengthMeters,
            String connectorTypeA,
            String portToMateA,
            String connectorTypeB,
            String portToMateB
    ) {
        // 1. Create Cable vertex
        MutableVertex cable = db.newVertex("Cable")
                .set("name", cableTag)
                .set("displayName", displayName)
                .set("partNumber", partNumber)
                .set("mediaType", mediaType)
                .set("color", color)
                .set("wiringStandard", wiringStandard)
                .set("lengthMeters", lengthMeters)
                .save();

        // 2. Spawn ConnectionPoint End A
        MutableVertex endA = db.newVertex("ConnectionPoint")
                .set("name", cableTag + "_A")
                .set("portName", "A")
                .set("connectorType", connectorTypeA)
                .set("connectorGender", "MALE")
                .set("gender", "MALE")
                .set("direction", "BIDIRECTIONAL")
                .save();

        // 3. Spawn ConnectionPoint End B
        MutableVertex endB = db.newVertex("ConnectionPoint")
                .set("name", cableTag + "_B")
                .set("portName", "B")
                .set("connectorType", connectorTypeB)
                .set("connectorGender", "MALE")
                .set("gender", "MALE")
                .set("direction", "BIDIRECTIONAL")
                .save();

        // 4. Connect Cable to End A and End B
        cable.newEdge("HAS_CONNECTION_POINT", endA).save();
        cable.newEdge("HAS_CONNECTION_POINT", endB).save();

        // 5. Establish canonical internal conductive signal path: End A -> End B
        endA.newEdge("CONNECTS_TO", endB).save();

        // 6. Query and mate End A -> portToMateA using parameterized query
        if (portToMateA != null) {
            ResultSet rsA = db.query("cypher", "MATCH (cp:ConnectionPoint {name: $name}) RETURN cp", Map.of("name", portToMateA));
            if (rsA.hasNext()) {
                Vertex targetA = rsA.next().getVertex().orElse(null);
                if (targetA != null) {
                    endA.newEdge("MATES_WITH", targetA).save();
                }
            }
        }

        // 6. Query and mate End B -> portToMateB using parameterized query
        if (portToMateB != null) {
            ResultSet rsB = db.query("cypher", "MATCH (cp:ConnectionPoint {name: $name}) RETURN cp", Map.of("name", portToMateB));
            if (rsB.hasNext()) {
                Vertex targetB = rsB.next().getVertex().orElse(null);
                if (targetB != null) {
                    endB.newEdge("MATES_WITH", targetB).save();
                }
            }
        }

        return cable;
    }
}
