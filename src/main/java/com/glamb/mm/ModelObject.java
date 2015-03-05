package com.glamb.mm;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public abstract class ModelObject {
    public long insertionTime;

    // ----- Creator Methods -----
    public ModelObject() {
        ModelManager.register(this);
    }

    public ModelObject(Object primaryKey) {
        Cursor cursor = null;
        try {
            getPrimaryField().set(this, primaryKey);

            cursor = ModelManager.query(getTableName(), getSelection());
            if(cursor.moveToFirst()) {
                init(cursor);
            } else {
                assignDefaults();
            }
            cursor.close();

        } catch (IllegalAccessException | SQLiteException e) {
            e.printStackTrace();
            assignDefaults();
        } finally {
            if(cursor != null){
                cursor.close();
            }
        }
    }

    public void init(Cursor cursor) {

        try {
            for (Field field : getClass().getDeclaredFields()) {
                try {
                    Class<?> c = field.getType();
                    int inx = cursor.getColumnIndex(field.getName());
                    if(inx == -1){
                        Previously previously = field.getAnnotation(Previously.class);
                        if(previously != null){
                            for(String oldName: previously.value()){
                                inx = cursor.getColumnIndex(oldName);
                                if(inx > 0){
                                    break;
                                }
                            }
                        }
                    }
                    if (c.equals(String.class) || c.equals(char.class)) {
                        field.set(this, cursor.getString(inx));
                    } else if (c.equals(int.class) || c.equals(Integer.class)) {
                        field.setInt(this, cursor.getInt(inx));
                    } else if (c.equals(long.class) || c.equals(Long.class)) {
                        field.setLong(this, cursor.getLong(inx));
                    } else if (c.equals(float.class) || c.equals(Float.class)) {
                        field.setFloat(this, cursor.getFloat(inx));
                    } else if (c.equals(double.class) || c.equals(Double.class)) {
                        field.setDouble(this, cursor.getDouble(inx));
                    } else if (c.equals(boolean.class) || c.equals(Boolean.class)){
                        field.setBoolean(this, cursor.getInt(inx) == 1);
                    } else if (c.getSuperclass() != null && c.getSuperclass().equals(ModelObject.class)) {
                        try {
                            Class<?> ic = ((ModelObject) c.newInstance()).getPrimaryField().getType();
                            Constructor<?> constructor = c.getConstructor(ic);
                            if (ic.equals(String.class) || ic.equals(char.class)) {
                                String key = cursor.getString(inx);
                                if (key == null) {
                                    field.set(this, null);
                                } else {
                                    field.set(this, constructor.newInstance(key));
                                }
                            } else if (ic.equals(int.class) || ic.equals(Integer.class)) {
                                field.set(this, constructor.newInstance(cursor.getInt(inx)));
                            } else if (ic.equals(long.class) || ic.equals(Long.class)) {
                                field.set(this, constructor.newInstance(cursor.getLong(inx)));
                            } else if (ic.equals(float.class) || ic.equals(Float.class)) {
                                field.set(this, constructor.newInstance(cursor.getFloat(inx)));
                            } else if (ic.equals(double.class) || ic.equals(Double.class)) {
                                field.set(this, constructor.newInstance(cursor.getDouble(inx)));
                            } else {
                                Log.w("Model Object", "Unhandled variable class " + c);
                            }
                        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.w("Model Object", "Unhandled variable class " + c);
                    }
                } catch (IllegalAccessException e) {
                    Log.e(getClassName(getClass()),
                            String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public void assignDefaults(){
        //Available for Override on case-by-case basis;
    }

    // ----- Bundle Methods -----
    public void init(Bundle bundle) {

        try {
            for (Field field : getClass().getDeclaredFields()) {
                try {
                    Class<?> c = field.getType();
                    if (c.equals(String.class)) {
                        field.set(this, bundle.getString(field.getName()));
                    } else if (c.equals(char.class) || c.equals(Character.class)) {
                        field.set(this, bundle.getChar(field.getName()));
                    } else if (c.equals(int.class) || c.equals(Integer.class)) {
                        field.setInt(this, bundle.getInt(field.getName()));
                    } else if (c.equals(long.class) || c.equals(Long.class)) {
                        field.setLong(this, bundle.getLong(field.getName()));
                    } else if (c.equals(float.class) || c.equals(Float.class)) {
                        field.setFloat(this, bundle.getFloat(field.getName()));
                    } else if (c.equals(double.class) || c.equals(Double.class)) {
                        field.setDouble(this, bundle.getDouble(field.getName()));
                    } else if (c.equals(boolean.class) || c.equals(Boolean.class)){
                        field.setBoolean(this, bundle.getBoolean(field.getName()));
                    } else if (c.getSuperclass() != null && c.getSuperclass().equals(ModelObject.class)) {
                        try {
                            Bundle innerBundle = bundle.getBundle(getArg());
                            ModelObject obj = (ModelObject) c.newInstance();
                            obj.init(innerBundle);
                            field.set(this, obj);
                        } catch (InstantiationException e){
                            Log.e("Model Object", String.format("Cannot Create Inner Object %s", field.getName()));
                        }
                    } else {
                        Log.w("Model Object", "Unhandled variable class " + c);
                    }
                } catch (IllegalAccessException e) {
                    Log.e(getClassName(getClass()),
                            String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public Bundle bundleUp(){
        Bundle bundle = new Bundle();

        try {
            for (Field field : getClass().getDeclaredFields()) {
                try {
                    Class<?> c = field.getType();
                    if (c.equals(String.class)) {
                        bundle.putString(field.getName(), (String) field.get(this));
                    } else if( c.equals(char.class) || c.equals(Character.class)){
                        bundle.putChar(field.getName(), field.getChar(this));
                    } else if (c.equals(int.class) || c.equals(Integer.class)) {
                        bundle.putInt(field.getName(), field.getInt(this));
                    } else if (c.equals(long.class) || c.equals(Long.class)) {
                        bundle.putLong(field.getName(), field.getLong(this));
                    } else if (c.equals(float.class) || c.equals(Float.class)) {
                        bundle.putFloat(field.getName(), field.getFloat(this));
                    } else if (c.equals(double.class) || c.equals(Double.class)) {
                        bundle.putDouble(field.getName(), field.getDouble(this));
                    } else if (c.equals(boolean.class) || c.equals(Boolean.class)){
                        bundle.putBoolean(field.getName(), field.getBoolean(this));
                    } else if (c.getSuperclass() != null && c.getSuperclass().equals(ModelObject.class)) {
                        ModelObject obj = (ModelObject)field.get(this);
                        bundle.putBundle(obj.getArg(), obj.bundleUp());
                    } else {
                        Log.w("Model Object", "Unhandled variable class " + c);
                    }
                } catch (IllegalAccessException e) {
                    Log.e(getClassName(getClass()),
                            String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return bundle;
    }

    public String getArg(){
        return getClassName(getClass()).toUpperCase();
    }

    // ----- Database Methods -----
    public Field save() {
        ModelManager.insert(this);
        return getPrimaryField();
    }

    public String getTableName() {
        return getClassName(getClass());
    }

    public Map<String, String> getColumns() {
        Map<String, String> columns = new HashMap<>();
        try {
            for (Field field : getClass().getDeclaredFields()) {
                columns.put(field.getName(), determineDatatype(field.getType()));
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return columns;
    }

    public String getPrimaryKey() {
        Field field = getPrimaryField();
        return field == null ? null : field.getName();
    }

    public String getSelection() {
        Field field = getPrimaryField();
        if (field != null) {
            String selection = field.getName() + " = ";
            try {
                Class<?> c = field.getType();
                if (c.equals(String.class) || c.equals(char.class)) {
                    selection += String.format("'%s'", (String) field.get(this));
                } else if (c.equals(int.class) || c.equals(Integer.class)) {
                    selection += String.format("%d", field.getInt(this));
                } else if (c.equals(long.class) || c.equals(Long.class)) {
                    selection += String.format("%d", field.getLong(this));
                } else if (c.equals(float.class) || c.equals(Float.class)) {
                    selection += String.format("%f", field.getFloat(this));
                } else if (c.equals(double.class) || c.equals(Double.class)) {
                    selection += String.format("%f", field.getDouble(this));
                } else {
                    Log.w("Model Object", "Unhandled variable class " + c);
                    selection = null;
                }

                return selection;
            } catch (IllegalAccessException e) {
                Log.e(getClassName(getClass()),
                        String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
            }
        }

        return null;
    }

    public String getOrderBy() {
        try {
            for (Field field : getClass().getDeclaredFields()) {
                OrderBy anno = field.getAnnotation(OrderBy.class);
                if (anno != null) {
                    try {
                        return String.format("%s %s", field.getName(), anno.order().toString());
                    } catch (SecurityException e) {
                        Log.e(getClassName(getClass()),
                                String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getOrderBy()", field.getName(), field.getName()));
                    }
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return null;
    }

    public ContentValues getValues() {
        ContentValues values = new ContentValues();
        try {
            for (Field field : getClass().getDeclaredFields()) {
                putValue(values, field, this);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return values;
    }

    public int getRevision(){
        try {
            Revision anno = getClass().getAnnotation(Revision.class);
            if(anno == null) {
                return 0;
            } else {
                return anno.value();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return -1;
    }


    //----- Utility Methods -----
    private static String getClassName(Class<?> clazz) {
        String[] fields = clazz.getName().split("\\.");
        return fields[fields.length - 1];
    }

    private Field getPrimaryField() {
        try {
            for (Field field : getClass().getDeclaredFields()) {
                PrimaryKey anno = field.getAnnotation(PrimaryKey.class);
                if (anno != null) {
                    try {
                        return field;
                    } catch (SecurityException e) {
                        Log.e(getClassName(getClass()),
                                String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                    }
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void nest(ContentValues values, String name, Field field, Object obj) {
        try {
            Class<?> c = field.getType();
            if (c.equals(String.class) || c.equals(char.class)) {
                values.put(name, (String) field.get(obj));
            } else if (c.equals(int.class) || c.equals(Integer.class)) {
                values.put(name, field.getInt(obj));
            } else if (c.equals(long.class) || c.equals(Long.class)) {
                values.put(name, field.getLong(obj));
            } else if (c.equals(float.class) || c.equals(Float.class)) {
                values.put(name, field.getFloat(obj));
            } else if (c.equals(double.class) || c.equals(Double.class)) {
                values.put(name, field.getDouble(obj));
            } else if (c.equals(boolean.class) || c.equals(Boolean.class)){
                values.put(name, field.getBoolean(obj));
            }else {
                Log.w("Model Object", "Unhandled variable class " + c);
            }
        } catch (IllegalAccessException e) {
            Log.e(getClassName(getClass()),
                    String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
        }
    }

    private void putValue(ContentValues values, Field field, Object obj) {
        try {
            Class<?> c = field.getType();
            if (c.equals(String.class) || c.equals(char.class)) {
                values.put(field.getName(), (String) field.get(obj));
            } else if (c.equals(int.class) || c.equals(Integer.class)) {
                values.put(field.getName(), field.getInt(obj));
            } else if (c.equals(long.class) || c.equals(Long.class)) {
                values.put(field.getName(), field.getLong(obj));
            } else if (c.equals(float.class) || c.equals(Float.class)) {
                values.put(field.getName(), field.getFloat(obj));
            } else if (c.equals(double.class) || c.equals(Double.class)) {
                values.put(field.getName(), field.getDouble(obj));
            } else if (c.equals(boolean.class) || c.equals(Boolean.class)){
                values.put(field.getName(), field.getBoolean(obj) ? 1 : 0);
            } else if (c.getSuperclass() != null && c.getSuperclass().equals(ModelObject.class)) {
                ModelObject inner = (ModelObject) field.get(obj);
                if (inner != null) {
                    nest(values, field.getName(), inner.save(), inner);
                }
            } else {
                Log.w("Model Object", "Unhandled variable class " + c);
            }
        } catch (IllegalAccessException e) {
            Log.e(getClassName(getClass()),
                    String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
        }
    }

    private String determineDatatype(Class<?> c) {
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
        } else if (c.getSuperclass() != null && c.getSuperclass().equals(ModelObject.class)) {
            try {
                return determineDatatype(((ModelObject) c.newInstance()).getPrimaryField().getType());
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
            return null;
        } else {
            Log.w("Model Object", "Unhandled variable class " + c);
            return null;
        }
    }
}
