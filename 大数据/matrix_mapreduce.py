#!/usr/bin/env python3)
"""单机模拟 MapReduce 的稀疏矩阵乘法 demo。

数据格式（稀疏）：每条记录为 i,j,value（下标从0开始）
在 main 中硬编码 A、B 的稀疏条目并直接运行。
"""
from collections import defaultdict
from typing import List, Tuple, Dict

Entry = Tuple[int, int, float]


def map_phase(A: List[Entry], B: List[Entry], m: int, p: int):
	emits = []
	# A entries are (i,k,val) where A is m x n
	for (i, k, val) in A:
		for j in range(p):
			emits.append(((i, j), ("A", k, val)))
	# B entries are (k,j,val) where B is n x p
	for (k, j, val) in B:
		for i in range(m):
			emits.append(((i, j), ("B", k, val)))
	return emits


def shuffle(emits):
	groups: Dict[Tuple[int, int], List[Tuple[str, int, float]]] = defaultdict(list)
	for key, val in emits:
		groups[key].append(val)
	return groups


def reduce_phase(groups):
	out = {}
	for (i, j), vals in groups.items():
		mapA = {}
		mapB = {}
		for tag, idx, v in vals:
			if tag == 'A':
				mapA[idx] = mapA.get(idx, 0) + v
			else:
				mapB[idx] = mapB.get(idx, 0) + v
		s = 0
		# iterate keys present in A (sparse strategy)
		for k, av in mapA.items():
			bv = mapB.get(k)
			if bv is not None:
				s += av * bv
		out[(i, j)] = s
	return out


def print_matrix_sparse(entries: List[Entry], name: str, rows: int, cols: int):
	print(f"{name} (sparse entries): rows={rows}, cols={cols}")
	for i, j, v in sorted(entries):
		print(f"  {i},{j} -> {v}")


def main():
	# 硬编码测试用例：A 是 2x3，B 是 3x2，结果 C 为 2x2
	m = 2  # rows of A
	n = 3  # common dim
	p = 2  # cols of B

	# A entries: (i,k,value)
	A: List[Entry] = [
		(0, 0, 1),
		(0, 1, 2),
		(0, 2, 3),
		(1, 0, 4),
		(1, 1, 5),
		(1, 2, 6),
	]

	# B entries: (k,j,value)
	B: List[Entry] = [
		(0, 0, 7),
		(0, 1, 8),
		(1, 0, 9),
		(1, 1, 10),
		(2, 0, 11),
		(2, 1, 12),
	]

	print_matrix_sparse(A, 'A', m, n)
	print_matrix_sparse(B, 'B', n, p)

	emits = map_phase(A, B, m, p)
	groups = shuffle(emits)
	results = reduce_phase(groups)

	print('\nResult C (non-zero entries):')
	for (i, j), v in sorted(results.items()):
		if v != 0:
			print(f"{i},{j}\t{v}")


if __name__ == '__main__':
	main()