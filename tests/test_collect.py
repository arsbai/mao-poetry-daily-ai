import hashlib, json, sys, unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'backend'))
from collect import parse_article, plain, ROOT, clean_author_text, clean_poem_text

class SourceTests(unittest.TestCase):
 def test_inline_note_markers_preserve_remaining_poem(self):
  raw='原驰蜡象，（原注）欲与天公试比高。\n还看今朝。\n作者原注\n原指高原。'
  self.assertEqual(clean_poem_text(raw),'原驰蜡象，欲与天公试比高。还看今朝。')
  poems=json.loads((ROOT/'corpus/poems.json').read_text(encoding='utf-8'))
  snow=next(d for d in poems if d['title']=='沁园春·雪')
  self.assertIn('还看今朝',snow['text']);self.assertNotIn('原指高原',snow['text'])
 def test_editorial_notes_and_footnotes_never_become_author_text(self):
  raw="<p class='title1'>测试</p><p class='date'>（一九四五年）</p><p align='center'>* 这是编者说明。</p><br>这是作者自己说的第一句话，原文应当保存。<a href='#n'><sup>[1]</sup></a><br>这是作者的第二段话，继续保留。<hr><b>注释</b>这段是他人注释。"
  d=parse_article(raw,'测试','https://example.com/source')
  self.assertIsNotNone(d)
  self.assertNotIn('编者',d['text']);self.assertNotIn('他人',d['text']);self.assertNotIn('[1]',d['text'])
  self.assertIn('作者自己',d['text'])
 def test_collective_works_are_excluded(self):
  raw="<p class='date'>一九三九年</p>"+'原文内容。'*20
  self.assertIsNone(parse_article(raw,'中国革命和中国共产党','https://example.com/'))
 def test_legacy_metadata_blocks_and_publisher_lines_are_not_indexed(self):
  text='* 这是毛泽东为报纸起草的评论。\n\n作者第一段，留下原文。\n\n（《毛泽东选集》第四卷，第3页）'
  self.assertEqual(clean_author_text(text),'作者第一段，留下原文。')
  text='毛泽东此文是为反对当时的倾向而写的。\n\n谁是我们的敌人？'
  self.assertEqual(clean_author_text(text),'谁是我们的敌人？')
 def test_interviewer_words_are_not_attributed_to_mao(self):
  text='记者问：问题不是作者的原话。\n\n毛答：这是作者的答语。\n\n继续答语。\n\n问：下一问。\n\n答：下一答。'
  actual=clean_author_text(text,'和记者的谈话')
  self.assertNotIn('问题不是',actual);self.assertNotIn('下一问',actual)
  self.assertIn('这是作者的答语',actual);self.assertIn('下一答',actual)
 def test_documents_have_unique_ids_and_valid_hashes(self):
  data=json.loads((ROOT/'web/corpus.json').read_text(encoding='utf-8'))['documents']
  self.assertEqual(len(data),len({x['id'] for x in data}))
  for d in data:
   self.assertEqual(d['author'],'毛泽东')
   self.assertEqual(d['sha256'],hashlib.sha256(d['text'].encode()).hexdigest())
   self.assertTrue(d['source_url'].startswith('https://'))
   self.assertNotIn('\ufffd',d['text'])
 def test_knowledge_export_contains_no_media_downloads(self):
  allowed={'.json','.jsonl','.txt'}
  for p in (ROOT/'corpus').rglob('*'):
   if p.is_file(): self.assertIn(p.suffix,allowed)

if __name__=='__main__': unittest.main()
