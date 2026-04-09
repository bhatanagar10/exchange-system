# New Trading Strategies

## Overview
Two new strategies have been added to keep the exchange active and balanced:

1. **MARKET_ACTIVITY** - Keeps exchange running actively, doesn't care about profit/loss
2. **ORDER_BOOK_BALANCER** - Balances buy and sell orders in the order book

---

## 1. MARKET_ACTIVITY Strategy

### Purpose
Keeps the exchange active by placing orders frequently, regardless of profit or loss considerations.

### Behavior
- **Order Placement**: Places orders frequently with random sizes and prices
- **Order Cancellation**: Cancels orders frequently (40% chance per cycle) to maintain activity
- **Price Strategy**: Uses wider spreads (~1%) to ensure orders get placed
- **Order Size**: Random between min and max order size
- **Risk Management**: Only checks basic balance availability, doesn't enforce strict risk limits

### Key Features
- ✅ Doesn't care about profit/loss
- ✅ High-frequency order placement
- ✅ Frequent order cancellation to keep things moving
- ✅ Random order sizes and prices
- ✅ Keeps exchange active

### Use Case
Use this strategy when you want to ensure the exchange has constant activity, regardless of trading outcomes.

---

## 2. ORDER_BOOK_BALANCER Strategy

### Purpose
Analyzes order book imbalance and places orders to balance buy and sell orders.

### Behavior
- **Imbalance Detection**: 
  - Checks order count difference (buy orders vs sell orders)
  - Checks depth imbalance (total buy quantity vs sell quantity)
  - Uses imbalance threshold of 10% or 5+ order count difference
  
- **Balancing Logic**:
  - **If more buy orders** (positive imbalance or 5+ more buy orders):
    - Places **SELL orders** to balance
    - Price: Slightly above best bid (competitive)
    - Size: Proportional to imbalance (minimum 10 units)
  
  - **If more sell orders** (negative imbalance or 5+ more sell orders):
    - Places **BUY orders** to balance
    - Price: Slightly below best ask (competitive)
    - Size: Proportional to imbalance (minimum 10 units)
  
  - **If balanced**: Skips this cycle

### Key Features
- ✅ Analyzes order book imbalance (both count and depth)
- ✅ Places orders on the side that needs more liquidity
- ✅ Competitive pricing (at or near best bid/ask)
- ✅ Order size proportional to imbalance
- ✅ Maintains order book balance

### Metrics Used
- `bidOrderCount` vs `askOrderCount` - Number of buy vs sell orders
- `imbalance` - Depth imbalance (-1 to +1, positive = more buys)
- `bidDepth` vs `askDepth` - Total quantity on each side

### Use Case
Use this strategy when you notice the order book is imbalanced (e.g., more buy orders than sell orders) and want to maintain a balanced market.

---

## Bot Initialization

The system now creates **14 bots** with the following distribution:

- 2 × MATCHER
- 2 × AGGRESSIVE
- 2 × CONSERVATIVE
- 2 × TIMEOUT
- 2 × MARKET_MAKER
- 1 × TREND_FOLLOWING
- 1 × MEAN_REVERSION
- **1 × MARKET_ACTIVITY** (NEW)
- **1 × ORDER_BOOK_BALANCER** (NEW)

---

## Strategy Comparison

| Strategy | Profit Focus | Activity Level | Order Book Balance |
|----------|-------------|----------------|-------------------|
| MARKET_ACTIVITY | ❌ No | ⭐⭐⭐⭐⭐ Very High | ⚠️ Not Considered |
| ORDER_BOOK_BALANCER | ⚠️ Secondary | ⭐⭐⭐ Medium | ✅ Yes |
| MARKET_MAKER | ✅ Yes | ⭐⭐⭐⭐ High | ✅ Yes |
| MATCHER | ✅ Yes | ⭐⭐⭐ Medium | ⚠️ Not Considered |
| AGGRESSIVE | ✅ Yes | ⭐⭐⭐⭐ High | ⚠️ Not Considered |
| CONSERVATIVE | ✅ Yes | ⭐⭐ Low | ⚠️ Not Considered |
| TIMEOUT | ✅ Yes | ⭐⭐⭐ Medium | ⚠️ Not Considered |
| TREND_FOLLOWING | ✅ Yes | ⭐⭐ Low | ⚠️ Not Considered |
| MEAN_REVERSION | ✅ Yes | ⭐⭐ Low | ⚠️ Not Considered |

---

## Configuration

Both strategies use existing configuration parameters:
- `simulation.minOrderSize` - Minimum order size
- `simulation.maxOrderSize` - Maximum order size
- Order book metrics from `OrderBookAnalyzer`

---

## Logging

Both strategies log their actions with prefixes:
- `[MARKET_ACTIVITY]` - For market activity strategy logs
- `[ORDER_BOOK_BALANCER]` - For order book balancer strategy logs

Example logs:
```
[MARKET_ACTIVITY] Bot Bot-MarketActivity-1 placed BUY order: 25.50 @ $495.00 (keeping exchange active)
[ORDER_BOOK_BALANCER] Detected imbalance: 15 buy orders vs 8 sell orders, imbalance: 0.35. Placing SELL order to balance
[ORDER_BOOK_BALANCER] Bot Bot-OrderBookBalancer-1 placed SELL order: 45.00 @ $502.50 to balance order book
```

---

## Notes

1. **MARKET_ACTIVITY** bots may have negative P&L since they don't optimize for profit
2. **ORDER_BOOK_BALANCER** helps maintain market liquidity and balance
3. Both strategies respect basic balance checks but don't enforce strict risk limits
4. Both strategies integrate with circuit breaker for error protection
5. Order tracking is maintained for both strategies
