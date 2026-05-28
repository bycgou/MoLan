import { Command } from 'commander';
import chalk from 'chalk';
import { getDb, StyleCardService } from '@moyun/core';

export const styleCommand = new Command('style')
  .description('风格卡管理');

styleCommand
  .command('list')
  .description('列出所有风格卡')
  .action(() => {
    const db = getDb();
    const service = new StyleCardService(db);
    const cards = service.list();
    if (cards.length === 0) {
      console.log(chalk.yellow('暂无风格卡。'));
      return;
    }
    console.log(chalk.bold('\n风格卡:\n'));
    for (const c of cards) {
      const pub = c.isPublic ? chalk.green('公开') : chalk.gray('私有');
      console.log(`  ${chalk.cyan(c.id)}  ${chalk.white(c.name)}  ${pub}`);
    }
    console.log();
  });

styleCommand
  .command('create')
  .description('创建风格卡')
  .requiredOption('--name <name>', '风格卡名称')
  .option('--sample <text>', '示例文本')
  .action((opts) => {
    const db = getDb();
    const service = new StyleCardService(db);
    const card = service.create({ name: opts.name, sampleText: opts.sample });
    console.log(chalk.green(`\n✓ 风格卡已创建: ${card.name} (${card.id})\n`));
  });

styleCommand
  .command('bind <novelId> <cardId>')
  .description('绑定风格卡到小说')
  .action((novelId, cardId) => {
    const db = getDb();
    const service = new StyleCardService(db);
    service.bindToNovel(novelId, cardId);
    console.log(chalk.green(`\n✓ 风格卡已绑定\n`));
  });

styleCommand
  .command('unbind <novelId> <cardId>')
  .description('解绑风格卡')
  .action((novelId, cardId) => {
    const db = getDb();
    const service = new StyleCardService(db);
    service.unbindFromNovel(novelId, cardId);
    console.log(chalk.green(`\n✓ 风格卡已解绑\n`));
  });
