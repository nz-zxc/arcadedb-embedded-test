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

import java.util.Map;
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
        ResultSet rsCat = db.query("cypher", "MATCH (t:EquipmentTemplate {name: 'jaycar:yn8054'}) RETURN t");
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

        // 5. Deploy Rack R02 if not present
        deployRackR02(db);

        // 6. Deploy Cupboard S01 Wall Plate
        deployCupboardWallPlate(db);

        // 7. Deploy Horizontal Building Cables
        deployHorizontalCables(db);

        // 8. Enforce single canonical directed edges (prevent duplicate paths in openCypher)
        enforceCanonicalEdges(db);

        // 9. Run audit of the deployed infrastructure
        runAudit(db);
    }

    private static void enforceCanonicalEdges(Database db) {
        db.transaction(() -> {
            // 1. Remove reverse anti-parallel cable edges (B -> A)
            db.command("cypher",
                    "MATCH (b:ConnectionPoint {portName: 'B'})-[r:CONNECTS_TO]->(a:ConnectionPoint {portName: 'A'}) " +
                    "DELETE r");

            // 2. Remove reverse anti-parallel keystone jack edges (Rear IDC -> Front RJ45)
            db.command("cypher",
                    "MATCH (rear:ConnectionPoint)-[r:CONNECTS_TO]->(front:ConnectionPoint) " +
                    "WHERE rear.name ENDS WITH '_REAR_IDC' AND front.name ENDS WITH '_FRONT_RJ45' " +
                    "DELETE r");

            // 3. Remove reverse anti-parallel template edges
            db.command("cypher",
                    "MATCH (rear:PortTemplate {name: 'REAR_IDC'})-[r:CONNECTS_TO]->(front:PortTemplate {name: 'FRONT_RJ45'}) " +
                    "DELETE r");
        });
    }

    private static void deployHorizontalCables(Database db) {
        ResultSet rsCable = db.query("cypher",
                "MATCH (c:Cable {name: 'NOR_B01_CBL_R02_U14J06_KIT_S01_WP01J03'}) RETURN c");
        if (!rsCable.hasNext()) {
            db.transaction(() -> {
                DeviceFactory.createCable(
                        db,
                        "NOR_B01_CBL_R02_U14J06_KIT_S01_WP01J03",
                        "Generic 25m Cat6 UTP Solid Horizontal Cable (Blue)",
                        "GEN-C6-UTP-SOLID",
                        "CAT6_UTP_SOLID",
                        "BLUE",
                        "T568A",
                        25.0,
                        "IDC_110_KRONE",
                        "NOR_B01_GND_IT_R02_U14_PP_J06_REAR_IDC",
                        "IDC_110_KRONE",
                        "NOR_B01_L01_KIT_S01_WP01_J03_REAR_IDC"
                );
                System.out.println(">>> Main: Successfully deployed horizontal cable from Rack R02 U14:06 to Cupboard S01 WP01:03.");
            });
        }
    }

    private static void deployCupboardWallPlate(Database db) {
        ResultSet rsJ03 = db.query("cypher", "MATCH (e:Equipment {name: 'NOR_B01_L01_KIT_S01_WP01_J03'}) RETURN e");
        if (!rsJ03.hasNext()) {
            db.transaction(() -> {
                // Remove legacy J01 if previously instantiated
                db.command("cypher", "MATCH (j:Equipment {name: 'NOR_B01_L01_KIT_S01_WP01_J01'}) DETACH DELETE j");
                db.command("cypher", "MATCH (cp:ConnectionPoint) WHERE cp.name STARTS WITH 'NOR_B01_L01_KIT_S01_WP01_J01_' DETACH DELETE cp");

                DeviceFactory factory = new DeviceFactory(db);

                // 1. Locate Cupboard S01 Space
                ResultSet rsSpace = db.query("cypher", "MATCH (s:Space {name: 'NOR_B01_L01_KIT_S01'}) RETURN s");
                Vertex cupboard01 = rsSpace.hasNext() ? rsSpace.next().getVertex().orElse(null) : null;

                // 2. Instantiate or retrieve Jaycar YN8054 Triple Keystone Wall Plate in Cupboard S01
                ResultSet rsWp = db.query("cypher", "MATCH (e:Equipment {name: 'NOR_B01_L01_KIT_S01_WP01'}) RETURN e");
                MutableVertex wp;
                if (rsWp.hasNext()) {
                    wp = rsWp.next().getVertex().get().modify();
                } else {
                    wp = factory.instantiateEquipment(
                            "jaycar:yn8054",
                            "NOR_B01_L01_KIT_S01_WP01",
                            "Cupboard S01 Triple Keystone Wall Plate",
                            cupboard01,
                            null
                    );
                }

                // 3. Insert Cat6 Keystone Jack into slot "03"
                factory.instantiateEquipment(
                        "dynamix:kj-cat6",
                        "NOR_B01_L01_KIT_S01_WP01_J03",
                        "Cupboard S01 Jack 03 (From Rack R02 U14:06)",
                        wp,
                        "03"
                );

                System.out.println(">>> Main: Successfully deployed Wall Plate WP01 with Jack 03 in Cupboard S01.");
            });
        }
    }

    private static void deployRackR02(Database db) {
        ResultSet rsU11 = db.query("cypher", "MATCH (e:Equipment {name: 'NOR_B01_GND_IT_R02_U11_PP'}) RETURN e");
        if (!rsU11.hasNext()) {
            db.transaction(() -> {
                // Clean up partial R02 if previously created without panels
                db.command("cypher", "MATCH (e:Equipment {name: 'NOR_B01_GND_IT_R02'}) DETACH DELETE e");

                DeviceFactory factory = new DeviceFactory(db);

                // 1. Locate IT Room Space
                ResultSet rsSpace = db.query("cypher", "MATCH (s:Space {name: 'NOR_B01_GND_IT'}) RETURN s");
                Vertex itRoom = rsSpace.hasNext() ? rsSpace.next().getVertex().orElse(null) : null;

                // 2. Instantiate Rack R02 in IT Room
                MutableVertex r02 = factory.instantiateEquipment(
                        "dynamix:ror47b",
                        "NOR_B01_GND_IT_R02",
                        "IT Room Rack R02",
                        itRoom,
                        null
                );

                // 3. Patch Panel U11 on Rack R02 (uPosition=11, rackUnits=1)
                MutableVertex u11Pp = factory.instantiateEquipment(
                        "dynamix:pp-ukstp-24rmb",
                        "NOR_B01_GND_IT_R02_U11_PP",
                        "IT Room Patch Panel U11",
                        r02,
                        null,
                        Map.of("uPosition", 11, "rackUnits", 1)
                );

                // Keystone Jack in slot "09" of U11_PP
                factory.instantiateEquipment(
                        "dynamix:kj-cat6",
                        "NOR_B01_GND_IT_R02_U11_PP_J09",
                        "PP U11 Jack 09 (From Meeting Room Table)",
                        u11Pp,
                        "09"
                );

                // 4. Patch Panel U14 on Rack R02 (uPosition=14, rackUnits=1)
                MutableVertex u14Pp = factory.instantiateEquipment(
                        "dynamix:pp-ukstp-24rmb",
                        "NOR_B01_GND_IT_R02_U14_PP",
                        "IT Room Patch Panel U14",
                        r02,
                        null,
                        Map.of("uPosition", 14, "rackUnits", 1)
                );

                // Keystone Jack in slot "06" of U14_PP
                factory.instantiateEquipment(
                        "dynamix:kj-cat6",
                        "NOR_B01_GND_IT_R02_U14_PP_J06",
                        "PP U14 Jack 06 (To Kitchen Cupboard S01)",
                        u14Pp,
                        "06"
                );

                // 5. Connect U11 Jack 09 to U14 Jack 06 via Cat6 Patch Lead
                DeviceFactory.createCable(
                        db,
                        "NOR_B01_GND_IT_R02_PL_U11J09_U14J06",
                        "Dynamix 0.5m Cat6 Yellow UTP Patch Lead (T568A)",
                        "PLY-C6A-0.5",
                        "CAT6_UTP_PATCH",
                        "YELLOW",
                        "T568A",
                        0.5,
                        "RJ45",
                        "NOR_B01_GND_IT_R02_U11_PP_J09_FRONT_RJ45",
                        "RJ45",
                        "NOR_B01_GND_IT_R02_U14_PP_J06_FRONT_RJ45"
                );

                System.out.println(">>> Main: Successfully deployed Rack R02 with Patch Panels U11 (Port 09), U14 (Port 06), and Patch Lead.");
            });
        } else {
            ResultSet rsCable = db.query("cypher", "MATCH (c:Cable {name: 'NOR_B01_GND_IT_R02_PL_U11J09_U14J06'}) RETURN c");
            if (!rsCable.hasNext()) {
                db.transaction(() -> {
                    DeviceFactory.createCable(
                            db,
                            "NOR_B01_GND_IT_R02_PL_U11J09_U14J06",
                            "Dynamix 0.5m Cat6 Yellow UTP Patch Lead (T568A)",
                            "PLY-C6A-0.5",
                            "CAT6_UTP_PATCH",
                            "YELLOW",
                            "T568A",
                            0.5,
                            "RJ45",
                            "NOR_B01_GND_IT_R02_U11_PP_J09_FRONT_RJ45",
                            "RJ45",
                            "NOR_B01_GND_IT_R02_U14_PP_J06_FRONT_RJ45"
                    );
                });
            } else {
                // Ensure canonical internal continuity CONNECTS_TO edge exists between cable End A and End B
                db.transaction(() -> {
                    db.command("cypher",
                            "MATCH (a:ConnectionPoint {name: 'NOR_B01_GND_IT_R02_PL_U11J09_U14J06_A'}), " +
                            "      (b:ConnectionPoint {name: 'NOR_B01_GND_IT_R02_PL_U11J09_U14J06_B'}) " +
                            "WHERE NOT (a)-[:CONNECTS_TO]->(b) " +
                            "CREATE (a)-[:CONNECTS_TO]->(b)");
                });
            }
        }
    }

    private static void deploySplicer(Database db) {
        db.transaction(() -> {
            DeviceFactory factory = new DeviceFactory(db);

            // 1. Locate
            // Cupboard S01 Space
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

        // 2. Deployed Equipment & Racks Audit
        ResultSet rsRacks = db.query("cypher",
                "MATCH (eq:Equipment)-[:LOCATED_IN]->(space:Space) " +
                "RETURN eq.name AS name, eq.displayName AS displayName, eq.model AS model, space.name AS space");
        System.out.println("\n--- Deployed Equipment & Racks ---");
        while (rsRacks.hasNext()) {
            Result r = rsRacks.next();
            System.out.printf("  Equipment/Rack: %-25s (\"%s\", model=%s) located in Space: %s%n",
                    r.getProperty("name"), r.getProperty("displayName"),
                    r.getProperty("model"), r.getProperty("space"));
        }

        // 3. Equipment Chassis & Cards Audit
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

        // 4. Rack R02 & Patch Panel Infrastructure Audit
        ResultSet rsRackDetails = db.query("cypher",
                "MATCH (rack:Equipment {name: 'NOR_B01_GND_IT_R02'})-[:LOCATED_IN]->(space:Space) " +
                "RETURN rack.name AS rack, rack.displayName AS rackDesc, space.name AS space");
        System.out.println("\n--- Rack R02 Infrastructure & Patch Panels ---");
        if (rsRackDetails.hasNext()) {
            Result r = rsRackDetails.next();
            System.out.printf("  Rack: %s (\"%s\") located in Space: %s%n",
                    r.getProperty("rack"), r.getProperty("rackDesc"), r.getProperty("space"));
        }

        ResultSet rsPanels = db.query("cypher",
                "MATCH (rack:Equipment {name: 'NOR_B01_GND_IT_R02'})-[rU:HAS_PART]->(pp:Equipment)-[rSlot:HAS_PART]->(jack:Equipment) " +
                "OPTIONAL MATCH (jack)-[:HAS_CONNECTION_POINT]->(cp:ConnectionPoint) " +
                "RETURN pp.name AS panel, rU.uPosition AS uPos, rSlot.slot AS slot, jack.name AS jackName, " +
                "       jack.displayName AS jackDesc, cp.name AS portName, cp.connectorType AS connType, cp.panelFace AS face " +
                "ORDER BY uPos, slot, face");
        while (rsPanels.hasNext()) {
            Result r = rsPanels.next();
            System.out.printf("    [U%02d] Panel: %s | Slot [%s] -> Jack: %s (\"%s\") | Port: %s (%s, face=%s)%n",
                    (Integer) r.getProperty("uPos"), r.getProperty("panel"),
                    r.getProperty("slot"), r.getProperty("jackName"), r.getProperty("jackDesc"),
                    r.getProperty("portName"), r.getProperty("connType"), r.getProperty("face"));
        }

        // 5. Field Wall Plates & Outlets Audit
        ResultSet rsPlates = db.query("cypher",
                "MATCH (wp:Equipment)-[hp:HAS_PART]->(jack:Equipment) " +
                "WHERE wp.model = 'jaycar:yn8054' " +
                "OPTIONAL MATCH (wp)-[:LOCATED_IN]->(space:Space) " +
                "OPTIONAL MATCH (jack)-[:HAS_CONNECTION_POINT]->(cp:ConnectionPoint) " +
                "RETURN wp.name AS plate, space.name AS space, hp.slot AS slot, jack.name AS jackName, " +
                "       jack.displayName AS jackDesc, cp.name AS portName, cp.panelFace AS face " +
                "ORDER BY slot, face");
        System.out.println("\n--- Field Wall Plates & Outlets ---");
        while (rsPlates.hasNext()) {
            Result r = rsPlates.next();
            System.out.printf("  Plate: %s (located in %s) | Slot [%s] -> Jack: %s (\"%s\") | Port: %s (%s)%n",
                    r.getProperty("plate"), r.getProperty("space"),
                    r.getProperty("slot"), r.getProperty("jackName"), r.getProperty("jackDesc"),
                    r.getProperty("portName"), r.getProperty("face"));
        }

        // 6. Physical Cabling & Interconnects Audit
        ResultSet rsCables = db.query("cypher",
                "MATCH (c:Cable) " +
                "RETURN c.name AS name, c.displayName AS desc, c.partNumber AS partNum, " +
                "       c.color AS color, c.lengthMeters AS len, c.mediaType AS media");
        System.out.println("\n--- Physical Cabling & Interconnects ---");
        while (rsCables.hasNext()) {
            Result r = rsCables.next();
            String cName = r.getProperty("name");
            System.out.printf("  Cable: %s (\"%s\") | P/N: %s | Color: %s | Length: %sm | Media: %s%n",
                    cName, r.getProperty("desc"), r.getProperty("partNum"),
                    r.getProperty("color"), r.getProperty("len"), r.getProperty("media"));

            ResultSet rsMates = db.query("cypher",
                    "MATCH (c:Cable {name: '" + cName + "'})-[:HAS_CONNECTION_POINT]->(end:ConnectionPoint)-[:MATES_WITH]->(target:ConnectionPoint) " +
                    "RETURN end.portName AS endName, end.connectorType AS endConn, target.name AS targetPort " +
                    "ORDER BY endName");
            while (rsMates.hasNext()) {
                Result m = rsMates.next();
                System.out.printf("    End %s (%s) MATES_WITH -> %s%n",
                        m.getProperty("endName"), m.getProperty("endConn"), m.getProperty("targetPort"));
            }
        }

        // 6. Connection Points / Ports Audit
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
