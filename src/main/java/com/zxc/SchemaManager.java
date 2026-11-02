package com.zxc;

import com.arcadedb.database.Database;
import com.arcadedb.database.Document;
import com.arcadedb.event.BeforeRecordUpdateListener;
import com.arcadedb.schema.PropertyType;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.VertexType;

public class SchemaManager {

    public static void createSchema(Database db) {
        db.transaction(() -> {
            Schema schema = db.getSchema();

            // 1. Root Entity with unique name index
            VertexType entity = schema.getOrCreateVertexType("Entity");
            entity.getOrCreateProperty("name", PropertyType.STRING);
            schema.getOrCreateTypeIndex(Schema.INDEX_TYPE.LSM_TREE, true, "Entity", "name");

            // 2. Location hierarchy
            VertexType location = (VertexType) schema.getOrCreateVertexType("Location").addSuperType(entity);
            schema.getOrCreateVertexType("Site").addSuperType(location);
            schema.getOrCreateVertexType("Building").addSuperType(location);
            schema.getOrCreateVertexType("Floor").addSuperType(location);
            schema.getOrCreateVertexType("Space").addSuperType(location);

            // 3. Domain entity vertex types inheriting from Entity
            schema.getOrCreateVertexType("EquipmentTemplate").addSuperType(entity);
            schema.getOrCreateVertexType("Equipment").addSuperType(entity);
            schema.getOrCreateVertexType("Cable").addSuperType(entity);
            schema.getOrCreateVertexType("Point").addSuperType(entity);
            schema.getOrCreateVertexType("ConfigurationBackup").addSuperType(entity);

            // 4. Component & template vertex types (standalone)
            schema.getOrCreateVertexType("PortTemplate");
            schema.getOrCreateVertexType("SlotTemplate");
            schema.getOrCreateVertexType("ConnectionPoint");

            // 5. Edge types
            schema.getOrCreateEdgeType("HAS_PART");
            schema.getOrCreateEdgeType("LOCATED_IN");
            schema.getOrCreateEdgeType("CONNECTS_TO");
            schema.getOrCreateEdgeType("HAS_CONNECTION_POINT");
            schema.getOrCreateEdgeType("HAS_POINT");
            schema.getOrCreateEdgeType("HAS_PORT_TEMPLATE");
            schema.getOrCreateEdgeType("HAS_SLOT_TEMPLATE");
            schema.getOrCreateEdgeType("INSTANCE_OF");
            schema.getOrCreateEdgeType("MATES_WITH");
            schema.getOrCreateEdgeType("HAS_CONFIGURATION");

            System.out.println(">>> SchemaManager: Initialized vertex & edge types and unique indexes.");
        });
    }

    public static void registerListeners(Database db) {
        Schema schema = db.getSchema();
        if (schema.existsType("Point")) {
            VertexType pointType = schema.getOrCreateVertexType("Point");
            pointType.getEvents().registerListener((BeforeRecordUpdateListener) record -> {
                if (record instanceof Document doc && doc.has("currentValue")) {
                    Object val = doc.get("currentValue");
                    double num = val instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(val));
                    if (num < -50.0 || num > 100.0) {
                        System.out.println(">>> [VETO] Point value out of range (-50 to 100): " + val);
                        throw new IllegalArgumentException("Point value out of range (-50 to 100): " + val);
                    }
                    System.out.println(">>> [ACCEPTED] Updating Point value to: " + val);
                    return true;
                }
                return true;
            });
            System.out.println(">>> SchemaManager: Registered BeforeRecordUpdateListener on Point type.");
        }
    }
}
