import { FormEvent, useCallback, useState } from 'react'
import { api, post, put, SignType } from '../api'
import { useLoad } from '../useLoad'

const GROUPS = ['PROHIBITORY', 'MANDATORY', 'WARNING', 'AUXILIARY', 'OTHER']

/** Danh mục loại biển: thêm, sửa, ngừng dùng (FR-24). */
export default function SignTypes() {
  const { data, error, reload } = useLoad(useCallback(() => api<SignType[]>('/sign-types'), []))
  const [editing, setEditing] = useState<SignType | 'new'>()
  const [formError, setFormError] = useState<string>()

  const save = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const f = new FormData(e.currentTarget)
    const body = {
      code: f.get('code'),
      group: f.get('group'),
      name: f.get('name'),
      description: f.get('description') || null,
      speedKmh: f.get('speedKmh') ? Number(f.get('speedKmh')) : null,
      active: f.get('active') === 'on',
    }
    try {
      if (editing === 'new') await post('/admin/sign-types', body)
      else await put(`/admin/sign-types/${editing!.code}`, body)
      setEditing(undefined)
      setFormError(undefined)
      reload()
    } catch (err) {
      setFormError((err as Error).message)
    }
  }

  if (error) return <p className="error">{error}</p>
  if (!data) return <p>Đang tải…</p>

  const t = editing === 'new' ? undefined : editing
  return (
    <>
      <button onClick={() => setEditing('new')}>Thêm loại biển</button>
      {editing && (
        <form className="panel" onSubmit={save} key={t?.code ?? 'new'}>
          <label>Mã <input name="code" defaultValue={t?.code} required readOnly={!!t} /></label>
          <label>
            Nhóm
            <select name="group" defaultValue={t?.group ?? 'PROHIBITORY'}>
              {GROUPS.map((g) => <option key={g}>{g}</option>)}
            </select>
          </label>
          <label>Tên <input name="name" defaultValue={t?.name} required /></label>
          <label>Ý nghĩa <textarea name="description" defaultValue={t?.description} /></label>
          <label>Tốc độ (km/h) <input name="speedKmh" type="number" min={5} max={150} defaultValue={t?.speedKmh} /></label>
          <label className="inline"><input name="active" type="checkbox" defaultChecked={t?.active ?? true} /> Đang dùng</label>
          {formError && <p className="error">{formError}</p>}
          <button type="submit">Lưu</button> <button type="button" onClick={() => setEditing(undefined)}>Huỷ</button>
        </form>
      )}
      <table>
        <thead>
          <tr><th>Mã</th><th>Nhóm</th><th>Tên</th><th>Tốc độ</th><th>Trạng thái</th><th /></tr>
        </thead>
        <tbody>
          {data.map((s) => (
            <tr key={s.code} className={s.active ? '' : 'muted'}>
              <td><code>{s.code}</code></td>
              <td>{s.group}</td>
              <td>{s.name}</td>
              <td>{s.speedKmh ?? ''}</td>
              <td>{s.active ? 'Đang dùng' : 'Ngừng dùng'}</td>
              <td><button onClick={() => setEditing(s)}>Sửa</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
