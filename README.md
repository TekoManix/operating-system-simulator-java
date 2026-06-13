# ICSI412 OS Simulator

A Java-based operating system simulator implementing core OS concepts: process scheduling, virtual memory with paging and swap, device I/O through a virtual file system, and inter-process messaging.

---

## Overview

The simulator models a complete OS kernel running on a simulated hardware layer. User processes run as Java threads and interact with the kernel exclusively through system calls (`OS.java`). The kernel handles scheduling, memory management, device I/O, and IPC — all coordinated through semaphore-based context switching.

---

## Architecture

| Layer | Class(es) | Responsibility |
|---|---|---|
| Hardware | `Hardware.java` | 1MB physical memory, 2-entry TLB, address translation |
| Kernel | `Kernel.java` | System call dispatch, page fault handling, resource cleanup |
| Scheduler | `Scheduler.java` | Priority queues, time-slicing, demotion policy |
| Memory | `VirtualToPhysicalMapping.java` | Per-process page table entries (virtual → physical/disk) |
| Devices | `VFS.java`, `Device.java` | Route I/O to `RandomDevice` or `FakeFileSystem` |
| IPC | `KernelMessage.java` | Copy-on-send message passing |
| Processes | `PCB.java`, `Process.java`, `UserlandProcess.java` | Process control block, thread lifecycle |

---

## Key Features

- **Priority scheduling** — three levels: realtime, interactive, background
- **Probabilistic dispatch** — realtime gets 60%, interactive 30%, background 10% (when all queues occupied)
- **Demotion policy** — after 5 consecutive quantum expirations without yielding, process drops one priority level
- **250ms time quantum** — enforced via Java `Timer`; voluntary `Sleep()` or `cooperate()` resets the timeout counter
- **Virtual memory** — 100 virtual pages per process (1KB pages), backed by 1MB physical memory
- **Paging with swap** — pages evicted to a swap file when physical memory is exhausted
- **2-entry TLB** — cleared on every context switch to enforce process isolation
- **Virtual File System** — device-type strings like `"random 42"` or `"file output.txt"` are parsed and routed to the correct device
- **Inter-process messaging** — messages are deep-copied on send; processes can block waiting for a message
- **Process isolation** — separate page tables, 10 open-device slots, and message queues per process

---

## Project Structure

```
Skeleton/OS/src/
├── Main.java                  # Entry point — calls OS.Startup(new Init())
├── OS.java                    # System call interface (static methods)
├── Kernel.java                # Kernel process — implements all system calls
├── Scheduler.java             # Priority queues, context switching, sleep/wake
├── Hardware.java              # Physical memory array, TLB, address translation
├── PCB.java                   # Process Control Block
├── Process.java               # Abstract base — semaphore-driven thread control
├── UserlandProcess.java       # Base class for all user processes
├── VirtualToPhysicalMapping.java  # Page table entry (physicalPage, diskPage)
├── KernelMessage.java         # IPC message (senderPid, targetPid, what, data[])
│
├── VFS.java                   # Virtual File System router
├── Device.java                # Device interface (Open/Close/Read/Write/Seek)
├── RandomDevice.java          # Seeded pseudo-random number generator device
├── FakeFileSystem.java        # Real-file-backed device (RandomAccessFile)
│
├── Init.java                  # Startup process — spawns all test processes
├── IdleProcess.java           # Minimal busy-wait idle process
├── HelloWorld.java            # Prints "Hello World" in a loop
├── GoodbyeWorld.java          # Prints "Goodbye World" in a loop
├── RealtimeHog.java           # Never yields — triggers demotion policy
├── RealtimeSleeper.java       # Realtime process that sleeps — never demoted
├── InteractiveProcess.java    # Interactive-priority loop with cooperate()
├── BackgroundProcess.java     # Background-priority loop with cooperate()
├── Ping.java                  # IPC test — sends messages to Pong
├── Pong.java                  # IPC test — replies to Ping
├── DeviceTestProcess.java     # Tests RandomDevice and FakeFileSystem via VFS
├── MemoryTestProcess.java     # Tests allocation, page faults, seg faults
└── MemoryTestProcess2.java    # Tests memory isolation between processes
```

---

## Build & Run

### IntelliJ IDEA

1. Open `Skeleton/OS/` as an IntelliJ project.
2. Run `Main.java` directly from the IDE.

### Command Line

```bash
# From Skeleton/OS/src/
javac *.java -d ../out/production/OS/

# Run from the output directory
cd ../out/production/OS/
java Main
```

---

## System Call Reference

All system calls go through `OS.java`. The kernel dispatches on `CallType`.

| Method | Description |
|---|---|
| `OS.Startup(UserlandProcess[])` | Boot the OS with initial processes |
| `OS.CreateProcess(UserlandProcess, Priority)` | Spawn a new process |
| `OS.GetPID()` | Return calling process's PID |
| `OS.Exit()` | Terminate calling process; free all memory and devices |
| `OS.Sleep(ms)` | Block for at least `ms` milliseconds |
| `OS.Open(String)` | Open device by descriptor string; returns userland device ID |
| `OS.Close(int id)` | Close device by userland ID |
| `OS.Read(int id, int size)` | Read `size` bytes from device |
| `OS.Write(int id, byte[] data)` | Write bytes to device |
| `OS.Seek(int id, int to)` | Seek device to position |
| `OS.SendMessage(KernelMessage)` | Send message to another process (deep copy) |
| `OS.WaitForMessage()` | Block until a message arrives; returns `KernelMessage` |
| `OS.GetPidByName(String)` | Look up PID by process class name |
| `OS.AllocateMemory(int size)` | Allocate virtual memory; returns virtual address or -1 |
| `OS.FreeMemory(int ptr, int size)` | Free previously allocated virtual memory |
| `OS.GetMapping(int virtualPage)` | Handle TLB miss (called by `Hardware`, not user code) |

---

## Test Processes

| Process | Priority | What it demonstrates |
|---|---|---|
| `RealtimeHog` | realtime | Demotion after 5 consecutive timeouts |
| `RealtimeSleeper` | realtime | Voluntary sleep prevents demotion |
| `InteractiveProcess` | interactive | Normal interactive scheduling |
| `BackgroundProcess` | background | Low-priority scheduling |
| `HelloWorld` / `GoodbyeWorld` | interactive | Basic cooperative multitasking |
| `Ping` / `Pong` | interactive | `SendMessage` / `WaitForMessage` round-trip |
| `DeviceTestProcess` | interactive | VFS open/read/write/seek/close for both device types |
| `MemoryTestProcess` | interactive | Allocation, write-back, page faults, expected seg fault on bad address |
| `MemoryTestProcess2` | interactive | Memory isolation — verifies another process cannot corrupt its pages |
