## 赛题背景
赛题名：数据湖元信息发现与分析

背景：数据湖分析（阿里云数据湖：https://www.aliyun.com/product/datalakeanalytics） 是目前炙手可热的、全新的大数据方向，主要在低成本、无限容量的对象存储（比如阿里云OSS）系统中，存储各种各样的数据，并以存储计算分离架构方式，构建出结构化的元信息，最终围绕这些元信息和数据，在上层构建各种各样的分析和计算服务。一般包含结构化、半结构化、非结构化等类型的数据，并且数据量巨大，分析难度很高。结构化和半结构化数据是数据湖目前主要的处理对象。

本题希望选手模拟数据湖分析场景，在云平台环境上，为纷繁复杂的云数据构建元信息，并实现简单的分析能力。
任务：在数据湖场景中，通过对大数据集信息的发现，结合索引优化，模糊匹配等相关技术，对给定的条件，快速过滤并准确计算出满足条件的记录行的总数。

出题单位：阿里云

## 赛题简介
本赛题背景是目前大数据研究热点数据湖，通过提供一大批未知的目录和CSV文件数据，要求我们能进行元数据的发现和分析。

查询格式为
${表名} ${比较列} ${操作列} ${比较值} ${like匹配列} ${like匹配类型} ${like匹配值数组}

其中比较列分为数据列和分区列。另外，根据查询类型，这里我额外把数据列分成比较列和匹配列类型。

运行环境：java8，服务器4C8G，不允许使用任开源框架，只能依赖JDK原生API

#### 赛题链接 [数据湖的元信息发现与分析](https://www.datafountain.cn/competitions/485)

## 赛题方案
本赛题我们分成两个阶段进行设计和实现。

#### 1.元数据发现阶段
![元数据发现](https://github.com/zhbitcxy/datalake-code/blob/main/docs/img/1.jpeg)
#### 首先先对数据进行元数据发现，这一阶段主要是为了获取数据元信息和建立相关索引方便后续查询优化。
通过递归扫描所有的目录文件数据，获取相关的元信息，并建立分区索引。
然后对于单个文件，我们先读取数据到缓冲区，由于文件原格式是行格式，为了加速后续数据查找，这里会将其转化成列格式并存储起来，最后对于每一列会统计对应的最大最小值，并建立对应的范围索引。

#### 2.数据查询阶段
![数据查询](https://github.com/zhbitcxy/datalake-code/blob/main/docs/img/2.jpeg)
#### 对于数据查询阶段。
依次从文件读取查询语句，解析语句并根据表名获取对应的表对象，然后判断是否存在分区列，如果存在分区列，则先利用分区索引进行分区裁剪，最终得到TableChunk（对应一个表分片文件）集合，并把它交给线程池进行处理。

#### 对于每个线程任务的处理。
（在这里我根据查询类型先把数据列进行分类。如果查询类型是like类型，那么表示为匹配列；如果是比较类型，那么表示为比较列。）首先先判断是否存在比较列，如果存在，则根据范围索引进行范围过滤。然后判断是否存在匹配列，如果存在，则获取对应列的数据，并进行like匹配过滤。

最终将线程池所有任务的结果进行汇总，从而获得对应语句的结果。

#### 3.like匹配过程
![数据查询](https://github.com/zhbitcxy/datalake-code/blob/main/docs/img/3.jpeg)
#### 这里介绍下like过滤过程。
like过滤类型有三种：ANY（任意一个满足匹配算匹配成功）,NONE（任意一个满足匹配算匹配不成功）,ALL（所有都满足匹配算匹配成功，也就是有一个不匹配就算匹配不成功）。注意到这些查询类型在匹配过程中是不需要把所有的模式串都判断一遍的，所以根据这点，我们可以对模式串进行分类，然后根据不同类型使用特定算法，从而可以获得比通用算法更好的算法性能，并且也避免了设计复杂状态机。

#### 这里介绍下模式串分类情况。
这里把模式串分类成2个特殊类型ANY和ANY_FIXED，5个基本类型CONST,PREFIX,SUFFIX,FIXED,FULL，然后对于基本类型内容含有“_”又另外归类为XXX_类型。特别的，对于XXX_类型，算法会特殊处理一下（其实也可以和基本类型合并，但是会有额外开销，所以这里就另外归类处理了）。最后会根据这些类型的算法开销按从小到大排序，然后依次匹配，这样可以尽可能的降低一趟匹配过程的算法开销。

#### 这里介绍下每种类型的处理算法。
对于ANY类型，恒成立，不需要另外判断；对于ANY_FIXED类型，只需要判断长度是否相等；对于CONST类型，只需要在长度相等的情况下判断内容是否相等；对于PREFIX和SUFFIX类型，我们构建字典树加速多模式串的匹配；对于FIXED类型，使用的是普通的字符串查找算法，这里也可以使用AC自动机加速多模式串匹配；对于FULL类型，先根据%进行拆分，生成一个token数组，然后对每个token进行匹配，每个token匹配的下一个位置就是下一个token匹配的起始位置，这样当所有token都匹配成功，那么才算匹配成功。

#### 4.字典树合并同类模式串
![字典树](https://github.com/zhbitcxy/datalake-code/blob/main/docs/img/4.jpeg)
#### 这里介绍下如何使用字典树来加速PREFIX和SUFFIX类型的多模式匹配。
当like语句为ANY和NONE类型时，由于多个模式串只需一个满足就可判断是否匹配成功或者不成功，所以这里可以将同类模式串构建成一颗字典树，然后在字典树上进行快速匹配。

此外对于ALL类型，也可以利用字典树去重同一路径上的模式串，这样假如存在a%,ab%,abc%模式串，那么只需要保留abc%即可，从而可以避免多余的无效匹配。

#### 5.分区索引介绍
![分区索引](https://github.com/zhbitcxy/datalake-code/blob/main/docs/img/5.jpeg)
#### 这里介绍下上面所提及的分区索引的数据结构。
我们可以发现分区在逻辑上其实是树形结构，但是维护树形结构不仅麻烦而且对于查询性能也不够高效，其实我们可以把树形结构进行路径压缩，那么只需使用一个HashMap即可维护分区键到分区值的关系，这样时间复杂度从O(logn) -> O(1)。

#### 对于获取的分区值集合，我们还需要根据查询条件进行进一步过滤。
考虑到比较查询存在范围查询，所以这里使用了TreeMap数据结构进行维护，这样对于范围查询只需O(logn)即可定位。

#### 除此之外分区索引还支持like查询。
这里对于like类型的查询需要遍历所有分区值进行判断，使用的匹配算法跟上面的like过滤算法一致。

#### 6.进程优化
![进程优化](https://github.com/zhbitcxy/datalake-code/blob/main/docs/img/6.jpeg)
#### 此外，除了流程、算法和数据结构方面的优化以外，这里还对进程进行了优化。
由于此次比赛只限于使用JAVA语言，所以我们需要关注GC方面的优化。

#### 在GC优化方面。
对于代码中需要使用到的原生String和List类型，我们使用了自定义的GC优化的数据结构替换，并且全程尽可能使用基础类型和byte类型，尽可能复用对象。

由于此次赛题比赛过程中公布了线上数据量为4.5g，而线上环境是4c8g，又因为线上jvm参数不能设置，所以只能使用不到2g的堆内内存，这里为了能利用那6g的堆外内存，我们使用Unsafe类进行堆外内存的分配和使用，最终在元数据发现阶段把数据都按列存储到堆外内存，从而避免了后续的io开销。

#### 对于线程并发处理方面。
这里使用了ThreadLocal避免了线程同步开销，整个任务处理过程都是无锁的。

#### 总结
最后成绩为45s，比第二名快了13s(提升28%)，比第五名快了37s（提升82%）
。代码已开源到github(https://github.com/zhbitcxy/datalake-code)， 欢迎大家交流和学习，谢谢。

#### 运行程序方法
-  cd code/
-  mvn compile
-  mvn exec:java -Dexec.mainClass="Main"
> 运行前请先下载相关数据，详情请参考赛题介绍
>> database/ params.txt code/

#### 赛题链接 [数据湖的元信息发现与分析](https://www.datafountain.cn/competitions/485)
