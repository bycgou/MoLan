export default function Home() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background">
      <main className="flex flex-col items-center gap-6 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-foreground">
          MoYun
        </h1>
        <p className="text-lg text-muted-foreground">
          AI小说创作系统
        </p>
        <p className="text-sm text-muted-foreground">
          百万字AI长篇小说创作系统
        </p>
      </main>
    </div>
  );
}
