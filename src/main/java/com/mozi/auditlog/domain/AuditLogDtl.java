package com.mozi.auditlog.domain;

import com.mozi.auditlog.util.UniqueIdGenerator;

import java.io.Serializable;

/**
 * 审计日志明细表
 *
 * @author chenhuafu chenhuafu@mozihealthcare.cn
 * @since 7.0.4.X  2025-11-23 10:12:14
 */
public class AuditLogDtl implements Serializable {
    private static final long serialVersionUID = 1L;
	/**
	* 审计日志明细主键ID
	*/

	private String auditLogdId ;

	/**
	* 审计日志主键ID
	*/
	private String auditLogId ;

	/**
	* 字段名称
	*/
	private String columnName ;

	/**
	* 字段描述
	*/
	private String columnDescription ;

	/**
	* 新值
	*/
	private Object newValue ;

	/**
	* 旧值
	*/
	private Object oldValue ;

    public String getAuditLogdId() {
        return auditLogdId;
    }

    public void setAuditLogdId(String auditLogdId) {
        this.auditLogdId = auditLogdId;
    }

    public String getAuditLogId() {
        return auditLogId;
    }

    public void setAuditLogId(String auditLogId) {
        this.auditLogId = auditLogId;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnDescription() {
        return columnDescription;
    }

    public void setColumnDescription(String columnDescription) {
        this.columnDescription = columnDescription;
    }

    public Object getNewValue() {
        return newValue;
    }

    public void setNewValue(Object newValue) {
        this.newValue = newValue;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public void setOldValue(Object oldValue) {
        this.oldValue = oldValue;
    }

    public AuditLogDtl(String auditLogId, String columnName, String columnDescription, Object newValue, Object oldValue) {
        this.auditLogdId = UniqueIdGenerator.generateUniqueId();
        this.auditLogId = auditLogId;
        this.columnName = columnName;
        this.columnDescription = columnDescription;
        this.newValue = newValue;
        this.oldValue = oldValue;
    }

    @Override
    public String toString() {
        return "AuditLogDtl{" +
                "auditLogdId='" + auditLogdId + '\'' +
                ", auditLogId='" + auditLogId + '\'' +
                ", columnName='" + columnName + '\'' +
                ", columnDescription='" + columnDescription + '\'' +
                ", newValue='" + newValue + '\'' +
                ", oldValue='" + oldValue + '\'' +
                '}';
    }
}