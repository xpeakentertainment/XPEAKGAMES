package com.xpeak.enderite.robot;

import net.minecraft.ChatFormatting;

/** Nucleo del torso robot. */
public enum RobotCore {
    BLUE("azul", 0x3FA9FF, ChatFormatting.AQUA),
    RED("rojo", 0xFF4040, ChatFormatting.RED),
    GREEN("verde", 0x4CFF6A, ChatFormatting.GREEN);

    public final String id;
    public final int color;
    public final ChatFormatting format;

    RobotCore(String id, int color, ChatFormatting format) {
        this.id = id;
        this.color = color;
        this.format = format;
    }
}
