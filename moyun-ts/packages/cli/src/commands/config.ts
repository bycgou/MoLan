import { Command } from 'commander';
import chalk from 'chalk';
import { getDb, LlmConfigService } from '@moyun/core';

export const configCommand = new Command('config')
  .description('LLM配置管理');

configCommand
  .command('list')
  .description('列出所有LLM配置')
  .action(() => {
    const db = getDb();
    const service = new LlmConfigService(db);
    const configs = service.list();
    if (configs.length === 0) {
      console.log(chalk.yellow('暂无LLM配置。使用 moyun config create 创建。'));
      return;
    }
    console.log(chalk.bold('\nLLM配置:\n'));
    for (const c of configs) {
      const active = c.isActive ? chalk.green('✓') : chalk.gray('✗');
      console.log(`  ${active} ${chalk.cyan(c.id)}  ${chalk.white(c.provider)}  ${c.model}  [${c.usageType}]`);
    }
    console.log();
  });

configCommand
  .command('create')
  .description('添加LLM配置')
  .requiredOption('--provider <provider>', '提供商 (openai/deepseek/ollama/zhipu/qwen/moonshot/wenxin)')
  .requiredOption('--model <model>', '模型名')
  .option('--api-key <key>', 'API Key')
  .option('--base-url <url>', 'Base URL')
  .option('--usage <usage>', '用途 (general/outline/writing/summary/audit)', 'general')
  .option('--temperature <temp>', '温度', '0.7')
  .action((opts) => {
    const db = getDb();
    const service = new LlmConfigService(db);
    const config = service.create({
      provider: opts.provider,
      model: opts.model,
      apiKey: opts.apiKey,
      baseUrl: opts.baseUrl,
      usageType: opts.usage,
      temperature: parseFloat(opts.temperature),
    });
    console.log(chalk.green(`\n✓ LLM配置已创建: ${config.provider}/${config.model} (${config.id})\n`));
  });

configCommand
  .command('delete <id>')
  .description('删除LLM配置')
  .action((id) => {
    const db = getDb();
    const service = new LlmConfigService(db);
    service.delete(id);
    console.log(chalk.green(`\n✓ 配置已删除\n`));
  });
