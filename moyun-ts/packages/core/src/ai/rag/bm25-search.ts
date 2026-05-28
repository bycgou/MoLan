import MiniSearch from 'minisearch';

export interface BM25Document {
  id: string;
  novelId: string;
  chapterNum: number;
  content: string;
  keywords: string;
}

export class BM25Search {
  private miniSearch: MiniSearch<BM25Document>;

  constructor() {
    this.miniSearch = new MiniSearch<BM25Document>({
      fields: ['content', 'keywords'],
      storeFields: ['novelId', 'chapterNum', 'content'],
      tokenize: (text) => this.chineseTokenize(text),
      searchOptions: { tokenize: (text) => this.chineseTokenize(text) },
    });
  }

  addDocuments(docs: BM25Document[]): void {
    this.miniSearch.addAll(docs);
  }

  addDocument(doc: BM25Document): void {
    this.miniSearch.add(doc);
  }

  search(query: string, novelId: string, topK: number = 10): Array<{ id: string; score: number; doc: BM25Document }> {
    const results = this.miniSearch.search(query, {
      filter: (result) => result.novelId === novelId,
    });
    return results.slice(0, topK).map(r => ({
      id: r.id,
      score: r.score,
      doc: r as unknown as BM25Document,
    }));
  }

  private chineseTokenize(text: string): string[] {
    const segments = text.split(/[，。！？：；“”‘’（）、\s]+/).filter(s => s.length > 0);
    const tokens: string[] = [];
    for (const seg of segments) {
      // 2-gram sliding window for Chinese
      for (let i = 0; i < seg.length - 1; i++) {
        tokens.push(seg.substring(i, i + 2));
      }
      if (seg.length >= 2) tokens.push(seg);
    }
    return tokens;
  }
}
