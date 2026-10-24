-- One database per microservice (each service owns its schema and Flyway history).
CREATE DATABASE authdb;
CREATE DATABASE weatherdb;
CREATE DATABASE alertdb;
