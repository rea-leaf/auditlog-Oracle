package com.mozi.auditlog.interceptor.handler;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditLogTableCreator
{

    private String defaultTableName;

    private String preTableName;

    private String currentValidTableName;

    private Boolean splitEnable;

    /**
     * 需要监控的表前缀
     */
    private  String monitorTableRegex;

    /**
     * 字段统一前缀_
     */
    private  String tableColumnPreFix;

    /**
     * 不需要监控的表前缀
     */
    private  String nonMonitorTableRegex;


    private  CopyOnWriteArrayList<String> monitorTables;
    private  CopyOnWriteArrayList<String> nonMonitorTables;

    public AuditLogTableCreator(Boolean splitEnable, String defaultTableName, String preTableName, String monitorTableRegex, String tableColumnPreFix, String nonMonitorTableRegex, CopyOnWriteArrayList<String> monitorTables,CopyOnWriteArrayList<String> nonMonitorTables)
    {

        this.splitEnable = splitEnable;
        this.defaultTableName = defaultTableName;
        this.preTableName = preTableName;
        this.monitorTableRegex = preTableName;
        this.tableColumnPreFix = preTableName;
        this.nonMonitorTableRegex = preTableName;
        this.monitorTables = monitorTables;
        this.nonMonitorTables = nonMonitorTables;
        currentValidTableName = getCurrentTableName();
    }

    String getCurrentTableName()
    {
        if (splitEnable)
        {
            Calendar calendar = Calendar.getInstance();
            String calendarMonth;
            if (calendar.get(Calendar.MONTH) < 9)
            {
                calendarMonth = "0" + (calendar.get(Calendar.MONTH) + 1);
            } else
            {
                calendarMonth = String.valueOf((calendar.get(Calendar.MONTH) + 1));
            }
            return preTableName + calendar.get(Calendar.YEAR) + calendarMonth;
        } else
        {
            return defaultTableName;
        }

    }

    String getCurrentValidTableName()
    {
        return currentValidTableName;
    }

    Boolean getSplitEnable()
    {
        return splitEnable;
    }
}
