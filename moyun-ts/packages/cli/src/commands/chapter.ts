import { Command } from 'commander';
import chalk from 'chalk';
import { getDb, ChapterService, findCliches, findTransitionCliches, findUpgradeCliches } from '@moyun/core';

export const chapterCommand = new Command('chapter')
  .description('章节管理');

chapterCommand
  .command('list <novelId>')
  .description('列出小说的所有章节')
  .action((novelId) => {
    const db = getDb();
    const service = new ChapterService(db);
    const chapters = service.list(novelId);

    if (chapters.length === 0) {
      console.log(chalk.yellow('暂无章节。'));
      return;
    }

    console.log(chalk.bold(`\n章节列表 (${chapters.length} 章):\n`));
    for (const ch of chapters) {
      const words = chalk.gray(`${ch.wordCount}字`);
      const strand = ch.strandType ? chalk.magenta(`[${ch.strandType}]`) : '';
      console.log(`  第${ch.chapterNum}章  ${ch.title ?? chalk.gray('(无标题)')}  ${words}  ${strand}`);
    }
    console.log();
  });

chapterCommand
  .command('read <novelId> <num>')
  .description('阅读章节')
  .action((novelId, num) => {
    const db = getDb();
    const service = new ChapterService(db);
    const ch = service.getByNum(novelId, parseInt(num));
    if (!ch) {
      console.log(chalk.red(`章节不存在`));
      return;
    }
    console.log(chalk.bold(`\n第${ch.chapterNum}章 ${ch.title ?? ''}\n`));
    console.log(`  字数: ${ch.wordCount}\n`);
    if (ch.content) {
      console.log(ch.content);
    } else {
      console.log(chalk.gray('(章节内容为空)'));
    }
    console.log();
  });

chapterCommand
  .command('create <novelId> <num>')
  .description('创建新章节')
  .option('--title <title>', '章节标题')
  .action((novelId, num, opts) => {
    const db = getDb();
    const service = new ChapterService(db);
    const ch = service.create({
      novelId,
      chapterNum: parseInt(num),
      title: opts.title,
    });
    console.log(chalk.green(`\n✓ 章节已创建: 第${ch.chapterNum}章 (${ch.id})\n`));
  });

chapterCommand
  .command('edit <novelId> <num>')
  .description('编辑章节')
  .option('--title <title>', '设置标题')
  .option('--content <content>', '设置内容')
  .action((novelId, num, opts) => {
    const db = getDb();
    const service = new ChapterService(db);
    const ch = service.getByNum(novelId, parseInt(num));
    if (!ch) {
      console.log(chalk.red(`章节不存在`));
      return;
    }
    const updates: { title?: string; content?: string } = {};
    if (opts.title) updates.title = opts.title;
    if (opts.content) updates.content = opts.content;
    const updated = service.update(novelId, parseInt(num), updates);
    console.log(chalk.green(`\n✓ 章节已更新: 第${updated.chapterNum}章 (${updated.wordCount}字)\n`));
  });

chapterCommand
  .command('delete <novelId> <num>')
  .description('删除章节')
  .action((novelId, num) => {
    const db = getDb();
    const service = new ChapterService(db);
    service.softDelete(novelId, parseInt(num));
    console.log(chalk.green(`\n✓ 章节已删除\n`));
  });

chapterCommand
  .command('generate <novelId> <num>')
  .description('AI生成章节')
  .action(async (novelId, num) => {
    const { ChapterGenerationService } = await import('@moyun/core');
    const db = getDb();
    const service = new ChapterGenerationService(db);
    console.log(chalk.bold(`\n正在生成第${num}章...\n`));
    try {
      const result = await service.generate({
        novelId,
        chapterNum: parseInt(num),
        callbacks: {
          onOutline: (outline) => {
            console.log(chalk.cyan(`大纲: ${outline.title}`));
            console.log(chalk.gray(`  ${outline.summary}`));
          },
          onWritingStart: () => console.log(chalk.yellow('写作中...')),
          onValidation: (results) => {
            const status = results.allPassed ? chalk.green('通过') : chalk.red('未通过');
            console.log(`验证: ${status}`);
          },
          onQuality: (score) => {
            console.log(chalk.cyan(`质量评分: ${score.totalScore}/100`));
          },
        },
      });
      console.log(chalk.green(`\n✓ 第${result.chapterNum}章生成完成: ${result.title} (${result.wordCount}字)\n`));
    } catch (error) {
      console.log(chalk.red(`\n✗ 生成失败: ${error instanceof Error ? error.message : '未知错误'}\n`));
    }
  });

chapterCommand
  .command('check-cliches <novelId> <num>')
  .description('检查章节AI套话')
  .action((novelId, num) => {
    const db = getDb();
    const service = new ChapterService(db);
    const ch = service.getByNum(novelId, parseInt(num));
    if (!ch || !ch.content) {
      console.log(chalk.red('章节不存在或内容为空'));
      return;
    }
    const cliches = findCliches(ch.content);
    const transitions = findTransitionCliches(ch.content);
    const upgrades = findUpgradeCliches(ch.content);
    const total = cliches.length + transitions.length + upgrades.length;
    if (total === 0) {
      console.log(chalk.green('\n✓ 未检测到AI套话\n'));
      return;
    }
    console.log(chalk.yellow(`\n检测到 ${total} 处AI套话:\n`));
    if (cliches.length > 0) console.log(chalk.red(`  高频套话 (${cliches.length}): ${cliches.join('、')}`));
    if (transitions.length > 0) console.log(chalk.red(`  过渡句 (${transitions.length}): ${transitions.join('、')}`));
    if (upgrades.length > 0) console.log(chalk.red(`  升级流水账 (${upgrades.length}): ${upgrades.join('、')}`));
    console.log();
  });
