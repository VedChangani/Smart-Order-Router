# Smart Order Router (SOR) Simulator

A distributed stock-trading simulation built in **Java 21** that models how a Smart Order Router (SOR) evaluates multiple exchanges, selects the best available prices, splits orders across venues when necessary, and aggregates the resulting executions into a single execution report.

The system consists of a trading client, a smart order router, and multiple independent exchange servers communicating through **TCP sockets** using a lightweight **newline-delimited JSON protocol**.

> **Note:** This is a simulation environment. It does not connect to live exchanges, real market data, or real trading infrastructure.

---

## Overview

In a fragmented market, the same security can be available at different prices and quantities across multiple trading venues.

A Smart Order Router sits between the trader and those venues. Instead of sending an order directly to one exchange, it can:

1. Query multiple venues for available liquidity.
2. Compare their prices.
3. Select the most favorable execution opportunities.
4. Split a large order across multiple exchanges when a single venue cannot fill it.
5. Execute the allocated quantities.
6. Aggregate all fills into one execution report.

This project simulates that workflow using independent TCP processes.

```text
                         TCP / JSON
┌─────────────────┐                      ┌─────────────────────┐
│  Trading Client │ ───────────────────► │ Smart Order Router │
└─────────────────┘                      └──────────┬──────────┘
                                                    │
                                      TCP / JSON    │    TCP / JSON
                                         ┌──────────┼──────────┐
                                         ▼          ▼          ▼
                                   ┌──────────┐ ┌──────────┐ ┌──────────┐
                                   │   NYSE   │ │  NASDAQ  │ │   BSE    │
                                   │ Exchange │ │ Exchange │ │ Exchange │
                                   └──────────┘ └──────────┘ └──────────┘
```

---

## Key Features

- **Smart price routing** across multiple simulated exchanges
- **Order splitting** when liquidity is insufficient at a single venue
- **Parallel quote collection** from exchanges using `ExecutorService`
- **Concurrent client handling** at the router and exchange servers
- **Persistent client-to-router TCP connection**
- **Short-lived router-to-exchange connections**
- **Custom JSON application-layer protocol**
- **Request validation** before business logic execution
- **Partial-fill handling**
- **Graceful degradation** when an exchange becomes unavailable
- **Socket and router-level timeouts**
- **Thread-safe in-memory exchange inventory**
- **Aggregated execution reports** with per-exchange fills
- **Externalized `.properties` configuration**
- **Single fat JAR** for running all system components

---

## System Architecture

The application is divided into three runtime roles and a shared module.

### Trading Client

The trading client is a console application used to submit orders.

It:

- Establishes one persistent TCP connection to the router.
- Accepts `BUY` and `SELL` commands.
- Sends an `ORDER_REQUEST`.
- Waits for an `EXECUTION_REPORT`.
- Displays the execution details and individual fills.

### Smart Order Router

The router is the core of the system.

It acts as:

- a **TCP server** for trading clients
- a **TCP client** for the configured exchanges

For every incoming order, the router:

1. Validates the request.
2. Queries all configured exchanges in parallel.
3. Discards failed or zero-liquidity responses.
4. Sorts available venues according to the order side.
5. Allocates the requested quantity across the best venues.
6. Sends execution requests to the selected exchanges.
7. Aggregates the actual fills.
8. Returns one final `ExecutionReport` to the client.

### Exchange Servers

Each exchange runs as an independent process with its own in-memory inventory.

An exchange supports two operations:

- `QUERY_REQUEST` — report the current price and available quantity.
- `EXECUTE_REQUEST` — execute as much of the requested order as the exchange can fill.

Each exchange can therefore have different prices and liquidity for the same symbol.

### Common Module

The common package contains functionality shared across all processes:

- DTOs
- protocol definitions
- JSON serialization
- message parsing
- validation
- configuration
- logging

---

## Order Routing Logic

The routing strategy is deliberately simple and deterministic.

### BUY orders

For a `BUY`, the router prefers the **lowest price**.

```text
Cheapest ───────────────────────────────► Most expensive

NASDAQ        NYSE        BSE
2743.60       2745.10     2746.80
```

The router consumes liquidity from the cheapest venue first and moves to the next venue only when necessary.

### SELL orders

For a `SELL`, the router prefers the **highest price**.

```text
Highest ────────────────────────────────► Lowest

NASDAQ        NYSE        BSE
246.10        245.75      245.40
```

This maximizes the simulated execution price for the seller.

---

## Order Splitting

Each exchange has finite liquidity.

For a symbol, an exchange maintains:

- `price`
- `availableQuantity`
- `maxCapacity`

For a `BUY`, the available quantity represents how many shares the exchange can sell.

For a `SELL`, the available quantity is constrained by the exchange's remaining capacity:

```text
sellCapacity = maxCapacity - availableQuantity
```

### Example

Suppose a `BUY GOOG 400` order arrives:

```text
NASDAQ → 200 @ 2743.60
NYSE   → 150 @ 2745.10
BSE    → 120 @ 2746.80
```

No single exchange can fill all 400 shares.

The router therefore allocates:

```text
NASDAQ → 200
NYSE   → 150
BSE    →  50
----------------
Total   → 400
```

The final execution report contains all three fills and calculates the weighted average execution price.

---

## Execution Flow

A complete order follows this path:

```text
Client
  │
  │ ORDER_REQUEST
  ▼
Router
  │
  ├────────── QUERY_REQUEST ──────────► NYSE
  │◄───────── QUERY_RESPONSE ─────────┘
  │
  ├────────── QUERY_REQUEST ──────────► NASDAQ
  │◄───────── QUERY_RESPONSE ─────────┘
  │
  ├────────── QUERY_REQUEST ──────────► BSE
  │◄───────── QUERY_RESPONSE ─────────┘
  │
  │ Select best prices
  │ Allocate quantity
  │
  ├──────── EXECUTE_REQUEST ──────────► Best venue
  │◄─────── EXECUTE_RESPONSE ─────────┘
  │
  ├──────── EXECUTE_REQUEST ──────────► Next venue
  │◄─────── EXECUTE_RESPONSE ─────────┘
  │
  │ Aggregate fills
  │
  │ EXECUTION_REPORT
  ▼
Client
```

---

## Communication Protocol

All network messages use a common JSON envelope:

```json
{
  "type": "ORDER_REQUEST",
  "payload": "{\"clientId\":\"CLIENT-1\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":100}"
}
```

The outer message identifies the message type, while the `payload` contains the serialized DTO.

### Message Types

| Message | Direction | Purpose |
|---|---|---|
| `ORDER_REQUEST` | Client → Router | Submit a trading order |
| `EXECUTION_REPORT` | Router → Client | Return final execution result |
| `QUERY_REQUEST` | Router → Exchange | Request price and available liquidity |
| `QUERY_RESPONSE` | Exchange → Router | Return quote information |
| `EXECUTE_REQUEST` | Router → Exchange | Request an actual execution |
| `EXECUTE_RESPONSE` | Exchange → Router | Return execution result |
| `ERROR` | Either direction | Report invalid or unsupported requests |

Messages are **newline-delimited JSON**, allowing each socket to process one complete message per line.

---

## Concurrency Model

Concurrency is used at multiple levels of the system.

### Router

The router accepts client connections using an `ExecutorService`.

Each connected trading client is handled independently, allowing multiple clients to interact with the router concurrently.

### Parallel Exchange Queries

When processing an order, exchange quote requests are dispatched concurrently rather than querying exchanges sequentially.

This allows the router to collect quotes without making the total query time depend linearly on the number of exchanges.

### Exchange Servers

Each exchange also uses an executor-backed client handler, allowing multiple router connections to be served concurrently.

### Thread-Safe Inventory

Inventory mutations are synchronized at the individual `InventoryItem` level so concurrent executions against the same symbol cannot update its quantity inconsistently.

---

## Failure Handling

The system is designed to continue operating when individual components fail.

### Exchange Timeout

Router-to-exchange connections use both connection and read timeouts.

A non-responsive exchange therefore cannot block the router indefinitely.

### Exchange Unavailable

When an exchange is unreachable:

```text
NYSE       ✓
NASDAQ     ✓
BSE        ✗ unavailable
```

The router excludes the failed venue and continues using the exchanges that responded successfully.

### Partial Fill

When total available liquidity is lower than the requested quantity, the router returns:

```text
PARTIALLY_FILLED
```

instead of treating the order as an application error.

For example:

```text
Requested: 1200
Executed:  1100
Status:    PARTIALLY_FILLED
```

### No Liquidity

When no reachable exchange can execute the order, the result is:

```text
REJECTED
```

with a descriptive message.

### Router Unavailable

The trading client handles connection failures cleanly and exits without exposing an uncontrolled stack trace.

---

## Project Structure

```text
src/
└── main/
    ├── java/
    │   └── com/
    │       └── sor/
    │           ├── client/
    │           │   ├── RouterConnector.java
    │           │   └── TradingClient.java
    │           │
    │           ├── common/
    │           │   ├── config/
    │           │   ├── model/
    │           │   ├── protocol/
    │           │   └── util/
    │           │
    │           ├── exchange/
    │           │   ├── ExchangeClientHandler.java
    │           │   ├── ExchangeServer.java
    │           │   ├── ExchangeService.java
    │           │   ├── Inventory.java
    │           │   └── InventoryItem.java
    │           │
    │           └── router/
    │               ├── Allocation.java
    │               ├── ExchangeConnector.java
    │               ├── ExchangeQueryService.java
    │               ├── ExecutionEngine.java
    │               ├── OrderAllocator.java
    │               ├── RouterClientHandler.java
    │               └── SmartOrderRouter.java
    │
    └── resources/
        ├── router.properties
        ├── NYSE.properties
        ├── NASDAQ.properties
        ├── BSE.properties
        └── logging.properties
```
## Getting Started

### Prerequisites

Make sure the following are installed:

- Java 21+
- Maven 3.9+

Verify:

```bash
java -version
mvn -version
```

---

## Build

From the project root:

```bash
mvn clean package
```

This creates:

```text
target/sor-simulator.jar
```

The JAR contains all application components and their required dependencies.

---

## Running the System

The simplest setup uses five terminals.

### 1. Start NYSE

```bash
java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer NYSE
```

### 2. Start NASDAQ

```bash
java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer NASDAQ
```

### 3. Start BSE

```bash
java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer BSE
```

### 4. Start the Smart Order Router

Start the router after the exchanges are running:

```bash
java -cp target/sor-simulator.jar com.sor.router.SmartOrderRouter router
```

### 5. Start a Trading Client

```bash
java -cp target/sor-simulator.jar com.sor.client.TradingClient localhost 6000 CLIENT-1
```

You can launch additional clients in separate terminals to demonstrate concurrent client handling.

---

## Trading Client

Once connected, the client accepts:

```text
BUY <SYMBOL> <QUANTITY>
SELL <SYMBOL> <QUANTITY>
HELP
EXIT
```

Examples:

```text
BUY AAPL 100
```

```text
SELL TSLA 200
```

A successful order produces an execution report containing:

- Order ID
- Client ID
- Symbol
- Side
- Requested quantity
- Executed quantity
- Execution status
- Per-exchange fills
- Average execution price
- Total trade value
- Execution message

---

## Configuration

The default router configuration is:

```properties
router.port=6000
router.exchanges=NYSE:localhost:7001,NASDAQ:localhost:7002,BSE:localhost:7003
router.connectTimeoutMs=3000
router.readTimeoutMs=4000
router.queryTimeoutMs=4000
```

Each exchange has its own configuration.

Example:

```properties
exchange.name=NYSE
exchange.port=7001
inventory=AAPL:189.50:400:800,GOOG:2745.10:150:300,MSFT:415.20:600:1000
```

Inventory entries follow:

```text
SYMBOL:price:availableQuantity:maxCapacity
```

The application can load configuration either from the classpath or from an external `.properties` file.

For example:

```bash
java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer /path/to/exchange.properties
```

This makes it possible to create different simulated market conditions without changing the Java source code.

## Project Highlights

This project demonstrates several practical systems concepts in one application:

```text
Networking
    │
    ├── TCP client/server communication
    ├── Connection lifecycle management
    └── Timeout handling
    │
Concurrency
    │
    ├── Concurrent client sessions
    ├── Parallel exchange queries
    └── Thread-safe inventory mutation
    │
Application Protocol
    │
    ├── Message envelopes
    ├── JSON serialization
    ├── Request validation
    └── Error responses
    │
Trading Logic
    │
    ├── Best-price selection
    ├── Liquidity-aware allocation
    ├── Order splitting
    └── Execution aggregation
```
