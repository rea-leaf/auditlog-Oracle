package com.mozi.auditlog.interceptor.handler;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleDeleteStatement;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleSelectQueryBlock;
import com.alibaba.druid.sql.parser.SQLStatementParser;
import com.mozi.auditlog.domain.AuditLog;
import com.mozi.auditlog.domain.AuditLogDtl;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class OracleDeleteSqlAuditHandler extends AbstractSQLAuditHandler
{
    private static final Logger logger = LoggerFactory.getLogger(OracleDeleteSqlAuditHandler.class);

    private String querySql;

    private List<AuditLog> auditLogsBeforeDelete;

    private Boolean preHandled = Boolean.FALSE;

    public OracleDeleteSqlAuditHandler(Connection connection, DBMetaDataHolder dbMetaDataHolder, String sql, String monitorTableRegex, String tableColumnPreFix, String nonMonitorTableRegex, CopyOnWriteArrayList<String> monitorTables, CopyOnWriteArrayList<String> nonMonitorTables)
    {
        super(connection, dbMetaDataHolder, sql, monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables);
    }

    @Override
    protected SQLStatement parseSQLStatement(SQLStatementParser statementParser)
    {
        return statementParser.parseDeleteStatement();
    }

    @Override
    protected SQLTableSource getMajorTableSource(SQLStatement statement)
    {
        if (statement instanceof OracleDeleteStatement)
        {
            return ((OracleDeleteStatement) statement).getFrom() != null ? ((OracleDeleteStatement) statement).getFrom() : ((OracleDeleteStatement) statement).getTableSource();
        } else
        {
            return null;
        }
    }

    private List<String> buildTableSourceAliases(SQLTableSource tableSource)
    {
        List<String> tables = new ArrayList<>();
        if (tableSource instanceof SQLExprTableSource)
        {
            SQLExpr expr = ((SQLExprTableSource) tableSource).getExpr();
            if (expr instanceof SQLPropertyExpr)
            {
                tables.add(SQLUtils.toOracleString(((SQLPropertyExpr) expr).getOwner()));
            } else if (expr instanceof SQLIdentifierExpr)
            {
                tables.add(getTableToAliasMap().get(((SQLIdentifierExpr) expr).getName()));
            }
        } else if (tableSource instanceof SQLJoinTableSource)
        {
            tables.addAll(buildTableSourceAliases(((SQLJoinTableSource) tableSource).getLeft()));
            tables.addAll(buildTableSourceAliases(((SQLJoinTableSource) tableSource).getRight()));
        }
        return tables;
    }


    @Override
    public void preHandle()
    {
        if (getSqlStatement() instanceof OracleDeleteStatement)
        {
        	OracleDeleteStatement deleteStatement = (OracleDeleteStatement) getSqlStatement();
            SQLTableSource affectTableSource = deleteStatement.getTableSource() != null ? deleteStatement.getTableSource() : deleteStatement.getFrom();
            List<String> affectAliasList = buildTableSourceAliases(affectTableSource);
            SQLTableSource from = deleteStatement.getFrom() != null ? deleteStatement.getFrom() : deleteStatement.getTableSource();
            SQLExpr where = deleteStatement.getWhere();
            //SQLOrderBy orderBy = deleteStatement.getOrderBy();
            //SQLLimit limit = deleteStatement.getLimit();
            OracleSelectQueryBlock selectQueryBlock = new OracleSelectQueryBlock();
            for (String alias : affectAliasList)
            {
                selectQueryBlock.getSelectList().add(new SQLSelectItem(SQLUtils.toSQLExpr(
                        String.format("%s.%s", alias, getDbMetaDataHolder().getPrimaryKeys().get(getAliasToTableMap().get(alias))))));
                for (String columnName : getDbMetaDataHolder().getTableColumns().get(getAliasToTableMap().get(alias)))
                {
                    selectQueryBlock.getSelectList().add(new SQLSelectItem(SQLUtils.toSQLExpr(
                            String.format("%s.%s", alias, columnName))));
                }
            }
            selectQueryBlock.setFrom(from);
            selectQueryBlock.setWhere(where);
            //selectQueryBlock.setOrderBy(orderBy);
            //selectQueryBlock.setLimit(limit);
            querySql = trimSQLWhitespaces(SQLUtils.toOracleString(selectQueryBlock));
            auditLogsBeforeDelete=getCurrentDataForTables();
            preHandled = Boolean.TRUE;
        }
    }

    @Override
    public void postHandle(Object args)
    {
        if (preHandled)
        {
            saveAuditLog(auditLogsBeforeDelete);
        }
    }


    @SuppressWarnings("unchecked")
    private  List<AuditLog> getCurrentDataForTables()
    {
        Map<String, List<List<AuditLog>>> resultListMap = new CaseInsensitiveMap();
        PreparedStatement statement = null;
        Date now = new Date();
        try
        {
            statement = getConnection().prepareStatement(querySql);
            ResultSet resultSet = statement.executeQuery();
            int columnCount = resultSet.getMetaData().getColumnCount();
            int row = 0;
            while (resultSet.next())
            {
                Map<String, Object> primaryKeyMap = new CaseInsensitiveMap();
                AuditLog auditLog=null;
                for (int i = 1; i < columnCount + 1; i++)
                {
                    //String tableName = resultSet.getMetaData().getTableName(i);
                	String currentTableName=resultSet.getMetaData().getTableName(i);
                    String tableName = (StringUtils.isBlank(currentTableName))?getTables().get(0):currentTableName;
                    String tableUpper = tableName.toUpperCase();

                    if (StringUtils.isNotBlank(tableName))
                    {
                        List<List<AuditLog>> list = resultListMap.get(tableName);
                        if (list == null)
                            list = new ArrayList<>();
                        if (list.size() <= row)
                        {
                            primaryKeyMap.put(tableName, resultSet.getObject(i));
                            auditLog = new AuditLog(AuditLog.OperationEnum.delete.name(),tableUpper, null, (String) primaryKeyMap.get(tableName), now);
                            Map<String, String> tableCommentsByTableName = getTableCommentsByTableName(tableUpper);
                            if (tableCommentsByTableName != null) {
                                auditLog.setTableDescription(tableCommentsByTableName.get(tableUpper));
                            }
                            List<AuditLog> auditDicLogList = new ArrayList<>();
                            auditDicLogList.add(auditLog);
                            list.add(auditDicLogList);
                        } else
                        {
                        	if(null==resultSet.getObject(i)){
                   			    continue;
                   		    }
                            AuditLogDtl auditLogDtl = new AuditLogDtl(auditLog.getAuditLogId(), getDbMetaDataHolder().getTableColumns().get(tableName).get(i - 2), null, null, resultSet.getObject(i));
                            Map<String, String> colComments = getColCommentsByTableNameWithCache(tableUpper);
                            if (colComments != null) {
                                auditLogDtl.setColumnDescription(colComments.get(auditLogDtl.getColumnName()));
                            }
                            auditLog.getAuditLogDtlList().add(auditLogDtl);
                        }
                        resultListMap.put(tableName, list);
                    }
                }
                row++;
            }
            resultSet.close();
        } catch (SQLException e)
        {
            logger.error("Error retrieving table data", e);
        } finally
        {
            if (statement != null)
            {
                try
                {
                    statement.close();
                } catch (SQLException e)
                {
                    logger.error("Error retrieving table data", e);
                }
            }
        }
        if(resultListMap==null||resultListMap.size()==0){
            return null;
    }
        List<AuditLog> allAuditLogs = resultListMap.values().stream()
                .flatMap(List::stream)      // Stream<List<AuditLogVo>>
                .flatMap(List::stream)      // Stream<AuditLogVo>
                .collect(Collectors.toList());
        return allAuditLogs;
    }

}
