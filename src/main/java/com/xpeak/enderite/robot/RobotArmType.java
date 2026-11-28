package com.xpeak.enderite.robot;

public enum RobotArmType {
    CHAINSAW("motosierra", 500),
    ROCKET("lanzacohetes", 300);

    public final String id;
    public final int durability;

    RobotArmType(String id, int durability) {
        this.id = id;
        this.durability = durability;
    }
}
