package com.glamb.mm;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            ModelUtil.getPrimaryField(getClass()).set(this, primaryKey);

            cursor = ModelManager.query(getTableName(), getSelection());
            if (cursor.moveToFirst()) {
                init(cursor);
            } else {
                assignDefaults();
            }
            cursor.close();

        } catch (IllegalAccessException | SQLiteException e) {
            e.printStackTrace();
            assignDefaults();
        } finally {
            if (cursor != null) {
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
                    if (inx == -1) {
                        Previously previously = field.getAnnotation(Previously.class);
                        if (previously != null) {
                            for (String oldName : previously.value()) {
                                inx = cursor.getColumnIndex(oldName);
                                if (inx > 0) {
                                    break;
                                }
                            }
                        }
                    }
                    if (inx == -1 && !ModelUtil.isSubclassOf(c, AbstractCollection.class)) {
                        Log.w(ModelUtil.getClassName(getClass()), String.format("Field %s not found in Database and will not be initialized", field.getName()));
                    } else if (c.equals(String.class) || c.equals(char.class)) {
                        field.set(this, cursor.getString(inx));
                    } else if (c.equals(int.class) || c.equals(Integer.class)) {
                        field.setInt(this, cursor.getInt(inx));
                    } else if (c.equals(long.class) || c.equals(Long.class)) {
                        field.setLong(this, cursor.getLong(inx));
                    } else if (c.equals(float.class) || c.equals(Float.class)) {
                        field.setFloat(this, cursor.getFloat(inx));
                    } else if (c.equals(double.class) || c.equals(Double.class)) {
                        field.setDouble(this, cursor.getDouble(inx));
                    } else if (c.equals(boolean.class) || c.equals(Boolean.class)) {
                        field.setBoolean(this, cursor.getInt(inx) == 1);
                    } else if (ModelUtil.isSubclassOf(c, ModelObject.class)) {
                        try {
                            Class<?> ic = ModelUtil.getPrimaryField(getClass()).getType();
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
                    } else if (ModelUtil.isSubclassOf(c, AbstractCollection.class)) {
                        ParameterizedType pType = (ParameterizedType) field.getGenericType();
                        Class<?> generic = (Class<?>) pType.getActualTypeArguments()[0];
                        Cursor subCursor = ModelManager.query(String.format("%s_%s",
                                        ModelUtil.getClassName(getClass()),
                                        field.getName()),
                                String.format("parentKey =%s", getSelection().split("=")[1]));
                        if (ModelUtil.isSubclassOf(generic, ModelObject.class)) {
                            List<ModelObject> data = new ArrayList<>(subCursor.getCount());
                            if (subCursor.moveToFirst()) {
                                while (!subCursor.isAfterLast()) {

                                    try {
                                        int childInx = subCursor.getColumnIndex("childKey");
                                        Class<?> ic = ModelUtil.getPrimaryField(generic).getType();
                                        Constructor<? extends ModelObject> constructor = ((Class<? extends ModelObject>) generic).getConstructor(ic);
                                        if (ic.equals(String.class) || ic.equals(char.class)) {
                                            data.add(constructor.newInstance(subCursor.getString(childInx)));
                                        } else if (ic.equals(int.class) || ic.equals(Integer.class)) {
                                            data.add(constructor.newInstance(subCursor.getInt(childInx)));
                                        } else if (ic.equals(long.class) || ic.equals(Long.class)) {
                                            data.add(constructor.newInstance(subCursor.getLong(childInx)));
                                        } else if (ic.equals(float.class) || ic.equals(Float.class)) {
                                            data.add(constructor.newInstance(subCursor.getFloat(childInx)));
                                        } else if (ic.equals(double.class) || ic.equals(Double.class)) {
                                            data.add(constructor.newInstance(subCursor.getDouble(childInx)));
                                        } else {
                                            Log.w("Model Object", "Unhandled variable class " + generic);
                                        }
                                    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException e) {
                                        Log.e("Model Object", "Error constructing class " + generic);
                                    }

                                    subCursor.moveToNext();
                                }
                                field.set(this, data);
                            }
                        } else {
                            AbstractCollection<Object> data = null;
                            if(ModelUtil.isSubclassOf(c, AbstractSet.class)){
                                data = new HashSet<>();
                            } else if(ModelUtil.isSubclassOf(c, AbstractList.class)){
                                data = new ArrayList<>();
                            }
                            if (subCursor.moveToFirst()) {
                                while (!subCursor.isAfterLast()) {

                                    int childInx = subCursor.getColumnIndex("childKey");
                                    Class<?> ic = generic;
                                    if (ic.equals(String.class) || ic.equals(char.class)) {
                                        data.add(subCursor.getString(childInx));
                                    } else if (ic.equals(int.class) || ic.equals(Integer.class)) {
                                        data.add(subCursor.getInt(childInx));
                                    } else if (ic.equals(long.class) || ic.equals(Long.class)) {
                                        data.add(subCursor.getLong(childInx));
                                    } else if (ic.equals(float.class) || ic.equals(Float.class)) {
                                        data.add(subCursor.getFloat(childInx));
                                    } else if (ic.equals(double.class) || ic.equals(Double.class)) {
                                        data.add(subCursor.getDouble(childInx));
                                    } else {
                                        Log.w("Model Object", "Unhandled variable class " + generic);
                                    }

                                    subCursor.moveToNext();
                                }
                                field.set(this, data);
                            }
                        }
                    } else {
                        Log.w("Model Object", "Unhandled variable class " + c);
                    }
                } catch (IllegalAccessException e) {
                    Log.e(ModelUtil.getClassName(getClass()),
                            String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public void assignDefaults() {
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
                    } else if (c.equals(boolean.class) || c.equals(Boolean.class)) {
                        field.setBoolean(this, bundle.getBoolean(field.getName()));
                    } else if (c.getSuperclass() != null && c.getSuperclass().equals(ModelObject.class)) {
                        try {
                            Bundle innerBundle = bundle.getBundle(getArg());
                            ModelObject obj = (ModelObject) c.newInstance();
                            obj.init(innerBundle);
                            field.set(this, obj);
                        } catch (InstantiationException e) {
                            Log.e("Model Object", String.format("Cannot Create Inner Object %s", field.getName()));
                        }
                    } else {
                        Log.w("Model Object", "Unhandled variable class " + c);
                    }
                } catch (IllegalAccessException e) {
                    Log.e(ModelUtil.getClassName(getClass()),
                            String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public Bundle bundleUp() {
        Bundle bundle = new Bundle();

        try {
            for (Field field : getClass().getDeclaredFields()) {
                try {
                    Class<?> c = field.getType();
                    if (c.equals(String.class)) {
                        bundle.putString(field.getName(), (String) field.get(this));
                    } else if (c.equals(char.class) || c.equals(Character.class)) {
                        bundle.putChar(field.getName(), field.getChar(this));
                    } else if (c.equals(int.class) || c.equals(Integer.class)) {
                        bundle.putInt(field.getName(), field.getInt(this));
                    } else if (c.equals(long.class) || c.equals(Long.class)) {
                        bundle.putLong(field.getName(), field.getLong(this));
                    } else if (c.equals(float.class) || c.equals(Float.class)) {
                        bundle.putFloat(field.getName(), field.getFloat(this));
                    } else if (c.equals(double.class) || c.equals(Double.class)) {
                        bundle.putDouble(field.getName(), field.getDouble(this));
                    } else if (c.equals(boolean.class) || c.equals(Boolean.class)) {
                        bundle.putBoolean(field.getName(), field.getBoolean(this));
                    } else if (c.getSuperclass() != null && c.getSuperclass().equals(ModelObject.class)) {
                        ModelObject obj = (ModelObject) field.get(this);
                        bundle.putBundle(obj.getArg(), obj.bundleUp());
                    } else {
                        Log.w("Model Object", "Unhandled variable class " + c);
                    }
                } catch (IllegalAccessException e) {
                    Log.e(ModelUtil.getClassName(getClass()),
                            String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return bundle;
    }

    public String getArg() {
        return ModelUtil.getClassName(getClass()).toUpperCase();
    }

    // ----- Database Methods -----
    public Field save() {
        ModelManager.insert(this);
        return ModelUtil.getPrimaryField(getClass());
    }

    public String getTableName() {
        return ModelUtil.getClassName(getClass());
    }

    public Map<String, String> getColumns() {
        Map<String, String> columns = new HashMap<>();
        try {
            for (Field field : getClass().getDeclaredFields()) {
                String datatype = ModelUtil.determineDatatype(field.getType());
                if (datatype != null) {
                    columns.put(field.getName(), datatype);
                } else if (ModelUtil.isSubclassOf(field.getType(), AbstractCollection.class)) {
                    ParameterizedType pType = (ParameterizedType) field.getGenericType();
                    Class<?> fieldClass = (Class<?>) pType.getActualTypeArguments()[0];
                    Class<?> parentClass = ModelUtil.getPrimaryField(getClass()).getType();
                    if (ModelUtil.isSubclassOf(fieldClass, ModelObject.class)) {
                        fieldClass = ModelUtil.getPrimaryField(fieldClass).getType();
                    }
                    ModelManager.createRelationship(getTableName(), field.getName(),
                            ModelUtil.determineDatatype(parentClass),
                            ModelUtil.determineDatatype(fieldClass));
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return columns;
    }

    public String getPrimaryKey() {
        Field field = ModelUtil.getPrimaryField(getClass());
        return field == null ? null : field.getName();
    }

    public String getSelection() {
        Field field = ModelUtil.getPrimaryField(getClass());
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
                Log.e(ModelUtil.getClassName(getClass()),
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
                        Log.e(ModelUtil.getClassName(getClass()),
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

    public int getRevision() {
        try {
            Revision anno = getClass().getAnnotation(Revision.class);
            if (anno == null) {
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
            } else if (c.equals(boolean.class) || c.equals(Boolean.class)) {
                values.put(name, field.getBoolean(obj));
            } else {
                Log.w("Model Object", "Unhandled variable class " + c);
            }
        } catch (IllegalAccessException e) {
            Log.e(ModelUtil.getClassName(getClass()),
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
            } else if (c.equals(boolean.class) || c.equals(Boolean.class)) {
                values.put(field.getName(), field.getBoolean(obj) ? 1 : 0);
            } else if (ModelUtil.isSubclassOf(c, ModelObject.class)) {
                ModelObject inner = (ModelObject) field.get(obj);
                if (inner != null) {
                    nest(values, field.getName(), inner.save(), inner);
                }
            } else if (ModelUtil.isSubclassOf(c, AbstractCollection.class)) {
                ParameterizedType pType = (ParameterizedType) field.getGenericType();
                Class<?> generic = (Class<?>) pType.getActualTypeArguments()[0];
                Class<?> parentClass = ModelUtil.getPrimaryField(getClass()).getType();
                Object parentKey = ModelUtil.getPrimaryField(getClass()).get(this);
                if (ModelUtil.isSubclassOf(generic, ModelObject.class)) {
                    AbstractCollection<? extends ModelObject> collection = (AbstractCollection<? extends ModelObject>) field.get(this);
                    if(collection != null) {
                        int inx = 0;
                        for (ModelObject object : collection) {
                            Field child = object.save();
                            ContentValues childValues = new ContentValues();
                            putValueWithName(childValues, "parentKey", parentClass, parentKey);
                            putValueWithName(childValues, "childKey", child.getType(), child.get(object));
                            childValues.put("inx", inx++);
                            ModelManager.connect(this.getTableName(), field.getName(), childValues);
                        }
                    }
                } else {
                    AbstractCollection collection = (AbstractCollection) field.get(this);
                    if(collection != null) {
                        int inx = 0;
                        for (Object object :collection) {
                            ContentValues childValues = new ContentValues();
                            putValueWithName(childValues, "parentKey", parentClass, parentKey);
                            putValueWithName(childValues, "childKey", generic, object);
                            childValues.put("inx", inx++);
                            ModelManager.connect(this.getTableName(), field.getName(), childValues);
                        }
                    }
                }
//                Commented out to warn of unhandled field type
//            } else if (ModelUtil.isSubclassOf(c, AbstractMap.class)){
//                System.out.printf("Found a Map: %s\n", field.getName());
            } else {
                Log.w("Model Object", "Unhandled field class " + c);
            }
        } catch (IllegalAccessException e) {
            Log.e(ModelUtil.getClassName(getClass()),
                    String.format("Access modifier on %s is preventing persistence.\nDeclare as public or Override String %s.getPrimaryKey()", field.getName(), field.getName()));
        }
    }

    private void putValueWithName(ContentValues values, String name, Class<?> c, Object object) {
        if (c.equals(String.class) || c.equals(char.class)) {
            values.put(name, (String) object);
        } else if (c.equals(int.class) || c.equals(Integer.class)) {
            values.put(name, (int) object);
        } else if (c.equals(long.class) || c.equals(Long.class)) {
            values.put(name, (long) object);
        } else if (c.equals(float.class) || c.equals(Float.class)) {
            values.put(name, (float) object);
        } else if (c.equals(double.class) || c.equals(Double.class)) {
            values.put(name, (double) object);
        } else if (c.equals(boolean.class) || c.equals(Boolean.class)) {
            values.put(name, ((boolean) object) ? 1 : 0);
        }
    }
}
