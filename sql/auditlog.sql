create table TB_AUDIT_DIC_LOG
(
    TC_AUDIT_LOG_ID      CHAR(24)             not null,
    TC_IP_ADDRESS        VARCHAR2(64),
    TC_SESSION_ID        VARCHAR2(128),
    TC_OPERATION_TYPE    VARCHAR2(16),
    TC_TABLE_NAME        VARCHAR2(48),
    TC_TABLE_DESCRIPTION NVARCHAR2(48),
    TC_PRIMARY_KEY_VALUE CHAR(24),
    TC_PARENT_ID         CHAR(24),
    TC_CREATE_BY         CHAR(24),
    TC_CREATE_NAME       NVARCHAR2(16),
    TC_CREATE_TIME       DATE,
    constraint PK_TB_AUDIT_DIC_LOG primary key (TC_AUDIT_LOG_ID)
);
comment on table TB_AUDIT_DIC_LOG is '审计日志主表';
comment on column TB_AUDIT_DIC_LOG.TC_AUDIT_LOG_ID is '审计日志主键ID';
comment on column TB_AUDIT_DIC_LOG.TC_IP_ADDRESS is 'IP地址';
comment on column TB_AUDIT_DIC_LOG.TC_SESSION_ID is '会话ID';
comment on column TB_AUDIT_DIC_LOG.TC_OPERATION_TYPE is '操作类型';
comment on column TB_AUDIT_DIC_LOG.TC_BATCH_ID is '操作批次ID：标识同一次操作的所有记录';
comment on column TB_AUDIT_DIC_LOG.TC_TABLE_NAME is '表名称';
comment on column TB_AUDIT_DIC_LOG.TC_TABLE_DESCRIPTION is '表描述';
comment on column TB_AUDIT_DIC_LOG.TC_PRIMARY_KEY_VALUE is '主键值';
comment on column TB_AUDIT_DIC_LOG.TC_CREATE_BY is '创建人ID';
comment on column TB_AUDIT_DIC_LOG.TC_CREATE_NAME is '创建人姓名';
comment on column TB_AUDIT_DIC_LOG.TC_CREATE_TIME is '创建时间';

--drop table TB_AUDIT_DIC_LOG_DTL cascade constraints;
create table TB_AUDIT_DIC_LOG_DTL
(
    TC_AUDIT_LOGD_ID     CHAR(24)             not null,
    TC_AUDIT_LOG_ID      CHAR(24),
    TC_COLUMN_NAME       VARCHAR2(48),
    TC_COLUMN_DESCRIPTION NVARCHAR2(48),
    TC_NEW_VALUE         VARCHAR2(4000),
    TC_OLD_VALUE         VARCHAR2(4000),
    constraint PK_TB_AUDIT_DIC_LOG_DTL primary key (TC_AUDIT_LOGD_ID)
);
comment on table TB_AUDIT_DIC_LOG_DTL is '审计日志明细表';
comment on column TB_AUDIT_DIC_LOG_DTL.TC_AUDIT_LOGD_ID is '审计日志明细主键ID';
comment on column TB_AUDIT_DIC_LOG_DTL.TC_AUDIT_LOG_ID is '审计日志主键ID';
comment on column TB_AUDIT_DIC_LOG_DTL.TC_COLUMN_NAME is '字段名称';
comment on column TB_AUDIT_DIC_LOG_DTL.TC_COLUMN_DESCRIPTION is '字段描述';
comment on column TB_AUDIT_DIC_LOG_DTL.TC_NEW_VALUE is '新值';
comment on column TB_AUDIT_DIC_LOG_DTL.TC_OLD_VALUE is '旧值';
