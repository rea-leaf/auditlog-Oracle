create table AUDIT_LOG
(
    id          NUMBER not null,
    tablename   VARCHAR2(128) not null,
    columnname  VARCHAR2(128),
    primarykey  VARCHAR2(24),
    parentid    NUMBER,
    newvalue    VARCHAR2(4000),
    oldvalue    VARCHAR2(4000),
    operation   VARCHAR2(50),
    createtime  DATE,
    createclerk VARCHAR2(24),
    PRIMARY KEY (ID)
);
-- Add comments to the columns
comment on column AUDIT_LOG.id
  is '本记录ID使用了SEQ_AUDIT_LOG';
comment on column AUDIT_LOG.tablename
  is '记录的表名';
comment on column AUDIT_LOG.columnname
  is '记录的列名';
comment on column AUDIT_LOG.primarykey
  is '记录的主键';
comment on column AUDIT_LOG.parentid
  is '父记录ID（例如：insert、delete、update语句 从第二个字段开始 会以第一个字段在本表的ID作为ParentID，以便于SQL分组）';
comment on column AUDIT_LOG.newvalue
  is '该字段的新值';
comment on column AUDIT_LOG.oldvalue
  is '该字段的旧值';
comment on column AUDIT_LOG.operation
  is '记录下的动作（目前有：update、insert、delete）';
comment on column AUDIT_LOG.createtime
  is '记录的创建时间';
comment on column AUDIT_LOG.createclerk
  is '操作人员ID';
create sequence SEQ_AUDIT_LOG
    minvalue 1
    maxvalue 9999999999999999999999999999
    start with 381
    increment by 1
    cache 20;
