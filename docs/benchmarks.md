# Benchmarks / ベンチマーク

Two questions, measured separately:

1. **Quality**: how close are the heuristic's plans to the true optimum, and how much better are
   they than the traveller's own order? (`QualityStudy`)
2. **Speed**: how long does planning take as trips grow? (JMH micro-benchmarks)

All numbers below were produced by the code in [`backend/benchmarks`](../backend/benchmarks) on the
machine described here. They are not lab measurements: the machine is a laptop with other software
running, so the ± columns matter and differences of a few milliseconds mean nothing.

| | |
| --- | --- |
| Date | 2026-11-18 |
| CPU | Intel Core i7-13620H (10 cores / 16 threads, hybrid P/E cores), laptop |
| Memory | 31.6 GB |
| OS / power plan | Windows 11 Home 10.0.26200, "Balanced" |
| JVM | Eclipse Temurin 21.0.13+9 (default GC and heap) |
| Harness | JMH 1.37, 1 fork, 1 thread |

## Reproduce

```bash
cd backend
./mvnw -pl benchmarks -am package -DskipTests
java -cp benchmarks/target/benchmarks.jar dev.tabiji.bench.QualityStudy          # about 30 s
java -jar benchmarks/target/benchmarks.jar -rf json -rff jmh-result.json         # about 6 min
java -jar benchmarks/target/benchmarks.jar OptimizerBenchmark.optimize -p candidates=60,62 -p days=7,14
```

The instances come from `BenchmarkProblems`: random places on a 10 km × 10 km plane, walking below
1.5 km and transit-like legs above, random opening hours (some places closed on some days, some with
a lunch break), random visit lengths and admissions, a few must-visits and a budget. Every instance is
seeded, so the numbers are reproducible on any machine (the timings, of course, are not).

## 1. Solution quality

### Heuristic vs the exact optimum

1,000 instances (seeds 10000–10999) with 3–8 places and 1–3 days, each solved by the heuristic
(`Strategy.OPTIMIZE`) and by the exhaustive reference solver (`Strategy.EXACT`), which is exact for
any travel matrix (see [algorithm.md](algorithm.md#7-verification)).

| Metric | Value |
| --- | --- |
| Same objective value as the exact optimum | **958 / 1,000 (95.8 %)** |
| Plans violating a hard constraint (checked by the independent `PlanValidator`) | **0** |
| Instances with fewer must-visits than the optimum | 0 |
| Instances with a lower score than the optimum | 11 |
| Instances with the same score but more travel or cost | 31 |
| Mean score gap | 0.18 % |
| Worst score gap | 50.0 % (explained below) |
| Mean extra travel at equal score | 0.12 % |

**Where the heuristic misses.** Of the 11 instances with a lower score, 7 schedule one optional place
fewer than the optimum (for example 7 instead of 8). The other 4, which include the worst case, all
have the same shape: three must-visits of which only two fit. Both solvers schedule two must-visits,
so the first level of the objective ties, but the heuristic keeps a lower-scoring pair. In seed 10453
it keeps must-visits scored 2 and 6 instead of 10 and 6, which is the 50 % gap. The cause is a missing
neighbourhood: local search can swap optional places for better ones, but it never exchanges a
scheduled must-visit for an unscheduled one. In the application every must-visit carries the same
base score, so this mostly affects synthetic instances; it is listed in
[known-limitations.md](known-limitations.md) and on the [roadmap](roadmap.md).

The unit test `OptimizerPropertyTest` enforces a related bound on every build: on 400 random small
instances the heuristic must reach the exact optimum in at least 95 % of cases and must never beat it
(which would reveal a bug in one of the two solvers).

### Optimized vs the traveller's own order

20 instances per size (seeds 50000–50019). "Own order" is the `KEEP_ORDER` strategy, which follows the
list as given and only moves on to the next day when a place no longer fits. In these instances the
list order is random, which is a pessimistic stand-in for a real traveller (people usually list
nearby places together), so real-world gains are smaller.

| Places × days | Places scheduled (own order → optimized) | Travel minutes per scheduled place (own order → optimized) | Median runtime of the optimizer |
| --- | --- | --- | --- |
| 10 × 3 | 5.7 → 8.2 (+44 %) | 38.3 → 31.3 (−18 %) | 3 ms |
| 25 × 3 | 7.2 → 11.0 (+53 %) | 36.4 → 27.6 (−24 %) | 13 ms |
| 25 × 7 | 14.9 → 22.6 (+52 %) | 38.4 → 24.6 (−36 %) | 91 ms |
| 50 × 7 | 15.1 → 26.4 (+75 %) | 39.3 → 25.6 (−35 %) | 259 ms |
| 50 × 14 | 26.0 → 41.3 (+59 %) | 38.6 → 25.5 (−34 %) | 1,024 ms |

The median runtimes in this table come from single calls with `System.nanoTime()` (including JIT
warm-up on the first instances), not from JMH.

## 2. Runtime (JMH)

### Optimizer

One fixed instance per size (seed 42), average time per call, 3 × 2 s warm-up, 5 × 2 s measurement.
The ± value is JMH's 99.9 % confidence half-width.

| Places | Days | `optimize` (ms) | `keepOrderBaseline` (ms) |
| ---: | ---: | ---: | ---: |
| 10 | 3 | 16.4 ± 12.4 | 0.006 ± 0.001 |
| 10 | 7 | 30.1 ± 37.7 | 0.007 ± 0.006 |
| 10 | 14 | 10.3 ± 2.9 | 0.014 ± 0.010 |
| 25 | 3 | 4.9 ± 0.4 | 0.019 ± 0.014 |
| 25 | 7 | 15.7 ± 3.5 | 0.028 ± 0.007 |
| 25 | 14 | 314.5 ± 217.3 | 0.032 ± 0.024 |
| 50 | 3 | 184.6 ± 64.2 | 0.065 ± 0.147 |
| 50 | 7 | 247.1 ± 242.8 | 0.048 ± 0.054 |
| 50 | 14 | 1,552.4 ± 1,387.9 | 0.422 ± 0.368 |

The largest inputs the API can produce are 50 chosen places plus 12 suggestions, so a second run
covered 60 and 62 candidates (same settings, a separate JMH invocation):

| Places | Days | `optimize` (ms) |
| ---: | ---: | ---: |
| 60 | 7 | 467.9 ± 50.7 |
| 60 | 14 | 139.0 ± 26.8 |
| 62 | 7 | 175.9 ± 74.8 |
| 62 | 14 | 144.3 ± 42.0 |

Reading the tables:

- **Runtime is not monotonic in size.** Each size is a different random instance, the search stops
  after 160 iterations without a new best (or 400 in total), and how quickly that happens depends on
  the instance: 10 places over 7 days took longer than 25 places over 3 days, and the 50 × 14 instance
  took ten times longer than the 60 × 14 one. The work per iteration grows with route lengths and the
  number of days.
- **The error bars are wide** because each call is long relative to a 2 s measurement window and the
  laptop's clock speed varies. Treat the values as orders of magnitude: tens of milliseconds for a
  typical trip, hundreds for large ones, and up to about 1.5 s for the slowest maximum-size instance
  measured. A 3 s wall-clock cap bounds the worst case; when it triggers, the plan is still valid and
  the solver statistics in the response say so.
- The keep-my-order baseline is a single greedy pass and takes well under a millisecond.

### Shortest paths (Dijkstra)

One-to-all shortest paths on square grid graphs (up to 4 neighbours per node, random weights 1–10),
indexed binary heap, CSR adjacency.

| Nodes | Time per search |
| ---: | ---: |
| 1,024 (32 × 32) | 122 ± 45 µs |
| 16,384 (128 × 128) | 2.7 ± 1.7 ms |
| 262,144 (512 × 512) | 55 ± 13 ms |

Each 16× step in graph size costs about 21–22× in time: the `O((V + E) log V)` bound plus cache
effects. The sample transit graph has about 57 stations and 25 lines, i.e. a few hundred nodes, so a
travel matrix needs one search per access station, each in the low microseconds.

## 3. What this means for the application

- The planning endpoint adds database reads, the travel-matrix build and JSON serialisation to the
  optimizer time; for the sample catalogue these are small next to the search itself.
- CPU per user is bounded twice: by the solver's iteration and time caps, and by the per-user rate
  limit on plan generation (12 per minute by default). See D1 in the [threat model](threat-model.md).
- Not measured yet: end-to-end latency of the HTTP endpoint under concurrent load (roadmap: a Gatling
  or k6 scenario in CI).
