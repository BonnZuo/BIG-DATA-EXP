from pathlib import Path
from urllib.request import urlopen, Request
from html.parser import HTMLParser
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib, re, json, subprocess
ROOT=Path(__file__).resolve().parents[1]
BASE='https://hadoop.apache.org/docs/r3.3.6/'
PAGES=[
('Single Node Cluster','hadoop-project-dist/hadoop-common/SingleCluster.html'),
('Cluster Setup','hadoop-project-dist/hadoop-common/ClusterSetup.html'),
('File System Shell','hadoop-project-dist/hadoop-common/FileSystemShell.html'),
('HDFS Architecture','hadoop-project-dist/hadoop-hdfs/HdfsDesign.html'),
('HDFS User Guide','hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html'),
('MapReduce Tutorial','hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html'),
('Hadoop Streaming','hadoop-streaming/HadoopStreaming.html'),
('Hadoop Archives','hadoop-archives/HadoopArchives.html'),
('YARN Architecture','hadoop-yarn/hadoop-yarn-site/YARN.html'),
('YARN Commands','hadoop-yarn/hadoop-yarn-site/YarnCommands.html')]
class Extract(HTMLParser):
 def __init__(self):
  super().__init__(); self.depth=0; self.out=[]
 def handle_starttag(self,tag,attrs):
  if tag=='div':
   if self.depth: self.depth+=1
   elif dict(attrs).get('id')=='bodyColumn': self.depth=1
  if self.depth and tag in ('p','div','br','li','h1','h2','h3','h4','pre','tr'): self.out.append('\n')
 def handle_endtag(self,tag):
  if self.depth:
   if tag in ('p','li','pre','tr'): self.out.append('\n')
   if tag=='div': self.depth-=1
 def handle_data(self,data):
  if self.depth: self.out.append(data)
def fetch(pair):
 i,(title,path)=pair; url=BASE+path
 raw=subprocess.check_output(['curl','-fL','--retry','2','--max-time','45','-sS',url])
 p=Extract();p.feed(raw.decode('utf-8'))
 text='\n'.join(s for line in ''.join(p.out).splitlines() if (s:=re.sub(r'\s+',' ',line).strip()))+'\n'
 assert len(text)>500,(url,len(text))
 name=f'document{i:02d}.txt'; (ROOT/'data'/name).write_text(text,encoding='utf-8')
 return dict(file=name,title=title,url=url,downloaded_at=datetime.now(timezone.utc).isoformat(),bytes=len(text.encode()),sha256=hashlib.sha256(text.encode()).hexdigest())
with ThreadPoolExecutor(max_workers=5) as pool: rows=list(pool.map(fetch,enumerate(PAGES,1)))
(ROOT/'sources.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
(ROOT/'sources.tsv').write_text('filename\ttitle\turl\n'+''.join(f"{r['file']}\t{r['title']}\t{r['url']}\n" for r in rows),encoding='utf-8')
for r in rows: print(f"{r['file']:18} {r['bytes']:7} bytes  {r['title']}")
print('Downloaded and extracted:',len(rows),'public English documents')
stop='a an the and or but if then else in on at to of for with from by as is are was were be been being it its this that these those i you he she we they me him her us them my your our their not no do does did have has had can could will would should may might must'.split()
(ROOT/'stopwords.txt').write_text('\n'.join(sorted(set(stop)))+'\n')
