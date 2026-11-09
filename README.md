# plugkit-util

Plug-and-play infrastructure libraries for Java.

Give plugkit-util your connection details, then use databases, Kafka, caches and resilience patterns
through simple methods or annotations, with safe defaults and production-grade behaviour built in.

> **Status:** in development. Database and Core are built; Kafka and Cache are not started.
> Nothing is released yet.

## Why

Every service that talks to a database, a message broker or a cache ends up rewriting the same
code: connection setup, timeouts, retries, consumer loops, idempotency, metrics, clean shutdown.
plugkit-util packages these patterns once, carefully tested, so projects can reuse them instead of
re-inventing them.

## Libraries

Each library is an independent module: use one, some or all of them. Core is included
automatically by the others.

| Library | Covers |
|---|---|
| **Database** | Relational databases over JDBC behind one API. PostgreSQL and MySQL; other databases later |
| **Kafka** | Kafka interaction (producing, consuming, transactions). Works with anything that speaks the Kafka protocol |
| **Cache** | Generic caching. Redis and in-memory first; others such as Memcached later |
| **Core** | Shared building blocks every library uses: configuration validation, retries, graceful shutdown, health and a common exception base. Resilience patterns (timeouts, retries, circuit breakers, bulkheads) live here |

## How it works

1. **Configure:** provide what's needed to connect (URL, credentials, a few settings). plugkit-util
   builds and manages the connections.
2. **Use methods:** inject an object and call it.
3. **Or use annotations:** put an annotation on a method as a shortcut (in Spring applications).

## Principles

- **Generic where it's honest:** a shared API only offers what every backend can really do.
- **Safe by default:** works with minimal configuration; sensible defaults everywhere.
- **Configurable, within limits:** the settings that matter are exposed; everything else stays out.
- **No bloat:** small API, few dependencies, nothing built "just in case".
- **Sound non-functional behaviour:** low overhead, no leaks, timeouts everywhere, observable.
