export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-stone-50 dark:bg-stone-900">
      {children}
    </div>
  )
}
