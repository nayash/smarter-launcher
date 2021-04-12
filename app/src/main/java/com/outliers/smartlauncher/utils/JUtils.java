package com.outliers.smartlauncher.utils;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

public class JUtils {

    public static void dropFirstKey(LinkedMap<Integer, ArrayRealVector> map){
        if(map.size() > 0)
            map.remove(0);
    }
}
