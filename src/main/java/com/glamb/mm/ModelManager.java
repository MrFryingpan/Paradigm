package com.glamb.mm;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public class ModelManager extends SQLiteOpenHelper {
    private static final String DB_VERSION_KEY = "com.glamb.db.version";

    private static ModelManager instance;

    public static void init(Context context, String name) {
        if (instance == null) {
            instance = new ModelManager(context, name);
        }
    }

    private static ModelManager get() {
        if (instance == null) {
            throw new RuntimeException("Database Not Initialized. \n Call ModelManager.init(Context) before use");
        }

        return instance;
    }

    private SQLiteDatabase db;
    private Set<Class<?>> registeredClasses = new HashSet<>();

    private ModelManager(Context context, String name) {
        super(context, name, null,
                PreferenceManager.getDefaultSharedPreferences(context).getInt(DB_VERSION_KEY, 1));
        db = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public static void register(ModelObject object) {
        if (!isRegistered(object.getClass()))
            get().registerObject(object);
    }

    public static void insert(ModelObject object) {
        get().insertData(object);
    }

    public static void insert(ModelObject object, int conflict) {
        get().insertData(object, conflict);
    }

    public static void update(ModelObject object) {
        get().updateData(object);
    }

    public static int count(Query query){
        return get().countData(query);
    }

    public static Cursor query(String table, String selection) {
        return get().queryData(table, selection);
    }

    public static Cursor query(Query query){
        return get().queryData(query);
    }

    private static boolean isRegistered(Class<?> clazz) {
        return get().registeredClasses.contains(clazz);
    }

    private void registerObject(ModelObject object) {
        registeredClasses.add(object.getClass());

        StringBuilder create = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(object.getTableName()).append(" ( ");
        for (String key : object.getColumns().keySet()) {
            create.append(key).append(" ").append(object.getColumns().get(key)).append(", ");
        }
        create.replace(create.length() - 1, create.length(), "");
        create.append(" PRIMARY KEY (").append(object.getPrimaryKey()).append("));");

        db.execSQL(create.toString());

    }

    private void insertData(ModelObject object) {
        object.insertionTime = System.currentTimeMillis();
        insertData(object, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void insertData(ModelObject object, int conflict) {
        object.insertionTime = System.currentTimeMillis();
        db.insertWithOnConflict(object.getTableName(), null, object.getValues(), conflict);
    }

    private void updateData(ModelObject object) {
        object.insertionTime = System.currentTimeMillis();
        db.update(object.getTableName(), object.getValues(), object.getSelection(), null);
    }

    private int countData(Query query){
        Cursor cursor =  db.query(query.table, new String[]{"count(*)"}, query.selection, query.args, query.groupBy, query.having, query.orderBy);
        if(cursor.moveToFirst()){
            return cursor.getInt(0);
        }
        return 0;
    }

    private Cursor queryData(String table, String selection) {
        return db.query(table, null, selection, null, null, null, null, null);
    }

    private Cursor queryData(Query query){
        return db.query(query.table, query.columns, query.selection, query.args, query.groupBy, query.having, query.orderBy, query.limit);
    }
}
