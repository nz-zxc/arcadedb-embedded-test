# Digital Twin Project Context - 8 Normanby Road (NOR)

## 1. Project Overview
A semantic Digital Twin application built on embedded ArcadeDB to model, visualize, audit, and interact with building facilities and AV/conferencing infrastructure.

## 2. Core Architecture & Ontology
* **Root Inheritance:** Root vertex `Entity` with a globally unique LSM_TREE index on property `name`.
* **Niagara Tagging Standard:** Strict dot/underscore hierarchical tags: `{Site}_{Building}_{Floor}_{Space}_{Equipment}_{SubPart}`.
* **Spatial Hierarchy:**
  - `Site`: `NOR` (8 Normanby Road, Auckland)
  - `Building`: `NOR_B01` (Main Building)
  - `Floor`: `NOR_B01_GND` (Ground Floor), `NOR_B01_L01` (Level 1)
  - `Space`:
    - `NOR_B01_GND_IT` (IT Room)
    - `NOR_B01_L01_KIT` (Level 1 Kitchen)
    - `NOR_B01_L01_KIT_S01` (Kitchen Storage Cupboard 01)
    - `NOR_B01_L01_MTG` (Meeting Room)
    - `NOR_B01_L01_MTG_TBL` (Meeting Room Table)
    - `NOR_B01_L01_MTG_NWW` (North West Wall)
* **Relationship Semantics:**
  - `HAS_PART`: Structural/chassis containment (Floor -> Room, Room -> Sub-space, Chassis -> Modular Card).
  - `LOCATED_IN`: Physical placement of equipment within a spatial container (e.g., Equipment -> Space).
  - `INSTANCE_OF`: Equipment to EquipmentTemplate catalog reference.
  - `HAS_PORT_TEMPLATE`: EquipmentTemplate to PortTemplate blueprint.
  - `HAS_CONNECTION_POINT`: Concrete ports attached to Equipment or Cables.
  - `MATES_WITH`: Physical mating between male and female ConnectionPoints.
  - `HAS_POINT`: Equiment/Port to telemetry Point vertex.
  - `HAS_CONFIGURATION`: Equipment to ConfigurationBackup vertex.

## 3. Modular Equipment & UI Strategy
* **Template vs. Instance:** Blueprints defined in `EquipmentTemplate` and `PortTemplate`. Concrete hardware instantiated via `DeviceFactory` into `Equipment` and `ConnectionPoint`.
* **Visual Rendering:** Coordinate system in millimeters (`xOffsetMm`, `yOffsetMm`) relative to panel faces (`FRONT`, `REAR`). Standard SVG symbols (`svg:hdmi-female`, `svg:rj45`, etc.) referenced for dynamic panel generation in web UIs.
* **Telemetry & Timeseries:** ArcadeDB native timeseries engine ingests dynamic operational states (e.g., video signal present, resolution) tied to static `Point` vertices.

## 4. Current Deployment State
* **Chassis:** `NOR_B01_L01_MTG_H2` (NovaStar H2 Video Wall Splicer, 2RU)
  - Physical Location: `NOR_B01_L01_KIT_S01` (`LOCATED_IN`)
  - Power: Single IEC C14 AC inlet (`AC_POWER`)
* **Installed Daughtercards:**
  - Slot `CTRL`: `NOR_B01_L01_MTG_H2_CTRL` (`novastar:h-control`) with Ethernet, Genlock, USB, and COM ports.
  - Slot `I-1`: `NOR_B01_L01_MTG_H2_I01` (`novastar:h-1xhdmi20-in`) with 1x HDMI 2.0 In and `NOR_B01_L01_MTG_H2_I01_SIG` signal status Point.
  - Slot `O-5`: `NOR_B01_L01_MTG_H2_O05` (`novastar:h-16xrj45-2xfiber-out`) with 16x RJ45 LED pixel outputs and 2x SFP+ optical ports.
