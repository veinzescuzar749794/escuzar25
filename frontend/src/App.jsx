import { useState, useEffect, useCallback } from 'react'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export default function App() {
  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])

  const [selectedProductId, setSelectedProductId] = useState('P100')
  const [inputQuantity, setInputQuantity] = useState(1)
  const [cart, setCart] = useState([])

  const [orderResult, setOrderResult] = useState(null)
  const [errorMessage, setErrorMessage] = useState(null)
  const [actionSuccess, setActionSuccess] = useState(null)

  const [isSubmittingOrder, setIsSubmittingOrder] = useState(false)
  const [cancellingOrderId, setCancellingOrderId] = useState(null)
  const [isLoadingData, setIsLoadingData] = useState(false)

  // Fetch live dashboard data
  const fetchData = useCallback(async () => {
    setIsLoadingData(true)
    try {
      const [invRes, ordRes, notifRes] = await Promise.all([
        fetch(`${API_BASE}/api/inventory`),
        fetch(`${API_BASE}/api/orders`),
        fetch(`${API_BASE}/api/notifications`),
      ])

      if (invRes.ok) {
        const invData = await invRes.json()
        setInventory(invData)
        if (invData.length > 0 && !selectedProductId) {
          setSelectedProductId(invData[0].productId)
        }
      }

      if (ordRes.ok) {
        const ordData = await ordRes.json()
        setOrders(ordData)
      }

      if (notifRes.ok) {
        const notifData = await notifRes.json()
        setNotifications(notifData)
      }
    } catch (err) {
      console.error('Failed to load dashboard data:', err)
    } finally {
      setIsLoadingData(false)
    }
  }, [selectedProductId])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // Cart Management
  function handleAddToCart(e) {
    e.preventDefault()
    setErrorMessage(null)
    const qty = Number(inputQuantity)
    if (qty <= 0) return

    const selectedProduct = inventory.find((p) => p.productId === selectedProductId)
    const productName = selectedProduct ? selectedProduct.name : selectedProductId

    setCart((prevCart) => {
      const existingIndex = prevCart.findIndex((item) => item.productId === selectedProductId)
      if (existingIndex >= 0) {
        const updated = [...prevCart]
        updated[existingIndex] = {
          ...updated[existingIndex],
          quantity: updated[existingIndex].quantity + qty,
        }
        return updated
      }
      return [...prevCart, { productId: selectedProductId, name: productName, quantity: qty }]
    })

    setInputQuantity(1)
  }

  function handleUpdateCartQuantity(productId, newQty) {
    if (newQty <= 0) {
      handleRemoveFromCart(productId)
      return
    }
    setCart((prev) =>
      prev.map((item) => (item.productId === productId ? { ...item, quantity: newQty } : item))
    )
  }

  function handleRemoveFromCart(productId) {
    setCart((prev) => prev.filter((item) => item.productId !== productId))
  }

  function handleClearCart() {
    setCart([])
  }

  // Multi-item Order Placement
  async function handleSubmitOrder() {
    if (cart.length === 0) return

    setIsSubmittingOrder(true)
    setErrorMessage(null)
    setOrderResult(null)
    setActionSuccess(null)

    const payload = {
      items: cart.map((i) => ({ productId: i.productId, quantity: i.quantity })),
    }

    try {
      const response = await fetch(`${API_BASE}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

      const data = await response.json()

      if (!response.ok) {
        setErrorMessage(data.message || `Order request failed with status ${response.status}`)
      } else {
        setOrderResult(data)
        if (data.status === 'CONFIRMED') {
          setCart([]) // Clear cart only on successful confirmation
          setActionSuccess(`Order #${data.orderId} confirmed successfully!`)
        } else {
          setErrorMessage(`Order #${data.orderId || ''} rejected: ${data.reason}`)
        }
      }
    } catch (err) {
      setErrorMessage(err.message)
    } finally {
      setIsSubmittingOrder(false)
      fetchData() // Refresh live inventory, orders, and notifications
    }
  }

  // Order Cancellation & Restock
  async function handleCancelOrder(orderId) {
    if (!window.confirm(`Are you sure you want to cancel Order #${orderId}? Reserved items will be restocked.`)) {
      return
    }

    setCancellingOrderId(orderId)
    setErrorMessage(null)
    setActionSuccess(null)

    try {
      const response = await fetch(`${API_BASE}/api/orders/${orderId}/cancel`, {
        method: 'POST',
      })

      const data = await response.json()

      if (!response.ok) {
        setErrorMessage(data.message || `Failed to cancel order: status ${response.status}`)
      } else {
        setActionSuccess(`Order #${orderId} was cancelled and reserved stock was returned.`)
      }
    } catch (err) {
      setErrorMessage(err.message)
    } finally {
      setCancellingOrderId(null)
      fetchData() // Refresh live inventory and order history
    }
  }

  return (
    <div className="container">
      <header className="header">
        <div className="header-content">
          <h1>Modular Monolith Store</h1>
          <span className="subtitle">Lab 2: Order, Inventory & Notification Modules</span>
        </div>
        <button className="refresh-btn" onClick={fetchData} disabled={isLoadingData}>
          {isLoadingData ? 'Refreshing...' : '🔄 Refresh Data'}
        </button>
      </header>

      {actionSuccess && <div className="alert success">{actionSuccess}</div>}
      {errorMessage && <div className="alert error">{errorMessage}</div>}

      <div className="dashboard-grid">
        {/* Left Column: Ordering & Live Inventory */}
        <div className="column">
          {/* Cart & Multi-Item Order Section */}
          <section className="card">
            <h2>🛒 Multi-Item Cart & Order</h2>
            <form onSubmit={handleAddToCart} className="add-to-cart-form">
              <div className="form-group">
                <label>Select Product</label>
                <select
                  value={selectedProductId}
                  onChange={(e) => setSelectedProductId(e.target.value)}
                >
                  {inventory.map((item) => (
                    <option key={item.productId} value={item.productId}>
                      {item.productId} — {item.name} (Stock: {item.stock})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group qty-group">
                <label>Quantity</label>
                <input
                  type="number"
                  min="1"
                  value={inputQuantity}
                  onChange={(e) => setInputQuantity(Math.max(1, Number(e.target.value)))}
                />
              </div>

              <button type="submit" className="btn btn-secondary">
                Add to Cart
              </button>
            </form>

            {/* Cart Table */}
            <div className="cart-area">
              <h3>Current Cart ({cart.length} unique item{cart.length === 1 ? '' : 's'})</h3>
              {cart.length === 0 ? (
                <p className="empty-hint">Your cart is empty. Add products above to build a multi-item order.</p>
              ) : (
                <>
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Product</th>
                        <th>Qty</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {cart.map((item) => (
                        <tr key={item.productId}>
                          <td>
                            <strong>{item.productId}</strong>
                            <div className="item-subname">{item.name}</div>
                          </td>
                          <td>
                            <div className="qty-controls">
                              <button
                                type="button"
                                className="btn-icon"
                                onClick={() => handleUpdateCartQuantity(item.productId, item.quantity - 1)}
                              >
                                -
                              </button>
                              <span>{item.quantity}</span>
                              <button
                                type="button"
                                className="btn-icon"
                                onClick={() => handleUpdateCartQuantity(item.productId, item.quantity + 1)}
                              >
                                +
                              </button>
                            </div>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="btn-link danger"
                              onClick={() => handleRemoveFromCart(item.productId)}
                            >
                              Remove
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>

                  <div className="cart-actions">
                    <button type="button" className="btn btn-outline" onClick={handleClearCart}>
                      Clear Cart
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={handleSubmitOrder}
                      disabled={isSubmittingOrder}
                    >
                      {isSubmittingOrder ? 'Validating & Ordering...' : `Submit Order (${cart.reduce((s, i) => s + i.quantity, 0)} items)`}
                    </button>
                  </div>
                </>
              )}
            </div>

            {/* Order Result Banner */}
            {orderResult && (
              <div
                className={`result-box ${orderResult.status === 'CONFIRMED' ? 'confirmed' : 'rejected'}`}
              >
                <h4>Order #{orderResult.orderId || ''}: {orderResult.status}</h4>
                {orderResult.reason && <p className="reason-text">{orderResult.reason}</p>}
                {orderResult.items && orderResult.items.length > 0 && (
                  <div className="outcome-list">
                    <strong>Line Item Outcomes:</strong>
                    <ul>
                      {orderResult.items.map((it, idx) => (
                        <li key={idx}>
                          Product <code>{it.productId}</code>: <span className={`badge ${it.outcome === 'RESERVED' ? 'badge-green' : 'badge-red'}`}>{it.outcome}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </section>

          {/* Live Inventory Table */}
          <section className="card">
            <h2>📦 Live Inventory (Stock Dashboard)</h2>
            <p className="section-desc">
              Auto-refreshes on every order & cancellation. Items with stock below 5 are highlighted as Low Stock.
            </p>
            <table className="data-table inventory-table">
              <thead>
                <tr>
                  <th>Product ID</th>
                  <th>Product Name</th>
                  <th>Current Stock</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {inventory.map((item) => {
                  const isOut = item.stock <= 0
                  const isLow = item.stock > 0 && item.stock < 5
                  const rowClass = isOut ? 'row-out-of-stock' : isLow ? 'row-low-stock' : ''

                  return (
                    <tr key={item.productId} className={rowClass}>
                      <td><code>{item.productId}</code></td>
                      <td>{item.name}</td>
                      <td><strong>{item.stock}</strong></td>
                      <td>
                        {isOut ? (
                          <span className="badge badge-red">Out of Stock</span>
                        ) : isLow ? (
                          <span className="badge badge-amber">Low Stock (&lt; 5)</span>
                        ) : (
                          <span className="badge badge-green">In Stock</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </section>
        </div>

        {/* Right Column: Order History & Notifications */}
        <div className="column">
          {/* Notifications Feed */}
          <section className="card">
            <h2>🔔 Activity & Notification Feed</h2>
            <p className="section-desc">
              In-monolith domain events published by Order and Inventory, consumed by Notification module.
            </p>
            <div className="notifications-feed">
              {notifications.length === 0 ? (
                <p className="empty-hint">No notifications recorded yet.</p>
              ) : (
                notifications.map((n) => {
                  const isConfirmed = n.message.includes('confirmed')
                  const isRejected = n.message.includes('rejected')
                  const isReorder = n.message.includes('Reorder') || n.message.includes('Low stock')
                  const itemClass = isConfirmed ? 'notif-confirmed' : isRejected ? 'notif-rejected' : isReorder ? 'notif-reorder' : ''

                  return (
                    <div key={n.notificationId} className={`notification-item ${itemClass}`}>
                      <div className="notif-message">
                        {isConfirmed && '✅ '}
                        {isRejected && '❌ '}
                        {isReorder && '⚠️ '}
                        {n.message}
                      </div>
                      <div className="notif-time">
                        {new Date(n.createdAt).toLocaleTimeString()}
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </section>

          {/* Order History */}
          <section className="card">
            <h2>📜 Order History</h2>
            <p className="section-desc">
              History of all orders. Cancel returns reserved quantities to inventory.
            </p>
            <div className="orders-list">
              {orders.length === 0 ? (
                <p className="empty-hint">No orders placed yet.</p>
              ) : (
                orders.map((ord) => (
                  <div key={ord.orderId} className={`order-card status-${ord.status.toLowerCase()}`}>
                    <div className="order-header">
                      <div>
                        <strong>Order #{ord.orderId}</strong>
                        <span className={`badge badge-${ord.status === 'CONFIRMED' ? 'green' : ord.status === 'CANCELLED' ? 'gray' : 'red'}`}>
                          {ord.status}
                        </span>
                      </div>
                      <div className="order-date">
                        {new Date(ord.createdAt).toLocaleTimeString()}
                      </div>
                    </div>

                    {ord.reason && <p className="order-reason">{ord.reason}</p>}

                    <div className="order-items-summary">
                      <span className="label">Items:</span>
                      <ul>
                        {ord.items.map((it, idx) => (
                          <li key={idx}>
                            <code>{it.productId}</code> × {it.quantity}
                          </li>
                        ))}
                      </ul>
                    </div>

                    {ord.status === 'CONFIRMED' && (
                      <div className="order-actions">
                        <button
                          type="button"
                          className="btn btn-sm btn-danger"
                          disabled={cancellingOrderId === ord.orderId}
                          onClick={() => handleCancelOrder(ord.orderId)}
                        >
                          {cancellingOrderId === ord.orderId ? 'Restocking...' : 'Cancel Order'}
                        </button>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
