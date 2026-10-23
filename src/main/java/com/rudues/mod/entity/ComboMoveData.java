package com.rudues.mod.entity;

/**
 * Timings for the Infernal Shadow's close-range combo (generated from
 * animations/entity/infernal_shadow.animation.json - do not edit by hand).
 * Positions are in blocks in the Shadow's own frame: forward, right, up from its feet.
 */
public final class ComboMoveData {
    private ComboMoveData() {
    }

    public static final int END_TICK = 39;
    /** slash to its left, slash to its right, fire punch */
    public static final int SLASH1_TICK = 6;
    public static final int SLASH2_TICK = 13;
    public static final int PUNCH_TICK = 22;
    /** fire starts gathering in the left fist */
    public static final int PUNCH_CHARGE_TICK = 16;
    /** small steps in (blocks/tick) */
    public static final float[] STEP = {0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.040F, 0.100F, 0.100F, 0.060F, 0.000F, 0.000F, 0.000F, 0.000F, 0.047F, 0.047F, 0.047F, 0.000F, 0.000F, 0.000F, 0.000F, 0.107F, 0.180F, 0.180F, 0.073F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F};
    public static final float[] TIP_F = {-0.655F, 1.260F, -0.326F, -0.912F, -0.981F, -0.950F, 1.700F, 1.613F, 0.608F, 0.014F, -0.115F, -0.081F, 0.876F, 1.801F, 0.500F, -0.806F, -0.548F, 0.108F, -0.334F, -0.435F, -0.731F, -1.380F, -1.388F, -1.394F, -1.397F, -1.390F, -1.366F, -1.321F, -1.257F, -1.178F, -1.091F, -1.010F, -0.945F, -0.902F, -0.858F, -0.799F, -0.732F, -0.680F, -0.656F, -0.655F};
    public static final float[] TIP_R = {0.651F, 1.302F, 0.907F, 0.169F, -0.090F, 0.042F, 0.649F, -0.534F, -1.054F, -1.101F, -1.480F, -1.472F, -1.384F, 0.453F, 1.919F, 1.825F, 1.781F, 1.386F, 0.846F, 1.201F, 1.178F, 0.254F, -0.071F, -0.029F, 0.036F, 0.117F, 0.215F, 0.319F, 0.417F, 0.502F, 0.568F, 0.613F, 0.635F, 0.639F, 0.644F, 0.655F, 0.666F, 0.669F, 0.663F, 0.651F};
    public static final float[] TIP_U = {0.025F, 1.567F, 2.686F, 2.427F, 2.236F, 2.327F, 1.755F, 0.649F, 0.122F, 0.129F, 0.855F, 1.679F, 1.431F, 1.111F, 0.970F, 0.845F, 0.543F, 0.043F, 0.021F, 0.405F, 0.721F, 0.879F, 0.979F, 0.951F, 0.896F, 0.827F, 0.744F, 0.652F, 0.560F, 0.473F, 0.395F, 0.331F, 0.283F, 0.248F, 0.210F, 0.154F, 0.091F, 0.048F, 0.028F, 0.025F};
    public static final float[] FIST_F = {0.117F, 0.445F, 0.574F, 0.617F, 0.632F, 0.640F, 0.376F, 0.039F, -0.241F, -0.378F, -0.445F, -0.470F, -0.272F, 0.254F, 0.618F, 0.651F, 0.618F, 0.493F, 0.241F, 0.039F, 0.087F, 0.781F, 0.897F, 0.892F, 0.883F, 0.868F, 0.841F, 0.799F, 0.739F, 0.665F, 0.583F, 0.505F, 0.444F, 0.404F, 0.361F, 0.314F, 0.264F, 0.213F, 0.163F, 0.117F};
    public static final float[] FIST_R = {-0.462F, -0.327F, -0.187F, -0.113F, -0.094F, -0.130F, -0.568F, -0.673F, -0.628F, -0.555F, -0.490F, -0.448F, -0.573F, -0.578F, -0.215F, 0.092F, 0.007F, -0.268F, -0.465F, -0.506F, -0.505F, -0.114F, 0.157F, 0.136F, 0.104F, 0.062F, 0.001F, -0.075F, -0.155F, -0.234F, -0.304F, -0.358F, -0.393F, -0.411F, -0.427F, -0.441F, -0.453F, -0.460F, -0.463F, -0.462F};
    public static final float[] FIST_U = {0.776F, 0.879F, 0.982F, 1.044F, 1.070F, 1.062F, 0.862F, 0.821F, 0.836F, 0.867F, 0.886F, 0.886F, 0.846F, 0.857F, 0.996F, 1.115F, 1.155F, 1.148F, 1.121F, 1.102F, 1.045F, 0.937F, 0.995F, 1.028F, 1.065F, 1.078F, 1.062F, 1.028F, 0.983F, 0.940F, 0.905F, 0.881F, 0.867F, 0.860F, 0.850F, 0.835F, 0.820F, 0.804F, 0.789F, 0.776F};
}
