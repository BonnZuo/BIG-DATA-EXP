# 独立参考算法，用于核对样例；不是 Hadoop MapReduce 运行。
from pathlib import Path
r=Path(__file__).resolve().parents[1]; out=r/'results/reference';out.mkdir(parents=True,exist_ok=True)
lines={ '\t'.join(l.split()) for p in (r/'data/dedup').glob('*.txt') for l in p.read_text().splitlines() if l.strip() }
(out/'dedup.txt').write_text('\n'.join(sorted(lines))+'\n')
nums=sorted(int(l) for p in (r/'data/sort').glob('*.txt') for l in p.read_text().splitlines() if l.strip())
(out/'sort.txt').write_text(''.join(f'{i}\t{n}\n' for i,n in enumerate(nums,1)))
rels=[l.split() for l in (r/'data/family/relations.txt').read_text().splitlines()[1:]]
pairs=sorted({(ch,gp) for ch,mid in rels for mid2,gp in rels if mid==mid2})
(out/'family.txt').write_text(''.join(f'{ch}\t{gp}\n' for ch,gp in pairs))
