package com.zxc;

import com.arcadedb.database.Database;
import com.arcadedb.graph.Edge;
import com.arcadedb.graph.MutableEdge;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.query.sql.executor.ResultSet;

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
        port.save();
        equipmentTemplate.newEdge("HAS_PORT_TEMPLATE", port).save();
        return port;
    }

    public MutableVertex instantiateEquipment(String templateName, String instanceName, String displayName) {
        return instantiateEquipment(templateName, instanceName, displayName, null, null);
    }

    public MutableVertex instantiateEquipment(String templateName, String instanceName, String displayName,
                                             Vertex parentLocationOrChassis, String slotName) {
        // 1. Query the EquipmentTemplate
        ResultSet rs = db.query("cypher", "MATCH (t:EquipmentTemplate {name: '" + templateName + "'}) RETURN t");
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

        // 4. Inspect parent vertex type and connect relationship
        if (parentLocationOrChassis != null) {
            Vertex parent = parentLocationOrChassis.asVertex();
            if ("Space".equals(parent.getTypeName()) || parent.getType().instanceOf("Location")) {
                equipment.newEdge("LOCATED_IN", parentLocationOrChassis.asVertex()).save();
            } else if ("Equipment".equals(parent.getTypeName()) || parent.getType().instanceOf("Equipment")) {
                MutableEdge edge = parentLocationOrChassis.asVertex().modify().newEdge("HAS_PART", equipment);
                if (slotName != null) edge.set("slot", slotName);
                edge.save();
            }
        }

        // 5. Iterate HAS_PORT_TEMPLATE and spawn concrete ConnectionPoints
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
        }

        return equipment;
    }
}
