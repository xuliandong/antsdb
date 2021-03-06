/*-------------------------------------------------------------------------------------------------
 _______ __   _ _______ _______ ______  ______
 |_____| | \  |    |    |______ |     \ |_____]
 |     | |  \_|    |    ______| |_____/ |_____]

 Copyright (c) 2016, antsdb.com and/or its affiliates. All rights reserved. *-xguo0<@

 This program is free software: you can redistribute it and/or modify it under the terms of the
 GNU GNU Lesser General Public License, version 3, as published by the Free Software Foundation.

 You should have received a copy of the GNU Affero General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/lgpl-3.0.en.html>
-------------------------------------------------------------------------------------------------*/
package com.antsdb.saltedfish.sql.vdm;

import org.apache.commons.lang.StringUtils;

public class ObjectName {
    public String catalog;
    public String schema;
    public String table;
    
    public String getNamespace() {
        if (catalog == null) {
            if (schema == null) {
                return "";
            }
            return schema;
        }
        else {
            return catalog + "." + schema;
        }
    }
    
    public String getTableName() {
        return this.table;
    }

    public ObjectName() {}
    
    public ObjectName(String schema, String table) {
        super();
        this.schema = schema;
        this.table = table;
    }
    
    public ObjectName(String catalog, String schema, String table) {
        super();
        this.catalog = catalog;
        this.schema = schema;
        this.table = table;
    }
    
    public static ObjectName parse(String fullname) {
        String[] names = StringUtils.split(fullname, '.');
        if (names.length == 2) {
            return new ObjectName(names[0], names[1]);
        }
        else if (names.length == 3) {
            return new ObjectName(names[0], names[1], names[2]);
        }
        else {
            throw new IllegalArgumentException();
        }
    }
    
    @Override
    public String toString() {
        String ns = getNamespace();
        if (!StringUtils.isEmpty(ns)) {
            return getNamespace() + "." + getTableName();
        }
        else {
            return getTableName();
        }
    }

    @Override
    public int hashCode() {
        return (this.table == null) ? 0 : this.table.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ObjectName)) {
            return false;
        }
        ObjectName that = (ObjectName)obj;
        if (!StringUtils.equalsIgnoreCase(this.catalog, that.catalog)) {
            return false;
        }
        if (!StringUtils.equalsIgnoreCase(this.schema, that.schema)) {
            return false;
        }
        if (!StringUtils.equalsIgnoreCase(this.table, that.table)) {
            return false;
        }
        return true;
    }

    
}
