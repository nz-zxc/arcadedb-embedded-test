package com.zxc;

import com.arcadedb.ContextConfiguration;
import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Database;
import com.arcadedb.database.Document;
import com.arcadedb.engine.ComponentFile;
import com.arcadedb.event.BeforeRecordUpdateListener;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.schema.PropertyType;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;
import com.arcadedb.schema.VertexType;
import com.arcadedb.server.ArcadeDBServer;

import java.util.Scanner;

public class Main {

    private static final String DB_PATH = "./databases";
    private static final String DB_NAME = "buildings_model";
    private static final String ROOT_PASSWORD = "playwithdata";

    public static void main(String[] args) {
        // 1. Configure the Embedded Server
        ContextConfiguration config = new ContextConfiguration();
        config.setValue(GlobalConfiguration.SERVER_DATABASE_DIRECTORY, DB_PATH);
        config.setValue(GlobalConfiguration.SERVER_ROOT_PASSWORD, ROOT_PASSWORD);
        config.setValue(GlobalConfiguration.SERVER_HTTP_INCOMING_PORT, "2480");

        ArcadeDBServer server = new ArcadeDBServer(config);
        server.start();
        System.out.println(">>> ArcadeDB Embedded Server started on http://localhost:2480");

        // 2. Open or create the database
        Database db;
        if (!server.existsDatabase(DB_NAME)) {
            db = server.createDatabase(DB_NAME, ComponentFile.MODE.READ_WRITE);
            System.out.println(">>> Created new database: " + DB_NAME);
            initializeSchemaAndData(db);
        } else {
            db = server.getDatabase(DB_NAME);
            System.out.println(">>> Opened existing database: " + DB_NAME);
        }
        registerListeners(db);

        // 3. Keep the process alive so external tools / Gemini can query it
        System.out.println("\n========================================================");
        System.out.println(" Database is online and ready for queries!");
        System.out.println(" Web Studio: http://localhost:2480 (user: root, pass: " + ROOT_PASSWORD + ")");
        System.out.println(" Press ENTER in this console to shut down cleanly.");
        System.out.println("========================================================\n");

        new Scanner(System.in).nextLine();

        // 4. Clean shutdown
        System.out.println(">>> Shutting down ArcadeDB Server...");
        server.stop();
        System.out.println(">>> Stopped.");
    }

    private static void registerListeners(Database db) {
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
            System.out.println(">>> Registered BeforeRecordUpdateListener on Point type.");
        }
    }

    private static void initializeSchemaAndData(Database db) {
        db.transaction(() -> {
            Schema schema = db.getSchema();

            // Root Entity vertex type with unique name index
            VertexType entity = schema.getOrCreateVertexType("Entity");
            entity.getOrCreateProperty("name", PropertyType.STRING);
            schema.getOrCreateTypeIndex(Schema.INDEX_TYPE.LSM_TREE, true, "Entity", "name");

            // Location hierarchy inheriting from Entity
            VertexType location = (VertexType) schema.getOrCreateVertexType("Location").addSuperType(entity);

            // Spatial subtypes inheriting from Location
            schema.getOrCreateVertexType("Site").addSuperType(location);
            schema.getOrCreateVertexType("Building").addSuperType(location);
            schema.getOrCreateVertexType("Floor").addSuperType(location);
            schema.getOrCreateVertexType("Space").addSuperType(location);

            // Domain subtypes inheriting directly from Entity
            schema.getOrCreateVertexType("Equipment").addSuperType(entity);
            schema.getOrCreateVertexType("Point").addSuperType(entity);

            // Standalone vertex type (does not inherit from Entity)
            schema.getOrCreateVertexType("ConnectionPoint");

            // Edge types
            schema.getOrCreateEdgeType("HAS_PART");
            schema.getOrCreateEdgeType("LOCATED_IN");
            schema.getOrCreateEdgeType("CONNECTS_TO");
            schema.getOrCreateEdgeType("HAS_CONNECTION_POINT");
            schema.getOrCreateEdgeType("HAS_POINT");

            // 1. Site
            MutableVertex site = db.newVertex("Site")
                    .set("name", "NOR")
                    .set("displayName", "8 Normanby Road")
                    .set("address", "8 Normanby Road, Mount Eden, Auckland 1024, New Zealand")
                    .set("timezone", "Pacific/Auckland")
                    .save();

            // 2. Building
            MutableVertex building = db.newVertex("Building")
                    .set("name", "NOR_B01")
                    .set("displayName", "Main Building")
                    .save();
            site.newEdge("HAS_PART", building).save();

            // 3. Floors
            MutableVertex flGnd = db.newVertex("Floor")
                    .set("name", "NOR_B01_GND")
                    .set("displayName", "Ground Floor")
                    .save();
            building.newEdge("HAS_PART", flGnd).save();

            MutableVertex fl01 = db.newVertex("Floor")
                    .set("name", "NOR_B01_L01")
                    .set("displayName", "Level 1")
                    .save();
            building.newEdge("HAS_PART", fl01).save();

            // 4. Spaces - Ground Floor
            MutableVertex itRoom = db.newVertex("Space")
                    .set("name", "NOR_B01_GND_IT")
                    .set("displayName", "IT Room")
                    .set("brickType", "Brick:Server_Room")
                    .save();
            flGnd.newEdge("HAS_PART", itRoom).save();

            // 5. Spaces - Level 1
            MutableVertex kitchen = db.newVertex("Space")
                    .set("name", "NOR_B01_L01_KIT")
                    .set("displayName", "Level 1 Kitchen")
                    .set("brickType", "Brick:Kitchen")
                    .save();
            fl01.newEdge("HAS_PART", kitchen).save();

            MutableVertex cupboard01 = db.newVertex("Space")
                    .set("name", "NOR_B01_L01_KIT_S01")
                    .set("displayName", "Storage Cupboard 01")
                    .save();
            kitchen.newEdge("HAS_PART", cupboard01).save();

            MutableVertex meetingRoom = db.newVertex("Space")
                    .set("name", "NOR_B01_L01_MTG")
                    .set("displayName", "Meeting Room")
                    .set("brickType", "Brick:Conference_Room")
                    .save();
            fl01.newEdge("HAS_PART", meetingRoom).save();

            MutableVertex table = db.newVertex("Space")
                    .set("name", "NOR_B01_L01_MTG_TBL")
                    .set("displayName", "Meeting Room Table")
                    .save();
            meetingRoom.newEdge("HAS_PART", table).save();

            MutableVertex nwWall = db.newVertex("Space")
                    .set("name", "NOR_B01_L01_MTG_NWW")
                    .set("displayName", "North West Wall")
                    .save();
            meetingRoom.newEdge("HAS_PART", nwWall).save();

            System.out.println(">>> Seeded Normanby Road spatial hierarchy.");
        });
    }
}
