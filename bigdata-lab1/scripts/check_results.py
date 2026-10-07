from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
actual=Path(sys.argv[1]) if len(sys.argv)>1 else root/'expected'
for task in ['dedup','sort','family']:
 a=(actual/(task+'.txt')).read_text().strip().splitlines()
 e=(root/'expected'/(task+'.txt')).read_text().strip().splitlines()
 if task=='family': assert set(a)==set(e), (task,a,e)
 else: assert a==e,(task,a,e)
 print(task, 'PASS', len(a), 'rows')
