import { useState, useEffect } from 'react'
import { useChatStore } from '../../store/chatStore'
import client from '../../api/client'
import { motion } from 'framer-motion'
import { CheckCircle2, XCircle, Plus, Settings, Zap } from 'lucide-react'
import ConnectorConfigModal from './ConnectorConfigModal'
import toast from 'react-hot-toast'

const CONNECTOR_META = {
  JIRA:        { label: 'Jira',         icon: '🎯', color: 'blue' },
  CONFLUENCE:  { label: 'Confluence',   icon: '📚', color: 'teal' },
  GITHUB:      { label: 'GitHub',       icon: '🐙', color: 'gray' },
  SHAREPOINT:  { label: 'SharePoint',   icon: '📁', color: 'orange' },
  EMAIL:       { label: 'Email (IMAP)', icon: '📧', color: 'purple' },
  DOCUMENTS:   { label: 'Documents',    icon: '📄', color: 'green' },
}

export default function ConnectorPanel() {
  const [connectors, setConnectors] = useState([])
  const [showModal, setShowModal] = useState(false)
  const [selectedConnector, setSelectedConnector] = useState(null)
  const { activeConnectors, toggleConnector } = useChatStore()

  const loadConnectors = async () => {
    try {
      const { data } = await client.get('/connectors')
      setConnectors(data)
    } catch (err) {
      toast.error('Failed to load connectors')
    }
  }

  useEffect(() => { loadConnectors() }, [])

  const handleHealthCheck = async (id) => {
    try {
      const { data } = await client.post(`/connectors/${id}/health`)
      toast[data.healthy ? 'success' : 'error'](
        data.healthy ? `Connected (${data.latencyMs}ms)` : `Failed: ${data.message}`
      )
    } catch {
      toast.error('Health check failed')
    }
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
        <h3 className="text-sm font-semibold text-gray-700">Data Sources</h3>
        <button onClick={() => { setSelectedConnector(null); setShowModal(true) }}
          className="flex items-center gap-1 rounded-lg bg-blue-50 px-2 py-1 text-xs font-medium text-blue-600 hover:bg-blue-100 transition">
          <Plus className="h-3 w-3" /> Add
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-2 space-y-1.5">
        {connectors.length === 0 && (
          <div className="py-8 text-center text-xs text-gray-400">
            <Zap className="h-6 w-6 mx-auto mb-2 opacity-40" />
            No connectors configured yet.
            <br />Click + Add to get started.
          </div>
        )}
        {connectors.map(connector => {
          const meta = CONNECTOR_META[connector.connectorType] || { label: connector.connectorType, icon: '🔌', color: 'gray' }
          const isActive = activeConnectors.includes(connector.id)
          return (
            <motion.div key={connector.id} layout
              className={`flex items-center gap-2.5 rounded-xl px-3 py-2.5 cursor-pointer transition
                ${isActive ? 'bg-blue-50 border border-blue-200' : 'hover:bg-gray-50 border border-transparent'}`}
              onClick={() => connector.enabled && toggleConnector(connector.id)}
            >
              <span className="text-lg">{meta.icon}</span>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-gray-800 truncate">{connector.name}</p>
                <p className="text-[10px] text-gray-400">{meta.label}</p>
              </div>
              <div className="flex items-center gap-1.5">
                {connector.verified
                  ? <CheckCircle2 className="h-3.5 w-3.5 text-green-500" />
                  : <XCircle className="h-3.5 w-3.5 text-gray-300" />
                }
                <button onClick={e => { e.stopPropagation(); setSelectedConnector(connector); setShowModal(true) }}
                  className="p-0.5 rounded text-gray-400 hover:text-gray-600">
                  <Settings className="h-3 w-3" />
                </button>
              </div>
            </motion.div>
          )
        })}
      </div>

      {showModal && (
        <ConnectorConfigModal
          connector={selectedConnector}
          onClose={() => setShowModal(false)}
          onSaved={() => { setShowModal(false); loadConnectors() }}
        />
      )}
    </div>
  )
}
