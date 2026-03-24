#!/usr/bin/env python3
"""
MapReduce Demo - 纯 Python 实现
任务: 统计大量文本中单词的出现频率
"""

from collections import defaultdict
from functools import reduce
import random


def map_function(text_chunk):
    """
    Map: 将输入数据分割，输出 (key, value) 对
    输入: 一段文本
    输出: [("word", 1), ("word", 1), ...]
    """
    result = []
    # 清洗并分割单词
    words = text_chunk.lower().replace(",", "").replace(".", "").split()
    for word in words:
        if word.isalpha():  # 只保留纯字母单词
            result.append((word, 1))
    return result


def shuffle_function(mapped_data_list):
    """
    Shuffle: 聚合相同 key 的 values
    输入: [[(k,v), ...], [(k,v), ...], ...] 来自多个 Mapper
    输出: {key: [v1, v2, ...], ...}
    """
    grouped = defaultdict(list)
    # 合并所有 Mapper 的输出
    for mapper_output in mapped_data_list:
        for key, value in mapper_output:
            grouped[key].append(value)
    return dict(grouped)


def reduce_function(key_values):
    """
    Reduce: 对每个 key 的 values 进行汇总
    输入: (key, [v1, v2, ...])
    输出: (key, 汇总结果)
    """
    key, values = key_values
    # 求和: 统计单词出现次数
    return (key, sum(values))


class MapReduceEngine:
    """模拟 MapReduce 调度引擎"""
    
    def __init__(self, num_mappers=3, num_reducers=2):
        self.num_mappers = num_mappers
        self.num_reducers = num_reducers
    
    def split_input(self, data, num_chunks):
        """将输入数据分割成多个块"""
        chunk_size = len(data) // num_chunks + 1
        return [data[i:i+chunk_size] for i in range(0, len(data), chunk_size)]
    
    def run(self, input_data):
        """执行完整的 MapReduce 流程"""
        print("=" * 50)
        print("🚀 MapReduce 开始执行")
        print(f"   Mapper 数量: {self.num_mappers}")
        print(f"   Reducer 数量: {self.num_reducers}")
        print("=" * 50)
        
        # Step 1: Split - 分割输入数据
        print("\n📦 [Split] 分割输入数据...")
        chunks = self.split_input(input_data, self.num_mappers)
        print(f"   分成 {len(chunks)} 个数据块")
        
        # Step 2: Map - 并行映射 (模拟)
        print("\n🗺️  [Map] 执行映射...")
        mapped_results = []
        for i, chunk in enumerate(chunks):
            result = map_function(chunk)
            mapped_results.append(result)
            print(f"   Mapper-{i+1}: 处理 {len(chunk)} 字符, 输出 {len(result)} 条记录")
        
        # Step 3: Shuffle - 重新分配 (关键步骤)
        print("\n🔀 [Shuffle] 聚合相同 Key...")
        shuffled = shuffle_function(mapped_results)
        print(f"   聚合为 {len(shuffled)} 个唯一单词")
        
        # 按 key 分配给不同 Reducer (简单哈希分配)
        reducer_buckets = [[] for _ in range(self.num_reducers)]
        for key, values in shuffled.items():
            bucket_idx = hash(key) % self.num_reducers
            reducer_buckets[bucket_idx].append((key, values))
        
        for i, bucket in enumerate(reducer_buckets):
            print(f"   Reducer-{i+1} 分配: {len(bucket)} 个 key")
        
        # Step 4: Reduce - 并行归约 (模拟)
        print("\n➕ [Reduce] 执行归约...")
        final_results = []
        for i, bucket in enumerate(reducer_buckets):
            for item in bucket:
                result = reduce_function(item)
                final_results.append(result)
            print(f"   Reducer-{i+1}: 处理 {len(bucket)} 个 key")
        
        # 排序并返回
        final_results.sort(key=lambda x: x[1], reverse=True)
        return final_results


# ============ 测试数据 ============
SAMPLE_TEXT = """
MapReduce is a programming model for processing large data sets with a parallel 
distributed algorithm on a cluster. MapReduce was introduced by Google. 
The MapReduce programming model is designed to process large volumes of data 
in parallel by dividing the work into a set of independent tasks. 
MapReduce consists of two main steps: the Map step and the Reduce step.
The Map step takes input data and converts it into a set of key-value pairs.
The Reduce step takes the output from the Map step and combines the data 
tuples into a smaller set of tuples. Hadoop uses MapReduce. 
Spark is faster than MapReduce for some workloads. 
MapReduce is durable and fault tolerant. MapReduce MapReduce MapReduce
"""

if __name__ == "__main__":
    # 创建更多测试数据 (复制多份模拟大数据)
    big_data = SAMPLE_TEXT * 10  # 模拟 10 倍数据量
    
    # 运行 MapReduce
    engine = MapReduceEngine(num_mappers=3, num_reducers=2)
    word_counts = engine.run(big_data)
    
    # 输出结果
    print("\n" + "=" * 50)
    print("📊 最终结果 (Top 10 高频词):")
    print("=" * 50)
    for word, count in word_counts[:10]:
        bar = "█" * (count // 5)  # 简单可视化
        print(f"   {word:12s}: {count:3d} {bar}")
    
    # 验证总次数
    total_words = sum(c for _, c in word_counts)
    print(f"\n   总单词数: {total_words}")