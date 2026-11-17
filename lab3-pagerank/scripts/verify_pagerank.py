from decimal import Decimal, getcontext
from pathlib import Path
import re

getcontext().prec = 45
root = Path(__file__).resolve().parents[1]
graph = {}
for line in (root / "data/graph.tsv").read_text().splitlines():
    page, targets = line.split("\t")
    graph[page] = list(dict.fromkeys(targets.split(","))) if targets else []
nodes = sorted(set(graph) | {target for links in graph.values() for target in links})
n = Decimal(len(nodes))
d = Decimal("0.85")
p = {page: 1 / n for page in nodes}
for _ in range(10):
    # Dense reference calculation: transition probability from each source to each target.
    p = {
        target: (1 - d) / n + d * sum(
            p[source] * (Decimal(1) / len(graph[source]) if target in graph[source] else 0)
            if graph.get(source) else p[source] / n
            for source in nodes
        )
        for target in nodes
    }

def read_result(name):
    result = {}
    for line in (root / "results" / name).read_text().splitlines():
        match = re.fullmatch(r"\(([^,]+),(\d+\.\d{10})\)", line)
        if not match:
            raise ValueError(f"Invalid result line: {line}")
        page, rank = match.groups()
        if page in result:
            raise ValueError(f"Duplicate page: {page}")
        result[page] = Decimal(rank)
    assert list(result) == nodes
    error = max(abs(result[page] - p[page]) for page in nodes)
    assert error <= Decimal("0.000000000050001"), error
    assert abs(sum(result.values()) - 1) <= n * Decimal("0.00000000005")
    print(f"PASS {name}: {len(result)} pages, max error={error:.3E}, displayed sum={sum(result.values()):.10f}")
    return result

mr = read_result("mapreduce.txt")
spark = read_result("spark.txt")
assert mr == spark
print("PASS MapReduce and Spark match for every page at 10 decimals")
print("Ranking:", " > ".join(sorted(nodes, key=mr.get, reverse=True)))
