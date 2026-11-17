# ADR-0008: Java 21 LTS, Spring Boot 4.1, PostgreSQL 18, Next.js 16

- Status: Accepted (2026-11-18)

## Context

Versions were checked against Maven Central, npm and Adoptium on 2026-11-18. Java 21 and 25 are both
supported LTS releases. Spring Boot 3.5 has left open-source support.

## Decision

- **Java 21 LTS** (Temurin): the LTS most widely deployed in Japanese enterprise environments, with full
  tool support (SpotBugs, JaCoCo, Mockito). Moving to Java 25 is a one-line change.
- **Spring Boot 4.1.2** (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3, JUnit 6).
- **PostgreSQL 18** with **Flyway 12**.
- **Next.js 16.4 / React 19.3 / TypeScript 6.0**. TypeScript 7 (the native compiler) is avoided until the
  Next.js toolchain officially supports it.
- **Node.js 24 LTS**.

## Consequences

Current, supported versions that receive security fixes; Dependabot keeps them current.
