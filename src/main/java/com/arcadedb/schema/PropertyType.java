package com.arcadedb.schema;

/**
 * Convenience compatibility bridge mapping PropertyType constants to ArcadeDB's Type enum.
 */
public final class PropertyType {
    public static final Type BOOLEAN = Type.BOOLEAN;
    public static final Type INTEGER = Type.INTEGER;
    public static final Type SHORT = Type.SHORT;
    public static final Type LONG = Type.LONG;
    public static final Type FLOAT = Type.FLOAT;
    public static final Type DOUBLE = Type.DOUBLE;
    public static final Type DATETIME = Type.DATETIME;
    public static final Type STRING = Type.STRING;
    public static final Type BINARY = Type.BINARY;
    public static final Type LIST = Type.LIST;
    public static final Type MAP = Type.MAP;
    public static final Type LINK = Type.LINK;
    public static final Type BYTE = Type.BYTE;
    public static final Type DATE = Type.DATE;
    public static final Type DECIMAL = Type.DECIMAL;
    public static final Type EMBEDDED = Type.EMBEDDED;

    private PropertyType() {
    }
}
