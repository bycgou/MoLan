"use client"

import { ArrowLeft } from "lucide-react"
import Link from "next/link"
import { buttonVariants } from "@/components/ui/button"
import WizardSteps from "@/components/novel/WizardSteps"
import { useAuth } from "@/hooks/useAuth"

export default function CreateWizardPage() {
  useAuth()

  return (
    <div className="min-h-screen">
      {/* Header */}
      <div className="max-w-[800px] mx-auto px-6 pt-6">
        <Link
          href="/novels"
          className={buttonVariants({
            variant: "ghost",
            size: "sm",
            className: "gap-1.5 text-stone-500 hover:text-stone-700 dark:hover:text-stone-300",
          })}
        >
          <ArrowLeft className="w-4 h-4" />
          返回
        </Link>
      </div>

      <WizardSteps />
    </div>
  )
}
