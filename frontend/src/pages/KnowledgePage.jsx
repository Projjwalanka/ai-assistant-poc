import { useState } from 'react'
import { GitBranch, Activity } from 'lucide-react'
import KnowledgeGraphPanel from '../components/kg/KnowledgeGraphPanel'
import PipelineMonitor from '../components/kg/PipelineMonitor'

const TABS = [
  { id: 'graph',   label: 'Knowledge Graph', Icon: GitBranch },
  { id: 'monitor', label: 'Pipeline Monitor', Icon: Activity  },
]

export default function KnowledgePage() {
  const [activeTab, setActiveTab] = useState('graph')

  return (
    <div className="flex flex-col h-screen bg-gray-50">
      {/* Top nav */}
      <div className="flex items-center gap-1 px-6 py-3 border-b border-gray-100 bg-white flex-shrink-0">
        <div className="flex items-center gap-2 mr-6">
          <div className="w-7 h-7 rounded-xl bg-indigo-600 flex items-center justify-center">
            <GitBranch className="h-4 w-4 text-white" />
          </div>
          <span className="text-sm font-bold text-gray-900">Knowledge Platform</span>
        </div>
        <div className="flex gap-1">
          {TABS.map(({ id, label, Icon }) => (
            <button
              key={id}
              onClick={() => setActiveTab(id)}
              className={`flex items-center gap-1.5 rounded-xl px-3.5 py-2 text-xs font-medium transition ${
                activeTab === id
                  ? 'bg-indigo-50 text-indigo-700'
                  : 'text-gray-500 hover:text-gray-700 hover:bg-gray-100'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-hidden">
        {activeTab === 'graph'   && <KnowledgeGraphPanel />}
        {activeTab === 'monitor' && <div className="h-full overflow-y-auto"><PipelineMonitor /></div>}
      </div>
    </div>
  )
}
