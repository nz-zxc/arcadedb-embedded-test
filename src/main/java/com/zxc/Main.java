package com.zxc;

import com.arcadedb.ContextConfiguration;
import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Database;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.VertexType;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.engine.ComponentFile;

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
        db.transaction(() -> {
            Schema schema = db.getSchema();

            // Create Vertex Types
            schema.getOrCreateVertexType("Equipment");
            schema.getOrCreateVertexType("ConnectionPoint");
            schema.getOrCreateVertexType("Point");

            // Create Edge Types
            schema.getOrCreateEdgeType("CONNECTS_TO");
            schema.getOrCreateEdgeType("HAS_CONNECTION_POINT");
            schema.getOrCreateEdgeType("HAS_POINT");

            // Seed sample entities
            MutableVertex ahu = db.newVertex("Equipment")
                    .set("name", "AHU-01")
                    .set("type", "Brick:Air_Handling_Unit")
                    .save();

            MutableVertex vav = db.newVertex("Equipment")
                    .set("name", "VAV-101")
                    .set("type", "Brick:Variable_Air_Volume_Box")
                    .save();

            MutableVertex supplyPort = db.newVertex("ConnectionPoint")
                    .set("name", "AHU-01-SupplyAirPort")
                    .set("direction", "Outlet")
                    .save();

            MutableVertex ahuDat = db.newVertex("Point")
                    .set("name", "AHU-01-DAT")
                    .set("type", "Brick:Discharge_Air_Temperature_Sensor")
                    .set("unit", "degC")
                    .save();

            // Establish topology edges
            ahu.newEdge("HAS_CONNECTION_POINT", supplyPort).save();
            supplyPort.newEdge("CONNECTS_TO", vav).save();
            ahu.newEdge("HAS_POINT", ahuDat).save();

            System.out.println(">>> Seed data created: AHU-01 -> Port -> VAV-101, AHU-01 -> Point (AHU-01-DAT)");
        });
    }
}
