package com.htffund.auditlog.interceptor.handler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.lang.StringUtils;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLInListExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUpdateSetItem;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleSelectQueryBlock;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleUpdateStatement;
import com.alibaba.druid.sql.parser.SQLStatementParser;
import com.htffund.auditlog.domain.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for auditing Oracle UPDATE SQL statements.
 * This class is responsible for capturing data before and after UPDATE operations
 * and generating audit logs that track the changes made to database records.
 */
public class OracleUpdateSqlAuditHandler extends AbstractSQLAuditHandler {
    private static final Logger logger = LoggerFactory.getLogger(OracleUpdateSqlAuditHandler.class);

    private final Map<String, List<String>> updateColumnListMap = new CaseInsensitiveMap();

    private final Map<String, Map<Object, Object[]>> rowsBeforeUpdateListMap = new CaseInsensitiveMap();

    private boolean preHandled = false;

    /**
     * Constructor for OracleUpdateSqlAuditHandler.
     *
     * @param connection             the database connection
     * @param dbMetaDataHolder       the database metadata holder
     * @param updateSQL              the UPDATE SQL statement
     * @param monitorTableRegex      the regex pattern for monitoring tables
     * @param tableColumnPreFix      the prefix for table columns
     * @param nonMonitorTableRegex   the regex pattern for non-monitoring tables
     * @param monitorTables          the list of tables to monitor
     * @param nonMonitorTables       the list of tables not to monitor
     */
    public OracleUpdateSqlAuditHandler(Connection connection, DBMetaDataHolder dbMetaDataHolder, String updateSQL, String monitorTableRegex, String tableColumnPreFix, String nonMonitorTableRegex, CopyOnWriteArrayList<String> monitorTables, CopyOnWriteArrayList<String> nonMonitorTables) {
        super(connection, dbMetaDataHolder, updateSQL, monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables);
    }

    /**
     * Get the major table source from the SQL statement.
     *
     * @param statement the SQL statement
     * @return the table source if it's an Oracle UPDATE statement, otherwise null
     */
    @Override
    protected SQLTableSource getMajorTableSource(SQLStatement statement) {
        if (statement instanceof OracleUpdateStatement)
            return ((OracleUpdateStatement) statement).getTableSource();
        else
            return null;
    }

    /**
     * Parse the SQL update statement.
     *
     * @param statementParser the SQL statement parser
     * @return the parsed SQL update statement
     */
    @Override
    protected SQLStatement parseSQLStatement(SQLStatementParser statementParser) {
        return statementParser.parseUpdateStatement();
    }

    /**
     * Pre-handle the UPDATE SQL statement to extract table and column information.
     * Also retrieves the data before the update operation for audit comparison.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void preHandle() {
        if (getSqlStatement() instanceof OracleUpdateStatement) {
            OracleUpdateStatement updateStatement = (OracleUpdateStatement) getSqlStatement();
            SQLTableSource tableSource = updateStatement.getTableSource();
            List<SQLUpdateSetItem> updateSetItems = updateStatement.getItems();
            SQLExpr where = updateStatement.getWhere();
            
            // Extract table and column information from update set items
            for (SQLUpdateSetItem sqlUpdateSetItem : updateSetItems) {
                String[] aliasAndColumn = separateAliasAndColumn(SQLUtils.toOracleString(sqlUpdateSetItem.getColumn()));
                String alias = aliasAndColumn[0];
                String column = aliasAndColumn[1];
                String tableName = null;
                if (StringUtils.isNotBlank(alias)) {
                    tableName = getAliasToTableMap().get(alias);
                } else if (getTables().size() == 1) {
                    tableName = getTables().get(0);
                } else {
                    tableName = determineTableForColumn(column);
                }
                if (StringUtils.isNotBlank(tableName)) {
                    List<String> columnList = updateColumnListMap.computeIfAbsent(tableName, k -> new ArrayList<>());
                    columnList.add(column);
                }
            }

            // Query database values before update
            OracleSelectQueryBlock selectQueryBlock = new OracleSelectQueryBlock();
            selectQueryBlock.setFrom(tableSource);
            selectQueryBlock.setWhere(where);
            for (Map.Entry<String, List<String>> updateInfoListEntry : updateColumnListMap.entrySet()) {
                // TODO: bug - PrimaryKeys is wrong!
                selectQueryBlock.getSelectList().add(new SQLSelectItem(SQLUtils.toSQLExpr(
                        String.format("%s.%s", getTableToAliasMap().get(updateInfoListEntry.getKey()),
                                getDbMetaDataHolder().getPrimaryKeys().get(updateInfoListEntry.getKey())))));
                for (String column : updateInfoListEntry.getValue()) {
                    selectQueryBlock.getSelectList().add(new SQLSelectItem(SQLUtils.toSQLExpr(
                            String.format("%s.%s", getTableToAliasMap().get(updateInfoListEntry.getKey()), column))));
                }
            }
            rowsBeforeUpdateListMap.putAll(getTablesData(trimSQLWhitespaces(SQLUtils.toOracleString(selectQueryBlock)), updateColumnListMap));
            preHandled = true;
        }
    }

    /**
     * Post-handle the UPDATE operation to generate audit logs.
     * Compares data before and after the update to create detailed audit records.
     *
     * @param args the parameters of the UPDATE operation
     */
    @SuppressWarnings("unchecked")
    @Override
    public void postHandle(Object args) {
        if (preHandled) {
            Map<String, List<List<AuditLog>>> auditLogListMap = new CaseInsensitiveMap();
            if (rowsBeforeUpdateListMap != null) {
                Map<String, Map<Object, Object[]>> rowsAfterUpdateListMap = getTablesDataAfterUpdate();
                for (String tableName : rowsBeforeUpdateListMap.keySet()) {
                    Map<Object, Object[]> rowsBeforeUpdateRowsMap = rowsBeforeUpdateListMap.get(tableName);
                    Map<Object, Object[]> rowsAfterUpdateRowsMap = rowsAfterUpdateListMap.get(tableName);
                    if (rowsBeforeUpdateRowsMap != null && rowsAfterUpdateRowsMap != null) {
                        List<List<AuditLog>> rowList = auditLogListMap.computeIfAbsent(tableName, k -> new ArrayList<>());
                        for (Object pKey : rowsBeforeUpdateRowsMap.keySet()) {
                            Object[] rowBeforeUpdate = rowsBeforeUpdateRowsMap.get(pKey);
                            Object[] rowAfterUpdate = rowsAfterUpdateRowsMap.get(pKey);
                            List<AuditLog> colList = new ArrayList<>();
                            for (int col = 0; col < rowBeforeUpdate.length; col++) {
                                if (rowBeforeUpdate[col] != null
                                        || (rowBeforeUpdate[col] == null && rowAfterUpdate[col] != null)) {
                                    String tableUpper = tableName.toUpperCase();
                                    AuditLog auditLog = new AuditLog(tableUpper, updateColumnListMap.get(tableName).get(col), null,
                                            pKey, AuditLog.OperationEnum.update.name(), rowBeforeUpdate[col], rowAfterUpdate[col]);
                                    Map<String, String> tableCommentsByTableName = getTableCommentsByTableName(tableUpper);
                                    if (tableCommentsByTableName != null) {
                                        auditLog.setTableComments(tableCommentsByTableName.get(tableUpper));
                                    }
                                    Map<String, String> colComments = getColCommentsByTableNameWithCache(tableUpper);
                                    if (colComments != null) {
                                        auditLog.setColComments(colComments.get(auditLog.getColumnName()));
                                    }
                                    colList.add(auditLog);
                                }
                            }
                            if (!colList.isEmpty())
                                rowList.add(colList);
                        }
                    }
                }
            }
            saveAuditLog(auditLogListMap);
        }
    }

    /**
     * Retrieve table data after the update operation.
     *
     * @return a map containing the updated table data
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<Object, Object[]>> getTablesDataAfterUpdate() {
        Map<String, Map<Object, Object[]>> resultListMap = new CaseInsensitiveMap();
        for (Map.Entry<String, Map<Object, Object[]>> tableDataEntry : rowsBeforeUpdateListMap.entrySet()) {
            String tableName = tableDataEntry.getKey();
            OracleSelectQueryBlock selectQueryBlock = new OracleSelectQueryBlock();
            selectQueryBlock.getSelectList().add(new SQLSelectItem(SQLUtils.toSQLExpr(getDbMetaDataHolder().getPrimaryKeys().get(tableName))));
            for (String column : updateColumnListMap.get(tableName)) {
                selectQueryBlock.getSelectList().add(new SQLSelectItem(SQLUtils.toSQLExpr(column)));
            }
            selectQueryBlock.setFrom(new SQLExprTableSource(new SQLIdentifierExpr(tableName)));
            SQLInListExpr sqlInListExpr = new SQLInListExpr();
            List<SQLExpr> sqlExprList = new ArrayList<>();
            for (Object primaryKey : tableDataEntry.getValue().keySet()) {
                sqlExprList.add(SQLUtils.toSQLExpr("'" + primaryKey.toString() + "'"));
            }
            sqlInListExpr.setExpr(new SQLIdentifierExpr(getDbMetaDataHolder().getPrimaryKeys().get(tableName)));
            sqlInListExpr.setTargetList(sqlExprList);
            selectQueryBlock.setWhere(sqlInListExpr);
            Map<String, List<String>> tableColumnMap = new CaseInsensitiveMap();
            tableColumnMap.put(tableName, updateColumnListMap.get(tableName));
            Map<String, Map<Object, Object[]>> map = getTablesData(trimSQLWhitespaces(SQLUtils.toOracleString(selectQueryBlock)), tableColumnMap);
            resultListMap.putAll(map);
        }
        return resultListMap;
    }

    /**
     * Retrieve table data using the provided SQL query.
     *
     * @param querySQL        the SQL query to execute
     * @param tableColumnsMap map of table names to their columns
     * @return a map containing the retrieved table data
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<Object, Object[]>> getTablesData(String querySQL, Map<String, List<String>> tableColumnsMap) {
        Map<String, Map<Object, Object[]>> resultListMap = new HashMap<>();
        PreparedStatement statement = null;
        try {
            statement = getConnection().prepareStatement(querySQL);
            ResultSet resultSet = statement.executeQuery();
            int columnCount = resultSet.getMetaData().getColumnCount();

            while (resultSet.next()) {
                Map<String, Object> currRowTablePKeyMap = new HashMap<>();
                for (int i = 1; i < columnCount + 1; i++) {
                    String tableName = resultSet.getMetaData().getTableName(i);
                    String currentTableName = (StringUtils.isBlank(tableName)) ? getTables().get(0) : tableName;

                    if (StringUtils.isNotBlank(currentTableName)) {
                        // Store primary key for the table
                        if (currRowTablePKeyMap.get(currentTableName) == null) {
                            currRowTablePKeyMap.put(currentTableName, resultSet.getObject(i));
                        } else {
                            // Store column data for the table
                            Map<Object, Object[]> rowsMap = resultListMap.get(currentTableName);
                            if (rowsMap == null) rowsMap = new HashMap<>();
                            Object[] rowData = rowsMap.get(currRowTablePKeyMap.get(currentTableName));
                            if (rowData == null) rowData = new Object[]{};
                            if (rowData.length < tableColumnsMap.get(currentTableName).size()) {
                                rowData = Arrays.copyOf(rowData, rowData.length + 1);
                                rowData[rowData.length - 1] = resultSet.getObject(i);
                            }
                            rowsMap.put(currRowTablePKeyMap.get(currentTableName), rowData);
                            resultListMap.put(currentTableName, rowsMap);
                        }
                    }
                }
            }
            resultSet.close();
        } catch (SQLException e) {
            logger.error("Error retrieving table data", e);
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    logger.error("Error closing statement", e);
                }
            }
        }
        return resultListMap;
    }

}