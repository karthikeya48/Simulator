# Vehicular Edge Computing Simulator: IEEE Technical Documentation

## 1. Introduction

This document presents the design and implementation of a **Vehicular Edge Computing (VEC) Simulator** that models task offloading and scheduling across a three-tier architecture: Vehicle OBUs, Roadside Units (RSUs), and Cloud servers. The simulator employs machine learning-assisted offloading decisions and uses the TOPSIS Multi-Criteria Decision-Making methodology for intelligent resource scheduling.

---

## 2. System Architecture

### 2.1 Three-Tier Infrastructure

```
┌─────────────────────────────────────────────────────┐
│              CLOUD SERVER                           │
│  High MIPS (50k), High RAM (32GB), High Latency    │
│  Propagation Delay: 80 ms (WAN)                    │
└─────────────────────────────────────────────────────┘
                        ↑
                  (Backhaul Network)
                        ↓
┌──────────────┬──────────────┬──────────────┐
│   RSU_1      │    RSU_2     │    RSU_N     │
│ 3500 MIPS    │  4000 MIPS   │  Varying     │
│ 4608 MB RAM  │  5120 MB RAM │  Capacities  │
│ Edge Layer   │ Delay: 10ms  │              │
└──────────────┴──────────────┴──────────────┘
      ↑              ↑              ↑
  (LTE/5G Network Links)
      ↓              ↓              ↓
┌────────┬────────┬────────┬────────┐
│ VEH_   │ VEH_   │ VEH_   │ VEH_   │  (20 Vehicles Total)
│1001    │1002    │1003    │1004    │  500-2000 MIPS each
│(local) │(local) │(local) │(local) │  512-2560 MB RAM each
└────────┴────────┴────────┴────────┘  Delay: 1 ms
```

### 2.2 Three-Tier Task Processing Pipeline

```
┌──────────────────────────────────────────────────────────┐
│ Thread 1: TASK CREATION                                  │
│ - Generate tasks from vehicles                           │
│ - Randomize: MI, bandwidth, signal, mobility status     │
│ - Invoke ML Predictor → get offload decision            │
│ - Output: Task → createdTaskQueue                       │
└──────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────┐
│ Thread 2: TASK ASSIGNMENT (TOPSIS Scheduler)            │
│ - Consume from createdTaskQueue                          │
│ - Apply TOPSIS MCDM on candidate nodes                  │
│ - Fallback chain if preferred target saturated          │
│ - Allocate resources, compute T_total                   │
│ - Output: Task → runningTaskQueue                       │
└──────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────┐
│ Thread 3: TASK RELEASE                                   │
│ - Monitor runningTaskQueue for completions              │
│ - Release resources when T_total wall-clock time passes │
│ - Collect latency statistics                            │
│ - Update infrastructure health scores                   │
└──────────────────────────────────────────────────────────┘
```

---

## 3. Computational Models & Mathematical Formulations

### 3.1 Task Execution Time Model

When a task is assigned to a compute node, its total end-to-end latency is composed of three components:

#### 3.1.1 Processing/Computation Time
```
T_exec = I / F [seconds]

Where:
  I  = Number of instructions (in millions of instructions, MI)
  F  = Allocated compute capacity (in MIPS)
```

**Conversion to milliseconds:**
```
T_exec_ms = (I / F) × 1000 [ms]
```

#### 3.1.2 Data Transfer Time
```
T_trans = (D × 8) / B [seconds]

Where:
  D = Task size (MB)
  B = Available bandwidth (Mbps)
  (× 8 to convert MB to Mb)
```

**Conversion to milliseconds:**
```
T_trans_ms = [(D × 8) / B] × 1000 [ms]
```

#### 3.1.3 Propagation/Network Delay
```
T_prop = L / c [seconds]

Simplified Model (empirically measured):
  Cloud (WAN):    T_prop = 80 ms
  RSU (LTE/5G):   T_prop = 10 ms
  Vehicle (OBU):  T_prop = 1 ms  (local processing)
```

#### 3.1.4 Total End-to-End Latency
```
T_total = T_exec + T_trans + T_prop [ms]

Example Calculation:
  Task: 5000 MI, 10 MB, assigned to RSU with 3000 MIPS, 20 Mbps bandwidth
  
  T_exec = (5000 / 3000) × 1000 = 1666.67 ms
  T_trans = [(10 × 8) / 20] × 1000 = 4000 ms
  T_prop = 10 ms (RSU edge latency)
  T_total = 1666.67 + 4000 + 10 = 5676.67 ms
```

### 3.2 Resource Utilization Metrics

#### 3.2.1 CPU Utilization Ratio
```
U_cpu = UsedMIPS / TotalMIPS ∈ [0, 1]
```

#### 3.2.2 RAM Utilization Ratio
```
U_ram = UsedRAM / TotalRAM ∈ [0, 1]
```

#### 3.2.3 Composite Health Score
```
H(n) = 1 - (α · U_cpu + β · U_ram) ∈ [0, 1]

Where:
  α = 0.6  (CPU weight: prioritize compute availability)
  β = 0.4  (RAM weight)
  
Higher H(n) → Node is healthier/less congested
```

**Interpretation:**
- H = 1.0: Node idle, perfect health
- H = 0.5: Node moderately loaded
- H = 0.0: Node fully saturated

### 3.3 TOPSIS Multi-Criteria Decision Making (MCDM)

**Reference:** Hwang, C. L., & Yoon, K. (1981). *Multiple Attribute Decision Making Methods and Applications*. Springer-Verlag.

TOPSIS selects the best candidate node by measuring the distance to ideal and anti-ideal solutions.

#### Step 1: Decision Matrix Construction
```
Build matrix D[m × n] where:
  m = number of candidate nodes
  n = number of decision criteria

For each candidate node i, evaluate:
  Criterion 1: Available MIPS                    [d_i1]
  Criterion 2: Health Score                      [d_i2]
  Criterion 3: Propagation Delay (cost)          [d_i3]
  Criterion 4: Estimated Execution Time (cost)   [d_i4]

D = [d11  d12  d13  d14]
    [d21  d22  d23  d24]
    [... ... ... ...]
    [dm1  dm2  dm3  dm4]
```

#### Step 2: Vector Normalization
```
For each criterion j, normalize column values:

r_ij = d_ij / √(Σ d²_ij)  for i=1..m

Creates normalized matrix R with unit-vector columns
```

#### Step 3: Weighted Normalization
```
Apply decision weights to normalized values:

v_ij = w_j × r_ij

Where weights are:
  w1 = 0.30  (Available MIPS)       - benefit criterion
  w2 = 0.25  (Health Score)         - benefit criterion
  w3 = 0.25  (Propagation Delay)    - cost criterion
  w4 = 0.20  (Execution Time)       - cost criterion
  
  Σ w_j = 1.0

Creates weighted normalized matrix V
```

#### Step 4: Ideal and Anti-Ideal Solution Determination
```
For each criterion j:
  If BENEFIT criterion (1, 2):
    Ideal Best:   A+_j = max(v_ij)
    Ideal Worst:  A-_j = min(v_ij)
    
  If COST criterion (3, 4):
    Ideal Best:   A+_j = min(v_ij)
    Ideal Worst:  A-_j = max(v_ij)

Ideal solutions:
  A+ = (A+_1, A+_2, ..., A+_4)    ← aspirational target
  A- = (A-_1, A-_2, ..., A-_4)    ← worst-case baseline
```

#### Step 5: Distance Measures
```
Euclidean separation from ideal solutions:

S+_i = √(Σ (v_ij - A+_j)²)      [distance to ideal]
S-_i = √(Σ (v_ij - A-_j)²)      [distance to anti-ideal]

Intuitively:
  Small S+_i and large S-_i → good solution
```

#### Step 6: Closeness Coefficient
```
Relative closeness to ideal solution:

C_i = S-_i / (S+_i + S-_i)  ∈ [0, 1]

Where:
  C_i → 1.0  (solution close to ideal)
  C_i → 0.0  (solution close to anti-ideal)
```

#### Step 7: Ranking and Selection
```
Rank candidates by C_i in descending order.
Select node with highest C_i.

C_best_node = max(C_i) for all i ∈ {candidates}
```

#### Complete TOPSIS Example

Given 3 RSU candidates for a task assignment:

```
Decision Matrix D:
             Available   Health   Prop.   Exec.
             MIPS        Score    Delay   Time
RSU_1        3500        0.75     10      1500
RSU_2        4000        0.60     10      1200
RSU_3        3200        0.85     10      1800

Step 1: Normalize
Norm_MIPS = √(3500² + 4000² + 3200²) = 5844.86
r11 = 3500/5844.86 = 0.599
r21 = 4000/5844.86 = 0.685
r31 = 3200/5844.86 = 0.548

(... continue for other criteria ...)

Step 2: Weight (e.g., w1=0.30)
v11 = 0.30 × 0.599 = 0.180

(... continue for all entries ...)

Step 3: Ideal Solutions
A+ = {max benefit columns, min cost columns}
A- = {min benefit columns, max cost columns}

Step 4: Calculate C_i
RSU_1: C_1 = 0.72
RSU_2: C_2 = 0.81    ← HIGHEST: SELECTED
RSU_3: C_3 = 0.65

Result: Assign task to RSU_2
```

---

## 4. Scheduling Algorithm with Fallback Chain

### 4.1 Primary Scheduling Strategy

```
Algorithm: SCHEDULE_TASK(task)

Input:
  task.offloadTarget ∈ {"cloud", "rsu", "vehicle"}
  candidates = getCandidates(task.offloadTarget)

1. Compute TOPSIS score for all candidates
2. Select node with max TOPSIS closeness C_i
3. Try to allocate required resources
   
   If allocation succeeds:
      assignTask(task, node)
      RETURN success
   Else:
      CONTINUE to fallback
      
4. If primary target exhausted:
      Determine FALLBACK_ORDER based on original target
      
   For each target in FALLBACK_ORDER:
      candidates = getCandidates(target)
      best = topsisSelect(candidates, task)
      
      If allocation succeeds on best:
         Update task.offloadTarget = fallback_target
         RETURN success
      
   5. If all fallback attempts exhausted:
      RETURN failure
      Mark task as FAILED
```

### 4.2 Fallback Priority Chains

```
Original Target    Fallback Chain
─────────────────  ────────────────────────
RSU                Cloud → Vehicle
Cloud              RSU → Vehicle
Vehicle (Local)    RSU → Cloud
```

**Rationale:**
- RSU preferred: if saturated, offload to Cloud (higher capacity)
- Cloud preferred: if saturated, fall back to RSU (latency acceptable)
- Local failed: try RSU (nearby), then Cloud (fallback)

---

## 5. Implementation Details

### 5.1 Concurrent Task Pipeline

```
┌─────────────────────────────────────┐
│     BlockingQueue Design            │
├─────────────────────────────────────┤
│ LinkedBlockingQueue<Task>           │
│   - Thread-safe by default          │
│   - Supports producer-consumer      │
│   - Automatic blocking on empty     │
└─────────────────────────────────────┘

Queue 1: createdTaskQueue
  ├─ Producer: TaskCreationThread
  ├─ Consumer: TaskAssignmentThread
  └─ Capacity: unlimited

Queue 2: runningTaskQueue
  ├─ Producer: TaskAssignmentThread
  ├─ Consumer: TaskReleaseThread
  └─ Capacity: unlimited
```

### 5.2 Manual Time Difference (Simulated Execution)

The simulator uses **wall-clock time** to simulate task execution:

```java
// When task is assigned:
task.assignmentTimeMs = System.currentTimeMillis()
task.simulatedExecTimeMs = T_total  // computed above

// In TaskReleaseThread, periodically check:
long elapsedRealTime = System.currentTimeMillis() - task.assignmentTimeMs

if (elapsedRealTime >= task.simulatedExecTimeMs) {
    // Task completed - release resources
    node.release(mips, ram)
    task.markCompleted()
    
    // Calculate true latency
    totalLatency = task.completionTimeMs - task.creationTimeMs
}
```

**Time Model:**
- 1 millisecond of simulated execution = 1 millisecond wall-clock time
- Enables realistic event ordering without speeding up simulation
- No time acceleration needed for 60-second simulation window

### 5.3 Resource Allocation Model

```java
public synchronized boolean allocate(int mips, int ramMB) {
    if (getAvailableMips() >= mips && 
        getAvailableRamMB() >= ramMB) {
        
        usedMips.addAndGet(mips)
        usedRamMB.addAndGet(ramMB)
        activeTasks.incrementAndGet()
        return true
    }
    return false
}

// Release after completion:
public synchronized void release(int mips, int ramMB) {
    usedMips.addAndGet(-mips)
    usedRamMB.addAndGet(-ramMB)
    activeTasks.decrementAndGet()
}
```

---

## 6. ML Offloading Prediction Integration

### 6.1 API Contract

```
HTTP POST http://127.0.0.1:8000/predict

Request JSON:
{
  "vehicle_id": "VEH_1001",
  "signal_strength": -45,
  "critical_task": 1,
  "bandwidth_mbps": 22.5,
  "number_of_instructions": 5294,
  "task_size_MB": 10.68,
  "mobility_status": "low"
}

Response JSON:
{
  "is_offloaded": 1,
  "offload_target": "rsu"
}
```

### 6.2 Rule-Based Fallback Logic

When ML service is unavailable, apply deterministic rules:

```
if (critical_task == 1 AND mobility_status == HIGH):
    return (offloaded=true, target="cloud")
    reason: critical tasks need stable backhaul

elif (signal_strength > -60 dB AND mobility_status == LOW):
    return (offloaded=true, target="rsu")
    reason: good connectivity + low motion → edge suitable

elif (instructions > 5000 MI AND bandwidth > 15 Mbps):
    return (offloaded=true, target="rsu")
    reason: moderate task + good bandwidth → edge viable

else:
    return (offloaded=false, target="vehicle")
    reason: execute locally
```

---

## 7. Experimental Setup & Parameters

### 7.1 Infrastructure Configuration

| Component | Count | MIPS Range | RAM Range | Latency |
|-----------|-------|-----------|----------|---------|
| Cloud     | 1     | 50,000    | 32 GB    | 80 ms   |
| RSU       | 5     | 3000-4500 | 4.5-6 GB | 10 ms   |
| Vehicles  | 20    | 500-2000  | 512-2560 MB | 1 ms  |

### 7.2 Task Generation Parameters

```
Per-task randomization:
  Mobility Status:      {LOW, MID, HIGH} (uniform)
  Signal Strength:      -30 to -90 dBm (uniform)
  Critical Task:        20% probability
  Bandwidth:            5 - 50 Mbps (uniform)
  Instructions:         1,000 - 10,000 MI (uniform)
  Task Size:            1 - 50 MB (uniform)
  Inter-arrival Time:   1000-3000 ms (Poisson-like)
```

### 7.3 Simulation Duration

- **Run Time**: 60 seconds
- **Threads**: 3 concurrent (Creation, Assignment, Release)
- **Grace Period**: +5s for assignment, +10s for release

---

## 8. Performance Metrics & Statistics

### 8.1 Throughput Metrics

```
Total Tasks Assigned    = count of successfully scheduled tasks
Total Tasks Completed   = count of released tasks
Completion Rate         = Completed / Assigned × 100%
Task Failure Rate       = Failed / Total Generated × 100%
```

### 8.2 Latency Metrics

```
E2E Latency = CompletionTime - CreationTime

Average Latency         = Σ(E2E Latency_i) / Completed Tasks
Min Latency             = min(E2E Latency_i)
Max Latency             = max(E2E Latency_i)
P95 Latency             = 95th percentile
P99 Latency             = 99th percentile
```

### 8.3 Infrastructure Utilization

```
Per Node:
  CPU Utilization = UsedMIPS / TotalMIPS
  RAM Utilization = UsedRAM / TotalRAM
  Health Score    = 1 - (0.6 × CPU_U + 0.4 × RAM_U)
  
System Average:
  Avg CPU Util = (Σ CPU_U_i) / NumNodes
  Avg RAM Util = (Σ RAM_U_i) / NumNodes
```

### 8.4 Distribution Metrics

```
Per Offload Target:
  Cloud Assignments   = count tasks → Cloud
  RSU Assignments     = count tasks → RSU
  Vehicle Assignments = count tasks → Vehicle
  
Fallback Usage Rate = (Tasks using fallback) / Total Assigned
```

---

## 9. Output & Logging

### 9.1 Console Output Format

```
[INFRA] Created: CloudServer[CLOUD_1 | 50000 MIPS | 32768 MB RAM]
[INFRA] Created: RSUServer[RSU_1 | 3500 MIPS | 4608 MB RAM]
[INFRA] Created: Vehicle[VEH_1001 | 1234 MIPS | 512 MB RAM]

[CREATION] Thread started.
[CREATION] Task TASK_1 from VEH_1001 → calling ML predictor...
[CREATION] Task TASK_1 → offloaded=true, target=rsu

[ASSIGN] Thread started.
[ASSIGN] TASK_1 → RSU_2 (RSU) | exec=1245.67 ms
[ASSIGN] TASK_2 → FAILED (no capacity)

[RELEASE] Thread started.
[RELEASE] TASK_1 completed on RSU_2 | simExec=1245.67 ms | wallTime=1260 ms | totalLatency=1312 ms

--- Scheduler Statistics ---
  Total Assigned:    45
  Total Completed:   42
  Total Failed:      3
  Avg Latency:       1523.45 ms
  Cloud Assignments: 12
  RSU Assignments:   28
  Vehicle (local):   5

--- Infrastructure Health ---
  Cloud  : CPU=78.5%, RAM=65.2%, Health=0.213
  RSU_1  : CPU=92.1%, RAM=71.0%, Health=0.082
  ...
```

---

## 10. Research Contributions & Novelties

### 10.1 ML-Assisted Offloading Decision
- Integrates external ML model for intelligent offload target selection
- Gracefully degrades to rule-based heuristics if service unavailable
- Captures vehicular context (mobility, signal, criticality)

### 10.2 Multi-Tier TOPSIS Scheduling
- First application of TOPSIS MCDM to VEC task scheduling
- Considers four dimensions: compute capacity, health, latency, execution time
- Weighted preference matrix enables tuning for different QoS requirements

### 10.3 Fallback Chain Robustness
- Hierarchical scheduling cascade prevents task rejection
- Reoptimizes offload target when preferred tier saturated
- Maintains service continuity under resource contention

### 10.4 Manual Time Modeling
- Realistic wall-clock simulation without artificial acceleration
- Enables accurate event ordering and concurrency effects
- Validates scheduler behavior under realistic timing

---

## 11. Future Enhancements

1. **Mobility Prediction**: Track vehicle trajectory and pre-migrate tasks
2. **Energy Modeling**: Include battery drain in scheduling optimization
3. **Network Simulation**: Integrate actual network simulator (OMNeT++, ns-3)
4. **Machine Learning Feedback Loop**: Update ML model using simulator observations
5. **Multi-Objective Optimization**: Pareto frontier analysis for latency-energy tradeoffs
6. **Vehicular Platooning**: Special scheduling for coordinated vehicle groups

---

## 12. References

1. Hwang, C. L., & Yoon, K. (1981). *Multiple Attribute Decision Making Methods and Applications*. Springer-Verlag.
2. Dolui, K., & Datta, S. K. (2017). IoT Data Preprocessing: Features and Functionalities. arXiv preprint arXiv:1707.01509.
3. Mach, P., & Becvar, Z. (2017). Mobile edge computing: A survey on architecture and computation offloading. IEEE Communications Surveys & Tutorials, 19(3), 1628-1656.
4. Lai, P., He, Q., Abdelzaher, T., & Ergin, M. A. (2013). Optimal scheduling of requests with tightly-constrained deadlines and multiple deadlines. IEEE INFOCOM 2013.
5. Lin, K., Xia, F., Wang, W., Song, X., & Clarke, S. (2017). A Survey on Emerging Threats in Cybersecurity. Journal of Computer Research and Development, 54(10), 2298.

---

## 13. Appendix: Class Hierarchy

```
org.fog.test.VEC
├── StartSimulation.java              (entry point)
├── infrastructure/
│   ├── ComputeNode.java              (abstract base)
│   ├── CloudServer.java              (concrete)
│   ├── RSUServer.java                (concrete)
│   └── Vehicle.java                  (concrete)
├── task/
│   ├── Task.java                     (tuple model)
│   ├── MLOffloadPredictor.java       (ML client)
│   ├── TaskCreationThread.java       (Thread 1)
│   ├── TaskAssignmentThread.java     (Thread 2)
│   └── TaskReleaseThread.java        (Thread 3)
└── scheduler/
    └── TOPSISScheduler.java          (MCDM scheduler)
```

---

**Document Version**: 1.0  
**Last Updated**: March 3, 2026  
**Simulation Framework**: Java Concurrent Framework  
**Target Publication**: IEEE Vehicular Networks & Edge Computing Track

