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
    public static final float DEMON_SCALE = 1.295F;  // titan model is 86.5 units (5.41 blocks) tall at scale 1 -> 7.0 blocks
    /** front-facing (F5 twice) camera is pulled this far back while a demon (on top of vanilla's 4 blocks) */
    public static final double CAMERA_EXTRA = 5.0;
    /**
     * Over-the-shoulder camera (normal third person): pivot this high above his feet and this far to his right,
     * camera this far back from the pivot along the view. Tuned so he fills the left of the screen.
     */
    public static final double CAM_HEIGHT = 4.7;
    public static final double CAM_SIDE = 3.6;
    public static final double CAM_DISTANCE = 5.0;

    /** the breath beam leaves his MOUTH: this high above his feet and this far ahead along the aim */
    public static final double BEAM_Y = 4.2;        // the mouth (head bone + muzzle offset) while firing, measured on the current model
    public static final double BEAM_FORWARD = 1.5;
}
