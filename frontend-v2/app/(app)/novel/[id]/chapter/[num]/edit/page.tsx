"use client"

import { useCallback, useEffect, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import Link from "next/link"
import { ArrowLeft, Loader2 } from "lucide-react"
import useSWR from "swr"
import { useEditor, EditorContent } from "@tiptap/react"
import StarterKit from "@tiptap/starter-kit"
import Placeholder from "@tiptap/extension-placeholder"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { chapterApi } from "@/lib/api/chapter"
import { useAuth } from "@/hooks/useAuth"

/** Convert plain text (newline-separated paragraphs) to HTML for Tiptap */
function plainTextToHtml(text: string): string {
  if (!text) return ""
  return text
    .split("\n")
    .filter((p) => p.trim())
    .map((p) => `<p>${p}</p>`)
    .join("")
}

/** Convert HTML back to plain text for saving */
function htmlToPlainText(html: string): string {
  if (!html) return ""
  // Use DOM parser to strip HTML tags
  const doc = new DOMParser().parseFromString(html, "text/html")
  const paragraphs = doc.body.querySelectorAll("p")
  if (paragraphs.length === 0) {
    return doc.body.textContent?.trim() ?? ""
  }
  return Array.from(paragraphs)
    .map((p) => p.textContent?.trim() ?? "")
    .filter((p) => p.length > 0)
    .join("\n")
}

/** Count Chinese characters and words */
function countWords(text: string): number {
  if (!text) return 0
  // Count Chinese characters individually, and English words by spaces
  const chinese = text.match(/[一-鿿]/g)
  const english = text.replace(/[一-鿿]/g, "").trim()
  const englishWords = english ? english.split(/\s+/).length : 0
  return (chinese?.length ?? 0) + englishWords
}

function TiptapEditor({
  content,
  onChange,
}: {
  content: string
  onChange: (html: string) => void
}) {
  const editor = useEditor({
    extensions: [
      StarterKit,
      Placeholder.configure({ placeholder: "开始写作..." }),
    ],
    content,
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
    immediatelyRender: false,
  })

  return (
    <div className="border border-stone-200 dark:border-stone-700 rounded-lg overflow-hidden min-h-[400px] [&_.tiptap]:min-h-[400px] [&_.tiptap]:p-4 [&_.tiptap]:outline-none [&_.tiptap]:text-[16px] [&_.tiptap]:leading-[2.0] [&_.tiptap]:text-stone-800 [&_.tiptap.dark]:text-stone-200 [&_.tiptap_p]:mb-3 [&_.tiptap_p]:text-indent-[2em] [&_.tiptap]:prose-sm [&_.tiptap_p.is-editor-empty:first-child::before]:content-[attr(data-placeholder)] [&_.tiptap_p.is-editor-empty:first-child::before]:text-stone-400 [&_.tiptap_p.is-editor-empty:first-child::before]:float-left [&_.tiptap_p.is-editor-empty:first-child::before]:h-0 [&_.tiptap_p.is-editor-empty:first-child::before]:pointer-events-none">
      <EditorContent editor={editor} />
    </div>
  )
}

export default function ChapterEditPage() {
  const { id, num } = useParams<{ id: string; num: string }>()
  const router = useRouter()
  useAuth()

  const chapterNum = Number(num)

  const { data: chapterData, isLoading } = useSWR(
    id && num ? `chapter-${id}-${num}` : null,
    () => chapterApi.get(id, chapterNum)
  )
  const chapter = chapterData?.data ?? null

  const [title, setTitle] = useState("")
  const [plotFingerprint, setPlotFingerprint] = useState("")
  const [editorHtml, setEditorHtml] = useState("")
  const [isSaving, setIsSaving] = useState(false)

  // Pre-fill form when chapter data loads
  useEffect(() => {
    if (chapter) {
      setTitle(chapter.chapterTitle)
      setPlotFingerprint(chapter.plotFingerprint ?? "")
      setEditorHtml(plainTextToHtml(chapter.content))
    }
  }, [chapter])

  const handleEditorChange = useCallback((html: string) => {
    setEditorHtml(html)
  }, [])

  const handleSave = async () => {
    if (!chapter) return
    setIsSaving(true)
    try {
      const plainContent = htmlToPlainText(editorHtml)
      await chapterApi.update(id, chapterNum, {
        chapterTitle: title,
        plotFingerprint,
        content: plainContent,
        wordCount: countWords(plainContent),
      })
      router.push(`/novel/${id}/chapter/${chapterNum}`)
    } catch {
      // Error handled by api client
    } finally {
      setIsSaving(false)
    }
  }

  const wordCount = countWords(htmlToPlainText(editorHtml))

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="w-6 h-6 animate-spin text-stone-400" />
      </div>
    )
  }

  if (!chapter) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <p className="text-stone-500">章节不存在</p>
        <Link href={`/novel/${id}`}>
          <Button variant="outline">返回小说详情</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="border-b border-stone-200 dark:border-stone-700 px-4 py-2.5 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <Link
            href={`/novel/${id}/chapter/${chapterNum}`}
            className="flex items-center gap-1 text-xs text-stone-500 hover:text-stone-700 dark:hover:text-stone-300"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            返回阅读
          </Link>
          <span className="text-xs text-stone-300 dark:text-stone-600">/</span>
          <span className="text-xs text-stone-700 dark:text-stone-300 font-medium">
            编辑第{chapterNum}章
          </span>
        </div>
        <div className="text-xs text-stone-500">{wordCount} 字</div>
      </div>

      {/* Editor area */}
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-[800px] mx-auto space-y-5">
          {/* Title */}
          <div className="space-y-1.5">
            <Label htmlFor="chapterTitle">章节标题</Label>
            <Input
              id="chapterTitle"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="请输入章节标题"
            />
          </div>

          {/* Plot Fingerprint */}
          <div className="space-y-1.5">
            <Label htmlFor="plotFingerprint">情节指纹</Label>
            <Input
              id="plotFingerprint"
              value={plotFingerprint}
              onChange={(e) => setPlotFingerprint(e.target.value)}
              placeholder="一句话概括本章核心情节"
            />
          </div>

          {/* Tiptap Editor */}
          <div className="space-y-1.5">
            <Label>正文</Label>
            {editorHtml !== undefined && (
              <TiptapEditor
                content={editorHtml}
                onChange={handleEditorChange}
              />
            )}
          </div>

          {/* Actions */}
          <div className="flex gap-3 pt-2">
            <Button onClick={handleSave} disabled={isSaving}>
              {isSaving && (
                <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
              )}
              保存
            </Button>
            <Link href={`/novel/${id}/chapter/${chapterNum}`}>
              <Button type="button" variant="outline">
                取消
              </Button>
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
