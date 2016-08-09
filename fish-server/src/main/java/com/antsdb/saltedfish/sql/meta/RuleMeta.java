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
package com.antsdb.saltedfish.sql.meta;

import static com.antsdb.saltedfish.sql.OrcaConstant.TABLENAME_SYSRULE;
import static com.antsdb.saltedfish.sql.OrcaConstant.TABLENAME_SYSRULECOL;

import java.util.ArrayList;
import java.util.List;

import com.antsdb.saltedfish.nosql.SlowRow;
import com.antsdb.saltedfish.sql.Orca;
import com.antsdb.saltedfish.sql.vdm.ObjectName;
import com.antsdb.saltedfish.util.CodingError;
import com.antsdb.saltedfish.util.UberObject;

public abstract class RuleMeta<T> extends UberObject {
    final static ObjectName RULE_SEQUENCE = new ObjectName(Orca.SYSNS, TABLENAME_SYSRULE); 
    final static ObjectName RULE_COL_SEQUENCE = new ObjectName(Orca.SYSNS, TABLENAME_SYSRULECOL); 
    
    public static enum Rule {
        PrimaryKey,
        Index,
        ForeignKey,
    }
    
    SlowRow row;
    List<SlowRow> ruleColumns = new ArrayList<SlowRow>();

    public RuleMeta(Orca orca, Rule type) {
        int id = (int)orca.getIdentityService().getSequentialId(RULE_SEQUENCE);
        row = new SlowRow(id);
        setId(id);
        setType(type);
    }
    
    protected RuleMeta(SlowRow row) {
        super();
        this.row = row;
    }

    public void setType(Rule type) {
        row.set(ColumnId.sysrule_rule_type.getId(), type.ordinal());
    }

    int getId() {
        return (Integer)this.row.get(ColumnId.sysrule_id.getId());
    }
    
    void setId(int id) {
        this.row.set(ColumnId.sysrule_id.getId(), id);
    }
    
    void setTableId(int id) {
        row.set(ColumnId.sysrule_table_id.getId(), id);
    }
    
    @SuppressWarnings("unchecked")
    public T setName(String name) {
        row.set(ColumnId.sysrule_rule_name.getId(), name);
        return (T)this;
    }
    
    public String getName() {
    	return (String)row.get(ColumnId.sysrule_rule_name.getId());
    }
    
    @SuppressWarnings("unchecked")
    public T addColumn(Orca orca, ColumnMeta column) {
        int key = (int)orca.getIdentityService().getSequentialId(RULE_COL_SEQUENCE);
        SlowRow ruleColumn = new SlowRow(key);
        ruleColumn.set(ColumnId.sysrulecol_id.getId(), key);
        ruleColumn.set(ColumnId.sysrulecol_rule_id.getId(), getId());
        ruleColumn.set(ColumnId.sysrulecol_column_id.getId(), column.getId());
        this.ruleColumns.add(ruleColumn);
        return (T)this;
    }

    public List<SlowRow> getRuleColumns() {
    	return this.ruleColumns;
    }
    
    public List<ColumnMeta> getColumns(TableMeta table) {
        List<ColumnMeta> list = new ArrayList<ColumnMeta>();
        for (SlowRow i:this.ruleColumns) {
            int columnId = (int)i.get(ColumnId.sysrulecol_column_id.getId());
            ColumnMeta col = table.getColumn(columnId);
            if (col == null) {
                throw new CodingError();
            }
            list.add(col);
        }
        return list;
    }

    public SlowRow findRuleColumn(ColumnMeta column) {
        for (SlowRow i:this.ruleColumns) {
            int columnId = (int)i.get(ColumnId.sysrulecol_column_id.getId());
            if (columnId == column.getId()) {
            	return i;
            }
        }
        return null;
    }

	@Override
	public String toString() {
		return getName();
	}
}