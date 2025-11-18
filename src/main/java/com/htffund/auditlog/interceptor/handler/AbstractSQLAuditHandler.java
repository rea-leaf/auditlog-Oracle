package com.htffund.auditlog.interceptor.handler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.parser.SQLStatementParser;
import com.htffund.auditlog.domain.AuditLog;
import com.htffund.auditlog.interceptor.TimestampUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 抽象SQL审计处理器，提供审计日志保存的基础功能
 * 包括生成序列ID、保存审计日志到数据库、判断是否跳过某些表等操作
 */
abstract class AbstractSQLAuditHandler extends AbstractSQLHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSQLAuditHandler.class);
    // Add these cache fields to the class
    private static final Map<String, Map<String, String>> tableCommentsCache = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> columnCommentsCache = new ConcurrentHashMap<>();
    /**
     * 审计日志插入SQL模板
     */
    private static final String AUDIT_LOG_INSERT_SQL = "insert into %s " +
            "(tc_audit_log_id,tc_table_name,tc_table_description, tc_column_name,tc_column_description, tc_primary_key_value, tc_parent_id, tc_new_value, tc_old_value, tc_operation_type, tc_create_time, tc_create_by,tc_create_name,tc_batch_id,tc_ip_address,tc_session_id) " +
            "values(?,?, ?,?,?, ?, ?, ?, ?, ?, sysdate, ?,?,?,?,?)";

    /**
     * 生成审计日志序列ID的SQL
     */
    private static final String GENERATE_SEQUENCE_ID_SQL = "select SEQ_audit_log.Nextval from dual";
    /**
     * 获取表名称注释SQL
     */
    private static final String GET_TABLE_COMMENTS_SQL = "SELECT table_name, comments FROM user_tab_comments WHERE table_name = ? ";
    /**
     * 获取列注释SQL
     */
    private static final String GET_COL_COMMENTS_SQL = "SELECT column_name, comments FROM user_col_comments WHERE table_name = ?";

    /**
     * 默认操作员ID
     */
    private static final Object DEFAULT_CLERK_ID = -1L;

    /**
     * 数据库元数据持有者
     */
    private final DBMetaDataHolder dbMetaDataHolder;

    /**
     * 需要监控的表前缀
     */
    private String monitorTableRegex;

    /**
     * 字段统一前缀_
     */
    private String tableColumnPreFix;

    /**
     * 不需要监控的表前缀
     */
    private String nonMonitorTableRegex;

    /**
     * 需要监控的表
     */
    private CopyOnWriteArrayList<String> monitorTables;
    /**
     * 需要排除的表
     */
    private CopyOnWriteArrayList<String> nonMonitorTables;

    /**
     * 是否跳过当前表的标志
     */
    private boolean isSkipTable;

    /**
     * 构造函数
     *
     * @param connection           数据库连接
     * @param dbMetaDataHolder     数据库元数据持有者
     * @param sql                  SQL语句
     * @param monitorTableRegex
     * @param tableColumnPreFix
     * @param nonMonitorTableRegex
     * @param monitorTables
     * @param nonMonitorTables
     */
    AbstractSQLAuditHandler(Connection connection, DBMetaDataHolder dbMetaDataHolder, String sql, String monitorTableRegex, String tableColumnPreFix, String nonMonitorTableRegex, CopyOnWriteArrayList<String> monitorTables, CopyOnWriteArrayList<String> nonMonitorTables) {
        super(connection, sql);
        this.dbMetaDataHolder = dbMetaDataHolder;
        this.monitorTableRegex = monitorTableRegex;
        this.tableColumnPreFix = tableColumnPreFix;
        this.nonMonitorTableRegex = nonMonitorTableRegex;
        this.monitorTables = monitorTables;
        this.nonMonitorTables = nonMonitorTables;
        this.isSkipTable = false;
        judgeIsSkip();
    }

    /**
     * 判断是否跳过当前表
     *
     * @return 是否跳过当前表
     */
    @Override
    public boolean IsSkipTable() {
        return isSkipTable;
    }

    /**
     * 解析SQL语句
     *
     * @param statementParser SQL语句解析器
     * @return 解析后的SQL语句对象
     */
    @Override
    protected SQLStatement parseSQLStatement(SQLStatementParser statementParser) {
        return statementParser.parseInsert();
    }

    /**
     * 保存审计日志列表
     *
     * @param auditLogList 审计日志列表
     */
    void saveAuditLog(List<List<AuditLog>> auditLogList) {
        // 如果没有需要保存的日志，则直接返回
        if (auditLogList == null || auditLogList.isEmpty()) {
            return;
        }

        boolean originalAutoCommit = true;
        try {
            // 获取并保存当前自动提交设置
            originalAutoCommit = getConnection().getAutoCommit();
            if (originalAutoCommit) {
                // 设置为手动提交以保证事务一致性
                getConnection().setAutoCommit(false);
            }

            // 遍历并保存所有审计日志
            for (List<AuditLog> auditLogs : auditLogList) {
                if (auditLogs != null && !auditLogs.isEmpty()) {
                    // 保存第一条日志并获取父ID
                    Object parentID = saveAuditLog(auditLogs.get(0), null);

                    // 保存剩余的日志记录，使用相同的父ID
                    for (int i = 1; i < auditLogs.size(); i++) {
                        saveAuditLog(auditLogs.get(i), parentID);
                    }
                }
            }

            // 如果原来是自动提交模式，则提交事务并恢复自动提交设置
            if (originalAutoCommit) {
                getConnection().commit();
            }
        } catch (SQLException e) {
            handleSQLException(e, originalAutoCommit);
        } finally {
            // 恢复原始的自动提交设置
            restoreAutoCommit(originalAutoCommit);
        }
    }

    /**
     * 保存来自Map的审计日志
     *
     * @param auditLogListMap 审计日志映射表
     */
    void saveAuditLog(Map<String, List<List<AuditLog>>> auditLogListMap) {
        // 将Map中的所有审计日志合并到一个列表中
        if (auditLogListMap == null || auditLogListMap.isEmpty()) {
            return;
        }

        List<List<AuditLog>> auditLogList = new ArrayList<>();
        for (List<List<AuditLog>> lists : auditLogListMap.values()) {
            if (lists != null) {
                auditLogList.addAll(lists);
            }
        }
        saveAuditLog(auditLogList);
    }

    /**
     * 保存单条审计日志
     *
     * @param auditLog 审计日志对象
     * @param parentID 父日志ID
     * @return 生成的日志ID
     */
    private Object saveAuditLog(AuditLog auditLog, Object parentID) {
        Object resultId = genAuditLogSeqID();
        if (resultId == null) {
            return null;
        }

        String tableName = dbMetaDataHolder.getAuditLogTableCreator().getCurrentValidTableName();

        // 使用 try-with-resources 简化资源管理
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(String.format(AUDIT_LOG_INSERT_SQL, tableName))) {
            int i = 1;
            preparedStatement.setObject(i++, resultId);
            preparedStatement.setObject(i++, auditLog.getTableName());
            preparedStatement.setObject(i++, auditLog.getTableComments());
            preparedStatement.setObject(i++, auditLog.getColumnName());
            preparedStatement.setObject(i++, auditLog.getColComments());
            preparedStatement.setObject(i++, auditLog.getPrimaryKey());
            preparedStatement.setObject(i++, parentID);

            // 处理时间戳类型的值
            preparedStatement.setObject(i++, formatValue(auditLog.getNewValue()));
            preparedStatement.setObject(i++, formatValue(auditLog.getOldValue()));
            preparedStatement.setObject(i++, auditLog.getOperation());

            // 设置操作员ID
            preparedStatement.setObject(i++, MDC.get("userId"));
            preparedStatement.setObject(i++, MDC.get("userName"));
            //批次id
            preparedStatement.setObject(i++, MDC.get("traceId"));

            preparedStatement.setObject(i++, MDC.get("clientIp"));
            //token
            preparedStatement.setObject(i++, MDC.get("token"));


            // 执行插入操作
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            handleSQLException(e);
        }

        return resultId;
    }

    /**
     * 格式化值，特别是处理时间戳类型
     *
     * @param value 原始值
     * @return 格式化后的值
     */
    private Object formatValue(Object value) {
        if (value instanceof Timestamp) {
            return TimestampUtils.timestampToString((Timestamp) value);
        }
        return value;
    }


    /**
     * 生成审计日志序列ID
     *
     * @return 生成的序列ID
     */
    private Object genAuditLogSeqID() {
        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(GENERATE_SEQUENCE_ID_SQL)) {

            if (resultSet.next()) {
                return resultSet.getObject(1);
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }

        return null;
    }

    /**
     * 判断是否需要跳过当前表
     */
    private void judgeIsSkip() {
        String currentDataTable = getCurrentDataTable();
        String tableName = currentDataTable.toUpperCase();
        // 检查是否在监控白名单中（表名或正则）
        boolean isInMonitorList = monitorTables.contains(tableName);
        boolean matchesMonitorRegex = (monitorTableRegex != null && tableName.matches(monitorTableRegex));

        // 检查是否在排除黑名单中（表名或正则）
        boolean isInNonMonitorList = nonMonitorTables.contains(tableName);
        boolean matchesNonMonitorRegex = (nonMonitorTableRegex != null && tableName.matches(nonMonitorTableRegex));

        // 只有在白名单内且不在黑名单内才监控
        isSkipTable = !(isInMonitorList || matchesMonitorRegex) && !(isInNonMonitorList || matchesNonMonitorRegex);
    }

    /**
     * 处理SQL异常
     *
     * @param e SQLException异常
     */
    private void handleSQLException(SQLException e) {
        // 使用日志框架记录异常
        logger.error("SQL execution error", e);
    }

    /**
     * 处理SQL异常并根据需要回滚事务
     *
     * @param e                  SQLException异常
     * @param originalAutoCommit 原始自动提交设置
     */
    private void handleSQLException(SQLException e, boolean originalAutoCommit) {
        // 使用日志框架记录异常
        logger.error("SQL execution error with transaction rollback", e);

        // 如果原来是自动提交模式，则回滚事务
        if (originalAutoCommit) {
            try {
                getConnection().rollback();
            } catch (SQLException rollbackException) {
                logger.error("Failed to rollback transaction", rollbackException);
            }
        }
    }

    /**
     * 恢复原始的自动提交设置
     *
     * @param originalAutoCommit 原始自动提交设置
     */
    private void restoreAutoCommit(boolean originalAutoCommit) {
        try {
            if (getConnection().getAutoCommit() != originalAutoCommit) {
                getConnection().setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }
    }

    /**
     * 获取数据库元数据持有者
     *
     * @return 数据库元数据持有者
     */
    DBMetaDataHolder getDbMetaDataHolder() {
        return dbMetaDataHolder;
    }


    /**
     * 获取是否跳过当前表的标志
     *
     * @return 是否跳过当前表
     */
    public boolean getIsSkipTable() {
        return isSkipTable;
    }

    /**
     * 根据表名获取表注释信息
     *
     * @param tableName 表名
     * @return 表注释映射
     */
    public Map<String, String> getTableCommentsByTableName(String tableName) {
        Map<String, String> tableComments = new HashMap<>();
        try (PreparedStatement statement = getConnection().prepareStatement(GET_TABLE_COMMENTS_SQL)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    tableComments.put(resultSet.getString("TABLE_NAME"), resultSet.getString("COMMENTS"));
                }
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }

        return tableComments;
    }

    /**
     * 根据表名获取所有列注释信息
     *
     * @param tableName 表名
     * @return 列注释映射 (column_name -> comments)
     */
    public Map<String, String> getColCommentsByTableName(String tableName) {
        Map<String, String> colComments = new HashMap<>();
        try (PreparedStatement statement = getConnection().prepareStatement(GET_COL_COMMENTS_SQL)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    colComments.put(resultSet.getString("COLUMN_NAME"), resultSet.getString("COMMENTS"));
                }
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }

        return colComments;
    }


    /**
     * 根据表名获取表注释信息(带缓存)
     *
     * @param tableName 表名
     * @return 表注释映射
     */
    public Map<String, String> getTableCommentsByTableNameWithCache(String tableName) {
        // Check cache first
        Map<String, String> cachedResult = tableCommentsCache.get(tableName);
        if (cachedResult != null) {
            return cachedResult;
        }

        // Cache miss - query from database
        Map<String, String> tableComments = new HashMap<>();
        String sql = "SELECT table_name, comments FROM user_tab_comments WHERE table_name = ?";

        try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    tableComments.put(resultSet.getString("TABLE_NAME"), resultSet.getString("COMMENTS"));
                }
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }

        // Cache the result
        tableCommentsCache.put(tableName, tableComments);
        return tableComments;
    }

    /**
     * 根据表名获取所有列注释信息(带缓存)
     *
     * @param tableName 表名
     * @return 列注释映射 (column_name -> comments)
     */
    public Map<String, String> getColCommentsByTableNameWithCache(String tableName) {
        // Check cache first
        Map<String, String> cachedResult = columnCommentsCache.get(tableName);
        if (cachedResult != null) {
            return cachedResult;
        }

        // Cache miss - query from database
        Map<String, String> colComments = new HashMap<>();
        String sql = "SELECT column_name, comments FROM user_col_comments WHERE table_name = ?";

        try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    colComments.put(resultSet.getString("COLUMN_NAME"), resultSet.getString("COMMENTS"));
                }
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }
        // Cache the result
        columnCommentsCache.put(tableName, colComments);
        return colComments;
    }
}