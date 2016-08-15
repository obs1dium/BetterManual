package com.obsidium.bettermanual;

import android.util.Pair;

public class CameraUtil
{
    /* These have been determined experimentally, values actually do not need to be exact... */
    public static final int[] MIN_SHUTTER_VALUES = new int[] {
            1, 276, 347, 437, 551, 693, 873, 1101, 1385, 1743, 2202, 2771, 3486, 4404, 5540, 6972, 8808,
            11081, 13945, 17617, 22162, 27888, 35232, 44321, 55777, 70465, 93451, 116628, 145553, 181652,
            226703, 282927, 354560, 446220, 563719, 709135, 892421, 1127429, 1418242, 1784846,
            2770094, 3462657, 4328353, 5410477, 6763136, 845956, 10567481, 13209387, 16511770,
            20639744, 25799712, 32249672
    };

    public static final int[][] SHUTTER_SPEEDS = new int[][] {
            new int[]{1, 4000},
            new int[]{1, 3200},
            new int[]{1, 2500},
            new int[]{1, 2000},
            new int[]{1, 1600},
            new int[]{1, 1250},
            new int[]{1, 1000},
            new int[]{1, 800},
            new int[]{1, 640},
            new int[]{1, 500},
            new int[]{1, 400},
            new int[]{1, 320},
            new int[]{1, 250},
            new int[]{1, 200},
            new int[]{1, 160},
            new int[]{1, 125},
            new int[]{1, 100},
            new int[]{1, 80},
            new int[]{1, 60},
            new int[]{1, 50},
            new int[]{1, 40},
            new int[]{1, 30},
            new int[]{1, 25},
            new int[]{1, 20},
            new int[]{1, 15},
            new int[]{1, 13},
            new int[]{1, 10},
            new int[]{1, 8},
            new int[]{1, 6},
            new int[]{1, 5},
            new int[]{1, 4},
            new int[]{1, 3},
            new int[]{10, 25},
            new int[]{1, 2},
            new int[]{10, 16},
            new int[]{4, 5},
            new int[]{1, 1},
            new int[]{13, 10},
            new int[]{16, 10},
            new int[]{2, 1},
            new int[]{25, 10},
            new int[]{16, 5},
            new int[]{4, 1},
            new int[]{5, 1},
            new int[]{6, 1},
            new int[]{8, 1},
            new int[]{10, 1},
            new int[]{13, 1},
            new int[]{15, 1},
            new int[]{20, 1},
            new int[]{25, 1},
            new int[]{30, 1},
    };

    public static int getShutterValueIndex(final Pair<Integer,Integer> speed)
    {
        return getShutterValueIndex(speed.first, speed.second);
    }

    public static int getShutterValueIndex(int n, int d)
    {
        for (int i = 0; i < SHUTTER_SPEEDS.length; ++i)
        {
            if (SHUTTER_SPEEDS[i][0] == n &&
                SHUTTER_SPEEDS[i][1] == d)
            {
                return i;
            }
        }
        return -1;
    }

    public static String formatShutterSpeed(int n, int d)
    {
        if (n == 1 && d != 2 && d != 1)
            return String.format("%d/%d", n, d);
        else if (d == 1)
        {
            if (n == 65535)
                return "BULB";
            else
                return String.format("%d\"", n);
        }
        else
            return String.format("%.1f\"", (float) n / (float) d);
    }
}
