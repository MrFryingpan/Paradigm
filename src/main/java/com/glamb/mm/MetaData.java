package com.glamb.mm;

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
