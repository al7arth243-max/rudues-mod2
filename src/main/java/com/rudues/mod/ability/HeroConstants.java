package com.rudues.mod.ability;

/** Shared (client + server) numbers for the Hero of Hell form. Plain constants only. */
public class HeroConstants {
    public static final int GAUGE_MAX = 100;
    /** How long the demon form lasts: 60 seconds. */
    public static final int DEMON_TICKS = 1200;

    /** The demon is 7 blocks tall. */
    public static final float DEMON_HEIGHT = 7.0F;
    public static final float DEMON_WIDTH = 2.2F;
    public static final float DEMON_EYE = 6.0F;
    public static final float DEMON_SCALE = 1.314F;  // titan model is 85.2 units (5.33 blocks) tall at scale 1 -> 7.0 blocks
    /** third-person camera is pulled this far back while a demon (on top of vanilla's 4 blocks) */
    public static final double CAMERA_EXTRA = 5.0;

    /** the breath beam leaves his MOUTH: this high above his feet and this far ahead along the aim */
    public static final double BEAM_Y = 4.3;
    public static final double BEAM_FORWARD = 1.8;
}
