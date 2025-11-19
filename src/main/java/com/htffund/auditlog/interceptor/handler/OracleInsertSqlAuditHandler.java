package com.htffund.auditlog.interceptor.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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


public class OracleInsertSqlAuditHandler extends AbstractSQLAuditHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(OracleInsertSqlAuditHandler.class);

    private final List<String> columnList = new ArrayList<>();
    private String table;
    private boolean preHandled = false;

    /**
     * Constructor for OracleInsertSqlAuditHandler.
     *
     * @param connection             the database connection
     * @param dbMetaDataHolder       the database metadata holder
     * @param insertSQL              the INSERT SQL statement
     * @param monitorTableRegex      the regex pattern for monitoring tables
     * @param tableColumnPreFix      the prefix for table columns
     * @param nonMonitorTableRegex   the regex pattern for non-monitoring tables
     * @param monitorTables          the list of tables to monitor
     * @param nonMonitorTables       the list of tables not to monitor
     */
    public OracleInsertSqlAuditHandler(Connection connection, DBMetaDataHolder dbMetaDataHolder, String insertSQL, String monitorTableRegex, String tableColumnPreFix, String nonMonitorTableRegex, CopyOnWriteArrayList<String> monitorTables, CopyOnWriteArrayList<String> nonMonitorTables) {
        super(connection, dbMetaDataHolder, insertSQL, monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables);
    }

    /**
     * Get the major table source from the SQL statement.
     *
     * @param statement the SQL statement
     * @return the table source if it's an Oracle INSERT statement, otherwise null
     */
    @Override
    protected SQLTableSource getMajorTableSource(SQLStatement statement) {
        if (statement instanceof OracleInsertStatement)
            return ((OracleInsertStatement) statement).getTableSource();
        else
            return null;
    }

    /**
     * Pre-handle the INSERT SQL statement to extract table and column information.
     */
    @Override
    public void preHandle() {
        if (getSqlStatement() instanceof OracleInsertStatement) {
            OracleInsertStatement sqlInsertStatement = (OracleInsertStatement) getSqlStatement();
            if (sqlInsertStatement.getColumns().size() > 0) {
                SQLExpr sqlExpr = sqlInsertStatement.getColumns().get(0);
                String[] aliasAndColumn = separateAliasAndColumn(SQLUtils.toOracleString(sqlExpr));
                if (aliasAndColumn[0] != null) {
                    table = getAliasToTableMap().get(aliasAndColumn[0]);
                } else if (getTables().size() == 1) {
                    table = getTables().get(0);
                } else {
                    table = determineTableForColumn(aliasAndColumn[1]);
                }
                if (StringUtils.isEmpty(table)) {
                    logger.error("Error data at table:null at preHandle:skip!!!!!");
                    return;
                }
                for (SQLExpr columnExpr : sqlInsertStatement.getColumns()) {
                    columnList.add(separateAliasAndColumn(SQLUtils.toOracleString(columnExpr))[1]);
                }
            }
            preHandled = true;
        }
    }

    /**
     * Post-handle the INSERT operation to generate audit logs.
     *
     * @param args the parameters of the INSERT operation
     */
    @Override
    public void postHandle(Object args) {
        if (StringUtils.isEmpty(table)) {
            logger.error("Error data at table:null at postHandle:skip!!!!!");
            return;
        }
        if (preHandled) {
            List<List<AuditLog>> auditLogs = new ArrayList<>();
            try {
                //要求每个表都要有主键
                String primaryKey = getDbMetaDataHolder().getPrimaryKeys().get(table);
                // 检查参数是否是Map包装的List
                Object paramToProcess = args;
                if (args instanceof Map) {
                    Map<?, ?> paramMap = (Map<?, ?>) args;
                    // 尝试获取常见的List键名
                    if (paramMap.containsKey("list")) {
                        if (paramMap.get("list") instanceof List)
                        {
                            List<?> parameterList = (List<?>) paramMap.get("list");
                            for (Object parameter : parameterList) {
                                processInsertParameter(parameter,primaryKey, auditLogs);
                            }
                        }
                    }
                    // 可根据实际情况检查其他
                }else if (paramToProcess instanceof List) {
                    List<?> parameterList = (List<?>) paramToProcess;
                    for (Object parameter : parameterList) {
                        processInsertParameter(parameter,primaryKey, auditLogs);
                    }
                }else{
                    processInsertParameter(args, primaryKey,auditLogs);
                }

            } catch (Exception e) {
                logger.error("Error processing insert audit log", e);
            }
            saveAuditLog(auditLogs);
        }
    }


    /**
     * Process a single INSERT parameter to generate audit logs.
     *
     * @param args       the parameter object
     * @param primaryKey the primary key of the table
     * @param auditLogs  the list to store generated audit logs
     * @throws Exception if there is an error processing the parameter
     */
    private void processInsertParameter(Object args, String primaryKey, List<List<AuditLog>> auditLogs) throws Exception {
        Map<String, Object> currentValueMap = MapUtil.convertDbColumnList(args, columnList, primaryKey);
        Object primaryValue = currentValueMap.get(primaryKey);
        List<AuditLog> list = new ArrayList<>();
        String tableName = table.toUpperCase();
        
        // Cache comments outside the loop for better performance
        Map<String, String> tableCommentsByTableName = getTableCommentsByTableName(tableName);
        Map<String, String> colComments = getColCommentsByTableNameWithCache(tableName);
        
        for (String column : columnList) {
            Object columnValue = currentValueMap.get(column);
            if (null == columnValue) {
                continue;
            }
            AuditLog auditLog = new AuditLog(tableName, column, null, primaryValue, AuditLog.OperationEnum.insert.name(), null, columnValue);
            if (tableCommentsByTableName != null) {
                auditLog.setTableComments(tableCommentsByTableName.get(tableName));
            }
            if (colComments != null) {
                auditLog.setColComments(colComments.get(auditLog.getColumnName()));
            }
            list.add(auditLog);
        }
        auditLogs.add(list);
    }
}
