package com.glamb.paradigm;

import com.glamb.paradigm.annotation.PrimaryKey;
import com.glamb.paradigm.annotation.Revision;

@Revision(0)
public class MetaData extends ModelObject{
    @PrimaryKey
    public String name;
    public int revision;

    public MetaData(){
        super();
    }

    public MetaData(String table){
        super(table);
    }

    public void assignDefaults(){
        revision = -1;
    }
}
