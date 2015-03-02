package com.glamb.mm;

public @interface OrderBy {
    Order order() default Order.ASC;

    public enum Order{
        ASC("ASC"),
        DESC("DESC");

        private String val;
        Order(String val){
            this.val = val;
        }

        public String toString(){
            return val;
        }
    }
}
