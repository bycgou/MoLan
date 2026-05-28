#!/usr/bin/env node
import { Command } from 'commander';
import { novelCommand } from './commands/novel.js';
import { chapterCommand } from './commands/chapter.js';
import { characterCommand } from './commands/character.js';
import { clueCommand } from './commands/clue.js';
import { configCommand } from './commands/config.js';
import { styleCommand } from './commands/style.js';
import { wizardCommand } from './commands/wizard.js';

const program = new Command();

program
  .name('moyun')
  .description('墨韵 AI 长篇小说创作系统')
  .version('0.1.0');

program.addCommand(novelCommand);
program.addCommand(chapterCommand);
program.addCommand(characterCommand);
program.addCommand(clueCommand);
program.addCommand(configCommand);
program.addCommand(styleCommand);
program.addCommand(wizardCommand);

program.parse();
