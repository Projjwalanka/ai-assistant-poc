import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { X, Eye, EyeOff, TestTube2, Save } from 'lucide-react'
import client from '../../api/client'
import toast from 'react-hot-toast'

const CONNECTOR_FIELDS = {
  JIRA:       [{ key: 'baseUrl', label: 'Base URL', placeholder: 'https://acme.atlassian.net' },
               { key: 'email',   label: 'Email' },
               { key: 'apiToken', label: 'API Token', secret: true }],
  CONFLUENCE: [{ key: 'baseUrl', label: 'Base URL', placeholder: 'https://acme.atlassian.net' },
               { key: 'email',   label: 'Email' },
               { key: 'apiToken', label: 'API Token', secret: true }],
  GITHUB:     [{ key: 'personalAccessToken', label: 'Personal Access Token', secret: true },
               { key: 'org', label: 'Organisation (optional)' }],
  SHAREPOINT: [{ key: 'tenantId',     label: 'Tenant ID' },
               { key: 'clientId',     label: 'Client ID' },
               { key: 'clientSecret', label: 'Client Secret', secret: true }],
  EMAIL:      [{ key: 'imapHost',  label: 'IMAP Host' },
               { key: 'imapPort',  label: 'IMAP Port', placeholder: '993' },
               { key: 'username',  label: 'Username / Email' },
               { key: 'password',  label: 'Password', secret: true }],
  DOCUMENTS:  [],
}

const CONNECTOR_TYPES = Object.keys(CONNECTOR_FIELDS)

export default function ConnectorConfigModal({ connector, onClose, onSaved }) {
  const isEdit = !!connector
  const [type, setType] = useState(connector?.connectorType || 'JIRA')
  const [name, setName] = useState(connector?.name || '')
  const [credentials, setCredentials] = useState({})
  const [showSecrets, setShowSecrets] = useState({})
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)

  const fields = CONNECTOR_FIELDS[type] || []

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = { connectorType: type, name, credentials, enabled: true }
      if (isEdit) {
        await client.put(`/connectors/${connector.id}`, payload)
        toast.success('Connector updated')
      } else {
        await client.post('/connectors', payload)
        toast.success('Connector created')
      }
      onSaved()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    if (!connector?.id) { toast.error('Save the connector first before testing'); return }
    setTesting(true)
    try {
      const { data } = await client.post(`/connectors/${connector.id}/health`)
      toast[data.healthy ? 'success' : 'error'](
        data.healthy ? `Connected! (${data.latencyMs}ms)` : `Failed: ${data.message}`
      )
    } catch { toast.error('Test failed') }
    finally { setTesting(false) }
  }

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="w-full max-w-md rounded-2xl bg-white shadow-2xl"
        >
          <div className="flex items-center justify-between px-6 py-4 border-b">
            <h2 className="font-semibold text-gray-900">{isEdit ? 'Edit Connector' : 'Add Connector'}</h2>
            <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-gray-100 transition">
              <X className="h-4 w-4 text-gray-500" />
            </button>
          </div>

          <form onSubmit={handleSave} className="p-6 space-y-4">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Connector Type</label>
              <select value={type} onChange={e => setType(e.target.value)} disabled={isEdit}
                className="input-field">
                {CONNECTOR_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Display Name</label>
              <input value={name} onChange={e => setName(e.target.value)}
                className="input-field" placeholder="e.g. Acme Jira" required />
            </div>

            {fields.map(f => (
              <div key={f.key}>
                <label className="block text-xs font-medium text-gray-700 mb-1">{f.label}</label>
                <div className="relative">
                  <input
                    type={f.secret && !showSecrets[f.key] ? 'password' : 'text'}
                    value={credentials[f.key] || ''}
                    onChange={e => setCredentials(c => ({ ...c, [f.key]: e.target.value }))}
                    placeholder={f.placeholder}
                    className="input-field pr-9"
                  />
                  {f.secret && (
                    <button type="button"
                      onClick={() => setShowSecrets(s => ({ ...s, [f.key]: !s[f.key] }))}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                      {showSecrets[f.key] ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  )}
                </div>
              </div>
            ))}

            <div className="flex gap-2 pt-2">
              {isEdit && (
                <button type="button" onClick={handleTest} disabled={testing}
                  className="btn-secondary flex-1 justify-center">
                  <TestTube2 className="h-4 w-4" />
                  {testing ? 'Testing…' : 'Test Connection'}
                </button>
              )}
              <button type="submit" disabled={saving}
                className="btn-primary flex-1 justify-center">
                <Save className="h-4 w-4" />
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </form>
        </motion.div>
      </div>
    </AnimatePresence>
  )
}
