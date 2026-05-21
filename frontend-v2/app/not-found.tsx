import Link from "next/link"
import { buttonVariants } from "@/components/ui/button"

export default function NotFound() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-stone-50 dark:bg-stone-900">
      <h1 className="text-4xl font-bold text-stone-900 dark:text-stone-50 mb-2">404</h1>
      <p className="text-sm text-stone-500 mb-6">页面不存在</p>
      <Link href="/" className={buttonVariants({ className: "bg-amber-600 hover:bg-amber-700 text-white" })}>
        返回首页
      </Link>
    </div>
  )
}
