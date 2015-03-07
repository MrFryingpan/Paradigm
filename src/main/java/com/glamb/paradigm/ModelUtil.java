package com.glamb.paradigm;

import android.util.Log;

import com.glamb.paradigm.annotation.PrimaryKey;

import java.lang.reflect.Field;
import java.util.AbstractCollection;

public class ModelUtil {
    public static String getClassName(Class<?> clazz) {
        String[] fields = clazz.getName().split("\\.");
        return fields[fields.length - 1];
    }

    public static String determineDatatype(Class<?> c) {
        if (c.equals(String.class) ||
                c.equals(char.class)) {
            return "TEXT";
        } else if (c.equals(int.class) ||
                c.equals(Integer.class) ||
                c.equals(long.class) ||
                c.equals(Long.class) ||
                c.equals(boolean.class) ||
                c.equals(Boolean.class)) {
            return "INTEGER";
        } else if (c.equals(float.class) ||
                c.equals(Float.class) ||
                c.equals(double.class) ||
                c.equals(Double.class)) {
            return "REAL";
        } else if (isSubclassOf(c, ModelObject.class)) {
            return determineDatatype(getPrimaryField(c).getType());
        } else if (isSubclassOf(c, AbstractCollection.class)) {
            return null;
        } else {
            Log.w("Model Object", "Unhandled variable class " + c);
            return null;
        }
    }

    public static Field getPrimaryField(Class<?> clazz) {
        try {
            for (Field field : clazz.getDeclaredFields()) {
                PrimaryKey anno = field.getAnnotation(PrimaryKey.class);
                if (anno != null) {
                    try {
                        return field;
                    } catch (SecurityException e) {
                        Log.e(getClassName(clazz),
                                String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                    }
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean isSubclassOf(Class<?> clazz, Class<?> superClass){
        while(clazz != null){
            if(clazz.equals(superClass)){
                return true;
            } else {
                clazz = clazz.getSuperclass();
            }
        }
        return false;
    }
}
