/**
 * [TRIfA], Java part of Tox Reference Implementation for Android
 * Copyright (C) 2017 Zoff <zoff@zoff.cc>
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package com.zoffcc.applications.sorm;

import com.zoffcc.applications.trifa.Log;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.zoffcc.applications.sorm.OrmaDatabase.*;
import static com.zoffcc.applications.sorm.OrmaDatabase.orma_semaphore_lastrowid_on_insert;

@Table
public class BootstrapNodeEntryDB
{
    static final String TAG = "trifa.BtpNodeEDB";

    @PrimaryKey(autoincrement = true, auto = true)
    public long id;

    @Column(indexed = true, helpers = Column.Helpers.ALL)
    public long num;

    @Column(indexed = true, helpers = Column.Helpers.ALL)
    public boolean udp_node; // true -> UDP bootstrap node, false -> TCP relay node

    @Column(indexed = true, helpers = Column.Helpers.ALL)
    public String ip;

    @Column(indexed = true, helpers = Column.Helpers.ALL)
    public long port;

    @Column(indexed = true, helpers = Column.Helpers.ALL)
    public String key_hex;

    // ______@@SORMA_END@@______

