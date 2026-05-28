import { Command } from 'commander';
import chalk from 'chalk';
import { getDb, ClueService } from '@moyun/core';

export const clueCommand = new Command('clue')
  .description('线索/伏笔管理');

clueCommand
  .command('list <novelId>')
  .description('列出所有线索')
  .option('--active', '仅显示未回收')
  .action((novelId, opts) => {
    const db = getDb();
    const service = new ClueService(db);
    const clues = opts.active ? service.listActive(novelId) : service.list(novelId);
    if (clues.length === 0) {
      console.log(chalk.yellow('暂无线索。'));
      return;
    }
    console.log(chalk.bold(`\n线索列表 (${clues.length} 条):\n`));
    for (const c of clues) {
      const type = c.type === 'main_plot' ? chalk.red('主线')
        : c.type === 'subplot' ? chalk.blue('支线')
        : chalk.magenta('暗线');
      const status = c.isRecycled ? chalk.gray('已回收') : chalk.green('活跃');
      const bury = c.buryChapter ? ` 第${c.buryChapter}章埋` : '';
      console.log(`  ${chalk.cyan(c.id)}  ${type}  ${chalk.white(c.title)}  ${status}${bury}`);
    }
    console.log();
  });

clueCommand
  .command('create <novelId>')
  .description('创建新线索')
  .requiredOption('--title <title>', '线索标题')
  .requiredOption('--type <type>', '类型 (main_plot/subplot/dark_line)')
  .option('--desc <desc>', '描述')
  .option('--bury <chapter>', '埋设章节')
  .action((novelId, opts) => {
    const db = getDb();
    const service = new ClueService(db);
    const clue = service.create({
      novelId,
      title: opts.title,
      type: opts.type,
      description: opts.desc,
      buryChapter: opts.bury ? parseInt(opts.bury) : undefined,
    });
    console.log(chalk.green(`\n✓ 线索已创建: ${clue.title} (${clue.id})\n`));
  });

clueCommand
  .command('recycle <id>')
  .description('回收线索')
  .requiredOption('--chapter <num>', '回收章节')
  .action((id, opts) => {
    const db = getDb();
    const service = new ClueService(db);
    const clue = service.recycle(id, parseInt(opts.chapter));
    console.log(chalk.green(`\n✓ 线索已回收: ${clue.title} (第${clue.recycleChapter}章)\n`));
  });
