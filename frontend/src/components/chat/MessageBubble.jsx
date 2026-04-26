import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { ThumbsUp, ThumbsDown, Bot, User, Download, FileText, Table2, Image } from 'lucide-react'
import { format } from 'date-fns'
import { useState } from 'react'

const ARTIFACT_ICONS = { PDF: FileText, EXCEL: Table2, IMAGE: Image, JSON: FileText }

export default function MessageBubble({ message, onFeedback }) {
  const isAssistant = message.role === 'ASSISTANT'
  const [feedbackGiven, setFeedbackGiven] = useState(null)

  const handleFeedback = (type) => {
    if (feedbackGiven) return
    setFeedbackGiven(type)
    onFeedback?.(message.id, type)
  }

  return (
    <div className={`flex gap-3 ${isAssistant ? '' : 'flex-row-reverse'}`}>
      {/* Avatar */}
      <div className={`flex-shrink-0 flex h-8 w-8 items-center justify-center rounded-full
        ${isAssistant ? 'bg-blue-600' : 'bg-gray-700'}`}>
        {isAssistant
          ? <Bot className="h-4 w-4 text-white" />
          : <User className="h-4 w-4 text-white" />
        }
      </div>

      {/* Bubble */}
      <div className={`max-w-[80%] group ${isAssistant ? '' : 'items-end flex flex-col'}`}>
        <div className={`rounded-2xl px-4 py-3 text-sm leading-relaxed
          ${isAssistant
            ? 'bg-white border border-gray-200 text-gray-800 shadow-sm'
            : 'bg-blue-600 text-white'
          }`}
        >
          {isAssistant ? (
            message.streaming && !message.content ? (
              /* Typing indicator */
              <div className="flex items-center gap-1 py-1">
                <span className="typing-dot h-2 w-2 rounded-full bg-gray-400" />
                <span className="typing-dot h-2 w-2 rounded-full bg-gray-400" />
                <span className="typing-dot h-2 w-2 rounded-full bg-gray-400" />
              </div>
            ) : (
              <div className="prose prose-sm max-w-none prose-p:my-1 prose-li:my-0">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {message.content}
                </ReactMarkdown>
              </div>
            )
          ) : (
            <p className="whitespace-pre-wrap">{message.content}</p>
          )}
        </div>

        {/* Artifacts */}
        {message.artifacts?.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-2">
            {message.artifacts.map((artifact, i) => {
              const Icon = ARTIFACT_ICONS[artifact.type] || FileText
              return (
                <a key={i} href={artifact.downloadUrl} download={artifact.filename}
                   className="inline-flex items-center gap-2 rounded-lg border border-blue-200 bg-blue-50
                              px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-100 transition">
                  <Icon className="h-3.5 w-3.5" />
                  {artifact.filename}
                  <Download className="h-3 w-3 opacity-60" />
                </a>
              )
            })}
          </div>
        )}

        {/* Footer: timestamp + feedback */}
        <div className={`mt-1 flex items-center gap-2 ${isAssistant ? '' : 'flex-row-reverse'}`}>
          {message.createdAt && (
            <span className="text-[11px] text-gray-400">
              {format(new Date(message.createdAt), 'HH:mm')}
            </span>
          )}
          {isAssistant && !message.streaming && onFeedback && (
            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button onClick={() => handleFeedback('THUMBS_UP')}
                className={`rounded-full p-1 transition ${feedbackGiven === 'THUMBS_UP'
                  ? 'text-green-600 bg-green-50' : 'text-gray-400 hover:text-green-600 hover:bg-green-50'}`}>
                <ThumbsUp className="h-3.5 w-3.5" />
              </button>
              <button onClick={() => handleFeedback('THUMBS_DOWN')}
                className={`rounded-full p-1 transition ${feedbackGiven === 'THUMBS_DOWN'
                  ? 'text-red-500 bg-red-50' : 'text-gray-400 hover:text-red-500 hover:bg-red-50'}`}>
                <ThumbsDown className="h-3.5 w-3.5" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
