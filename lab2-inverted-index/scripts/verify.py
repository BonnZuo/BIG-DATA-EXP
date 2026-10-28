from pathlib import Path
from collections import Counter
import re
root=Path(__file__).resolve().parents[1]
stops=set((root/'stopwords.txt').read_text().split())
expected={}; tokens=0
for file in sorted((root/'data').glob('*.txt')):
 counts=Counter(w for w in re.findall('[a-z]+',file.read_text().lower()) if w not in stops)
 tokens+=sum(counts.values())
 for word,count in counts.items(): expected.setdefault(word,{})[file.name]=count
actual={}
for line in (root/'results'/'part-r-00000').read_text().splitlines():
 word,postings=line.split('\t')
 pairs=re.findall(r'<([^,<>]+),(\d+)>',postings)
 assert word not in actual, 'Duplicate word: '+word
 assert pairs[-1][0]=='total'
 docs={name:int(n) for name,n in pairs[:-1]}
 assert len(docs)==len(pairs)-1
 assert sum(docs.values())==int(pairs[-1][1]),word
 actual[word]=docs
assert actual==expected, 'Hadoop output differs from independent Python reference'
assert not set(actual).intersection(stops)
print('PASS: Hadoop output matches independent Python reference for ALL words.')
print('Input documents:',len(list((root/'data').glob('*.txt'))))
print('Unique indexed words:',len(actual))
print('Total indexed tokens:',tokens)
print('PASS: All per-document frequencies and totals match.')
print('PASS: No stopwords appear in the output.')
print('PASS: Each word and each document posting appears only once.')
for word in ['data','hadoop','mapreduce','file','cluster']:
 print(word, actual.get(word,{}), 'total=',sum(actual.get(word,{}).values()))
