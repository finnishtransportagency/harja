-- create tables for monitoring
-- postgresql-version
SET SCHEMA 'public'; 

drop table if exists MON_LOG_ENTRY;
drop table if exists MON_EVENT_TYPE;
drop table if exists MON_EXT_REFERENCE;
drop table if exists MON_PROCESS_TRANSACTION;
drop table if exists MON_INT_CONF_PARAMETER;
drop table if exists MON_INTEGRATION_PROCESS;
drop sequence if exists mon_logentry_seq;
drop sequence if exists mon_processtransaction_seq;
drop sequence if exists mon_integration_process_seq;

begin;

create table MON_EVENT_TYPE (
  EVENT_TYPE_ID varchar(32) PRIMARY KEY,
  DESCRIPTION varchar(100) 
);
ALTER TABLE MON_EVENT_TYPE OWNER TO "monitor";

create table MON_INTEGRATION_PROCESS (
 INTEGRATION_PROCESS_ID integer PRIMARY KEY,
 PROCESS_NAME varchar(200) not null,
 DESCRIPTION varchar(2000),
 CONSTRAINT UNIQUE_PROCESSNAME UNIQUE (PROCESS_NAME)
);
ALTER TABLE MON_INTEGRATION_PROCESS OWNER TO "monitor";

create table MON_PROCESS_TRANSACTION (
 PROCESS_TRANSACTION_ID integer PRIMARY KEY,
 INTEGRATION_PROCESS_ID integer not null,
 FOREIGN KEY (INTEGRATION_PROCESS_ID) references MON_INTEGRATION_PROCESS
);
ALTER TABLE MON_PROCESS_TRANSACTION OWNER TO "monitor";

create table MON_LOG_ENTRY (
 LOG_ENTRY_ID integer PRIMARY KEY,
 MESSAGE varchar(2000),
 SERVICE_ID varchar(200),
 originator varchar(200) not null,
 log_entry_ts timestamp not null,
 EVENT_TYPE_ID varchar(32) not null,
 PROCESS_TRANSACTION_ID integer not null,
 FOREIGN KEY (EVENT_TYPE_ID) references MON_EVENT_TYPE,
 FOREIGN KEY (PROCESS_TRANSACTION_ID) references MON_PROCESS_TRANSACTION
);
ALTER TABLE MON_LOG_ENTRY OWNER TO "monitor";

create table MON_EXT_REFERENCE (
    EXT_REFERENCE_ID integer PRIMARY KEY,
    EXTERNAL_ID varchar(64),
    CORRELATION_ID varchar(64),
    LOGICAL_ID varchar(64),
    PROCESS_TRANSACTION_ID integer not null,
    FOREIGN KEY (PROCESS_TRANSACTION_ID) references MON_PROCESS_TRANSACTION
);
ALTER TABLE MON_EXT_REFERENCE OWNER TO "monitor";

create table MON_INT_CONF_PARAMETER (
    INT_CONF_PARAM_ID integer PRIMARY KEY,
    paramvalue varchar(200) not null,
    name varchar(200) not null,
    updated timestamp not null,
    INTEGRATION_PROCESS_ID integer not null,
 FOREIGN KEY (INTEGRATION_PROCESS_ID) references MON_INTEGRATION_PROCESS
);
ALTER TABLE MON_INT_CONF_PARAMETER OWNER TO "monitor";

CREATE SEQUENCE mon_logentry_seq
  INCREMENT 1
  START 1;
ALTER TABLE mon_logentry_seq OWNER TO "monitor";

CREATE SEQUENCE mon_processtransaction_seq
  INCREMENT 1
  START 1;
ALTER TABLE mon_processtransaction_seq OWNER TO "monitor";

CREATE SEQUENCE mon_integration_process_seq
  INCREMENT 1
  START 1;
ALTER TABLE mon_integration_process_seq OWNER TO "monitor";

-- insert event types

insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('INTEGRATION_START', 'Integration start');
insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('INTEGRATION_SUCCESS', 'Integration success');
insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('INTEGRATION_FAILED', 'Integration failed');
insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('INTERFACE_START', 'Interface start');
insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('INTERFACE_SUCCESS', 'Interface success');
insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('INTERFACE_FAILED', 'Interface failed');
insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('INFO', 'Info');
insert into MON_EVENT_TYPE(EVENT_TYPE_ID, description) values ('ERROR', 'Error');

-- insert MON_INTEGRATION_PROCESSes

insert into mon_integration_process (process_name, integration_process_id) values ('api', 1);
insert into mon_integration_process (process_name, integration_process_id) values ('sampo', 2);

commit;