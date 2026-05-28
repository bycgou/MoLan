import { Command } from 'commander';
import chalk from 'chalk';
import { getDb, WizardService } from '@moyun/core';

export const wizardCommand = new Command('wizard')
  .description('向导模式创建小说');

wizardCommand
  .command('start')
  .description('启动5步向导创建小说')
  .requiredOption('--title <title>', '小说标题')
  .requiredOption('--genre <genre>', '类型')
  .option('--outline <outline>', '总大纲')
  .option('--world <world>', '世界观设定')
  .option('--type <type>', '小说类型')
  .option('--tone <tone>', '基调')
  .option('--taboo <taboo...>', '禁忌事项')
  .action(async (opts) => {
    const db = getDb();
    const service = new WizardService(db);

    const settings = {
      novelName: opts.title,
      genre: opts.genre,
      totalOutline: opts.outline,
      worldSetting: opts.world,
      novelType: opts.type,
      tone: opts.tone,
      tabooList: opts.taboo,
    };

    // Step 1: Generate volume outlines
    console.log(chalk.cyan('\n[Step 1/5] 正在生成卷纲...'));
    let arcOutlines;
    try {
      arcOutlines = await service.generateVolumeOutlines(settings);
    } catch (err: any) {
      console.log(chalk.red(`卷纲生成失败: ${err.message}`));
      return;
    }

    if (arcOutlines.length === 0) {
      console.log(chalk.red('未能生成卷纲，请检查大纲设置'));
      return;
    }

    console.log(chalk.green(`\n生成了 ${arcOutlines.length} 卷:\n`));
    for (const arc of arcOutlines) {
      console.log(chalk.bold(`  ${arc.arcName}`));
      console.log(chalk.gray(`    第${arc.startChapter}-${arc.endChapter}章 | ${arc.rhythmTag} | 强度${arc.rhythmIntensity}`));
      console.log(chalk.gray(`    ${arc.arcOutline.substring(0, 80)}...`));
    }

    // Step 2: Save novel with arcs (auto-approve for CLI)
    console.log(chalk.cyan('\n[Step 2/5] 保存小说结构...'));
    const novelId = service.saveNovelWithArcs(settings, arcOutlines);
    console.log(chalk.green(`小说已创建: ${novelId}`));

    // Step 3: Generate chapter outlines for first arc
    console.log(chalk.cyan('\n[Step 3/5] 生成第一卷章节大纲...'));
    try {
      const chapterOutlines = await service.generateArcChapterOutlines(novelId, 0);
      console.log(chalk.green(`生成了 ${chapterOutlines.length} 章大纲`));
      for (const ch of chapterOutlines.slice(0, 5)) {
        console.log(chalk.gray(`  第${ch.chapterNum}章: ${ch.chapterTitle}`));
      }
      if (chapterOutlines.length > 5) {
        console.log(chalk.gray(`  ... 还有 ${chapterOutlines.length - 5} 章`));
      }
    } catch (err: any) {
      console.log(chalk.yellow(`章节大纲生成失败: ${err.message}`));
      console.log(chalk.gray('可以稍后使用 wizard batch 命令重新生成'));
    }

    // Step 4: Ask about batch generation
    console.log(chalk.cyan('\n[Step 4/5] 准备批量生成...'));
    console.log(chalk.gray(`使用 moyun wizard batch ${novelId} 启动批量正文生成`));

    console.log(chalk.green('\n[Step 5/5] 向导完成!'));
    console.log(chalk.bold(`\n小说ID: ${novelId}`));
    console.log(chalk.gray('后续操作:'));
    console.log(chalk.gray(`  moyun wizard batch ${novelId}    - 批量生成正文`));
    console.log(chalk.gray(`  moyun novel get ${novelId}       - 查看小说详情`));
    console.log();
  });

wizardCommand
  .command('batch <novelId>')
  .description('启动批量正文生成')
  .action(async (novelId) => {
    const db = getDb();
    const service = new WizardService(db);

    try {
      const taskId = await service.startBatchGeneration(novelId);
      console.log(chalk.green(`\n批量生成已启动`));
      console.log(chalk.gray(`任务ID: ${taskId}`));
      console.log(chalk.gray(`使用 moyun wizard status ${novelId} ${taskId} 查看进度\n`));
    } catch (err: any) {
      console.log(chalk.red(`启动失败: ${err.message}`));
    }
  });

wizardCommand
  .command('status <novelId> <taskId>')
  .description('查看批量生成进度')
  .action((novelId, taskId) => {
    const db = getDb();
    const service = new WizardService(db);

    const progress = service.getBatchProgress(taskId);
    if (!progress) {
      console.log(chalk.red(`任务 ${taskId} 不存在`));
      return;
    }

    console.log(chalk.bold('\n批量生成进度:\n'));
    console.log(`  状态:   ${statusLabel(progress.status)}`);
    console.log(`  总计:   ${progress.total} 章`);
    console.log(`  完成:   ${chalk.green(progress.completed)} 章`);
    console.log(`  失败:   ${progress.failed > 0 ? chalk.red(progress.failed) : chalk.gray('0')} 章`);
    if (progress.currentChapterNum) {
      console.log(`  当前:   第${progress.currentChapterNum}章`);
    }
    if (progress.errorMessage) {
      console.log(`  错误:   ${chalk.red(progress.errorMessage)}`);
    }
    console.log();
  });

wizardCommand
  .command('cancel <novelId> <taskId>')
  .description('取消批量生成')
  .action((novelId, taskId) => {
    const db = getDb();
    const service = new WizardService(db);

    service.cancelBatchGeneration(taskId);
    console.log(chalk.yellow(`\n已发送取消请求: ${taskId}\n`));
  });

function statusLabel(status: string): string {
  switch (status) {
    case 'generating': return chalk.blue('生成中');
    case 'completed': return chalk.green('已完成');
    case 'failed': return chalk.red('失败');
    case 'cancelled': return chalk.yellow('已取消');
    default: return status;
  }
}
