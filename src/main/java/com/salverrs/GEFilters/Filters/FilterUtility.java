package com.salverrs.GEFilters.Filters;

import java.util.List;
import java.util.Objects;

public final class FilterUtility
{
    private FilterUtility()
    {
        // Utility class.
    }

    public static short[] getPrimitiveShortArray(List<Short> shorts)
    {
        Objects.requireNonNull(shorts, "shorts");

        int nonNullCount = 0;
        for (Short value : shorts)
        {
            if (value != null)
            {
                nonNullCount++;
            }
        }

        final short[] recentItems = new short[nonNullCount];
        int index = 0;
        for (Short value : shorts)
        {
            if (value != null)
            {
                recentItems[index++] = value;
            }
        }

        return recentItems;
    }
}
