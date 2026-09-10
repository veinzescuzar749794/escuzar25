import { useState } from 'react'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// Matches the seed data in sql/schema.sql. In a real app you'd fetch this
// list from the backend instead of hardcoding it.
const PRODUCTS = [
  { productId: 'P100', label: 'P100 -- Wireless Mouse' },
  { productId: 'P200', label: 'P200 -- Mechanical Keyboard' },
  { productId: 'P300', label: 'P300 -- USB-C Hub' },
]

export default function App() {
  const [productId, setProductId] = useState(PRODUCTS[0].productId)
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const response = await fetch(`${API_BASE}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      })

      const data = await response.json()

      if (!response.ok) {
        setError(data.message || `Request failed with status ${response.status}`)
      } else {
        setResult(data)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page">
      <h1>Place an Order</h1>

      <form onSubmit={handleSubmit} className="order-form">
        <label>
          Product
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            {PRODUCTS.map((p) => (
              <option key={p.productId} value={p.productId}>
                {p.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          Quantity
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </label>

        <button type="submit" disabled={loading}>
          {loading ? 'Submitting...' : 'Submit Order'}
        </button>
      </form>

      <section className="result-area">
        {error && <p className="error">Error: {error}</p>}

        {result && (
          <div className={result.status === 'CONFIRMED' ? 'result confirmed' : 'result rejected'}>
            <p className="status">{result.status}</p>
            {result.reason && <p className="reason">{result.reason}</p>}
            {result.inventory && (
              <p className="inventory">
                {result.inventory.name} -- remaining stock: {result.inventory.stock}
              </p>
            )}
          </div>
        )}
      </section>
    </main>
  )
}
