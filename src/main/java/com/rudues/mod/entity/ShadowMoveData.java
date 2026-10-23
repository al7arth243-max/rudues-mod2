package com.rudues.mod.entity;

/**
 * Per-tick gameplay data for the Infernal Shadow's "Hellscrape" (generated from
 * animations/entity/infernal_shadow.animation.json - do not edit by hand).
 * Positions are in blocks in the Shadow's own frame: forward, right, up from its feet.
 */
public final class ShadowMoveData {
    private ShadowMoveData() {
    }

    /** last tick of the move (animation length 2.48 s) */
    public static final int END_TICK = 50;
    /** the sprint begins (first foot strike) */
    public static final int SPRINT_TICK = 4;
    /** the brake: steering locks, the Shadow skids into its coil */
    public static final int BRAKE_TICK = 27;
    /** the blade tears out of the floor */
    public static final int RELEASE_TICK = 31;
    /** the rising slash connects during these ticks */
    public static final int SLASH_FROM = 32;
    public static final int SLASH_TO = 33;
    /** apex of the slash */
    public static final int APEX_TICK = 34;
    /** distance covered at speed scale 1 (blocks) */
    public static final float NOMINAL_TRAVEL = 7.172F;
    /** walk loop: ground speed of the feet at animation speed 1 (blocks per second) */
    public static final float STALK_SPEED = 1.056F;

    /** ground speed per tick (blocks/tick) at speed scale 1 */
    public static final float[] SPEED = {0.000F, 0.000F, 0.040F, 0.121F, 0.174F, 0.201F, 0.227F, 0.254F, 0.280F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.293F, 0.288F, 0.186F, 0.095F, 0.024F, 0.000F, 0.070F, 0.104F, 0.078F, 0.040F, 0.006F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F};
    /** blade tip position (forward, right, up) */
    public static final float[] TIP_F = {-0.655F, -0.948F, -0.984F, -0.945F, -0.894F, -0.759F, -0.816F, -0.901F, -1.036F, -0.849F, -0.777F, -0.760F, -0.818F, -0.754F, -0.818F, -0.965F, -0.941F, -0.808F, -0.727F, -0.823F, -0.742F, -0.771F, -0.852F, -1.033F, -0.856F, -0.774F, -0.874F, -1.095F, -1.140F, -1.182F, -1.197F, -1.003F, 1.239F, -0.098F, -0.440F, 0.032F, 0.346F, 0.386F, 0.402F, 0.395F, 0.738F, 1.615F, 1.442F, 0.686F, 0.260F, -0.145F, -0.416F, -0.545F, -0.603F, -0.644F, -0.655F};
    public static final float[] TIP_R = {0.651F, 0.562F, 0.531F, 0.631F, 0.722F, 0.735F, 0.654F, 0.576F, 0.471F, 0.622F, 0.687F, 0.743F, 0.736F, 0.705F, 0.643F, 0.508F, 0.549F, 0.643F, 0.732F, 0.731F, 0.741F, 0.682F, 0.595F, 0.489F, 0.595F, 0.691F, 0.632F, 0.565F, 0.541F, 0.562F, 0.562F, 0.867F, 0.484F, 0.327F, 0.697F, 0.662F, 0.673F, 0.661F, 0.665F, 0.688F, 0.459F, 0.036F, 0.444F, 0.797F, 0.840F, 0.781F, 0.708F, 0.680F, 0.671F, 0.658F, 0.651F};
    public static final float[] TIP_U = {0.025F, 0.023F, 0.020F, 0.023F, 0.026F, 0.019F, 0.018F, 0.021F, 0.025F, 0.019F, 0.018F, 0.019F, 0.022F, 0.017F, 0.018F, 0.023F, 0.022F, 0.018F, 0.017F, 0.023F, 0.018F, 0.018F, 0.019F, 0.026F, 0.020F, 0.017F, 0.018F, 0.024F, 0.024F, 0.025F, 0.025F, 0.026F, 0.355F, 3.219F, 3.099F, 3.072F, 2.932F, 2.911F, 2.904F, 2.898F, 2.901F, 2.110F, 0.732F, 0.298F, 0.156F, 0.083F, 0.082F, 0.080F, 0.041F, 0.025F, 0.025F};
    /** the tip is scraping the floor */
    public static final boolean[] CONTACT = {false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false};
}
