export interface GenreTemplate {
  id: string;
  name: string;
  description: string;
  suggestedOutline: string;
  characterTemplates: { name: string; role: string; description: string }[];
  worldSettingTemplate: string;
  toneTags: string[];
  storyTags: string[];
  tabooList: string[];
}

export const GENRE_TEMPLATES: GenreTemplate[] = [
  {
    id: 'xuanhuan',
    name: '玄幻',
    description: '东方玄幻修仙题材，主角从凡人修炼成仙',
    suggestedOutline:
      '一个出身平凡的少年，偶得机缘踏上修仙之路。历经磨难，不断突破境界，最终站在世界之巅。\n\n主线：修炼升级 → 遭遇强敌 → 获得机缘 → 突破境界\n支线：宗门争斗、秘境探险、上古遗迹、天道之谜',
    characterTemplates: [
      { name: '主角', role: 'protagonist', description: '出身平凡但天赋异禀的少年，性格坚韧不拔' },
      { name: '红颜知己', role: 'supporting', description: '与主角青梅竹马或因缘际会的女修' },
      { name: '宗门长老', role: 'supporting', description: '亦师亦友的长辈，给予主角指引' },
      { name: '反派BOSS', role: 'villain', description: '最终大反派，可能是魔道巨擘或天道化身' },
    ],
    worldSettingTemplate:
      '修炼体系：练气→筑基→金丹→元婴→化神→合体→大乘→渡劫\n\n势力分布：各大宗门、散修联盟、魔道势力、上古种族\n\n天材地宝：灵石、丹药、法宝、功法\n\n特殊区域：秘境、禁地、上古战场',
    toneTags: ['热血', '爽文', '升级'],
    storyTags: ['修仙', '玄幻', '打怪升级'],
    tabooList: ['主角不能长期受虐', '不能连续3章以上没有突破或收获'],
  },
  {
    id: 'dog-blood-romance',
    name: '狗血言情',
    description: '霸道总裁/豪门恩怨/虐恋情深',
    suggestedOutline:
      '女主因某种原因与男主产生纠葛（契约婚姻/替嫁/误会），从互相看不顺眼到暗生情愫。中间穿插白月光、恶毒女配、身世之谜等元素，最终HE。\n\n主线：相遇误会 → 日久生情 → 虐心分离 → 真相大白 → HE\n支线：豪门争斗、身世之谜、职场逆袭',
    characterTemplates: [
      { name: '女主', role: 'protagonist', description: '外表柔弱内心坚强，有自己的事业心' },
      { name: '男主', role: 'supporting', description: '表面冷漠实则深情，有隐藏的温柔面' },
      { name: '白月光', role: 'villain', description: '男主的前任或暗恋对象，表面清纯实则心机' },
      { name: '闺蜜', role: 'supporting', description: '女主的好姐妹，提供情感支持' },
    ],
    worldSettingTemplate:
      '现代都市背景\n\n核心场景：豪门大宅、高端写字楼、奢侈品店、高档餐厅\n\n社会关系：豪门家族、商业帝国、娱乐圈\n\n关键道具：契约、遗嘱、DNA报告、监控录像',
    toneTags: ['甜宠', '虐恋', '爽文'],
    storyTags: ['言情', '豪门', '都市'],
    tabooList: ['女主不能太圣母', '误会不能超过5章不解释'],
  },
  {
    id: 'period-drama',
    name: '古风宫斗/宅斗',
    description: '古代背景的宫廷或宅院争斗',
    suggestedOutline:
      '女主重生/穿越到古代，凭借前世记忆或现代知识，在后宅/后宫中步步为营，从被人欺压的小角色成长为掌权者。\n\n主线：隐忍蛰伏 → 小试牛刀 → 步步高升 → 大权在握\n支线：家族兴衰、朝堂风云、情感纠葛',
    characterTemplates: [
      { name: '女主', role: 'protagonist', description: '聪慧冷静，善于隐忍和谋划' },
      { name: '男主', role: 'supporting', description: '身份尊贵的皇子/王爷/世家公子' },
      { name: '嫡母/皇后', role: 'villain', description: '主要对手，掌控后宅/后宫的实权人物' },
      { name: '忠仆/丫鬟', role: 'supporting', description: '女主的心腹，忠心耿耿' },
    ],
    worldSettingTemplate:
      '架空古代王朝\n\n核心场景：后宅/后宫、朝堂、宴会、寺庙\n\n权力结构：皇室、世家、外戚、宦官\n\n关键元素：嫡庶之分、礼法规矩、选秀、赐婚',
    toneTags: ['权谋', '宫斗', '爽文'],
    storyTags: ['古风', '宫斗', '宅斗'],
    tabooList: ['女主不能太白莲', '权谋不能太降智'],
  },
  {
    id: 'realistic',
    name: '现实题材',
    description: '贴近现实生活的都市/乡村故事',
    suggestedOutline:
      '主人公面对现实生活中的困境（职场、家庭、情感），通过努力和成长找到出路。\n\n主线：遭遇困境 → 挣扎求变 → 找到方向 → 实现价值\n支线：人际关系、社会问题、自我认知',
    characterTemplates: [
      { name: '主角', role: 'protagonist', description: '普通人，有缺点但努力生活的平凡人' },
      { name: '挚友', role: 'supporting', description: '主角的知心朋友，互相支持' },
      { name: '对手', role: 'villain', description: '职场或生活中的竞争对手' },
    ],
    worldSettingTemplate:
      '现代中国城市/乡村\n\n核心场景：公司、家庭、社区、学校\n\n社会背景：职场内卷、房价压力、代际矛盾、中年危机\n\n时代特征：互联网、外卖、短视频、996',
    toneTags: ['现实', '温暖', '成长'],
    storyTags: ['都市', '现实', '职场'],
    tabooList: ['不能太鸡汤', '不能回避现实问题'],
  },
  {
    id: 'rules-mystery',
    name: '规则怪谈',
    description: '诡异规则类悬疑恐怖',
    suggestedOutline:
      '主角进入一个充满诡异规则的空间（副本/异世界/密室），必须遵守规则才能生存，违反规则就会遭遇恐怖事件。\n\n主线：进入副本 → 发现规则 → 探索真相 → 破解规则\n支线：队友背叛、规则漏洞、隐藏真相',
    characterTemplates: [
      { name: '主角', role: 'protagonist', description: '冷静理性，善于观察和推理' },
      { name: '队友', role: 'supporting', description: '临时组队的同伴，可能有隐藏身份' },
      { name: 'BOSS', role: 'villain', description: '规则的制定者或守护者' },
    ],
    worldSettingTemplate:
      '诡异副本/异空间\n\n核心机制：规则系统（必须遵守的诡异规则）\n\n危险源：违反规则的惩罚、隐藏的陷阱、NPC的谎言\n\n生存要素：观察力、推理能力、心理素质',
    toneTags: ['悬疑', '恐怖', '烧脑'],
    storyTags: ['规则怪谈', '无限流', '悬疑'],
    tabooList: ['规则不能前后矛盾', '不能靠运气过关'],
  },
  {
    id: 'zhihu-short',
    name: '知乎短篇',
    description: '短小精悍的知乎体故事',
    suggestedOutline:
      '以第一人称讲述一个引人入胜的故事，开头抛出悬念，中间反转不断，结尾出人意料。\n\n主线：抛出悬念 → 展开故事 → 反转 → 高潮 → 意外结局\n特点：节奏快、反转多、代入感强',
    characterTemplates: [
      { name: '我', role: 'protagonist', description: '第一人称叙述者' },
      { name: '关键人物', role: 'supporting', description: '推动情节发展的关键角色' },
    ],
    worldSettingTemplate:
      '现代都市或特定场景\n\n叙事风格：口语化、碎片化、有代入感\n\n结构特点：短段落、多对话、快节奏\n\n关键元素：悬念、反转、意外结局',
    toneTags: ['反转', '悬疑', '短篇'],
    storyTags: ['知乎体', '短篇', '反转'],
    tabooList: ['不能太长', '反转不能太生硬'],
  },
];

export function getGenreTemplate(id: string): GenreTemplate | undefined {
  return GENRE_TEMPLATES.find((t) => t.id === id);
}

export function listGenreTemplates(): Pick<GenreTemplate, 'id' | 'name' | 'description'>[] {
  return GENRE_TEMPLATES.map(({ id, name, description }) => ({ id, name, description }));
}
