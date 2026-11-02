# Digital Twin Project Context - 8 Normanby Road (NOR)

## 1. Project Overview
A semantic Digital Twin application built on embedded ArcadeDB to model, visualize, audit, and interact with building facilities and AV/conferencing infrastructure.

## 2. Core Architecture & Ontology
* **Root Inheritance & Unique Indexing:**
  - Root vertex `Entity` with a globally unique `LSM_TREE` index on property `name`.
  - Standalone vertex `ConnectionPoint` with a globally unique `LSM_TREE` index on property `name` for $O(1)$ port lookups.
* **Niagara Tagging Standard:** Strict dot/underscore hierarchical tags: `{Site}_{Building}_{Floor}_{Space}_{Equipment}_{SubPart}`.
* **Port vs. Jack Tagging Rule ("J" vs. "P"):**
  - `J` denotes modular / keystone jacks on patch panels or wallplates (e.g., `U11J09` = Patch Panel at U11, Keystone Jack 09).
  - `P` denotes active network switch or device ports (e.g., `SW01_P01` = Switch 01, Port 01).
* **Spatial Hierarchy:**
  - `Site`: `NOR` (8 Normanby Road, Auckland)
  - `Building`: `NOR_B01` (Main Building)
  - `Floor`: `NOR_B01_GND` (Ground Floor), `NOR_B01_L01` (Level 1)
  - `Space`:
    - `NOR_B01_GND_IT` (IT Room) - `brickType: "Brick:Server_Room"`
    - `NOR_B01_L01_KIT` (Level 1 Kitchen) - `brickType: "Brick:Kitchen"`
    - `NOR_B01_L01_KIT_S01` (Kitchen Storage Cupboard 01)
    - `NOR_B01_L01_MTG` (Meeting Room) - `brickType: "Brick:Conference_Room"`
    - `NOR_B01_L01_MTG_TBL` (Meeting Room Table)
    - `NOR_B01_L01_MTG_NWW` (North West Wall)
* **Relationship Semantics:**
  - `HAS_PART`: Structural containment and modular slots.
    - Rack to Equipment: metadata properties `uPosition` (e.g., `11`, `14`) and `rackUnits` (e.g., `1`, `2`).
    - Chassis / Panel to Module / Jack: metadata property `slot` (e.g., `"06"`, `"09"`, `"CTRL"`, `"I-1"`, `"O-5"`).
  - `LOCATED_IN`: Physical placement of equipment within a spatial container (Equipment -> Space / Sub-space).
  - `INSTANCE_OF`: Concrete hardware / port to catalog blueprint reference (`Equipment -> EquipmentTemplate`, `ConnectionPoint -> PortTemplate`).
  - `HAS_PORT_TEMPLATE`: EquipmentTemplate to PortTemplate blueprint.
  - `HAS_SLOT_TEMPLATE`: EquipmentTemplate to SlotTemplate blueprint.
  - `HAS_CONNECTION_POINT`: Concrete ports attached to Equipment or Cables.
  - `CONNECTS_TO`: Internal port-to-port signal continuity and pass-through paths (e.g. Keystone `FRONT_RJ45 -> REAR_IDC`, HDMI In -> HDMI Loop, Genlock In -> Genlock Loop).
  - `MATES_WITH`: Physical mating between male cable ends and female jack/equipment connection points.
  - `HAS_POINT`: Equipment to telemetry `Point` vertex (e.g., `Brick:Signal_Status`).
  - `HAS_CONFIGURATION`: Equipment to ConfigurationBackup vertex.
* **Canonical Edge Direction Rule (Anti-Loop & Path Uniqueness):**
  - Physical junctions are modeled using **single canonical directed edges** and queried undirectionally (`-[:CONNECTS_TO|MATES_WITH]-`).
  - **Rationale:** openCypher path-finding enforces relationship uniqueness, not vertex uniqueness. Anti-parallel edges ($A \rightarrow B$ and $B \rightarrow A$) cause exponential path duplication ($2^N$ duplicate paths for the same sequence) and allow ping-pong bounce loops ($A \rightarrow B \rightarrow A$) in variable-length queries.
  - **Standard Canonical Orientations:**
    - **Cables (`CONNECTS_TO`):** Always directed from `End A -> End B`.
    - **Keystone Jacks (`CONNECTS_TO`):** Always directed from `FRONT_RJ45 -> REAR_IDC`.
    - **Passive Loop-Outs (`CONNECTS_TO`):** Always directed from `IN -> LOOP`.
    - **Interface Mating (`MATES_WITH`):** Always directed from `Cable End (Male) -> Device/Jack Port (Female)`.
* **Cable & Interconnect Schema:**
  - `Cable` vertex inherits from `Entity` (`name`, `displayName`, `partNumber`, `mediaType`, `color`, `wiringStandard`, `lengthMeters`).
  - Connection Model: A `Cable` has two concrete `ConnectionPoint` ends (`_A` and `_B`, gender `MALE`) linked via `HAS_CONNECTION_POINT`.
  - Internal Continuity: The two ends of a cable are connected via a single canonical `CONNECTS_TO` edge (`End A -> End B`), representing the conductive media.
  - Mating Model: Male cable ends connect to female device or jack ports via directed `MATES_WITH` edges (`Cable End -> Jack/Device Port`).
  - Unified Signal Traversal: Alternating between `MATES_WITH` (inter-asset mating) and `CONNECTS_TO` (intra-asset continuity) enables end-to-end signal tracing across devices, patch panels, and cables in a single Cypher expression:
    `MATCH path = (start:ConnectionPoint)-[:MATES_WITH|CONNECTS_TO*]-(end:ConnectionPoint) RETURN path`

## 3. Modular Equipment & UI Strategy
* **Template vs. Instance:** Blueprints defined in `EquipmentTemplate`, `SlotTemplate`, and `PortTemplate`. Concrete hardware instantiated via `DeviceFactory` into `Equipment` and `ConnectionPoint`.
* **DeviceFactory Capabilities:**
  - **Polymorphic Placement:** Automatically detects parent vertex type (`Location`/`Space` $\rightarrow$ creates `LOCATED_IN`, `Equipment` $\rightarrow$ creates `HAS_PART`).
  - **Strict Slot Validation:** Validates requested slot names against the parent template's `HAS_SLOT_TEMPLATE` definitions, rejecting typos or invalid slots with an `IllegalArgumentException`.
  - **Port Materialization:** Automatically iterates `HAS_PORT_TEMPLATE` to spawn `{Equipment.name}_{PortTemplate.name}` connection points copying connector types, direction, gender, signal types, panel faces, and coordinates.
  - **Internal Signal Continuity Propagation:** Automatically replicates template-level `CONNECTS_TO` edges across concrete `ConnectionPoint` instances (enabling unbroken signal graph traversals).
  - **Safe Execution:** Uses parameterized Cypher queries (`$name` bindings) for execution safety and query cache efficiency.
* **Catalog Blueprints:**
  - `novastar:h2`: NovaStar H2 Video Wall Splicer Chassis (2RU) with slots `CTRL`, `I-1`..`I-4`, `O-1`..`O-6`, and single AC power inlet.
  - `novastar:h-control`: Main Control Card with Ethernet, Genlock In/Loop (`CONNECTS_TO`), USB, and COM ports.
  - `novastar:h-1xhdmi20-in`: 1x HDMI 2.0 Input Card with HDMI In to Loop pass-through (`CONNECTS_TO`).
  - `novastar:h-16xrj45-2xfiber-out`: 16x RJ45 LED pixel outputs and 2x SFP+ optical ports.
  - `dynamix:ror47b`: Dynamix ROR47B 47RU Open Rack (543.0 x 2230.0 x 280.0 mm, static load 200 kg, ANSI/EIA RS310-D).
  - `dynamix:pp-ukstp-24rmb`: Dynamix 24-Port 1RU Keystone Patch Panel (482.6 x 44.45 x 150.0 mm) with 24 `KEYSTONE_APERTURE` slots (`01` through `24`).
  - `dynamix:kj-cat6`: Dynamix Cat6 RJ45 Keystone Jack with canonical `FRONT_RJ45 -> REAR_IDC` internal signal continuity (`CONNECTS_TO`).
  - `jaycar:yn8054`: Jaycar Triple Keystone Wall Plate White (70.0 x 114.0 x 6.0 mm, standard flush mounting) with 3 `KEYSTONE_APERTURE` slots (`01`, `02`, `03`).
* **Visual Rendering:** Coordinate system in millimeters (`xOffsetMm`, `yOffsetMm`) relative to panel faces (`FRONT`, `REAR`). Standard SVG symbols (`svg:hdmi-female`, `svg:rj45`, etc.) referenced for dynamic panel generation in web UIs.
* **Telemetry & Timeseries:**
  - ArcadeDB native timeseries engine ingests dynamic operational states (e.g., video signal present, resolution) tied to static `Point` vertices.
  - `BeforeRecordUpdateListener` validates `currentValue` on `Point` records, rejecting out-of-range values ($[-50.0, 100.0]$) with an explicit `IllegalArgumentException`.
  - Event listeners are in-memory Java runtime hooks re-registered via `SchemaManager.registerListeners(db)` upon database open and creation.

## 4. Current Deployment State
* **Chassis:** `NOR_B01_L01_MTG_H2` (NovaStar H2 Video Wall Splicer, 2RU)
  - Physical Location: `NOR_B01_L01_KIT_S01` (`LOCATED_IN`)
  - Power: Single IEC C14 AC inlet (`AC_POWER`)
* **Installed Daughtercards:**
  - Slot `CTRL`: `NOR_B01_L01_MTG_H2_CTRL` (`novastar:h-control`) with Ethernet, Genlock, USB, and COM ports.
  - Slot `I-1`: `NOR_B01_L01_MTG_H2_I01` (`novastar:h-1xhdmi20-in`) with 1x HDMI 2.0 In and `NOR_B01_L01_MTG_H2_I01_SIG` signal status Point.
  - Slot `O-5`: `NOR_B01_L01_MTG_H2_O05` (`novastar:h-16xrj45-2xfiber-out`) with 16x RJ45 LED pixel outputs and 2x SFP+ optical ports.
* **Racks & Patch Panels:**
  - `NOR_B01_GND_IT_R02` (Dynamix ROR47B 47RU Open Rack):
    - Physical Location: `NOR_B01_GND_IT` (`LOCATED_IN`)
    - Installed Panels & Jacks:
      - Slot U11 (`uPosition=11, rackUnits=1`): Patch Panel `NOR_B01_GND_IT_R02_U11_PP`
        - Slot `09`: Keystone Jack `NOR_B01_GND_IT_R02_U11_PP_J09` ("PP U11 Jack 09 (From Meeting Room Table)")
      - Slot U14 (`uPosition=14, rackUnits=1`): Patch Panel `NOR_B01_GND_IT_R02_U14_PP`
        - Slot `06`: Keystone Jack `NOR_B01_GND_IT_R02_U14_PP_J06` ("PP U14 Jack 06 (To Kitchen Cupboard S01)")
* **Field Wall Plates & Outlets:**
  - `NOR_B01_L01_KIT_S01_WP01` (Jaycar YN8054 Triple Keystone Wall Plate):
    - Physical Location: `NOR_B01_L01_KIT_S01` (`LOCATED_IN`)
    - Installed Jacks:
      - Slot `03`: Keystone Jack `NOR_B01_L01_KIT_S01_WP01_J03` ("Cupboard S01 Jack 03 (From Rack R02 U14:06)")
        - `FRONT_RJ45`: Female RJ45 front port
        - `REAR_IDC`: Female 110/Krone IDC punchdown
* **Physical Cabling & Interconnects:**
  - `NOR_B01_GND_IT_R02_PL_U11J09_U14J06`:
    - Display Name: "Dynamix 0.5m Cat6 Yellow UTP Patch Lead (T568A)"
    - Part Number: `PLY-C6A-0.5`, Media: `CAT6_UTP_PATCH`, Color: `YELLOW`, Length: `0.5m`, Wiring: `T568A`
    - End A (`_A`): RJ45 Male `MATES_WITH` -> `NOR_B01_GND_IT_R02_U11_PP_J09_FRONT_RJ45` (U11 Jack 09 Front)
    - End B (`_B`): RJ45 Male `MATES_WITH` -> `NOR_B01_GND_IT_R02_U14_PP_J06_FRONT_RJ45` (U14 Jack 06 Front)
  - `NOR_B01_CBL_R02_U14J06_KIT_S01_WP01J03`:
    - Display Name: "Generic 25m Cat6 UTP Solid Horizontal Cable (Blue)"
    - Part Number: `GEN-C6-UTP-SOLID`, Media: `CAT6_UTP_SOLID`, Color: `BLUE`, Length: `25.0m`, Wiring: `T568A`
    - End A (`_A`): 110/Krone IDC Male `MATES_WITH` -> `NOR_B01_GND_IT_R02_U14_PP_J06_REAR_IDC` (U14 Jack 06 Rear)
    - End B (`_B`): 110/Krone IDC Male `MATES_WITH` -> `NOR_B01_L01_KIT_S01_WP01_J03_REAR_IDC` (Cupboard S01 Jack 03 Rear)
* **Verified End-to-End Signal Path Query:**
  ```cypher
  // Traces full physical continuity from U11 Jack 09 Rear IDC through Patch Lead and Horizontal Cable to Cupboard S01 Jack 03 Front RJ45
  MATCH path = (start:ConnectionPoint {name: 'NOR_B01_GND_IT_R02_U11_PP_J09_REAR_IDC'})
               -[:MATES_WITH|CONNECTS_TO*1..12]-
               (dest:ConnectionPoint {name: 'NOR_B01_L01_KIT_S01_WP01_J03_FRONT_RJ45'})
  RETURN [n in nodes(path) | n.name] AS portSequence,
         [r in relationships(path) | type(r)] AS relationshipSequence
  LIMIT 1
  ```
  - **Sequence (9 alternating hops):**
    `U11_J09_REAR_IDC` $\xrightarrow{\text{CONNECTS\_TO}}$ `U11_J09_FRONT_RJ45` $\xrightarrow{\text{MATES\_WITH}}$ `PatchLead_A` $\xrightarrow{\text{CONNECTS\_TO}}$ `PatchLead_B` $\xrightarrow{\text{MATES\_WITH}}$ `U14_J06_FRONT_RJ45` $\xrightarrow{\text{CONNECTS\_TO}}$ `U14_J06_REAR_IDC` $\xrightarrow{\text{MATES\_WITH}}$ `HorizCbl_A` $\xrightarrow{\text{CONNECTS\_TO}}$ `HorizCbl_B` $\xrightarrow{\text{MATES\_WITH}}$ `WP01_J03_REAR_IDC` $\xrightarrow{\text{CONNECTS\_TO}}$ `WP01_J03_FRONT_RJ45`
