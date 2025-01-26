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
    static final boolean wal_mode = true;

    static final int iterations_in_threads = 50;
    static final int reader_threads = 1000;
    static boolean running = true;

    static class CK1 implements Runnable {
        public void run()
        {
            for (int x=0;x<iterations_in_threads;x++)
            {
                try
                {
                    chkp();
                }
                catch(Exception e)
                {
                }

                try
                {
                    Thread.sleep(14);
                }
                catch(Exception e)
                {
                }

                if (!running)
                {
                    return;
                }
            }
        }
    }

    static class WriteM implements Runnable {
        OrmaDatabase orma;
        public WriteM(OrmaDatabase orma)
        {
            this.orma = orma;
        }

        public void run()
        {
            for (int x=0;x<iterations_in_threads;x++)
            {
                try {
                    Message m = new Message();
                    m.tox_friendpubkey = "BBBBBBB" + x;
                    m.text = "________TEXT22________" + x;
                    long rowid = orma.insertIntoMessage(m);
                    System.out.println(getCurrentTimeStamp() + "rowid2: " + rowid);
                    Thread.sleep(5);
                }
                catch(Exception e)
                {
                }
            }
        }
    }

    static class UpdateM implements Runnable {
        OrmaDatabase orma;
        public UpdateM(OrmaDatabase orma)
        {
            this.orma = orma;
        }

        public void run()
        {
            for (int x=0;x<iterations_in_threads;x++)
            {
                try {
                    orma.updateMessage().tox_friendpubkeyEq("AAAAAAA").text("22222222222").execute();
                    Thread.sleep(3);
                }
                catch(Exception e)
                {
                }
            }
        }
    }

    static class DeleteM implements Runnable {
        OrmaDatabase orma;
        public DeleteM(OrmaDatabase orma)
        {
            this.orma = orma;
        }

        public void run()
        {
            for (int x=0;x<iterations_in_threads;x++)
            {
                try {
                    orma.deleteFromMessage().execute();
                    Thread.sleep(100);
                }
                catch(Exception e)
                {
                }
            }
        }
    }

    static class ReadM implements Runnable {

        OrmaDatabase orma;
        public ReadM(OrmaDatabase orma)
        {
            this.orma = orma;
        }

        public void run()
        {
            for (int i=0;i<iterations_in_threads;i++)
            try
            {
                Thread.sleep(2);
                int c = this.orma.selectFromMessage().count();
                // System.out.println(getCurrentTimeStamp() + "count: " + c);
            }
            catch(Exception e)
            {
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

        OrmaDatabase orma = new OrmaDatabase("./main.db", "", wal_mode);
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

        Thread tchkp = new Thread(new CK1());
        tchkp.start();

        Thread t1 = new Thread(new ReadM(orma));
        t1.start();

        Thread t2 = new Thread(new WriteM(orma));
        t2.start();

        Thread t3 = new Thread(new UpdateM(orma));
        t3.start();

        Thread t4 = new Thread(new DeleteM(orma));
        t4.start();

        try
        {
            Thread.sleep(200);
        }
        catch(Exception e)
        {
        }

        Thread[] tread = new Thread[reader_threads];
        for (int i=0;i<reader_threads;i++) {
            tread[i] = new Thread(new ReadM(orma));
            tread[i].start();
        }

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
            Thread.sleep(2 * 1000);
        }
        catch(Exception e)
        {
        }

        chkp();

        running = false;
        tchkp.interrupt();

        System.out.println(getCurrentTimeStamp() + "stop threads");

        for (int i=0;i<reader_threads;i++) {
            try
            {
                tread[i].interrupt();
                tread[i].join();
            }
            catch(Exception e)
            {
            }
        }

        try
        {
            t1.join();
            t2.join();
            t3.join();
            t4.join();
            tchkp.join();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        System.out.println(getCurrentTimeStamp() + "stop threads done");

        shutdown();
    }
}
