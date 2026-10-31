import { useCallback } from 'react'
import { api, Conflict, post } from '../api'
import { useLoad } from '../useLoad'

/** Cụm biển có nhiều mã cạnh tranh; quản trị viên chốt mã đúng hoặc loại cụm (FR-21). */
export default function Conflicts() {
  const { data, error, reload } = useLoad(useCallback(() => api<Conflict[]>('/admin/conflicts'), []))

  const resolve = async (id: string, body: object) => {
    await post(`/admin/conflicts/${id}/resolve`, body)
    reload()
  }

  if (error) return <p className="error">{error}</p>
  if (!data) return <p>Đang tải…</p>
  if (data.length === 0) return <p>Không có ca mâu thuẫn nào.</p>

  return (
    <div className="cards">
      {data.map(({ sign, codeWeights }) => (
        <article key={sign.id} className="card">
          {sign.imageUrl ? <img src={sign.imageUrl} alt={`Ảnh cắt biển ${sign.signCode}`} /> : <div className="noimg">Chưa có ảnh</div>}
          <p>
            {sign.location.lat.toFixed(6)}, {sign.location.lng.toFixed(6)} · {sign.observationCount} quan sát
          </p>
          <ul>
            {Object.entries(codeWeights)
              .sort((a, b) => b[1] - a[1])
              .map(([code, weight]) => (
                <li key={code}>
                  <button onClick={() => resolve(sign.id, { signCode: code })}>Chốt {code}</button> trọng số{' '}
                  {weight.toFixed(2)}
                </li>
              ))}
          </ul>
          <button
            className="danger"
            onClick={() => {
              const reason = prompt('Lý do loại cụm biển này?')
              if (reason) resolve(sign.id, { reject: true, reason })
            }}
          >
            Loại cụm
          </button>
        </article>
      ))}
    </div>
  )
}
