CREATE TABLE TB_AUDIT_LOG
(
    tc_audit_log_id          NUMBER not null,
    tc_batch_id              CHAR(24) NOT NULL,
    tc_table_name            VARCHAR2(128) NOT NULL,
    tc_table_description     NVARCHAR2(256) NOT NULL,
    tc_column_name           VARCHAR2(128),
    tc_column_description    NVARCHAR2(256),
    tc_primary_key_value     VARCHAR2(64),
    tc_parent_id             CHAR(24),
    tc_new_value             VARCHAR2(4000),
    tc_old_value             VARCHAR2(4000),
    tc_operation_type        VARCHAR2(32),
    tc_create_time           DATE,
    tc_create_by             CHAR(24),
    tc_create_name           VARCHAR2(64),
    tc_memo                  NVARCHAR2(128),
    tc_ip_address            VARCHAR2(64),
    tc_session_id            VARCHAR2(128),
    CONSTRAINT PK_TB_AUDIT_LOG PRIMARY KEY (tc_audit_log_id)
);

-- 表注释
COMMENT ON TABLE TB_AUDIT_LOG IS '审计日志表';

-- 列注释
COMMENT ON COLUMN TB_AUDIT_LOG.tc_audit_log_id IS '审计日志主键ID';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_batch_id IS '操作批次ID：标识同一次操作的所有记录';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_table_name IS '表名称';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_table_description IS '表描述';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_column_name IS '字段名称';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_column_description IS '字段描述';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_primary_key_value IS '主键值';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_parent_id IS '父记录ID';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_new_value IS '新值';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_old_value IS '旧值';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_operation_type IS '操作类型';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_create_time IS '创建时间';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_create_by IS '创建人ID';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_create_name IS '创建人姓名';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_memo IS '备注';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_ip_address IS 'IP地址';
COMMENT ON COLUMN TB_AUDIT_LOG.tc_session_id IS '会话ID';
create sequence SEQ_AUDIT_LOG
    minvalue 1
    maxvalue 9999999999999999999999999999
    start with 381
    increment by 1
    cache 20;
