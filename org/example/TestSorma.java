package org.example;

import com.zoffcc.applications.sorm.OrmaDatabase;

public class TestSorma {
    private static final String TAG = "TestSorma";
    static final String Version = "0.99.0";

    public static void main(String[] args) {
        System.out.println("TestSorma v" + Version);

        com.zoffcc.applications.sorm.OrmaDatabase.init("./", "main.db");
        OrmaDatabase orma = new OrmaDatabase();
        System.out.println("orma: " + orma);
        com.zoffcc.applications.sorm.OrmaDatabase.shutdown();
    }
}
