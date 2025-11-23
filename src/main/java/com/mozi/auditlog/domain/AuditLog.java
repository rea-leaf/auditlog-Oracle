package com.mozi.auditlog.domain;


import com.mozi.auditlog.util.UniqueIdGenerator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 审计日志主表
 *
 * @author chenhuafu chenhuafu@mozihealthcare.cn
 * @since 7.0.4.X  2025-11-23 10:13:34
 * @since 7.0.4.X  2025-11-23 10:13:34
 */
public class AuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public static enum OperationEnum
    {
        insert, update, delete
    }

    /**
	* 审计日志主键ID
	*/
	private String auditLogId ;

	/**
	* IP地址
	*/
	private String ipAddress ;

	/**
	* 会话ID
	*/
	private String sessionId ;

	/**
	* 操作类型
	*/
	private String operationType ;

	/**
	* 操作批次ID：标识同一次操作的所有记录
	*/
	private String batchId ;

	/**
	* 表名称
	*/
	private String tableName ;

	/**
	* 表描述
	*/
	private String tableDescription ;

	/**
	* 主键值
	*/
	private String primaryKeyValue ;

	/**
	* 父记录ID
	*/
	private String parentId ;

	/**
	* 创建人ID
	*/
	private String createBy ;

	/**
	* 创建人姓名
	*/
	private String createName ;

	/**
	* 创建时间
	*/
	private Date createTime ;

    private List<AuditLogDtl> auditLogDtlList=new ArrayList<AuditLogDtl>();

    public String getAuditLogId() {
        return auditLogId;
    }

    public void setAuditLogId(String auditLogId) {
        this.auditLogId = auditLogId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableDescription() {
        return tableDescription;
    }

    public void setTableDescription(String tableDescription) {
        this.tableDescription = tableDescription;
    }

    public String getPrimaryKeyValue() {
        return primaryKeyValue;
    }

    public void setPrimaryKeyValue(String primaryKeyValue) {
        this.primaryKeyValue = primaryKeyValue;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getCreateName() {
        return createName;
    }

    public void setCreateName(String createName) {
        this.createName = createName;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public AuditLog(String operationType, String tableName, String tableDescription, String primaryKeyValue,Date createTime) {
        this.auditLogId = UniqueIdGenerator.generateUniqueId();
        this.operationType = operationType;
        this.tableName = tableName;
        this.tableDescription = tableDescription;
        this.primaryKeyValue = primaryKeyValue;
        this.createTime = createTime;

    }

    public List<AuditLogDtl> getAuditLogDtlList() {
        return auditLogDtlList;
    }

    public void setAuditLogDtlList(List<AuditLogDtl> auditLogDtlList) {
        this.auditLogDtlList = auditLogDtlList;
    }

    @Override
    public String toString() {
        return "AuditDicLog{" +
                "auditLogId='" + auditLogId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", operationType='" + operationType + '\'' +
                ", batchId='" + batchId + '\'' +
                ", tableName='" + tableName + '\'' +
                ", tableDescription='" + tableDescription + '\'' +
                ", primaryKeyValue='" + primaryKeyValue + '\'' +
                ", parentId='" + parentId + '\'' +
                ", createBy='" + createBy + '\'' +
                ", createName='" + createName + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}