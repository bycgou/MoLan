import { Command } from 'commander';
import chalk from 'chalk';
import { getDb, CharacterService } from '@moyun/core';

export const characterCommand = new Command('character')
  .description('角色管理');

characterCommand
  .command('list <novelId>')
  .description('列出小说的所有角色')
  .action((novelId) => {
    const db = getDb();
    const service = new CharacterService(db);
    const chars = service.list(novelId);
    if (chars.length === 0) {
      console.log(chalk.yellow('暂无角色。'));
      return;
    }
    console.log(chalk.bold(`\n角色列表 (${chars.length} 个):\n`));
    for (const c of chars) {
      const role = c.role === 'protagonist' ? chalk.red('主角')
        : c.role === 'supporting' ? chalk.blue('配角')
        : c.role === 'villain' ? chalk.magenta('反派')
        : chalk.gray('路人');
      console.log(`  ${chalk.cyan(c.id)}  ${chalk.white(c.name)}  ${role}`);
    }
    console.log();
  });

characterCommand
  .command('create <novelId>')
  .description('创建新角色')
  .requiredOption('--name <name>', '角色名')
  .option('--role <role>', '角色类型 (protagonist/supporting/villain/extra)', 'supporting')
  .option('--desc <desc>', '角色描述')
  .option('--personality <personality>', '性格')
  .option('--background <background>', '背景')
  .action((novelId, opts) => {
    const db = getDb();
    const service = new CharacterService(db);
    const ch = service.create({
      novelId,
      name: opts.name,
      role: opts.role,
      description: opts.desc,
      personality: opts.personality,
      background: opts.background,
    });
    console.log(chalk.green(`\n✓ 角色已创建: ${ch.name} (${ch.id})\n`));
  });

characterCommand
  .command('get <id>')
  .description('查看角色详情')
  .action((id) => {
    const db = getDb();
    const service = new CharacterService(db);
    const ch = service.getById(id);
    if (!ch) {
      console.log(chalk.red(`角色 ${id} 不存在`));
      return;
    }
    console.log(chalk.bold(`\n${ch.name}\n`));
    console.log(`  ID:       ${ch.id}`);
    console.log(`  类型:     ${ch.role}`);
    if (ch.description) console.log(`  描述:     ${ch.description}`);
    if (ch.personality) console.log(`  性格:     ${ch.personality}`);
    if (ch.background) console.log(`  背景:     ${ch.background}`);
    if (ch.growthNodes.length > 0) {
      console.log(`\n  成长节点:`);
      for (const g of ch.growthNodes) {
        console.log(`    第${g.chapterNum}章 [${g.type}] ${g.title}`);
      }
    }
    console.log();
  });

characterCommand
  .command('delete <id>')
  .description('删除角色')
  .action((id) => {
    const db = getDb();
    const service = new CharacterService(db);
    service.delete(id);
    console.log(chalk.green(`\n✓ 角色已删除\n`));
  });
