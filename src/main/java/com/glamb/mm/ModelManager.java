package com.glamb.mm;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;

import java.util.List;

public class ModelManager extends SQLiteOpenHelper {
    private static final String DB_VERSION_KEY = "com.glamb.db.version";

    private static ModelManager instance;

    public static void init(Context context, String name) {
        if (instance == null) {
            instance = new ModelManager(context, name);
            MetaData dTable = new MetaData();
            dTable.name = dTable.getTableName();
            dTable.revision = dTable.getRevision();
            dTable.save();
        }
    }

    private static ModelManager get() {
        if (instance == null) {
            throw new RuntimeException("Database Not Initialized. \n Call ModelManager.init(Context) before use");
        }

        return instance;
    }

    private SQLiteDatabase db;

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
        get().registerObject(object);
    }

    public static void createRelationship(String parent, String field, String pType, String cType){
        get().createRelationshipTable(parent, field, pType, cType);
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

    public static void connect(String parent, String field, ContentValues values){
        get().connectTables(parent, field, values);
    }

    public static int count(Query query) {
        return get().countData(query);
    }

    public static Cursor query(String table, String selection) {
        return get().queryData(table, selection);
    }

    public static Cursor query(Query query) {
        return get().queryData(query);
    }

    private void registerObject(ModelObject object) {
        MetaData data = new MetaData(object.getTableName());
        if (data.revision == -1) {
            createTable(object);
        } else {
            if (data.revision == object.getRevision()) {
                return;
            } else if (data.revision < object.getRevision()) {
                data.revision = object.getRevision();
                data.save();
                upgradeTable(object);
            } else if (data.revision >= 0 && data.revision > object.getRevision()) {
                throw new RuntimeException(String.format("Database version of %s is newer than model version", object.getTableName()));
            }
        }


        data.revision = object.getRevision();
        data.save();
    }

    public void createRelationshipTable(String parent, String field, String pType, String cType){
        String sql = String.format("CREATE TABLE IF NOT EXISTS %s_%s (" +
                        "parentKey %s, " +
                        "childKey %s, " +
                        "inx INTEGER, " +
                        "PRIMARY KEY(parentKey, inx))",
                parent, field, pType, cType);

        db.execSQL(sql);
    }

    public void createTable(ModelObject object) {
        StringBuilder create = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(object.getTableName()).append(" ( ");
        for (String key : object.getColumns().keySet()) {
            create.append(key).append(" ").append(object.getColumns().get(key)).append(", ");
        }
        create.replace(create.length() - 1, create.length(), "");
        create.append(" PRIMARY KEY (").append(object.getPrimaryKey()).append("));");

        db.execSQL(create.toString());
    }

    public void upgradeTable(ModelObject object) {
        db.beginTransaction();
        try {
            //Move Table Data to new Table
            String sql = String.format("ALTER TABLE %s RENAME TO %s_old", object.getTableName(), object.getTableName());
            db.execSQL(sql);
            createTable(object);
            Class<? extends ModelObject> clazz = object.getClass();
            Query<? extends ModelObject> query = Query.allOfObject(clazz);
            query.table += "_old";
            List<? extends ModelObject> objects = query.execute();
            for (ModelObject transfer : objects) {
                transfer.save();
            }
            db.execSQL(String.format("DROP TABLE %s_old", object.getTableName()));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insertData(ModelObject object) {
        object.insertionTime = System.currentTimeMillis();
        insertData(object, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void insertData(ModelObject object, int conflict) {
        object.insertionTime = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.insertWithOnConflict(object.getTableName(), null, object.getValues(), conflict);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void updateData(ModelObject object) {
        object.insertionTime = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.update(object.getTableName(), object.getValues(), object.getSelection(), null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void connectTables(String parent, String field, ContentValues values){
        db.beginTransaction();
        try {
            db.insertWithOnConflict(parent + "_" + field, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private int countData(Query query) {
        Cursor cursor = db.query(query.table, new String[]{"count(*)"}, query.selection, query.args, query.groupBy, query.having, query.orderBy);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();

        return count;


    }

    private Cursor queryData(String table, String selection) {
        return db.query(table, null, selection, null, null, null, null, null);
    }

    private Cursor queryData(Query query) {
        return db.query(query.table, query.columns, query.selection, query.args, query.groupBy, query.having, query.orderBy, query.limit);
    }
}
