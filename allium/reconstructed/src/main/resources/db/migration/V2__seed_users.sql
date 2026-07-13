-- Users are external reference data: they exist before the system runs and
-- are never written by it (spec: external entity User).
insert into app_user (name, email, role) values ('Alice Adams', 'alice@example.com', 'EMPLOYEE');
insert into app_user (name, email, role) values ('Ben Brown', 'ben@example.com', 'EMPLOYEE');
insert into app_user (name, email, role) values ('Mara Miles', 'mara@example.com', 'MANAGER');
insert into app_user (name, email, role) values ('Frank Field', 'frank@example.com', 'FINANCE');
