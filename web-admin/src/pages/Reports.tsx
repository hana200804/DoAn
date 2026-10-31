import { useCallback } from 'react'
import { api, post, Report } from '../api'
import { useLoad } from '../useLoad'

const REASONS: Record<string, string> = {
  NOT_EXIST: 'Không tồn tại',
  WRONG_TYPE: 'Sai loại',
  WRONG_POSITION: 'Sai vị trí',
  OTHER: 'Khác',
}

/** Báo cáo biển sai từ người lái (FR-22). */
export default function Reports() {
  const { data, error, reload } = useLoad(useCallback(() => api<Report[]>('/admin/reports'), []))

  const resolve = async (id: string, body: object) => {
    await post(`/admin/reports/${id}/resolve`, body)
    reload()
  }

  const accept = (r: Report) => {
    const code = prompt('Nhập mã biển đúng để sửa, hoặc để trống để loại cụm biển:')
    if (code === null) return
    resolve(r.id, code.trim()
      ? { accept: true, action: 'CHANGE_CODE', signCode: code.trim() }
      : { accept: true, action: 'REJECT_CLUSTER' })
  }

  const reject = (r: Report) => {
    const note = prompt('Lý do từ chối báo cáo?')
    if (note) resolve(r.id, { accept: false, note })
  }

  if (error) return <p className="error">{error}</p>
  if (!data) return <p>Đang tải…</p>
  if (data.length === 0) return <p>Không có báo cáo nào đang chờ.</p>

  return (
    <table>
      <thead>
        <tr>
          <th>Thời điểm</th>
          <th>Cụm biển</th>
          <th>Lý do</th>
          <th>Ghi chú</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {data.map((r) => (
          <tr key={r.id}>
            <td>{new Date(r.createdAt).toLocaleString('vi-VN')}</td>
            <td><code>{r.clusterId.slice(0, 8)}</code></td>
            <td>{REASONS[r.reason] ?? r.reason}</td>
            <td>{r.note}</td>
            <td>
              <button onClick={() => accept(r)}>Chấp nhận</button>{' '}
              <button className="danger" onClick={() => reject(r)}>Từ chối</button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
