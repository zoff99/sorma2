package com.example.sorma2exampleapp;

import android.content.Context;
import android.util.Log;

import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Todo;

import java.io.File;
import java.util.Date;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static com.zoffcc.applications.sorm.OrmaDatabase.run_multi_sql;
import static com.zoffcc.applications.sorm.OrmaDatabase.set_schema_upgrade_callback;

@SuppressWarnings("ALL")
public class sorma2example
{
    private String path;
    private static final String TAG = "Sorma2-Example:";
    private static String ret = "";

    static OrmaDatabase orma = null;
    final static int ORMA_CURRENT_DB_SCHEMA_VERSION = 1; // increase for database schema changes // minimum is 1
    private final static String MAIN_DB_NAME = "main.db";
    private static boolean PREF__DB_wal_mode = true; // use WAL mode
    private final String PREF__DB_secrect_key = "this is the password: ken sent me !?%";


    static final int N_ITEMS = 10;
    static final int N_OPS = 100;
    final String titlePrefix = "title ";
    final String contentPrefix = "content content content\n"
                                 + "content content content\n"
                                 + "content content content\n"
                                 + " ";

    void upgrade_db_schema_do(int old_version, int new_version)
    {
        if (new_version == 1) {
            run_multi_sql("CREATE TABLE IF NOT EXISTS \"Category\" (\n" + "  \"id\" INTEGER,\n" + "  \"name\" TEXT,\n" +
                          "  PRIMARY KEY(\"id\" AUTOINCREMENT)\n" + ");\n");
            run_multi_sql("CREATE TABLE IF NOT EXISTS \"Todo\" (\n" + "  \"id\" INTEGER,\n" + "  \"title\" TEXT,\n" +
                          "  \"content\" TEXT,\n" + "  \"done\" BOOLEAN,\n" + "  \"createdTime\" INTEGER,\n" +
                          "  PRIMARY KEY(\"id\" AUTOINCREMENT)\n" + ");\n");
            run_multi_sql("CREATE TABLE IF NOT EXISTS \"Item\" (\n" + "  \"name\" TEXT,\n" + "  \"category\" TEXT,\n" +
                          "  PRIMARY KEY(\"name\" )\n" + ");");
            run_multi_sql("CREATE TABLE IF NOT EXISTS \"Item2\" (\n" + "  \"name\" TEXT,\n" +
                          "  \"category1\" TEXT,\n" + "  \"category2\" TEXT,\n" + "  \"zonedTimestamp\" INTEGER,\n" +
                          "  \"localDateTime\" INTEGER,\n" + "  PRIMARY KEY(\"name\" )\n" + ");");
        }
    }

    private OrmaDatabase OrmaDatabase_wrapper(String dbs_path, String pref__db_secrect_key, boolean pref__db_wal_mode)
    {
        set_schema_upgrade_callback(new OrmaDatabase.schema_upgrade_callback()
        {
            @Override
            public void upgrade(int old_version, int new_version)
            {
                Log.i(TAG, "trying to upgrade schema from " + old_version + " to " + new_version);
                upgrade_db_schema_do(old_version, new_version);
            }
        });

        OrmaDatabase orma = new OrmaDatabase(dbs_path, pref__db_secrect_key, pref__db_wal_mode);
        try
        {
            OrmaDatabase.init(ORMA_CURRENT_DB_SCHEMA_VERSION);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return orma;
    }

    String testme(Context c)
    {
        long time_start = System.currentTimeMillis();

        System.out.println(TAG + "starting ...");
        ret = ret + "\n" + "starting ...";

        // define the path where the db file will be located
        String dbs_path = c.getDir("dbs", MODE_PRIVATE).getAbsolutePath() + "/" + MAIN_DB_NAME;
        // Log.i(TAG, "db:path=" + dbs_path);
        File database_dir = new File(new File(dbs_path).getParent());
        database_dir.mkdirs();

        orma = OrmaDatabase_wrapper(dbs_path, PREF__DB_secrect_key, PREF__DB_wal_mode);
        System.out.println(TAG + "db is open");
        ret = ret + "\n" + "db is open";

        run_multi_sql("DELETE FROM todo;");
        System.out.println(TAG + "cleaned out table");
        ret = ret + "\n" + "cleaned out table";

        startInsertWithOrma();
        startSelectAllWithOrma();

        // all finished
        System.out.println(TAG + "finished.");
        ret = ret + "\n" + "finished";

        return ret;
    }

    private void startSelectAllWithOrma()
    {
        long start = System.currentTimeMillis();
        for (int j = 0; j < N_OPS; j++)
        {
            List<Todo> todos = orma.selectFromTodo().orderByCreatedTimeAsc().toList();
            for (Todo todo : todos)
            {
                @SuppressWarnings("unused") String title = todo.title;
                @SuppressWarnings("unused") String content = todo.content;
                @SuppressWarnings("unused") String createdTime = todo.createdTime;
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(TAG + "Orma/insert " + (end-start) + "ms");
        ret = ret + "\n" + "Orma/insert " + (end-start) + "ms";
    }

    private void startInsertWithOrma()
    {
        long start = System.currentTimeMillis();
        for (int j = 0; j < N_OPS; j++)
        {
            long now = System.currentTimeMillis();
            for (int i = 0; i < N_ITEMS; i++)
            {
                Todo todo = new Todo();
                todo.title = titlePrefix + i;
                todo.content = contentPrefix + i;
                todo.createdTime = new Date(now).toString();
                orma.insertIntoTodo(todo);
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(TAG + "Orma/forEachAll " + (end-start) + "ms");
        ret = ret + "\n" + "Orma/forEachAll " + (end-start) + "ms";
    }
}
