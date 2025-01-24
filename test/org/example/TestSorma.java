package org.example;

import com.zoffcc.applications.sorm.OrmaDatabase;
import com.zoffcc.applications.sorm.Message;
import static com.zoffcc.applications.sorm.OrmaDatabase.*;
import static com.zoffcc.applications.sorm.OrmaDatabase.init;
import static com.zoffcc.applications.sorm.OrmaDatabase.shutdown;
import java.util.Date;
import java.text.SimpleDateFormat;

public class TestSorma {
    private static final String TAG = "TestSorma";
    static final String Version = "0.99.0";

    static class WriteB implements Runnable {
        public void run()
        { 
            try {
                OrmaDatabase.orma_global_writeLock.lock();
                System.out.println(getCurrentTimeStamp() + "write lock: locked =================");
                chkp();
                Thread.sleep(1 * 1000);
            }
            catch(Exception e)
            {
            }
            finally
            {
                System.out.println(getCurrentTimeStamp() + "write lock: unlocked  ==============");
                OrmaDatabase.orma_global_writeLock.unlock();
            }
        }
    }

    public static String getCurrentTimeStamp() {
        return (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date())) + ": ";
    }

    public static void chkp()
    {
        System.out.println(
            getCurrentTimeStamp() +
            "checkpoint: " + OrmaDatabase.run_query_for_single_result("PRAGMA busy_timeout = 1000; PRAGMA wal_checkpoint(TRUNCATE);"));
    }

    public static void main(String[] args) {
        System.out.println("TestSorma v" + Version);

        OrmaDatabase orma = new OrmaDatabase("./main.db", "", true);
        init();
        System.out.println(getCurrentTimeStamp() + "orma: " + orma);

        chkp();

        OrmaDatabase.run_multi_sql("CREATE TABLE IF NOT EXISTS Message ("+
        "message_id	INTEGER NOT NULL,"+
        "tox_friendpubkey	TEXT NOT NULL,"+
        "direction	INTEGER NOT NULL,"+
        "TOX_MESSAGE_TYPE	INTEGER NOT NULL,"+
        "TRIFA_MESSAGE_TYPE	INTEGER NOT NULL DEFAULT 0,"+
        "state	INTEGER NOT NULL DEFAULT 1,"+
        "ft_accepted	BOOLEAN NOT NULL DEFAULT false,"+
        "ft_outgoing_started	BOOLEAN NOT NULL DEFAULT false,"+
        "filedb_id	INTEGER NOT NULL DEFAULT -1,"+
        "filetransfer_id	INTEGER NOT NULL DEFAULT -1,"+
        "sent_timestamp	INTEGER DEFAULT 0,"+
        "sent_timestamp_ms	INTEGER DEFAULT 0,"+
        "rcvd_timestamp	INTEGER DEFAULT 0,"+
        "rcvd_timestamp_ms	INTEGER DEFAULT 0,"+
        "filetransfer_kind	INTEGER DEFAULT 0,"+
        "read	BOOLEAN NOT NULL,"+
        "send_retries	INTEGER NOT NULL DEFAULT 0,"+
        "is_new	BOOLEAN NOT NULL,"+
        "ft_outgoing_queued BOOLEAN NOT NULL,"+
        "msg_at_relay BOOLEAN NOT NULL,"+
        "sent_push BOOLEAN NOT NULL,"+
        "text	TEXT,"+
        "filename_fullpath	TEXT,"+
        "msg_idv3_hash	TEXT,"+
        "msg_id_hash	TEXT,"+
        "raw_msgv2_bytes	TEXT,"+
        "msg_version	INTEGER NOT NULL DEFAULT 0,"+
        "resend_count	INTEGER NOT NULL DEFAULT 2,"+
        "id	INTEGER,"+
        "PRIMARY KEY(\"id\" AUTOINCREMENT)"+
        ");");

        Message m = new Message();
        m.tox_friendpubkey = "AAAAAAA";
        m.text = "________TEXT11________";
        long rowid = orma.insertIntoMessage(m);
        System.out.println(getCurrentTimeStamp() + "rowid1: " + rowid);

        chkp();


        Thread t3 = new Thread(new WriteB());
        t3.start();
        Thread t4 = new Thread(new WriteB());
        t4.start();
        Thread t5 = new Thread(new WriteB());
        t5.start();

        System.out.println(getCurrentTimeStamp() + "trying to select ...");

        int c = orma.selectFromMessage().count();
        System.out.println(getCurrentTimeStamp() + "count: " + c);

        m = new Message();
        m.tox_friendpubkey = "BBBBBBB";
        m.text = "________TEXT22________";
        rowid = orma.insertIntoMessage(m);
        System.out.println(getCurrentTimeStamp() + "rowid2: " + rowid);

        c = orma.selectFromMessage().count();
        System.out.println(getCurrentTimeStamp() + "count: " + c);

        orma.updateMessage().tox_friendpubkeyEq("AAAAAAA").text("22222222222").execute();

        orma.deleteFromMessage().tox_friendpubkeyEq("BBBBBBB").execute();

        chkp();

        c = orma.selectFromMessage().count();
        System.out.println(getCurrentTimeStamp() + "count: " + c);

        try
        {
            Thread.sleep(1 * 1000);
        }
        catch(Exception e)
        {
        }

        chkp();

        shutdown();
    }
}
