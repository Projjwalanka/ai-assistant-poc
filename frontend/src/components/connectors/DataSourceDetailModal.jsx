import { useState, useEffect, useCallback, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  X, Trash2, Settings, RefreshCw, Lock, CheckCircle2, XCircle,
  AlertTriangle, Activity, FileText, Zap, Clock
} from 'lucide-react'
import client from '../../api/client'
import { CONNECTOR_META } from './connectorMeta'
import toast from 'react-hot-toast'
import { format, formatDistanceToNow } from 'date-fns'

const JOB_STATUS_META = {
  PENDING:   { cls: 'bg-yellow-100 text-yellow-700',              label: 'Pending' },
  RUNNING:   { cls: 'bg-blue-100 text-blue-700 animate-pulse',    label: 'Running' },
  COMPLETED: { cls: 'bg-green-100 text-green-700',                label: 'Completed' },
  FAILED:    { cls: 'bg-red-100 text-red-700',                    label: 'Failed' },
  CANCELLED: { cls: 'bg-gray-100 text-gray-500',                  label: 'Cancelled' },
}

function StatusBadge({ status }) {
  const s = JOB_STATUS_META[status] ?? { cls: 'bg-gray-100 text-gray-500', label: status }
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold ${s.cls}`}>
      {s.label}
    </span>
  )
}

export default function DataSourceDetailModal({ connector, onClose, onEdit, onDeleted }) {
  const [jobs, setJobs] = useState([])
  const [loadingJobs, setLoadingJobs] = useState(false)
  const [testing, setTesting] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef(null)

  const meta = CONNECTOR_META[connector.connectorType] ?? { label: connector.connectorType, bg: 'bg-gray-100' }
  const Icon = meta.Icon

  const loadJobs = useCallback(async () => {
    setLoadingJobs(true)
    try {
      const { data } = await client.get('/ingestion/jobs')
      setJobs(data.filter(j => j.connectorType === connector.connectorType))
    } catch { /* silent — jobs panel is non-critical */ }
    finally { setLoadingJobs(false) }
  }, [connector.connectorType])

  useEffect(() => { loadJobs() }, [loadJobs])

  const handleTest = async () => {
    setTesting(true)
    try {
      const { data } = await client.post(`/connectors/${connector.id}/health`)
      toast[data.healthy ? 'success' : 'error'](
        data.healthy ? `Connected! (${data.latencyMs}ms)` : `Failed: ${data.message}`
      )
    } catch { toast.error('Health check failed') }
    finally { setTesting(false) }
  }

  const handleDelete = async () => {
    if (connector.readOnly && connector.connectorType !== 'DOCUMENTS') { toast.error('Cannot delete a Read Only data source'); return }
    if (!window.confirm(`Remove "${connector.name}"? This cannot be undone.`)) return
    setDeleting(true)
    try {
      await client.delete(`/connectors/${connector.id}`)
      toast.success('Data source removed')
      onDeleted()
    } catch { toast.error('Remove failed') }
    finally { setDeleting(false) }
  }

  const completedJobs = jobs.filter(j => j.status === 'COMPLETED')
  const totalChunks = completedJobs.reduce((sum, j) => sum + (j.chunksProcessed ?? 0), 0)
  const failedJobs = jobs.filter(j => j.status === 'FAILED').length
  const isDocuments = connector.connectorType === 'DOCUMENTS'

  const handleDocumentUpload = async (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      await client.post('/ingestion/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      toast.success(`"${file.name}" uploaded and queued for ingestion`)
      loadJobs()
    } catch {
      toast.error('Document upload failed')
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 10 }}
          className="w-full max-w-2xl rounded-2xl bg-white shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
        >
          {/* Header */}
          <div className="flex items-start gap-4 px-6 py-5 border-b border-gray-100">
            <div className={`w-12 h-12 rounded-2xl flex items-center justify-center flex-shrink-0 ${meta.bg}`}>
              {Icon ? <Icon size={26} /> : <span className="text-xl">🔌</span>}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="text-base font-bold text-gray-900">{connector.name}</h2>
                {connector.readOnly && !isDocuments && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                    <Lock className="h-2.5 w-2.5" /> Read Only
                  </span>
                )}
                {connector.verified ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-semibold text-green-700">
                    <CheckCircle2 className="h-2.5 w-2.5" /> Connected
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-semibold text-gray-500">
                    <XCircle className="h-2.5 w-2.5" /> Not Verified
                  </span>
                )}
              </div>
              <p className="text-xs text-gray-400 mt-0.5">{meta.label}</p>
              {connector.lastSyncAt && (
                <div className="flex items-center gap-1 mt-1">
                  <Clock className="h-3 w-3 text-gray-300" />
                  <span className="text-[10px] text-gray-400">
                    Last synced {formatDistanceToNow(new Date(connector.lastSyncAt), { addSuffix: true })}
                  </span>
                </div>
              )}
            </div>
            <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-gray-100 transition flex-shrink-0">
              <X className="h-4 w-4 text-gray-500" />
            </button>
          </div>

          {/* Scrollable body */}
          <div className="flex-1 overflow-y-auto">
            {/* Stats row */}
            <div className="grid grid-cols-3 divide-x divide-gray-100 border-b border-gray-100">
              <div className="py-4 text-center">
                <p className="text-2xl font-bold text-gray-900">{completedJobs.length}</p>
                <p className="text-[10px] text-gray-400 mt-0.5">Sync Jobs</p>
              </div>
              <div className="py-4 text-center">
                <p className="text-2xl font-bold text-gray-900">{totalChunks.toLocaleString()}</p>
                <p className="text-[10px] text-gray-400 mt-0.5">Chunks Indexed</p>
              </div>
              <div className="py-4 text-center">
                <p className={`text-2xl font-bold ${failedJobs > 0 ? 'text-red-600' : 'text-gray-900'}`}>
                  {failedJobs}
                </p>
                <p className="text-[10px] text-gray-400 mt-0.5">Failed Jobs</p>
              </div>
            </div>

            {/* Last error alert */}
            {connector.lastError && (
              <div className="mx-6 mt-4 flex items-start gap-2.5 rounded-xl bg-red-50 border border-red-200 px-4 py-3">
                <AlertTriangle className="h-4 w-4 text-red-500 flex-shrink-0 mt-px" />
                <div>
                  <p className="text-xs font-semibold text-red-700">Last Error</p>
                  <p className="text-xs text-red-600 mt-0.5 break-words">{connector.lastError}</p>
                </div>
              </div>
            )}

            {/* Ingestion history */}
            <div className="px-6 py-4">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Activity className="h-4 w-4 text-gray-400" />
                  <h3 className="text-sm font-semibold text-gray-700">Ingestion History</h3>
                  <span className="text-[10px] text-gray-400">· {meta.label} jobs</span>
                </div>
                <button
                  onClick={loadJobs}
                  title="Refresh"
                  className="p-1 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition"
                >
                  <RefreshCw className={`h-3.5 w-3.5 ${loadingJobs ? 'animate-spin' : ''}`} />
                </button>
              </div>

              {jobs.length === 0 ? (
                <div className="py-10 text-center">
                  <FileText className="h-8 w-8 mx-auto mb-2 text-gray-200" />
                  <p className="text-xs text-gray-400">No ingestion jobs recorded for this source type yet</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {jobs.slice(0, 12).map(job => (
                    <div
                      key={job.id}
                      className="flex items-center gap-3 rounded-xl border border-gray-100 bg-gray-50 px-3 py-2.5"
                    >
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-medium text-gray-700 truncate">
                          {job.sourceRef || 'Batch ingestion'}
                        </p>
                        <p className="text-[10px] text-gray-400 mt-0.5">
                          {job.createdAt ? format(new Date(job.createdAt), 'MMM d, HH:mm') : '—'}
                          {job.chunksProcessed != null && (
                            <span className="ml-1.5">· {job.chunksProcessed}/{job.chunksTotal ?? '?'} chunks</span>
                          )}
                        </p>
                      </div>
                      <StatusBadge status={job.status} />
                    </div>
                  ))}
                  {jobs.length > 12 && (
                    <p className="text-[10px] text-center text-gray-400 pt-1">
                      +{jobs.length - 12} more jobs not shown
                    </p>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Footer actions */}
          <div className="flex items-center gap-2 px-6 py-4 border-t border-gray-100 bg-gray-50">
            <button
              onClick={handleTest}
              disabled={testing}
              className="flex items-center gap-1.5 rounded-xl border border-gray-200 bg-white px-3.5 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 transition"
            >
              <Zap className="h-3.5 w-3.5 text-blue-500" />
              {testing ? 'Testing…' : 'Test Connection'}
            </button>
            <button
              onClick={() => onEdit(connector)}
              className="flex items-center gap-1.5 rounded-xl border border-gray-200 bg-white px-3.5 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 transition"
            >
              <Settings className="h-3.5 w-3.5 text-gray-500" />
              Edit Config
            </button>
            <div className="flex-1" />
            <button
              onClick={handleDelete}
              disabled={deleting || (connector.readOnly && !isDocuments)}
              title={connector.readOnly && !isDocuments ? 'Cannot remove a Read Only source' : 'Remove this data source'}
              className="flex items-center gap-1.5 rounded-xl border border-red-200 bg-red-50 px-3.5 py-2 text-xs font-medium text-red-600 hover:bg-red-100 transition disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Trash2 className="h-3.5 w-3.5" />
              {deleting ? 'Removing…' : 'Remove'}
            </button>
            {isDocuments && (
              <>
                <input
                  ref={fileInputRef}
                  type="file"
                  className="hidden"
                  accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md,.csv"
                  onChange={handleDocumentUpload}
                />
                <button
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  className="flex items-center gap-1.5 rounded-xl border border-blue-200 bg-blue-50 px-3.5 py-2 text-xs font-medium text-blue-700 hover:bg-blue-100 transition disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <FileText className="h-3.5 w-3.5" />
                  {uploading ? 'Uploading…' : 'Upload Document'}
                </button>
              </>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  )
}
