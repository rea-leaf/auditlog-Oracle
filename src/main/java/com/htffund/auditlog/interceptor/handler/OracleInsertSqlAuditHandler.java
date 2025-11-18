package com.htffund.auditlog.interceptor.handler;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleInsertStatement;
import com.alibaba.druid.util.StringUtils;
import com.htffund.auditlog.MapUtil;
import com.htffund.auditlog.domain.AuditLog;


public class OracleInsertSqlAuditHandler extends AbstractSQLAuditHandler
{

    private String table;
    private List<String> columnList = new ArrayList<>();
    private Boolean preHandled = Boolean.FALSE;

    public OracleInsertSqlAuditHandler(Connection connection, DBMetaDataHolder dbMetaDataHolder, String insertSQL, String monitorTableRegex, String tableColumnPreFix, String nonMonitorTableRegex, CopyOnWriteArrayList<String> monitorTables, CopyOnWriteArrayList<String> nonMonitorTables)
    {
        super(connection, dbMetaDataHolder, insertSQL, monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables);
    }

    @Override
    protected SQLTableSource getMajorTableSource(SQLStatement statement)
    {
        if (statement instanceof OracleInsertStatement)
            return ((OracleInsertStatement) statement).getTableSource();
        else
            return null;
    }

    @Override
    public void preHandle()
    {
        if (getSqlStatement() instanceof OracleInsertStatement)
        {
        	OracleInsertStatement sqlInsertStatement = (OracleInsertStatement) getSqlStatement();
            if (sqlInsertStatement.getColumns().size() > 0)
            {
                SQLExpr sqlExpr = sqlInsertStatement.getColumns().get(0);
                String[] aliasAndColumn = separateAliasAndColumn(SQLUtils.toOracleString(sqlExpr));
                if (aliasAndColumn[0] != null)
                {
                    table = getAliasToTableMap().get(aliasAndColumn[0]);
                } else if (getTables().size() == 1)
                {
                    table = getTables().get(0);
                } else
                {
                    table = determineTableForColumn(aliasAndColumn[1]);
                }
                if(StringUtils.isEmpty(table)){
                	System.err.println("Error data at table:null at preHandle:skip!!!!!");
                	return;
                }
                for (int i = 0; i < sqlInsertStatement.getColumns().size(); i++)
                {
                    SQLExpr columnExpr = sqlInsertStatement.getColumns().get(i);
                    columnList.add(separateAliasAndColumn(SQLUtils.toOracleString(columnExpr))[1]);
                }
            }
            preHandled = Boolean.TRUE;
        }
    }

    @Override
    public void postHandle(Object args)
    {
        if(StringUtils.isEmpty(table)){
        	System.err.println("Error data at table:null at postHandle:skip!!!!!");
        	return;
        }
        if (preHandled)
        {
            List<List<AuditLog>> auditLogs = new ArrayList<>();
            try
            {   
            	//要求每个表都要有主键
            	 String PrimaryKey=getDbMetaDataHolder().getPrimaryKeys().get(table);
            	 Map currentValueMap=MapUtil.convertDbColumnList(args,columnList,PrimaryKey);
            	 Object primaryValue = currentValueMap.get(PrimaryKey) ;
            	 List<AuditLog> list = new ArrayList<>();
            	 for (String column : columnList)
                 {
            		 if(null==currentValueMap.get(column)){
            			 continue;
            		 }
                     String tableName = table.toUpperCase();
            		 AuditLog auditLog = new AuditLog(tableName, column, null, primaryValue, AuditLog.OperationEnum.insert.name(), null, currentValueMap.get(column));
                     Map<String, String> tableCommentsByTableName = getTableCommentsByTableName(tableName);
                     if (tableCommentsByTableName != null) {
                         auditLog.setTableComments(tableCommentsByTableName.get(tableName));
                     }
                     Map<String, String> colComments = getColCommentsByTableNameWithCache(tableName);
                     if (colComments != null) {
                         auditLog.setColComments(colComments.get(auditLog.getColumnName()));
                     }
                     list.add(auditLog);
                 }
            	 auditLogs.add(list);
            } catch (Exception e)
            {
                e.printStackTrace();
            } 
            saveAuditLog(auditLogs);
        }
    }

}
