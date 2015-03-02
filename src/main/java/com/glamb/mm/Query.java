package com.glamb.mm;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class Query<T extends ModelObject> {
    private Class<T> mClazz;
    protected String table;
    protected String[] columns;
    protected String selection;
    protected String[] args;
    protected String groupBy;
    protected String having;
    protected String orderBy;
    protected String limit;


    public static <T extends ModelObject> Query<T> allOfObject(Class<T> clazz) {
        return new Query<>(clazz);
    }

    public static <T extends ModelObject> Query<T> selectionOfObject(Class<T> clazz, String selection){
        Query<T> query = new Query<>(clazz);
        query.selection = selection;
        return query;
    }

    private Query(Class<T> clazz) {
        mClazz = clazz;
        T t = instanceOfClass();
        table = t.getTableName();
        orderBy = t.getOrderBy();
    }

    private T instanceOfClass() {
        try {
            return mClazz.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void populateList(List<T> list, Cursor cursor) {
        try {
            if (cursor.moveToFirst()) {
                do {
                    T t = mClazz.newInstance();
                    t.init(cursor);
                    list.add(t);
                    cursor.moveToNext();
                } while(!cursor.isAfterLast());
            }
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public List<T> execute() {
        List<T> list = new ArrayList<>();
        Cursor cursor = ModelManager.query(this);

        populateList(list, cursor);

        return list;
    }

    public List<T> page(int size, int number){
        limit = String.format("%d, %d", number * size, size);
        return execute();
    }

    public int count(){
        return ModelManager.count(this);
    }
}
