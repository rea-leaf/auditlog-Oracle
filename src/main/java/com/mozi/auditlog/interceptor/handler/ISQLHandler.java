package com.mozi.auditlog.interceptor.handler;

public interface ISQLHandler
{
    void preHandle();
    boolean IsSkipTable();
    void postHandle(Object args);
}
