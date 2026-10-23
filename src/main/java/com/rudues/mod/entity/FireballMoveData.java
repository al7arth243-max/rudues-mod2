package com.rudues.mod.entity;

/**
 * Timings for the Infernal Shadow's "Hellfire Volley" (generated from animations/entity/infernal_shadow.animation.json
 * - do not edit by hand). Positions are in blocks in the Shadow's own frame (forward, right, up from its feet).
 */
public final class FireballMoveData {
    private FireballMoveData() {
    }

    public static final int END_TICK = 67;
    public static final int TAKEOFF_TICK = 5;
    /** the animation's touchdown; the entity lands 2 ticks earlier so the client's smoothing catches up on time */
    public static final int LAND_TICK = 52;
    public static final int[] SHOT_TICKS = {20, 28, 37};
    /** how high the Shadow flies above the ground it took off from, per tick */
    public static final float[] HEIGHT = {0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.258F, 1.438F, 2.437F, 3.259F, 3.912F, 4.402F, 4.738F, 4.931F, 4.999F, 5.035F, 5.076F, 5.113F, 5.143F, 5.165F, 5.177F, 5.180F, 5.171F, 5.153F, 5.126F, 5.091F, 5.051F, 5.009F, 4.965F, 4.924F, 4.887F, 4.857F, 4.835F, 4.823F, 4.820F, 4.829F, 4.847F, 4.874F, 4.909F, 4.949F, 4.991F, 5.035F, 5.076F, 5.113F, 5.143F, 5.165F, 5.177F, 5.069F, 4.705F, 4.130F, 3.362F, 2.412F, 1.289F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F};
    /** the left fist, which hurls the fireballs */
    public static final float[] FIST_F = {0.117F, -0.002F, -0.024F, -0.018F, 0.078F, 0.642F, 0.080F, 0.132F, 0.260F, 0.413F, 0.534F, 0.584F, 0.560F, 0.491F, 0.316F, 0.056F, 0.021F, 0.252F, 0.609F, 0.806F, 0.827F, 0.807F, 0.759F, 0.465F, 0.043F, 0.047F, 0.324F, 0.667F, 0.820F, 0.828F, 0.801F, 0.738F, 0.363F, 0.011F, 0.084F, 0.398F, 0.716F, 0.829F, 0.821F, 0.784F, 0.743F, 0.692F, 0.628F, 0.562F, 0.508F, 0.477F, 0.475F, 0.487F, 0.502F, 0.515F, 0.521F, 0.512F, 0.399F, 0.226F, 0.181F, 0.172F, 0.162F, 0.152F, 0.142F, 0.134F, 0.127F, 0.123F, 0.120F, 0.118F, 0.117F, 0.117F, 0.117F, 0.117F};
    public static final float[] FIST_R = {-0.462F, -0.499F, -0.516F, -0.530F, -0.544F, -0.359F, -0.183F, -0.183F, -0.198F, -0.231F, -0.280F, -0.338F, -0.392F, -0.431F, -0.486F, -0.454F, -0.435F, -0.486F, -0.340F, -0.080F, -0.010F, -0.074F, -0.165F, -0.423F, -0.428F, -0.430F, -0.466F, -0.279F, -0.042F, -0.038F, -0.093F, -0.198F, -0.464F, -0.423F, -0.461F, -0.462F, -0.235F, -0.032F, -0.052F, -0.131F, -0.203F, -0.272F, -0.338F, -0.392F, -0.427F, -0.445F, -0.445F, -0.465F, -0.491F, -0.516F, -0.531F, -0.558F, -0.640F, -0.679F, -0.672F, -0.659F, -0.643F, -0.623F, -0.600F, -0.575F, -0.549F, -0.523F, -0.499F, -0.480F, -0.467F, -0.462F, -0.462F, -0.462F};
    public static final float[] FIST_U = {0.776F, 0.741F, 0.693F, 0.668F, 0.634F, 1.319F, 1.935F, 1.919F, 1.863F, 1.753F, 1.591F, 1.415F, 1.272F, 1.195F, 1.223F, 1.351F, 1.373F, 1.256F, 1.119F, 1.057F, 1.052F, 1.072F, 1.108F, 1.197F, 1.373F, 1.371F, 1.237F, 1.105F, 1.054F, 1.050F, 1.081F, 1.117F, 1.225F, 1.384F, 1.339F, 1.192F, 1.081F, 1.050F, 1.069F, 1.091F, 1.106F, 1.116F, 1.126F, 1.140F, 1.158F, 1.175F, 1.194F, 1.193F, 1.190F, 1.190F, 1.192F, 1.138F, 0.811F, 0.630F, 0.603F, 0.609F, 0.626F, 0.651F, 0.677F, 0.711F, 0.739F, 0.758F, 0.770F, 0.775F, 0.776F, 0.776F, 0.776F, 0.776F};
}
