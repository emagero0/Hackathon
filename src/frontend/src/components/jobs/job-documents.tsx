"use client"

import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { Button } from "../../components/ui/button"
import { Download, Eye, FileText, Image, PaperclipIcon as PaperClip } from "lucide-react"
import { useState } from "react"

interface JobDocumentsProps {
  jobId: string
}

export function JobDocuments({ jobId }: JobDocumentsProps) {
  console.log(jobId)
  const [selectedDocument, setSelectedDocument] = useState<string | null>(null)

  // This would normally fetch data based on the jobId
  const documents = [
    {
      id: "doc-1",
      name: "Invoice_9012.pdf",
      type: "pdf",
      size: "1.2 MB",
      uploadedAt: "Apr 2, 2023 09:45 AM",
      uploadedBy: "John Doe",
      thumbnail: "/placeholder.svg?height=200&width=150",
    },
    {
      id: "doc-2",
      name: "Delivery_Confirmation.jpg",
      type: "image",
      size: "850 KB",
      uploadedAt: "Apr 2, 2023 09:46 AM",
      uploadedBy: "John Doe",
      thumbnail: "/placeholder.svg?height=200&width=150",
    },
    {
      id: "doc-3",
      name: "Terms_and_Conditions.pdf",
      type: "pdf",
      size: "450 KB",
      uploadedAt: "Apr 2, 2023 09:47 AM",
      uploadedBy: "John Doe",
      thumbnail: "/placeholder.svg?height=200&width=150",
    },
  ]

  const getDocumentIcon = (type: string) => {
    switch (type) {
      case "pdf":
        return <FileText className="h-5 w-5 text-red-500" />
      case "image":
        return <Image className="h-5 w-5 text-blue-500" />
      default:
        return <PaperClip className="h-5 w-5" />
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Attached Documents</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {documents.map((doc) => (
            <div
              key={doc.id}
              className={`flex items-center justify-between rounded-lg border p-3 ${
                selectedDocument === doc.id ? "border-primary bg-primary/5" : ""
              }`}
            >
              <div className="flex items-center gap-3">
                {getDocumentIcon(doc.type)}
                <div>
                  <div className="font-medium">{doc.name}</div>
                  <div className="text-xs text-muted-foreground">
                    {doc.size} • {doc.uploadedAt}
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => setSelectedDocument(selectedDocument === doc.id ? null : doc.id)}
                >
                  <Eye className="h-4 w-4" />
                  <span className="sr-only">View document</span>
                </Button>
                <Button variant="ghost" size="icon">
                  <Download className="h-4 w-4" />
                  <span className="sr-only">Download document</span>
                </Button>
              </div>
            </div>
          ))}

          {selectedDocument && (
            <div className="mt-4 rounded-lg border p-4">
              <div className="flex justify-center">
                <img
                  src={documents.find((d) => d.id === selectedDocument)?.thumbnail || "/placeholder.svg"}
                  alt="Document preview"
                  className="max-h-[300px] object-contain"
                />
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

