"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { PenLine, Eye, EyeOff } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useAuthStore } from "@/stores/auth"

const loginSchema = z.object({
  username: z.string().min(1, "请输入用户名"),
  password: z.string().min(1, "请输入密码"),
})

const registerSchema = z.object({
  username: z.string().min(3, "用户名至少3个字符"),
  password: z.string().min(6, "密码至少6个字符"),
  email: z.string().email("请输入有效邮箱"),
  nickname: z.string().min(1, "请输入昵称"),
})

type LoginFormData = z.infer<typeof loginSchema>
type RegisterFormData = z.infer<typeof registerSchema>

export default function LoginPage() {
  const router = useRouter()
  const { login, register } = useAuthStore()
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState("")

  const loginForm = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  })

  const registerForm = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  })

  const onLogin = async (data: LoginFormData) => {
    try {
      setError("")
      await login(data)
      router.push("/")
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "登录失败")
    }
  }

  const onRegister = async (data: RegisterFormData) => {
    try {
      setError("")
      await register(data)
      router.push("/")
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "注册失败")
    }
  }

  return (
    <Card className="w-[380px] border-stone-200 dark:border-stone-700">
      <CardHeader className="text-center pb-2">
        <div className="w-12 h-12 rounded-xl bg-amber-100 dark:bg-amber-900 flex items-center justify-center mx-auto mb-3">
          <PenLine className="w-6 h-6 text-amber-700 dark:text-amber-400" />
        </div>
        <h1 className="text-lg font-bold text-stone-900 dark:text-stone-50">
          MoYun
        </h1>
        <p className="text-xs text-stone-500">AI小说创作系统</p>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="login">
          <TabsList className="w-full mb-4">
            <TabsTrigger value="login" className="flex-1">
              登录
            </TabsTrigger>
            <TabsTrigger value="register" className="flex-1">
              注册
            </TabsTrigger>
          </TabsList>

          {error && (
            <div className="mb-3 p-2 rounded-md bg-red-50 dark:bg-red-950 text-red-600 dark:text-red-400 text-xs">
              {error}
            </div>
          )}

          <TabsContent value="login">
            <form
              onSubmit={loginForm.handleSubmit(onLogin)}
              className="space-y-3"
            >
              <div>
                <Label htmlFor="login-username" className="text-xs">
                  用户名
                </Label>
                <Input
                  id="login-username"
                  {...loginForm.register("username")}
                  className="mt-1"
                />
                {loginForm.formState.errors.username && (
                  <p className="text-xs text-red-500 mt-1">
                    {loginForm.formState.errors.username.message}
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="login-password" className="text-xs">
                  密码
                </Label>
                <div className="relative mt-1">
                  <Input
                    id="login-password"
                    type={showPassword ? "text" : "password"}
                    {...loginForm.register("password")}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-stone-400"
                  >
                    {showPassword ? (
                      <EyeOff className="w-4 h-4" />
                    ) : (
                      <Eye className="w-4 h-4" />
                    )}
                  </button>
                </div>
              </div>
              <Button
                type="submit"
                className="w-full bg-amber-600 hover:bg-amber-700 text-white"
              >
                登录
              </Button>
            </form>
          </TabsContent>

          <TabsContent value="register">
            <form
              onSubmit={registerForm.handleSubmit(onRegister)}
              className="space-y-3"
            >
              <div>
                <Label htmlFor="reg-username" className="text-xs">
                  用户名
                </Label>
                <Input
                  id="reg-username"
                  {...registerForm.register("username")}
                  className="mt-1"
                />
                {registerForm.formState.errors.username && (
                  <p className="text-xs text-red-500 mt-1">
                    {registerForm.formState.errors.username.message}
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="reg-nickname" className="text-xs">
                  昵称
                </Label>
                <Input
                  id="reg-nickname"
                  {...registerForm.register("nickname")}
                  className="mt-1"
                />
                {registerForm.formState.errors.nickname && (
                  <p className="text-xs text-red-500 mt-1">
                    {registerForm.formState.errors.nickname.message}
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="reg-email" className="text-xs">
                  邮箱
                </Label>
                <Input
                  id="reg-email"
                  type="email"
                  {...registerForm.register("email")}
                  className="mt-1"
                />
                {registerForm.formState.errors.email && (
                  <p className="text-xs text-red-500 mt-1">
                    {registerForm.formState.errors.email.message}
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="reg-password" className="text-xs">
                  密码
                </Label>
                <Input
                  id="reg-password"
                  type="password"
                  {...registerForm.register("password")}
                  className="mt-1"
                />
                {registerForm.formState.errors.password && (
                  <p className="text-xs text-red-500 mt-1">
                    {registerForm.formState.errors.password.message}
                  </p>
                )}
              </div>
              <Button
                type="submit"
                className="w-full bg-amber-600 hover:bg-amber-700 text-white"
              >
                注册
              </Button>
            </form>
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}
