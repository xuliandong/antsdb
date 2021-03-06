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
package com.antsdb.saltedfish.sql.mysql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;

import com.antsdb.saltedfish.lexer.MysqlParser.Column_defContext;
import com.antsdb.saltedfish.lexer.MysqlParser.Constraint_defContext;
import com.antsdb.saltedfish.lexer.MysqlParser.Create_defContext;
import com.antsdb.saltedfish.lexer.MysqlParser.Create_table_stmtContext;
import com.antsdb.saltedfish.lexer.MysqlParser.Index_defContext;
import com.antsdb.saltedfish.lexer.MysqlParser.Primary_key_defContext;
import com.antsdb.saltedfish.lexer.MysqlParser.Table_optionContext;
import com.antsdb.saltedfish.sql.DdlGenerator;
import com.antsdb.saltedfish.sql.GeneratorContext;
import com.antsdb.saltedfish.sql.OrcaException;
import com.antsdb.saltedfish.sql.planner.Planner;
import com.antsdb.saltedfish.sql.vdm.CreateColumn;
import com.antsdb.saltedfish.sql.vdm.CreateForeignKey;
import com.antsdb.saltedfish.sql.vdm.CreateIndex;
import com.antsdb.saltedfish.sql.vdm.CreatePrimaryKey;
import com.antsdb.saltedfish.sql.vdm.CreateTable;
import com.antsdb.saltedfish.sql.vdm.CursorMaker;
import com.antsdb.saltedfish.sql.vdm.FieldMeta;
import com.antsdb.saltedfish.sql.vdm.Flow;
import com.antsdb.saltedfish.sql.vdm.IfTableNotExist;
import com.antsdb.saltedfish.sql.vdm.InsertSelectByName;
import com.antsdb.saltedfish.sql.vdm.Instruction;
import com.antsdb.saltedfish.sql.vdm.ObjectName;
import com.antsdb.saltedfish.util.Pair;

public class Create_table_stmtGenerator extends DdlGenerator<Create_table_stmtContext>{

    @Override
    public boolean isTemporaryTable(GeneratorContext ctx, Create_table_stmtContext rule) {
        return rule.K_TEMPORARY() != null;
    }

    @Override
    public Instruction gen(GeneratorContext ctx, Create_table_stmtContext rule) throws OrcaException {
        Flow flow = new Flow();
        ObjectName tableName = TableName.parse(ctx, rule.table_name_());
        CreateTable createTable = new CreateTable(tableName);
        createTable.setTemporary(rule.K_TEMPORARY() != null);
        flow.add(createTable);
        
        // collect table options
        Map<String, String> options = new HashMap<>();
        for (Table_optionContext i:rule.table_options().table_option()) {
            String option = i.table_option_name().getText();
            String value = i.table_option_value().getText();
            options.put(option, value);
            if (option.equalsIgnoreCase("DEFAULTCHARSET")) {
                createTable.setCharset(value);
            }
            else if (option.equalsIgnoreCase("ENGINE")) {
                createTable.setEngine(value);
            }
        }
        
        // count number of unique keys
        int nUniqueKeys = 0;
        if (rule.create_defs() != null) {
            for (Create_defContext i:rule.create_defs().create_def()) {
                if (i.primary_key_def() != null) {
                    nUniqueKeys++;
                }
                else if (i.index_def() != null) {
                    if (i.index_def().K_UNIQUE() != null) {
                        nUniqueKeys++;
                    }
                }
            }
        }
        
        // create columns and constraints
        boolean hasPrimaryKey = false;
        if (rule.create_defs() != null) {
            for (Create_defContext i:rule.create_defs().create_def()) {
                if (i.primary_key_def() != null) {
                    flow.add(createPrimrayKey(ctx, tableName, i.primary_key_def()));
                    hasPrimaryKey = true;
                }
                else if (i.index_def() != null) {
                    boolean isUnique = i.index_def().K_UNIQUE() != null;
                    if (isUnique && !hasPrimaryKey && (nUniqueKeys==1)) {
                        // special treatment, if there is only one unique key, make it as primary key
                        flow.add(createPrimrayKey(ctx, tableName, i.index_def()));
                    }
                    else {
                        flow.add(createIndex(ctx, tableName, i.index_def()));
                    }
                }
                else if (i.column_def() != null) {
                    List<String> keyColumns = new ArrayList<>();
                    CreateColumn cc = createColumn(ctx, i.column_def(), tableName, keyColumns);
                    flow.add(cc);
                    if (keyColumns.size() > 0) {
                        CreatePrimaryKey cpk = new CreatePrimaryKey(tableName, keyColumns);
                        hasPrimaryKey = true;
                        flow.add(cpk);
                    }
                    else if (keyColumns.size() > 1) {
                        throw new OrcaException("Multiple primary key defined");
                    }
                }
                else if (i.constraint_def() != null) {
                    Instruction step = createConstraint(ctx, tableName, i.constraint_def());
                    flow.add(step);
                }
                else {
                    throw new NotImplementedException();
                }
            }
        }
        
        // ctas
        if (rule.select_or_values() != null) {
            genSelect(ctx, flow, tableName, rule);
        }
        
        // finishing up
        if (flow.getInstructions().stream().filter((x)->{return x instanceof CreateColumn;}).count() == 0) {
            throw new OrcaException("A table must have at least 1 column");
        }
        flow.add(new SyncTableSequence(tableName, options));
        if (rule.K_NOT() == null) {
            return flow;
        }
        else {
            return new IfTableNotExist(tableName, flow);
        }
    }

    private void genSelect(GeneratorContext ctx, 
                           Flow flow, 
                           ObjectName tableName, 
                           Create_table_stmtContext rule) {
        Planner planner = Select_or_valuesGenerator.gen(ctx, rule.select_or_values(), null);
        CursorMaker maker = planner.run();
        for (FieldMeta i:maker.getCursorMeta().getColumns()) {
            boolean foundMatch = false;
            for (Instruction j:flow.getInstructions()) {
                if (j instanceof CreateColumn) {
                    CreateColumn jj = (CreateColumn) j;
                    if (jj.getColumnName().equalsIgnoreCase(i.getName())) {
                        foundMatch = true;
                        break;
                    }
                }
            }
            if (!foundMatch) {
                CreateColumn cc = new CreateColumn();
                cc.tableName = tableName;
                cc.columnName = i.getName();
                cc.type = ctx.getOrca().getTypeFactory().findDefaultType(i.getType().getJavaType());
                flow.add(cc);
            }
        }
        flow.add(new InsertSelectByName(tableName, maker));
    }

    private Instruction createConstraint(GeneratorContext ctx, ObjectName tableName, Constraint_defContext rule) {
        ObjectName parentTableName = TableName.parse(ctx, rule.table_name_());
        List<String> childColumns = Utils.getColumns(rule.columns(0));
        List<String> parentColumns = Utils.getColumns(rule.columns(1));
        String name = (rule.identifier() == null) ? null : Utils.getIdentifier(rule.identifier());
        String onDelete = Alter_table_stmtGenerator.getOnAction(true, rule.cascade_option());
        String onUpdate = Alter_table_stmtGenerator.getOnAction(false, rule.cascade_option());
        CreateForeignKey fk;
        fk = new CreateForeignKey(tableName, name, parentTableName, childColumns, parentColumns, onDelete, onUpdate);
        fk.setRebuildIndex(false);
        return fk;
    }

    private CreateColumn createColumn(GeneratorContext ctx, 
                                      Column_defContext rule, 
                                      ObjectName tableName, 
                                      List<String> keyColumns) {
        CreateColumn createColumn = new CreateColumn();
        createColumn.tableName = tableName;
        Utils.updateColumnAttributes(ctx, createColumn, rule, keyColumns);
        return createColumn;
    }

    private Instruction createPrimrayKey(GeneratorContext ctx, ObjectName tableName, Primary_key_defContext rule) {
        List<String> columns = Utils.getColumns(rule.index_columns());
        CreatePrimaryKey step = new CreatePrimaryKey(tableName, columns);
        return step;
    }

    private Instruction createPrimrayKey(GeneratorContext ctx, ObjectName tableName, Index_defContext rule) {
        List<String> columns = Utils.getColumns(rule.index_columns());
        CreatePrimaryKey step = new CreatePrimaryKey(tableName, columns);
        return step;
    }

    private Instruction createIndex(GeneratorContext ctx, ObjectName tableName, Index_defContext rule) {
        boolean isUnique = rule.K_UNIQUE() != null;
        String indexName = null;
        List<Pair<String, Integer>> columns = Utils.getIndexColumns(rule.index_columns());
            if (rule.identifier() != null) {
                indexName = Utils.getIdentifier(rule.identifier());
            }
            else {
                indexName = columns.get(0).x; 
            }
        boolean isFullText = rule.K_FULLTEXT() != null;
        CreateIndex step = new CreateIndex(indexName, isFullText, isUnique, false, tableName, columns);
        step.setRebuild(false);
        return step;
    }

}
