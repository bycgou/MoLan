import fs from 'fs';
import { Command } from 'commander';
import chalk from 'chalk';
import { getDb, NovelService } from '@moyun/core';

export const novelCommand = new Command('novel')
  .description('小说管理');

novelCommand
  .command('list')
  .description('列出所有小说')
  .action(() => {
    const db = getDb();
    const service = new NovelService(db);
    const novels = service.list();

    if (novels.length === 0) {
      console.log(chalk.yellow('暂无小说。使用 moyun novel create 创建。'));
      return;
    }

    console.log(chalk.bold('\n我的小说:\n'));
    for (const n of novels) {
      const status = n.status === 'draft' ? chalk.gray('草稿')
        : n.status === 'serializing' ? chalk.blue('连载中')
        : n.status === 'completed' ? chalk.green('已完成')
        : chalk.gray(n.status);
      console.log(`  ${chalk.cyan(n.id)}  ${chalk.white(n.title)}  ${status}  ${chalk.gray(n.genre)}`);
    }
    console.log();
  });

novelCommand
  .command('create')
  .description('创建新小说')
  .requiredOption('--title <title>', '小说标题')
  .option('--genre <genre>', '类型', 'xuanhuan')
  .option('--outline <outline>', '总大纲')
  .option('--world <world>', '世界观设定')
  .action((opts) => {
    const db = getDb();
    const service = new NovelService(db);
    const novel = service.create({
      title: opts.title,
      genre: opts.genre,
      totalOutline: opts.outline,
      worldSetting: opts.world,
    });
    console.log(chalk.green(`\n✓ 小说已创建: ${novel.title} (${novel.id})\n`));
  });

novelCommand
  .command('get <id>')
  .description('查看小说详情')
  .action((id) => {
    const db = getDb();
    const service = new NovelService(db);
    const novel = service.getById(id);
    if (!novel) {
      console.log(chalk.red(`小说 ${id} 不存在`));
      return;
    }
    console.log(chalk.bold(`\n${novel.title}\n`));
    console.log(`  ID:     ${novel.id}`);
    console.log(`  类型:   ${novel.genre}`);
    console.log(`  状态:   ${novel.status}`);
    console.log(`  模式:   ${novel.creationMode}`);
    if (novel.totalOutline) console.log(`  大纲:   ${novel.totalOutline.substring(0, 100)}...`);
    if (novel.worldSetting) console.log(`  世界观: ${novel.worldSetting.substring(0, 100)}...`);
    console.log();
  });

novelCommand
  .command('delete <id>')
  .description('删除小说')
  .action((id) => {
    const db = getDb();
    const service = new NovelService(db);
    const novel = service.getById(id);
    if (!novel) {
      console.log(chalk.red(`小说 ${id} 不存在`));
      return;
    }
    service.delete(id);
    console.log(chalk.green(`\n✓ 已删除: ${novel.title}\n`));
  });

novelCommand
  .command('export <id>')
  .description('导出小说为TXT文件')
  .option('-o, --output <path>', '输出文件路径')
  .action((id, opts) => {
    const db = getDb();
    const service = new NovelService(db);
    const novel = service.getById(id);
    if (!novel) {
      console.log(chalk.red(`小说 ${id} 不存在`));
      return;
    }
    const txt = service.exportTxt(id);
    const outputPath = opts.output ?? `${novel.title}.txt`;
    fs.writeFileSync(outputPath, txt, 'utf-8');
    console.log(chalk.green(`\n✓ 已导出: ${outputPath}\n`));
  });
