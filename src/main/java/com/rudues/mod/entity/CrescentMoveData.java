package com.rudues.mod.entity;

/**
 * Timings for the Infernal Shadow's "Infernal Crescent" (generated from animations/entity/infernal_shadow.animation.json
 * - do not edit by hand). Positions are in blocks in the Shadow's own frame (forward, right, up from its feet).
 */
public final class CrescentMoveData {
    private CrescentMoveData() {
    }

    public static final int END_TICK = 42;
    /** the blade is drawn back and the warning crescent starts filling */
    public static final int CHARGE_TICK = 4;
    /** the sweep starts; the crescent locks a few ticks before */
    public static final int RELEASE_TICK = 19;
    /** the sweep crosses the front: everything in the crescent is hit */
    public static final int HIT_TICK = 21;
    public static final int RELEASE_END_TICK = 23;
    /** the area: a 130-degree crescent out to this many blocks */
    public static final float REACH = 5.5F;
    public static final float HALF_ANGLE = 65.0F;
    public static final float[] TIP_F = {-0.655F, 0.554F, -0.054F, -1.165F, -1.680F, -1.726F, -1.739F, -1.746F, -1.745F, -1.743F, -1.747F, -1.751F, -1.748F, -1.742F, -1.741F, -1.743F, -1.746F, -1.753F, -1.728F, -1.378F, 0.048F, 1.750F, 0.405F, -0.730F, -0.697F, -0.636F, -0.541F, -0.413F, -0.257F, 0.075F, 0.809F, 0.960F, 0.951F, 1.656F, 1.185F, -0.303F, -0.547F, -0.598F, -0.624F, -0.631F, -0.631F, -0.638F, -0.655F};
    public static final float[] TIP_R = {0.651F, 1.437F, 2.004F, 1.651F, 0.935F, 0.796F, 0.738F, 0.684F, 0.668F, 0.661F, 0.621F, 0.581F, 0.591F, 0.609F, 0.575F, 0.535F, 0.556F, 0.580F, 0.785F, 1.459F, 2.049F, 0.671F, -1.495F, -1.246F, -1.278F, -1.333F, -1.390F, -1.432F, -1.448F, -1.428F, -0.939F, -0.052F, 0.134F, -0.374F, 0.146F, 0.805F, 0.834F, 0.807F, 0.782F, 0.758F, 0.732F, 0.697F, 0.651F};
    public static final float[] TIP_U = {0.025F, 0.201F, 0.992F, 1.218F, 1.314F, 1.311F, 1.333F, 1.366F, 1.390F, 1.402F, 1.415F, 1.426F, 1.429F, 1.442F, 1.477F, 1.498F, 1.472F, 1.425F, 1.302F, 0.999F, 0.698F, 0.467F, 0.570F, 0.850F, 0.896F, 0.999F, 1.145F, 1.309F, 1.473F, 1.734F, 2.282F, 2.626F, 2.671F, 1.788F, 0.047F, 0.026F, 0.137F, 0.140F, 0.119F, 0.084F, 0.050F, 0.030F, 0.025F};
}
