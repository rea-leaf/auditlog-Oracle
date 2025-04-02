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
)

create sequence SEQ_AUDIT_LOG
    minvalue 1
    maxvalue 9999999999999999999999999999
    start with 381
    increment by 1
    cache 20;
