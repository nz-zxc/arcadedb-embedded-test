package com.zxc;

import com.arcadedb.database.Database;
import com.arcadedb.graph.MutableVertex;

public class CatalogSeeder {

    public static void seedCatalog(Database db) {
        db.transaction(() -> {
            DeviceFactory factory = new DeviceFactory(db);

            // 1. NovaStar H2 Modular Video Wall Splicer (Chassis)
            MutableVertex h2 = factory.registerTemplate(
                    "novastar:h2",
                    "Brick:Video_Processor",
                    "NovaStar H2 Video Wall Splicer Chassis"
            );
            // Slots on H2 Chassis
            factory.addSlot(h2, "CTRL", "Control", 0, 10.0, 10.0);
            factory.addSlot(h2, "I-1", "Input", 1, 30.0, 10.0);
            factory.addSlot(h2, "I-2", "Input", 2, 50.0, 10.0);
            factory.addSlot(h2, "I-3", "Input", 3, 70.0, 10.0);
            factory.addSlot(h2, "I-4", "Input", 4, 90.0, 10.0);
            factory.addSlot(h2, "O-1", "Output", 5, 30.0, 40.0);
            factory.addSlot(h2, "O-2", "Output", 6, 50.0, 40.0);
            factory.addSlot(h2, "O-3", "Output", 7, 70.0, 40.0);
            factory.addSlot(h2, "O-4", "Output", 8, 90.0, 40.0);
            factory.addSlot(h2, "O-5", "Output", 9, 110.0, 40.0);
            factory.addSlot(h2, "O-6", "Output", 10, 130.0, 40.0);
            // Chassis single power inlet
            factory.addPort(h2, "POWER_1", "IN", "IEC_C14", "MALE", "POWER_AC", 10.0, 80.0);

            // 2. NovaStar H_Control Card
            MutableVertex hControl = factory.registerTemplate(
                    "novastar:h-control",
                    "Brick:Controller",
                    "NovaStar H Series Main Control Card"
            );
            factory.addPort(hControl, "ETHERNET", "Bidirectional", "RJ45", 20.0, 10.0);
            MutableVertex glIn = factory.addPort(hControl, "GENLOCK_IN", "In", "BNC", 40.0, 10.0);
            MutableVertex glLoop = factory.addPort(hControl, "GENLOCK_LOOP", "Out", "BNC", 60.0, 10.0);
            glIn.newEdge("CONNECTS_TO", glLoop).save();
            factory.addPort(hControl, "RS232", "Bidirectional", "RJ45", 80.0, 10.0);

            // 3. NovaStar 1x HDMI 2.0 Input Card
            MutableVertex hdmiIn = factory.registerTemplate(
                    "novastar:h-1xhdmi20-in",
                    "Brick:Video_Input",
                    "NovaStar H Series 1x HDMI 2.0 Input Card"
            );
            MutableVertex hIn = factory.addPort(hdmiIn, "HDMI_IN", "In", "HDMI_2.0", 25.0, 15.0);
            MutableVertex hLoop = factory.addPort(hdmiIn, "HDMI_LOOP", "Out", "HDMI_2.0", 65.0, 15.0);
            hIn.newEdge("CONNECTS_TO", hLoop).save();

            // 4. NovaStar 16x RJ45 + 2x Fiber Output Card
            MutableVertex rj45FiberOut = factory.registerTemplate(
                    "novastar:h-16xrj45-2xfiber-out",
                    "Brick:Video_Output",
                    "NovaStar H Series 16x RJ45 + 2x Fiber Output Card"
            );
            // 16 RJ45 Ports in 2 rows of 8
            for (int i = 1; i <= 16; i++) {
                int col = (i - 1) % 8;
                int row = (i - 1) / 8;
                double px = 10.0 + (col * 12.0);
                double py = 10.0 + (row * 15.0);
                factory.addPort(rj45FiberOut, "RJ45_" + i, "Out", "RJ45", px, py);
            }
            // 2 Optical Fiber Ports
            factory.addPort(rj45FiberOut, "OPT_1", "Out", "OPTICAL_FIBER", 115.0, 10.0);
            factory.addPort(rj45FiberOut, "OPT_2", "Out", "OPTICAL_FIBER", 115.0, 25.0);

            // 5. Dynamix ROR47B 47RU Open Rack
            MutableVertex rack = factory.registerTemplate(
                    "dynamix:ror47b",
                    "Equipment_Rack",
                    "Dynamix ROR47B 47RU Open Rack"
            );
            rack.set("manufacturer", "Dynamix")
                    .set("model", "ROR47B 47RU Open Rack")
                    .set("deviceType", "Equipment_Rack")
                    .set("rackUnits", 47)
                    .set("widthMm", 543.0)
                    .set("heightMm", 2230.0)
                    .set("depthMm", 280.0)
                    .set("rearSvgBackground", "svg/racks/dynamix_ror47b_front.svg")
                    .set("staticLoadKg", 200.0)
                    .set("standards", "ANSI/EIA RS310-D")
                    .save();

            // 6. Dynamix 24-Port 1RU Keystone Patch Panel
            MutableVertex patchPanel = factory.registerTemplate(
                    "dynamix:pp-ukstp-24rmb",
                    "Patch_Panel",
                    "Dynamix PP-UKSTP-24RMB 24-Port 1RU Keystone Patch Panel"
            );
            patchPanel.set("manufacturer", "Dynamix")
                    .set("model", "PP-UKSTP-24RMB")
                    .set("deviceType", "Patch_Panel")
                    .set("rackUnits", 1)
                    .set("widthMm", 482.6)
                    .set("heightMm", 44.45)
                    .set("depthMm", 150.0)
                    .save();
            // 24 Keystone Aperture slots named "01" through "24"
            for (int i = 1; i <= 24; i++) {
                String slotName = String.format("%02d", i);
                double slotX = 10.0 + (i * 18.0);
                factory.addSlot(patchPanel, slotName, "KEYSTONE_APERTURE", i, slotX, 10.0);
            }

            // 7. Dynamix Cat6 RJ45 Keystone Jack
            MutableVertex keystoneJack = factory.registerTemplate(
                    "dynamix:kj-cat6",
                    "Modular_Jack",
                    "Dynamix Cat6 RJ45 Keystone Jack"
            );
            keystoneJack.set("manufacturer", "Dynamix")
                    .set("model", "Cat6 RJ45 Keystone Jack")
                    .set("deviceType", "Modular_Jack")
                    .save();
            MutableVertex frontPort = factory.addPort(keystoneJack, "FRONT_RJ45", "BIDIRECTIONAL", "RJ45", "FEMALE", "DATA_ETHERNET_OR_HDBASET", "FRONT", 10.0, 10.0);
            MutableVertex rearPort = factory.addPort(keystoneJack, "REAR_IDC", "BIDIRECTIONAL", "IDC_110_KRONE", "FEMALE", "DATA_RAW_TWISTED_PAIR", "REAR", 10.0, 10.0);
            frontPort.newEdge("CONNECTS_TO", rearPort).save();
            rearPort.newEdge("CONNECTS_TO", frontPort).save();

            System.out.println(">>> CatalogSeeder: Seeded blueprints for novastar:h2, cards, dynamix:ror47b, dynamix:pp-ukstp-24rmb, and dynamix:kj-cat6.");
        });
    }
}
