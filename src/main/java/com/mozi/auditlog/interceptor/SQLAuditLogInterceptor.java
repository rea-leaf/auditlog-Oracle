package com.mozi.auditlog.interceptor;

import java.sql.Connection;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;

import com.mozi.auditlog.domain.AuditLog;
import com.mozi.auditlog.interceptor.handler.AuditLogTableCreator;
import com.mozi.auditlog.interceptor.handler.DBMetaDataHolder;
import com.mozi.auditlog.interceptor.handler.ISQLHandler;
import com.mozi.auditlog.interceptor.handler.OracleDeleteSqlAuditHandler;
import com.mozi.auditlog.interceptor.handler.OracleInsertSqlAuditHandler;
import com.mozi.auditlog.interceptor.handler.OracleUpdateSqlAuditHandler;

@Intercepts(
        {
                @Signature(type = Executor.class, method = "update", args = {
                        MappedStatement.class, Object.class
                })
        })
public class SQLAuditLogInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(SQLAuditLogInterceptor.class);

    private static final Pattern PARAMETER_PLACEHOLDER_PATTERN = Pattern.compile("\\?(?=\\s*[^']*\\s*,?\\s*(\\w|$))");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s]+");
    private static final String EXCLUDE_TABLE_SEPARATOR = ",";
    private final static String TABLE_COLUMN_PRE_FIX = "columnPreFix";
    private final static String NON_MONITOR_TABLE_REGEX = "nonMonitorTableRegex";
    private final static String NON_MONITOR_TABLES = "nonMonitorTables";
    private final static String MONITOR_TABLE_REGEX = "monitorTableRegex";
    private final static String MONITOR_TABLES = "monitorTables";
    private Boolean auditEnable;
    private DBMetaDataHolder dbMetaDataHolder;
    /**
     * 需要监控的表前缀
     */
    private static String monitorTableRegex;

    /**
     * 字段统一前缀_
     */
    private static String tableColumnPreFix;

    /**
     * 不需要监控的表前缀
     */
    private static String nonMonitorTableRegex;


    CopyOnWriteArrayList<String> monitorTables = new CopyOnWriteArrayList<String>();
    CopyOnWriteArrayList<String> nonMonitorTables = new CopyOnWriteArrayList<String>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 检查审计功能是否启用以及参数是否正确
        if (!isAuditEnabled(invocation)) {
            return invocation.proceed();
        }

        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        String sqlCommandType = mappedStatement.getSqlCommandType().name();

        // 只处理插入、更新、删除操作
        if (!isSupportedOperation(sqlCommandType)) {
            return invocation.proceed();
        }

        ISQLHandler sqlAuditHandler = null;
        try {
            // 初始化处理器
            sqlAuditHandler = initializeSQLHandler(invocation, mappedStatement, sqlCommandType);
            
            // 如果需要跳过处理，则直接执行原方法
            if (shouldSkipProcessing(sqlAuditHandler)) {
                logDebugInfo(sqlAuditHandler, mappedStatement);
                return invocation.proceed();
            }
            
            // 预处理阶段
            if (sqlAuditHandler != null) {
                sqlAuditHandler.preHandle();
            }
        } catch (Throwable ex) {
            log.error("记录修改日志异常", ex);
        }

        // 执行原始方法并获取结果
        Object result = invocation.proceed();
        Object resultDataArgs = extractResultDataArgs(invocation);

        // 后处理阶段
        try {
            if (sqlAuditHandler != null) {
                sqlAuditHandler.postHandle(resultDataArgs);
            }
        } catch (Throwable ex) {
            log.error("记录修改日志异常", ex);
        }
        
        return result;
    }

    /**
     * 检查审计功能是否启用以及参数是否正确
     * 
     * @param invocation 调用信息
     * @return 是否启用审计
     */
    private boolean isAuditEnabled(Invocation invocation) {
        return auditEnable != null 
                && auditEnable 
                && invocation.getArgs()[0] instanceof MappedStatement;
    }

    /**
     * 检查操作类型是否支持
     * 
     * @param sqlCommandType SQL命令类型
     * @return 是否支持的操作
     */
    private boolean isSupportedOperation(String sqlCommandType) {
        return AuditLog.OperationEnum.insert.name().equalsIgnoreCase(sqlCommandType)
                || AuditLog.OperationEnum.update.name().equalsIgnoreCase(sqlCommandType)
                || AuditLog.OperationEnum.delete.name().equalsIgnoreCase(sqlCommandType);
    }

    /**
     * 初始化SQL处理器
     * 
     * @param invocation 调用信息
     * @param mappedStatement 映射语句
     * @param sqlCommandType SQL命令类型
     * @return SQL处理器
     * @throws Throwable 异常信息
     */
    private ISQLHandler initializeSQLHandler(Invocation invocation, MappedStatement mappedStatement, String sqlCommandType) throws Throwable {
        Executor executor = (Executor) invocation.getTarget();
        Connection connection = executor.getTransaction().getConnection();
        dbMetaDataHolder.init(connection);
        
        Object parameter = extractParameter(invocation);
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        Configuration configuration = mappedStatement.getConfiguration();
        String sql = getParameterizedSql(configuration, boundSql);
        
        // 检查是否为不支持的操作
        if (isUnsupportedOperation(sql)) {
            log.warn("审计日志不支持的操作: {}", sql);
            return null;
        }
        
        // 检查SQL命令类型是否匹配
        if (!isSqlCommandTypeMatch(sql, sqlCommandType)) {
            log.warn("SQL命令类型与SQL不匹配，请检查: {}", sql);
            return null;
        }
        
        // 创建对应的处理器
        return createSQLHandler(connection, sql, sqlCommandType);
    }

    /**
     * 检查是否为不支持的操作
     * 
     * @param sql SQL语句
     * @return 是否为不支持的操作
     */
    private boolean isUnsupportedOperation(String sql) {
        return sql.toLowerCase().startsWith("merge");
    }

    /**
     * 检查SQL命令类型是否匹配
     * 
     * @param sql SQL语句
     * @param sqlCommandType SQL命令类型
     * @return 是否匹配
     */
    private boolean isSqlCommandTypeMatch(String sql, String sqlCommandType) {
        return sql.toLowerCase().startsWith(sqlCommandType.toLowerCase());
    }

    /**
     * 创建对应的SQL处理器
     * 
     * @param connection 数据库连接
     * @param sql SQL语句
     * @param sqlCommandType SQL命令类型
     * @return SQL处理器
     */
    private ISQLHandler createSQLHandler(Connection connection, String sql, String sqlCommandType) {
        if (AuditLog.OperationEnum.insert.name().equalsIgnoreCase(sqlCommandType)) {
            return new OracleInsertSqlAuditHandler(connection, dbMetaDataHolder, sql,monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables);
        } else if (AuditLog.OperationEnum.update.name().equalsIgnoreCase(sqlCommandType)) {
            return new OracleUpdateSqlAuditHandler(connection, dbMetaDataHolder, sql, monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables);
        } else if (AuditLog.OperationEnum.delete.name().equalsIgnoreCase(sqlCommandType)) {
            return new OracleDeleteSqlAuditHandler(connection, dbMetaDataHolder, sql, monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables);
        }
        return null;
    }

    /**
     * 提取调用参数
     * 
     * @param invocation 调用信息
     * @return 参数对象
     */
    private Object extractParameter(Invocation invocation) {
        if (invocation.getArgs().length > 1) {
            return invocation.getArgs()[1];
        }
        return null;
    }

    /**
     * 提取结果数据参数
     * 
     * @param invocation 调用信息
     * @return 结果数据参数
     */
    private Object extractResultDataArgs(Invocation invocation) {
        if (invocation != null && invocation.getArgs().length > 1) {
            return invocation.getArgs()[1];
        }
        return null;
    }

    /**
     * 检查是否应该跳过处理
     * 
     * @param sqlAuditHandler SQL处理器
     * @return 是否应该跳过处理
     */
    private boolean shouldSkipProcessing(ISQLHandler sqlAuditHandler) {
        return sqlAuditHandler != null && sqlAuditHandler.IsSkipTable();
    }

    /**
     * 记录调试信息
     * 
     * @param sqlAuditHandler SQL处理器
     * @param mappedStatement 映射语句
     */
    private void logDebugInfo(ISQLHandler sqlAuditHandler, MappedStatement mappedStatement) {
        if (log.isDebugEnabled()) {
            log.debug("跳过处理的SQL: {}", mappedStatement.getBoundSql(extractParameter(null)).getSql());
        }
    }

    /**
     * 获取带参数值的SQL语句
     * 
     * @param configuration 配置信息
     * @param boundSql 绑定SQL
     * @return 带参数值的SQL语句
     */
    private String getParameterizedSql(Configuration configuration, BoundSql boundSql) {
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        String sql = WHITESPACE_PATTERN.matcher(boundSql.getSql()).replaceAll(" ");
        
        if (CollectionUtils.isNotEmpty(parameterMappings) && parameterObject != null) {
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                sql = PARAMETER_PLACEHOLDER_PATTERN.matcher(sql).replaceFirst(
                        Matcher.quoteReplacement(getParameterValue(parameterObject)));
            } else {
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                for (ParameterMapping parameterMapping : parameterMappings) {
                    String propertyName = parameterMapping.getProperty();
                    if (metaObject.hasGetter(propertyName)) {
                        Object obj = metaObject.getValue(propertyName);
                        sql = PARAMETER_PLACEHOLDER_PATTERN.matcher(sql).replaceFirst(
                                Matcher.quoteReplacement(getParameterValue(obj)));
                    } else if (boundSql.hasAdditionalParameter(propertyName)) {
                        Object obj = boundSql.getAdditionalParameter(propertyName);
                        sql = PARAMETER_PLACEHOLDER_PATTERN.matcher(sql).replaceFirst(
                                Matcher.quoteReplacement(getParameterValue(obj)));
                    } else {
                        sql = PARAMETER_PLACEHOLDER_PATTERN.matcher(sql).replaceFirst("缺失");
                    }
                }
            }
        }
        return sql;
    }

    /**
     * 获取参数值字符串表示
     * 
     * @param obj 参数对象
     * @return 参数值字符串
     */
    private static String getParameterValue(Object obj) {
        String value;
        if (obj instanceof String) {
            value = "'" + obj.toString() + "'";
        } else if (obj instanceof Date) {
            DateFormat formatter = DateFormat.getDateTimeInstance(
                    DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.CHINA);
            value = "'" + formatter.format(new Date()) + "'";
        } else {
            if (obj != null) {
                value = obj.toString();
            } else {
                value = "null";
            }
        }
        return value;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        Boolean splitEnableOption = Boolean.valueOf(properties.getProperty("split", Boolean.FALSE.toString()));
        String defaultTableNameOption = String.valueOf(properties.getProperty("defaultTableName", "TB_AUDIT_LOG"));
        String preTableNameOption = String.valueOf(properties.getProperty("preTableName", "TB_AUDIT_LOG_"));
        String table_column_pre_fix = String.valueOf(properties.getProperty(TABLE_COLUMN_PRE_FIX, ""));
        String non_monitor_table_regex = String.valueOf(properties.getProperty(NON_MONITOR_TABLE_REGEX, ""));
        String non_monitor_tables = String.valueOf(properties.getProperty(NON_MONITOR_TABLES, ""));
        String monitor_table_regex = String.valueOf(properties.getProperty(MONITOR_TABLE_REGEX, ""));
        String monitor_tables = String.valueOf(properties.getProperty(MONITOR_TABLES, ""));
        
        if (StringUtils.isNotBlank(table_column_pre_fix)) {
            tableColumnPreFix=table_column_pre_fix;
        }
        if (StringUtils.isNotBlank(monitor_table_regex)) {
            monitorTableRegex=monitor_table_regex;
        }
        if (StringUtils.isNotBlank(non_monitor_table_regex)) {
            nonMonitorTableRegex=non_monitor_table_regex;
        }
        if (StringUtils.isNotBlank(non_monitor_tables)) {
            nonMonitorTableRegex=non_monitor_tables;
        }
        if (StringUtils.isNotBlank(monitor_tables)) {
            String[] monitor_tablesArray = monitor_tables.split(",");
            monitorTables = new CopyOnWriteArrayList<String>(monitor_tablesArray);
        }
        if (StringUtils.isNotBlank(non_monitor_tables)) {
            String[] non_monitor_tablesArray = non_monitor_tables.split(",");
            nonMonitorTables = new CopyOnWriteArrayList<String>(non_monitor_tablesArray);
        }
        
        auditEnable = Boolean.valueOf(properties.getProperty("enable", Boolean.FALSE.toString()));
        dbMetaDataHolder = new DBMetaDataHolder(new AuditLogTableCreator(splitEnableOption, defaultTableNameOption, preTableNameOption, monitorTableRegex, tableColumnPreFix, nonMonitorTableRegex, monitorTables, nonMonitorTables));
    }
}