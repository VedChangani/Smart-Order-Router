# Smart Order Router (SOR) Simulator

A simulated distributed stock trading platform built for a Computer Networks
course project. It demonstrates TCP client/server programming, a custom
JSON application-layer protocol, concurrent request handling with
`ExecutorService`, and a real smart-order-routing algorithm (parallel
quoting, best-price selection, and order splitting across venues).

## 1. Architecture

```
                 TCP (JSON protocol)              TCP (JSON protocol)
  Trading Client  ------------------->  Smart Order Router  ------------------->  Exchange: NYSE
  Trading Client  ------------------->        (Router)      ------------------->  Exchange: NASDAQ
  Trading Client  ------------------->                      ------------------->  Exchange: BSE
```

* **Trading Client** (`com.sor.client`) — console app. Sends `BUY`/`SELL`
  orders to the Router over one persistent TCP connection and prints the
  `ExecutionReport` it gets back.
* **Smart Order Router** (`com.sor.router`) — a TCP **server** to clients and
  a TCP **client** to every exchange. For each order it:
  1. Queries every exchange **in parallel** for price/quantity.
  2. Picks the best-priced exchange(s) and **splits** the order across them
     if no single exchange can fill it alone.
  3. Sends `EXECUTE` requests to each chosen exchange.
  4. Reduces the results into one `ExecutionReport` for the client.
* **Exchange Server** (`com.sor.exchange`) — an independent TCP server, one
  process per exchange, each with its own in-memory `Inventory`
  (symbol → price / available quantity / capacity). Responds to `QUERY` and
  `EXECUTE` requests.
* **Common module** (`com.sor.common`) — shared DTOs (`model`), the wire
  protocol (`protocol`), configuration loading (`config`) and a logging
  helper (`util`), used by all three roles.

### Package layout

```
com.sor.common.model     OrderRequest, ExecutionReport, Fill, QueryRequest/Response,
                          ExecuteRequest/Response, OrderSide, OrderStatus
com.sor.common.protocol  MessageType, ProtocolMessage, MessageParser, JsonUtil,
                          ProtocolConstants, MessageValidator, ValidationException
com.sor.common.config    AppConfig, ExchangeEndpoint, RouterConfig
com.sor.common.util      LoggingConfig
com.sor.exchange          InventoryItem, Inventory, ExchangeService,
                          ExchangeClientHandler, ExchangeServer (main)
com.sor.router            ExchangeConnector, ExchangeQueryService, OrderAllocator,
                          Allocation, ExecutionEngine, RouterClientHandler,
                          SmartOrderRouter (main)
com.sor.client             RouterConnector, TradingClient (main)
```

## 2. Wire protocol

Every message on every socket is a single line of JSON — a `ProtocolMessage`
envelope with a `type` and a `payload` (the JSON of the actual DTO, so the
receiver knows exactly which class to parse it into):

```json
{"type":"ORDER_REQUEST","payload":"{\"clientId\":\"C1\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":100}"}
```

| type              | direction                | payload class    |
|-------------------|--------------------------|-------------------|
| `ORDER_REQUEST`   | Client → Router          | `OrderRequest`    |
| `EXECUTION_REPORT`| Router → Client          | `ExecutionReport` |
| `QUERY_REQUEST`   | Router → Exchange        | `QueryRequest`    |
| `QUERY_RESPONSE`  | Exchange → Router        | `QueryResponse`   |
| `EXECUTE_REQUEST` | Router → Exchange        | `ExecuteRequest`  |
| `EXECUTE_RESPONSE`| Exchange → Router        | `ExecuteResponse` |
| `ERROR`           | either direction         | `String` (reason) |

`MessageValidator` rejects malformed requests (missing symbol, non-positive
quantity, missing side, etc.) before they ever reach business logic, and the
handler replies with an `ERROR` message instead of crashing the connection.

## 3. Order splitting algorithm (Stage 7)

Each exchange models **limited liquidity on both sides**:

* `availableQuantity` — how much stock it currently holds and can *sell* to
  a client (fills client `BUY` orders).
* `maxCapacity - availableQuantity` — how much more it can *buy* from a
  client before running out of room (fills client `SELL` orders).

When the Router processes an order it:
1. Queries all exchanges in parallel (`ExchangeQueryService`, Stage 6) and
   keeps only the ones that responded successfully with quantity > 0.
2. Sorts them by best price — **ascending** for `BUY` (cheapest first),
   **descending** for `SELL` (highest first).
3. Greedily allocates the requested quantity across that sorted list,
   taking as much as each exchange can offer before moving to the next
   (`OrderAllocator`).
4. Sends one `EXECUTE_REQUEST` per allocation and aggregates the real fills
   into the final `ExecutionReport`, which reports `FILLED`,
   `PARTIALLY_FILLED`, or `REJECTED` depending on how much of the order
   actually got filled.

## 4. Failure handling (Stage 8)

* **Socket timeouts** — `ExchangeConnector` sets both a connect timeout and
  a read timeout per socket; a hung exchange cannot block the router
  forever.
* **Router-level timeout** — `ExchangeQueryService` also bounds each
  `Future.get(...)` with `router.queryTimeoutMs` as a second safety net.
* **Exchange unavailable** — a `ConnectException` / timeout for one exchange
  is caught, logged, and turned into a *failed* `QueryResponse` for just
  that exchange; the router keeps going with whichever exchanges did
  respond (graceful degradation — see Test Scenario 5 below).
* **Partial fills** — if the total liquidity across all reachable exchanges
  is less than the requested quantity, the order is reported
  `PARTIALLY_FILLED` with the real executed quantity, instead of throwing.
* **No liquidity anywhere** — if no exchange can fill any of the order, it
  is reported `REJECTED` with a clear reason, and the client is told why.
* **Client-side** — if the router itself is unreachable, `TradingClient`
  prints a clean message and exits instead of leaking a stack trace.

## 5. Build

Requires **Java 21** and **Maven**. From the project root:

```bash
mvn clean package
```

This produces a single runnable fat jar containing all three entry points
plus Jackson: `target/sor-simulator.jar`.

> Note: this jar bundles everything needed to run any of the three roles;
> you pick which one to run with the fully-qualified main class on the
> command line, as shown below.

## 6. Run

Open **5 terminals** in the project root (3 exchanges + 1 router + 1+ client).

```bash
# Terminal 1 — NYSE exchange
java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer NYSE

# Terminal 2 — NASDAQ exchange
java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer NASDAQ

# Terminal 3 — BSE exchange
java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer BSE

# Terminal 4 — Smart Order Router (start after the exchanges are up)
java -cp target/sor-simulator.jar com.sor.router.SmartOrderRouter router

# Terminal 5 — Trading Client
java -cp target/sor-simulator.jar com.sor.client.TradingClient localhost 6000 CLIENT-1
```

Bundled sample configs (in `src/main/resources`, packaged into the jar):

* `router.properties` — router port `6000`, exchanges `NYSE:localhost:7001`,
  `NASDAQ:localhost:7002`, `BSE:localhost:7003`.
* `NYSE.properties`, `NASDAQ.properties`, `BSE.properties` — each exchange's
  port and starting inventory (`SYMBOL:price:availableQuantity:maxCapacity`),
  with slightly different prices per exchange so best-price routing has
  something real to choose between.

You can also point any role at your own properties file instead of a
bundled name, e.g. `java -cp target/sor-simulator.jar com.sor.exchange.ExchangeServer /path/to/my-exchange.properties`.

### Running without building a jar first

```bash
mvn exec:java -Dexec.mainClass=com.sor.exchange.ExchangeServer -Dexec.args=NYSE
mvn exec:java -Dexec.mainClass=com.sor.router.SmartOrderRouter -Dexec.args=router
mvn exec:java -Dexec.mainClass=com.sor.client.TradingClient -Dexec.args="localhost 6000 CLIENT-1"
```

### Trading Client commands

```
BUY <SYMBOL> <QUANTITY>    e.g. BUY AAPL 500
SELL <SYMBOL> <QUANTITY>   e.g. SELL TSLA 200
HELP
EXIT
```

## 7. Test scenarios (Stage 9)

These were run and verified against the bundled sample configuration.

**1. Simple single-exchange fill**
`BUY AAPL 100` → NYSE quotes the best price (189.50) with plenty of
inventory, so the whole order fills there. Status `FILLED`, one fill line.

**2. Order split across three exchanges (BUY)**
`BUY GOOG 400` → no single exchange has 400 shares (NYSE 150, NASDAQ 200,
BSE 120 = 470 total). The router fills NASDAQ (cheapest, 200) → NYSE (150)
→ BSE (50) until 400 shares are filled. Status `FILLED`, three fill lines,
correctly weighted average price.

**3. Order split across three exchanges (SELL)**
`SELL TSLA 500` → sorted by *highest* price this time (NASDAQ 246.10 → NYSE
245.75 → BSE 245.40), filled up to each exchange's remaining buy capacity.
Status `FILLED`, three fill lines.

**4. Unknown symbol**
`BUY FAKESYM 10` → every exchange reports "symbol not found", so the router
returns `REJECTED` with a clear message instead of guessing.

**5. Exchange goes down mid-session (graceful degradation)**
Kill the BSE process, then send `BUY MSFT 100`. The router's query to BSE
fails (connection refused), is logged as a warning, and is simply excluded
— the order still fills normally from NYSE/NASDAQ. The client sees a normal
`FILLED` report with no indication anything went wrong on the backend.

**6. Genuine partial fill**
With BSE still down, request more of a symbol than NYSE + NASDAQ combined
can supply, e.g. `BUY MSFT 1200` when only 1100 remain between them.
Result: `PARTIALLY_FILLED`, `executedQuantity=1100`, with fills from both
remaining exchanges and a message stating exactly how much filled.

**7. Router unreachable**
Start `TradingClient` before the router is up (or after killing it).
It prints `Could not connect to router at localhost:6000 - Connection
refused` and exits cleanly — no stack trace, no hang.

## 8. Design notes / assumptions

* Each exchange models **finite liquidity on both sides** of the market via
  `availableQuantity` (stock on hand, limits client `BUY`s) and
  `maxCapacity` (warehouse ceiling, limits client `SELL`s) — this is what
  makes order splitting a real necessity rather than a decorative feature.
* `ExchangeConnector` opens a fresh, short-lived socket per query/execute
  call. This keeps the router's TCP-client side simple and stateless and
  makes it trivial to run many exchange calls concurrently from
  `ExchangeQueryService`'s thread pool without any shared-connection
  synchronization.
* A Trading Client connection stays open for the whole session — one
  `ORDER_REQUEST`/`EXECUTION_REPORT` pair per line — so a user can place
  several orders without reconnecting each time.
* `InventoryItem`'s mutating methods are `synchronized` so concurrent
  `EXECUTE_REQUEST`s for the same symbol (from different router threads or
  different router instances) never race.
* Logging uses `java.util.logging` throughout, configured once at startup
  from `logging.properties`.
