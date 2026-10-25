package com.rudues.mod.entity;

/** Generated from the Feathered Knight's clips (tools/knight_anim.py): ticks, and points in her frame (forward, right, up). */
public final class KnightMoveData {
    private KnightMoveData() {
    }

    public static final int INTRO_END = 160;
    public static final int INTRO_LOOK = 70;
    public static final int INTRO_SWING = 112;
    public static final int INTRO_PUSH = 128;
    public static final int INTRO_LAND = 140;
    public static final float INTRO_SEAT_BACK = -3.000F;
    public static final float INTRO_SEAT_UP = 1.310F;
    public static final float[] INTRO_HEAD_F = {-3.248F, -3.246F, -3.242F, -3.238F, -3.233F, -3.229F, -3.225F, -3.221F, -3.217F, -3.214F, -3.210F, -3.207F, -3.204F, -3.201F, -3.199F, -3.197F, -3.195F, -3.193F, -3.192F, -3.190F, -3.189F, -3.189F, -3.188F, -3.188F, -3.188F, -3.188F, -3.189F, -3.189F, -3.190F, -3.191F, -3.192F, -3.193F, -3.194F, -3.196F, -3.197F, -3.199F, -3.200F, -3.202F, -3.205F, -3.207F, -3.209F, -3.211F, -3.214F, -3.216F, -3.218F, -3.220F, -3.222F, -3.224F, -3.225F, -3.227F, -3.228F, -3.229F, -3.230F, -3.231F, -3.232F, -3.232F, -3.232F, -3.232F, -3.231F, -3.230F, -3.229F, -3.227F, -3.221F, -3.213F, -3.204F, -3.193F, -3.181F, -3.169F, -3.158F, -3.148F, -3.140F, -3.132F, -3.124F, -3.116F, -3.107F, -3.098F, -3.088F, -3.080F, -3.072F, -3.065F, -3.059F, -3.054F, -3.050F, -3.048F, -3.046F, -3.045F, -3.045F, -3.046F, -3.047F, -3.048F, -3.050F, -3.051F, -3.051F, -3.052F, -3.053F, -3.053F, -3.053F, -3.053F, -3.053F, -3.053F, -3.053F, -3.049F, -3.038F, -3.019F, -2.995F, -2.966F, -2.936F, -2.909F, -2.891F, -2.884F, -2.890F, -2.907F, -2.931F, -2.960F, -2.987F, -3.003F, -2.997F, -2.965F, -2.909F, -2.835F, -2.753F, -2.670F, -2.590F, -2.516F, -2.426F, -2.304F, -2.165F, -2.024F, -1.892F, -1.771F, -1.665F, -1.546F, -1.368F, -1.134F, -0.906F, -0.690F, -0.486F, -0.276F, -0.108F, 0.078F, 0.482F, 0.556F, 0.556F, 0.563F, 0.566F, 0.566F, 0.528F, 0.418F, 0.266F, 0.121F, 0.029F, 0.007F, 0.028F, 0.055F, 0.086F, 0.111F, 0.118F, 0.119F, 0.120F, 0.120F, 0.121F};
    public static final float[] INTRO_HEAD_R = {-0.003F, -0.003F, -0.002F, -0.002F, -0.001F, -0.001F, -0.001F, -0.001F, -0.000F, -0.000F, -0.000F, -0.000F, 0.000F, 0.000F, -0.000F, -0.000F, -0.000F, -0.000F, -0.000F, -0.000F, -0.000F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.000F, -0.000F, -0.000F, -0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, -0.000F, -0.000F, -0.000F, -0.001F, -0.001F, -0.001F, -0.001F, -0.002F, -0.002F, -0.003F, -0.005F, -0.008F, -0.011F, -0.015F, -0.018F, -0.021F, -0.023F, -0.025F, -0.026F, -0.027F, -0.028F, -0.029F, -0.029F, -0.028F, -0.026F, -0.023F, -0.019F, -0.015F, -0.011F, -0.006F, -0.001F, 0.003F, 0.007F, 0.010F, 0.013F, 0.014F, 0.014F, 0.014F, 0.012F, 0.011F, 0.009F, 0.007F, 0.005F, 0.003F, 0.001F, -0.001F, -0.003F, -0.004F, -0.006F, -0.003F, 0.005F, 0.015F, 0.021F, 0.021F, 0.011F, -0.009F, -0.039F, -0.075F, -0.109F, -0.138F, -0.156F, -0.160F, -0.145F, -0.112F, -0.069F, -0.025F, 0.010F, 0.028F, 0.030F, 0.019F, 0.004F, -0.002F, -0.002F, -0.002F, -0.003F, -0.004F, -0.004F, -0.004F, -0.004F, -0.003F, -0.002F, -0.002F, -0.002F, -0.002F, -0.002F, -0.002F, -0.002F, -0.002F, 0.004F, -0.010F, -0.009F, -0.008F, -0.009F, -0.009F, -0.041F, -0.103F, -0.135F, -0.107F, -0.049F, -0.019F, -0.031F, -0.051F, -0.069F, -0.080F, -0.082F, -0.083F, -0.084F, -0.084F, -0.085F};
    public static final float[] INTRO_HEAD_U = {3.024F, 3.024F, 3.026F, 3.027F, 3.029F, 3.030F, 3.032F, 3.033F, 3.034F, 3.035F, 3.036F, 3.037F, 3.037F, 3.038F, 3.039F, 3.039F, 3.039F, 3.040F, 3.040F, 3.040F, 3.040F, 3.040F, 3.040F, 3.040F, 3.040F, 3.040F, 3.040F, 3.040F, 3.039F, 3.039F, 3.039F, 3.038F, 3.038F, 3.038F, 3.037F, 3.037F, 3.037F, 3.036F, 3.036F, 3.036F, 3.035F, 3.035F, 3.034F, 3.034F, 3.034F, 3.033F, 3.033F, 3.032F, 3.032F, 3.032F, 3.031F, 3.031F, 3.031F, 3.031F, 3.030F, 3.030F, 3.030F, 3.030F, 3.030F, 3.030F, 3.031F, 3.031F, 3.033F, 3.035F, 3.037F, 3.039F, 3.042F, 3.044F, 3.046F, 3.048F, 3.049F, 3.050F, 3.051F, 3.052F, 3.053F, 3.054F, 3.055F, 3.056F, 3.057F, 3.057F, 3.057F, 3.057F, 3.057F, 3.057F, 3.057F, 3.057F, 3.056F, 3.056F, 3.056F, 3.056F, 3.055F, 3.055F, 3.056F, 3.056F, 3.056F, 3.056F, 3.056F, 3.056F, 3.056F, 3.057F, 3.057F, 3.059F, 3.065F, 3.074F, 3.084F, 3.096F, 3.107F, 3.117F, 3.126F, 3.133F, 3.139F, 3.142F, 3.143F, 3.142F, 3.139F, 3.134F, 3.126F, 3.116F, 3.102F, 3.086F, 3.067F, 3.048F, 3.030F, 3.015F, 3.034F, 3.096F, 3.169F, 3.230F, 3.255F, 3.251F, 3.220F, 3.128F, 2.998F, 2.845F, 2.660F, 2.464F, 2.266F, 2.031F, 1.844F, 1.772F, 1.615F, 1.326F, 1.319F, 1.311F, 1.306F, 1.305F, 1.336F, 1.403F, 1.460F, 1.562F, 1.659F, 1.705F, 1.709F, 1.707F, 1.703F, 1.700F, 1.699F, 1.698F, 1.698F, 1.698F, 1.698F};
    public static final float[] INTRO_HIPS_F = {-3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.011F, -3.010F, -3.010F, -3.009F, -3.008F, -3.008F, -3.007F, -3.007F, -3.006F, -3.006F, -3.006F, -3.005F, -3.005F, -3.005F, -3.004F, -3.004F, -3.004F, -3.003F, -3.003F, -3.003F, -3.003F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.002F, -3.001F, -2.999F, -2.998F, -2.996F, -2.993F, -2.992F, -2.990F, -2.990F, -2.991F, -2.993F, -2.995F, -2.996F, -2.993F, -2.985F, -2.974F, -2.957F, -2.935F, -2.910F, -2.881F, -2.850F, -2.817F, -2.783F, -2.737F, -2.671F, -2.587F, -2.485F, -2.369F, -2.191F, -1.937F, -1.657F, -1.396F, -1.162F, -0.934F, -0.717F, -0.514F, -0.303F, -0.136F, -0.045F, 0.026F, 0.036F, 0.037F, 0.038F, 0.038F, 0.038F, 0.036F, 0.029F, 0.019F, 0.009F, 0.002F, -0.000F, 0.001F, 0.002F, 0.004F, 0.005F, 0.005F, 0.005F, 0.005F, 0.005F, 0.005F};
    public static final float[] INTRO_HIPS_R = {0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, -0.000F, -0.000F, -0.000F, -0.000F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.002F, -0.002F, -0.002F, -0.002F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.000F, 0.000F, 0.001F, 0.000F, -0.000F, -0.002F, -0.005F, -0.008F, -0.011F, -0.013F, -0.014F, -0.014F, -0.013F, -0.011F, -0.007F, -0.004F, -0.001F, 0.001F, 0.002F, 0.001F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, 0.000F, -0.000F, -0.000F, -0.000F, -0.000F, 0.000F, -0.002F, -0.006F, -0.008F, -0.006F, -0.002F, 0.000F, -0.001F, -0.002F, -0.003F, -0.004F, -0.004F, -0.004F, -0.004F, -0.004F, -0.004F};
    public static final float[] INTRO_HIPS_U = {2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.184F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.185F, 2.187F, 2.192F, 2.201F, 2.211F, 2.222F, 2.235F, 2.247F, 2.258F, 2.268F, 2.276F, 2.281F, 2.283F, 2.281F, 2.275F, 2.266F, 2.254F, 2.242F, 2.228F, 2.215F, 2.203F, 2.193F, 2.186F, 2.183F, 2.218F, 2.303F, 2.405F, 2.491F, 2.527F, 2.489F, 2.393F, 2.262F, 2.125F, 1.971F, 1.786F, 1.590F, 1.392F, 1.157F, 0.970F, 0.906F, 0.869F, 0.636F, 0.635F, 0.634F, 0.634F, 0.634F, 0.640F, 0.648F, 0.645F, 0.707F, 0.788F, 0.830F, 0.835F, 0.835F, 0.835F, 0.835F, 0.835F, 0.835F, 0.835F, 0.835F, 0.835F};

    public static final int WIND_BURST_END = 34;
    public static final int WIND_BURST_FIRE = 11;
    public static final float[] WIND_BURST_HAND = {0.907F, -0.056F, 1.315F};

    public static final int AEROGA_END = 50;
    public static final int AEROGA_GATHER = 8;
    public static final int AEROGA_LAUNCH = 34;
    public static final float[] AEROGA_ORB = {-0.275F, -0.061F, 2.747F};

    public static final int PIERCE_END = 56;
    public static final int PIERCE_LEAP = 6;
    public static final int PIERCE_LEAP_END = 14;
    public static final int PIERCE_RISE_END = 24;
    public static final int PIERCE_WINDUP_END = 35;
    public static final int PIERCE_HIT = 39;
    public static final int PIERCE_LAND = 44;
    public static final float PIERCE_LEAP_BACK = 6.000F;
    public static final float PIERCE_RISE = 3.800F;

    public static final int WIND_BLADE_END = 46;
    public static final int WIND_BLADE_FORM = 6;
    public static final int WIND_BLADE_RELEASE = 38;

    public static final int SEMI_TRANSFORM_END = 60;
    public static final int SEMI_TRANSFORM_SEMI = 25;
    public static final int SEMI_TRANSFORM_BURST = 28;

    public static final int SEMI_ASCEND_END = 40;
    public static final int SEMI_ASCEND_LIFT = 3;
    public static final float[] SEMI_ASCEND_GRIP = {0.836F, -0.015F, -0.923F};

    public static final int RUBBLE_DIVE_END = 30;
    public static final int RUBBLE_DIVE_GO = 10;
    public static final float[] RUBBLE_DIVE_GRIP = {0.347F, 0.000F, -0.030F};

    public static final float[] RUBBLE_STRAIN_GRIP = {0.245F, -0.000F, 0.083F};

    public static final int RUBBLE_KNOCKED_END = 50;
    public static final int RUBBLE_KNOCKED_LAND = 26;
    public static final int RUBBLE_KNOCKED_UP = 44;

    public static final int RUBBLE_RELEASE_END = 28;

    public static final int NOSEDIVE_RISE_END = 20;

    public static final int DIVE_END = 40;
    public static final int DIVE_FOLD = 12;
    public static final int DIVE_HIT = 18;
    public static final int DIVE_RISE = 30;

    public static final int SWARM_END = 76;
    public static final int SWARM_BACK_END = 12;
    public static final int SWARM_ARC_END = 52;
    public static final int SWARM_SHOT1 = 25;
    public static final int SWARM_SHOT2 = 40;
    public static final int SWARM_SLASH = 61;
    public static final float[] SWARM_HAND = {0.898F, 0.024F, 1.184F};

    public static final int TRIPLE_AEROGA_END = 68;
    public static final int TRIPLE_AEROGA_GATHER = 10;
    public static final int TRIPLE_AEROGA_THROW1 = 35;
    public static final int TRIPLE_AEROGA_THROW2 = 42;
    public static final int TRIPLE_AEROGA_THROW3 = 49;
    public static final float[] TRIPLE_AEROGA_ORB0 = {-0.066F, 0.132F, 2.878F};
    public static final float[] TRIPLE_AEROGA_ORB1 = {0.172F, -0.170F, 2.525F};
    public static final float[] TRIPLE_AEROGA_ORB2 = {-0.046F, 0.134F, 2.550F};

    public static final int SLASH_LAUNCH_END = 60;
    public static final int SLASH_LAUNCH_DASH = 8;
    public static final int SLASH_LAUNCH_RAKE = 19;
    public static final int SLASH_LAUNCH_SMASH = 32;
    public static final int SLASH_LAUNCH_DISMISS = 51;

    public static final int RAIN_VOLLEY_END = 16;
    public static final int RAIN_VOLLEY_FIRE = 7;

    public static final int GRINDER_SCREECH_END = 24;
    public static final int GRINDER_SCREECH_SCREECH = 10;

    public static final float[] GRINDER_CARRY_GRIP = {0.616F, 0.000F, 0.096F};

    public static final int GRINDER_HURL_END = 24;
    public static final int GRINDER_HURL_THROW = 8;

    public static final float[] PLAYER_CHARGE_FIST = {-0.029F, 0.515F, 0.994F};
    public static final float[] PLAYER_STRAIN_FIST = {0.160F, 0.252F, 1.850F};

}
