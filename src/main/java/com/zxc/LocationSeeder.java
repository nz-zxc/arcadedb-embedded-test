package com.zxc;

import com.arcadedb.database.Database;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.query.sql.executor.ResultSet;

public class LocationSeeder {

    public static void seedLocations(Database db) {
        db.transaction(() -> {
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

            // Deploy Rack R02 in IT Room
            ResultSet rsRackTpl = db.query("cypher", "MATCH (t:EquipmentTemplate {name: 'dynamix:ror47b'}) RETURN t");
            if (rsRackTpl.hasNext()) {
                DeviceFactory factory = new DeviceFactory(db);
                factory.instantiateEquipment("dynamix:ror47b", "NOR_B01_GND_IT_R02", "IT Room Rack R02", itRoom, null);
            }

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

            System.out.println(">>> LocationSeeder: Seeded Normanby Road spatial hierarchy (NOR, NOR_B01, GND/L01, Spaces).");
        });
    }
}
