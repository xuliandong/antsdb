/*-------------------------------------------------------------------------------------------------
 _______ __   _ _______ _______ ______  ______
 |_____| | \  |    |    |______ |     \ |_____]
 |     | |  \_|    |    ______| |_____/ |_____]

 Copyright (c) 2016, antsdb.com and/or its affiliates. All rights reserved. *-xguo0<@

 This program is free software: you can redistribute it and/or modify it under the terms of the
 GNU Affero General Public License, version 3, as published by the Free Software Foundation.

 You should have received a copy of the GNU Affero General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
-------------------------------------------------------------------------------------------------*/
package com.antsdb.saltedfish.nosql;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;

import com.antsdb.saltedfish.cpp.Unsafe;
import com.antsdb.saltedfish.util.UberUtil;

public class CheckPoint {
	/* humpback data starts from 0 */

	/** byte, tracks server crash */
	static final int OFFSET_IS_OPEN = 4;
	
	/** 8 bytes. unique identifier */
	static final int OFFSET_SERVER_ID = 8;
	
	/* orca setting */
	
	/** orca data starts from 80 */
	static final int OFFSET_ROWID = 0x80;
	
	/* slave settings */
	
	/** slave log file */
	static final int OFFSET_SLAVE_LOG_FILE = 0x100;
	/** slave log position */
	static final int OFFSET_SLAVE_LOG_POS = 0x120;
	
    File file;
    FileChannel ch;
    ByteBuffer buf;
    long addr;
    
    public CheckPoint(File file) throws IOException {
        super();
        this.file = file;
        this.ch = FileChannel.open(
                file.toPath(), 
                StandardOpenOption.CREATE, 
                StandardOpenOption.WRITE, 
                StandardOpenOption.READ);
        this.ch.lock();
        this.buf = this.ch.map(MapMode.READ_WRITE, 0, 512);
        this.addr = UberUtil.getAddress(buf);
        if (getServerId() == 0) {
        	Unsafe.putLong(this.addr + OFFSET_SERVER_ID, System.nanoTime());
        }
    }
    
    void close() throws IOException {
        this.ch.close();
    }
    
    boolean isDatabaseOpen() {
        int n = buf.getInt(OFFSET_IS_OPEN);
        return n != 0;
    }
    
    CheckPoint setDatabaseOpen(boolean b) {
        buf.putInt(OFFSET_IS_OPEN, b ? 1 : 0);
        return this;
    }
    
    public long getRowid() {
    	long rowid = Unsafe.getLong(this.addr + OFFSET_ROWID);
    	return rowid;
    }
    
    public long getAndIncrementRowid() {
    	long rowid = Unsafe.getAndAddLong(this.addr + OFFSET_ROWID, 1);
    	return rowid;
    }
    
    public void setRowid(long value) {
    	Unsafe.putLong(this.addr + OFFSET_ROWID, value);
    }
    
    public long getServerId() {
    	long id = Unsafe.getLong(this.addr + OFFSET_SERVER_ID);
    	return id;
    }

    public void setSlaveLogFile(String file) {
    	if (file.length() >= 0x20) {
    		throw new IllegalArgumentException();
    	}
    	Unsafe.setMemory(this.addr + OFFSET_SLAVE_LOG_FILE, 0x20, (byte)0);
    	for (int i=0; i<file.length(); i++) {
    		int ch = file.charAt(i);
    		Unsafe.putByte(this.addr + OFFSET_SLAVE_LOG_FILE + i, (byte)ch);
    	}
    }
    
    public void setSlaveLogPosition(long pos) {
    	Unsafe.putLong(this.addr + OFFSET_SLAVE_LOG_POS, pos);
    }
    
    public String getSlaveLogFile() {
    	StringBuilder buf = new StringBuilder();
    	for (int i=0; i<0x20; i++) {
    		int ch = Unsafe.getByte(this.addr + OFFSET_SLAVE_LOG_FILE + i);
    		if (ch == 0) {
    			break;
    		}
    		buf.append((char)ch);
    	}
    	return buf.toString();
    }
    
    public long getSlaveLogPosition() {
    	long value = Unsafe.getLong(this.addr + OFFSET_SLAVE_LOG_POS);
    	return value;
    }
}