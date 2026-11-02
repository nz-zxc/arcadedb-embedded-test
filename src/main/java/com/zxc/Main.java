package com.zxc;

import com.arcadedb.ContextConfiguration;
import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Database;
import com.arcadedb.engine.ComponentFile;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
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
            // Ensure schema types, catalog, and splicer deployment exist
            initializeSchemaAndData(db);
        }
        SchemaManager.registerListeners(db);

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

    private static void initializeSchemaAndData(Database db) {
        // 1. Setup schema types and unique indexes
        SchemaManager.createSchema(db);

        // 2. Seed catalog blueprints if not already present
        ResultSet rsCat = db.query("cypher", "MATCH (t:EquipmentTemplate {name: 'novastar:h2'}) RETURN t");
        if (!rsCat.hasNext()) {
            CatalogSeeder.seedCatalog(db);
        }

        // 3. Seed spatial locations if not already present
        ResultSet rsLoc = db.query("cypher", "MATCH (s:Site {name: 'NOR'}) RETURN s");
        if (!rsLoc.hasNext()) {
            LocationSeeder.seedLocations(db);
        }

        // 4. Deploy NovaStar H2 splicer with cards and signal point
        ResultSet rsH2 = db.query("cypher", "MATCH (e:Equipment {name: 'NOR_B01_L01_MTG_H2'}) RETURN e");
        if (!rsH2.hasNext()) {
            deploySplicer(db);
        } else {
            // Remove any legacy POWER_2 artifacts on existing database instances
            db.transaction(() -> {
                db.command("cypher", "MATCH (cp:ConnectionPoint {name: 'NOR_B01_L01_MTG_H2_POWER_2'}) DETACH DELETE cp");
                db.command("cypher", "MATCH (pt:PortTemplate {name: 'POWER_2'}) DETACH DELETE pt");
            });
        }

        // 5. Run audit of the deployed infrastructure
        runAudit(db);
    }

    private static void deploySplicer(Database db) {
        db.transaction(() -> {
            DeviceFactory factory = new DeviceFactory(db);

            // 1. Locate Cupboard S01 Space
            ResultSet rsSpace = db.query("cypher", "MATCH (s:Space {name: 'NOR_B01_L01_KIT_S01'}) RETURN s");
            Vertex cupboard01 = rsSpace.hasNext() ? rsSpace.next().getVertex().orElse(null) : null;

            // 2. Instantiate NovaStar H2 Chassis located in Cupboard S01
            MutableVertex h2 = factory.instantiateEquipment(
                    "novastar:h2",
                    "NOR_B01_L01_MTG_H2",
                    "Meeting Room Video Splicer",
                    cupboard01,
                    null
            );

            // 3. Card in CTRL Slot
            factory.instantiateEquipment(
                    "novastar:h-control",
                    "NOR_B01_L01_MTG_H2_CTRL",
                    "H2 Main Control Card",
                    h2,
                    "CTRL"
            );

            // 4. Card in I-1 Slot (with Signal Point)
            MutableVertex inCard = factory.instantiateEquipment(
                    "novastar:h-1xhdmi20-in",
                    "NOR_B01_L01_MTG_H2_I01",
                    "H2 HDMI 2.0 Input Card (I-1)",
                    h2,
                    "I-1"
            );

            // Telemetry Signal Point attached to Input Card
            MutableVertex signalPoint = db.newVertex("Point")
                    .set("name", "NOR_B01_L01_MTG_H2_I01_SIG")
                    .set("displayName", "HDMI In Signal Status")
                    .set("type", "Brick:Signal_Status")
                    .set("currentValue", 1.0)
                    .save();
            inCard.newEdge("HAS_POINT", signalPoint).save();

            // 5. Card in O-5 Slot
            factory.instantiateEquipment(
                    "novastar:h-16xrj45-2xfiber-out",
                    "NOR_B01_L01_MTG_H2_O05",
                    "H2 RJ45/Fiber Output Card (O-5)",
                    h2,
                    "O-5"
            );

            System.out.println(">>> Main: Successfully deployed NovaStar H2 splicer with CTRL, I-1 (with Signal Point), and O-5 cards.");
        });
    }

    private static void runAudit(Database db) {
        System.out.println("\n========================================================");
        System.out.println("           TOPOLOGY & EQUIPMENT AUDIT REPORT           ");
        System.out.println("========================================================");

        // 1. Locations Audit
        ResultSet rsLoc = db.query("cypher",
                "MATCH (site:Site)-[:HAS_PART]->(b:Building)-[:HAS_PART]->(f:Floor)-[:HAS_PART]->(s:Space) " +
                "RETURN site.name AS site, b.name AS building, f.name AS floor, s.name AS space, s.displayName AS spaceName");
        System.out.println("\n--- Spatial Hierarchy ---");
        while (rsLoc.hasNext()) {
            Result r = rsLoc.next();
            System.out.printf("  [%s -> %s -> %s] Space: %s (\"%s\")%n",
                    r.getProperty("site"), r.getProperty("building"), r.getProperty("floor"),
                    r.getProperty("space"), r.getProperty("spaceName"));
        }

        // 2. Equipment Chassis & Cards Audit
        ResultSet rsEquip = db.query("cypher",
                "MATCH (chassis:Equipment)-[hp:HAS_PART]->(card:Equipment) " +
                "OPTIONAL MATCH (chassis)-[:LOCATED_IN]->(space:Space) " +
                "RETURN chassis.name AS chassis, space.name AS space, hp.slot AS slot, card.name AS card");
        System.out.println("\n--- Modular Equipment & Slots ---");
        while (rsEquip.hasNext()) {
            Result r = rsEquip.next();
            System.out.printf("  Chassis: %s (located in %s) | Slot [%s] -> Card: %s%n",
                    r.getProperty("chassis"), r.getProperty("space"),
                    r.getProperty("slot"), r.getProperty("card"));
        }

        // 3. Connection Points / Ports Audit
        ResultSet rsPorts = db.query("cypher",
                "MATCH (e:Equipment)-[:HAS_CONNECTION_POINT]->(cp:ConnectionPoint) " +
                "RETURN e.name AS equipment, count(cp) AS portCount");
        System.out.println("\n--- Concrete Connection Points per Equipment ---");
        while (rsPorts.hasNext()) {
            Result r = rsPorts.next();
            System.out.printf("  Device: %-30s | Spawned Ports: %s%n",
                    r.getProperty("equipment"), r.getProperty("portCount"));
        }

        // 4. Telemetry Points Audit
        ResultSet rsPoints = db.query("cypher",
                "MATCH (e:Equipment)-[:HAS_POINT]->(p:Point) " +
                "RETURN e.name AS equipment, p.name AS point, p.type AS brickType, p.currentValue AS val");
        System.out.println("\n--- Telemetry Points ---");
        while (rsPoints.hasNext()) {
            Result r = rsPoints.next();
            System.out.printf("  Equipment: %-28s | Point: %s (%s, val=%s)%n",
                    r.getProperty("equipment"), r.getProperty("point"),
                    r.getProperty("brickType"), r.getProperty("val"));
        }
        System.out.println("========================================================\n");
    }
}
