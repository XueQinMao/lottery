# 自动预测稳定性修复与验证

实施日期：2026-10-20。起因和Windows崩溃证据见[故障分析](auto-predict-pipe-error-analysis.md)。本次修改本地源码，未替换运行服务、未升级依赖、未训练或改写模型、未运行1000万组业务预测。

## 修改内容与兼容边界

### 1. Python模型线程与诊断

`predict.py`将每次请求创建的线程池改为进程级固定执行器。LSTM有独立单线程，加载和`model.predict(x, verbose=0)`都在该线程执行；其他模型使用最多7个工作线程。融合依然按照`meta_feature_names`顺序组装输入，模型、特征、默认概率、融合权重与响应字段均未改变。退出时关闭执行器。

新增`prediction_diagnostics.py`，不依赖psutil，Windows每分钟记录PID、Private Bytes、工作集、原生线程数、句柄数到stderr；Linux读取`/proc`，其他平台保留可用指标。stdout仍只承载逐行JSON协议。Java单独消费stderr，保留最后30行、每行最多2048字符，记录进程PID、退出码与错误尾部。新启动的进程启用`PYTHONFAULTHANDLER`以辅助捕获原生崩溃。诊断不参与模型计算。

JAR提取Python脚本时同时提取`ssq_features.py`和诊断模块，避免只在开发环境可运行。Java直接启动Python，不再通过cmd/bash包装，确保超时销毁的是实际推理进程。

### 2. 通信恢复与完成语义

- `runInference(Map)`签名和正常String返回值不变。失败不再返回null，改为明确的`IllegalStateException`，使调用方进入失败路径。
- 同一输入最多尝试2次；写失败、EOF、无效JSON、Python错误响应、实际I/O超时都会隔离连接。中断不重试，保留中断标记。
- 池保留固定逻辑槽位。坏连接不作为健康连接归还，下一次借用在该槽位受控重建；重建失败将槽位归还，不能让池容量永久消失。
- 初始化部分失败时关闭已启动进程并报告失败；不允许空池假装初始化成功。关闭池时也关闭借用中的连接，阻塞借用者会获知关闭。
- 再次调用初始化仍重新加载模型与历史，新池就绪后切换并关闭旧池，避免跨期开奖继续使用旧历史，也避免原实现重复初始化遗留旧进程。
- 专用I/O线程执行一轮写请求/读响应；超时时先终止进程解除阻塞，防止迟到响应串到下一请求。同步锁确保同一连接不会交叉消费响应。
- 预测执行器仍为2线程、队列3、调用线程背压；关闭后明确拒绝任务，不能静默丢弃。
- 自动预测使用Phaser只追踪当前有界任务，全部成功后才返回；出现失败停止继续提交，排空已提交任务后向责任链抛异常，阻止继续推荐。
- 生成结束后的排空等待另设30分钟上限；超时关闭Python池、报告失败，迟到的推理响应不得发布成功事件。该上限只覆盖排空阶段，不是1000万组生成/推理的总时限，也不能强制回滚已经开始的文件或数据库写入。
- 事件中的文件和进度写入改为在预测任务内同步完成，消除公共线程池积压；成功回调只在事件处理完成后增加成功计数。文件格式和入库筛选条件保持原样。它不是跨缓存、文件、数据库的分布式事务，写入中途失败仍可能留下部分数据，不承诺自动回滚文件。

### 3. 候选缓存索引

缓存保留100,000,000条的原上限，不调整筛选策略。缓存条目保存分数和字节数据，索引版本与当前条目关联；替换分数移除旧桶，删除/淘汰移除旧索引，空桶清除。`clear()`同时清除缓存、反向索引和分数索引。

异步移除通知以对象身份校验旧版本，防止清空后重新加入的同名号码被迟到通知删掉。并发淘汰期间，查询遇到已经不存在的数据直接跳过，不将null送入解压流程。上限按条数而非字节设置，大量有效候选仍会占用内存；本次不修改该业务容量。

## 超时与失败策略

可通过JVM系统属性设置，单位毫秒：

| 属性 | 默认值 | 含义 |
|---|---|---|
| `lottery.python.init-timeout-ms` | 300000 | 单个进程初始化握手时限 |
| `lottery.python.request-timeout-ms` | 120000 | 单次实际写请求、读响应的时限 |
| `lottery.python.borrow-timeout-ms` | 120000 | 等待可用逻辑槽位的时限 |
| `lottery.prediction.drain-timeout-ms` | 1800000 | 生成结束后等待剩余预测与结果写入的时限 |

各阶段独立计时；重试最多一次，所以一次业务调用的总耗时可能超过两分钟，并非两次尝试共享两分钟。连接重建使用初始化时限。真实I/O超时在CallerRuns执行时也生效，不依赖Future的`orTimeout`。

永久故障时明确失败，不跳过错误号码生成替代结果；停止后续推荐是对旧有“失败也当成功”的修正。该行为变化是有意的，不能称为所有异常路径完全不变。现有随机抽样、1000万组预生成、断点计数语义没有重写。

## 固定输入与回归验证

真实模型对照工具：`lottery-python/src/test/python/prediction_stability.py`。固定随机种子20261020生成64组输入；比较每个子模型概率、融合概率和reason。测试进程不写业务数据库或预测结果文件。

报告目录中的`environment.json`保存Python/包版本、模型文件SHA-256、脚本SHA-256和全部固定输入。当前模型和历史文件未由本次操作修改。修改前源码快照位于`lottery-python/target/stability/baseline`，为本机生成文件，clean会删除，不替代Git提交回退。

本轮环境清单、64组原始对照输出及差异摘要另存于[验证证据](validation/auto-predict-stability/environment.json)，随修复提交保留；构建清理不会删除该副本。

真实模型对照发现：LSTM、Transformer、Markov、XGBoost输出完全相同；RF/voting/stacking出现约10⁻¹⁶浮点差异，最终融合概率最大差异约2.08×10⁻¹⁷。原实现独立重复运行也出现相同数量级差异，与并行归约舍入波动一致。reason逐字相同，没有对业务概率做舍入或改写。比较容差为绝对误差10⁻¹²，原始输出均保留，可审计差异。

Java回归对64组真实输出分别使用[0,1]、[0.05,0.95]、[0.1,0.9]、[0.2,0.8]以及反向无效区间，共320组筛选场景，验证修改前后入库决定一致；同时验证阈值包含边界、空配置和空概率。这不意味着任意精确落在10⁻¹⁷差异之间的未来阈值都不可能改变结果，也不证明生产当前数据库阈值的全量回放已完成。

### 本地重现命令

在项目根目录执行（`python`需指向原环境，`mvn`需在PATH中或使用本机完整路径）：

```shell
python -X utf8 lottery-python/src/test/python/prediction_stability.py compare --baseline lottery-python/target/stability/baseline/lottery-python/src/main/resources/python/predict.py --script lottery-python/src/main/resources/python/predict.py --model-dir D:/home/python/model --output lottery-python/target/stability/comparison
python -X utf8 lottery-python/src/test/python/test_prediction_executors.py
mvn -pl lottery-application -am -Dtest=PythonConnectionTest,PythonConnectionPoolTest,PythonResourceExtractionTest,InitPythonProcessServiceImplTest,PredictServiceStabilityTest,PredictionCandidateCacheTest,PredictCacheServiceStabilityTest,PredictionPersistenceRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl lottery-python -am -Dtest=PythonConnectionRealModelTest -Dpython.real-model-dir=D:/home/python/model -Dsurefire.failIfNoSpecifiedTests=false test
```

`PredictionPersistenceRegressionTest`中的真实模型集成项要求前述comparison报告存在，否则明确跳过；普通阈值测试不依赖真实模型。`PythonConnectionRealModelTest`只有显式提供模型目录才运行，会启动并关闭两个真实Python进程。

长时间测试命令：

```shell
python -X utf8 lottery-python/src/test/python/prediction_stability.py stress --script lottery-python/src/main/resources/python/predict.py --model-dir D:/home/python/model --output lottery-python/target/stability/soak --seconds 3600 --min-requests 10000
```

测试同时满足运行至少3600秒和至少10000次真实推理才退出；逐分钟进度在`stress.jsonl`，资源指标与Python错误在`stress.stderr.log`，子进程退出码在`stress.exit.json`。原生崩溃会使测试失败，不能通过自动重启掩盖；子模型返回None、错误响应也会直接失败，不能用默认概率冒充通过。

## 验证状态与运行边界

39项Java测试、2项Python测试全部通过，包含普通故障测试、真实双连接、缓存并发清理和320组入库筛选回归；无失败、无跳过。64组真实输入的子模型及融合概率对照通过，浮点差异范围如前文所述。

按用户“问题修复了就行，不用跑那么多对照”的最新要求，11:40停止剩余长时间验证。修复版最近一分钟记录为连续2161.469秒（约36分钟）、27,388次真实推理，无自发崩溃或子模型失败；停止前335个原生线程、1,312个句柄、1,676,996,608字节Private Bytes。线程与句柄在有限范围波动，未出现原版的爆发式累积；内存有小幅增长，不能据此承诺完全不存在长期内存增长。测试是按要求主动结束，非零退出码不能作为自发崩溃解释，**未完成原定60分钟验收**。汇总、逐分钟资源及受控终止记录保存在[验证摘要](validation/auto-predict-stability/summary.json)。结束时再次校验推理脚本、模型和历史文件SHA-256，与对照环境清单一致。

2026-10-20的原版对照运行出现显著资源累积：最近一分钟进度为480.516秒、3427次推理，11:39:03采样达到53,402个原生线程、107,391个句柄、9,971,691,520字节Private Bytes（约9.29 GiB）。为避免耗尽本机资源，测试控制端主动终止原版进程；其非零退出码是本次终止导致，**不是复现了TensorFlow访问违规**。同期修复版约300多线程、1千多个句柄、1.68 GB私有内存。证据见[原版受控终止记录](validation/auto-predict-stability/baseline-controller-stop.json)。这支持“反复换线程调用推理导致原生资源持续累积”的修复方向；2026-10-19访问违规的唯一触发机制仍未通过原生转储证明。

运行中的服务未热替换。本次不执行1000万组原规模业务验证，因为它会写入真实预测进度、结果和推荐记录；该阶段应在隔离业务数据副本中进行，不能借验证之名改变当前数据。真实模型的长期测试仅证明当前样本、依赖和单进程测试路径在所测时长内的表现，不能承诺所有未来输入绝不崩溃。

## 分阶段提交与回退

本次分为三个提交：Python工作线程复用与诊断、Java通信恢复与完成等待、候选缓存索引与验证记录。只包含本次稳定性修复涉及的文件，保留工作区其他已有改动。

需要回退时，在确认工作区相关文件已妥善保存后，按上述提交的逆序执行`git revert <提交号>`；不使用`reset --hard`或覆盖模型。前两个提交分别为`c822449`（Python工作线程/诊断）和第二阶段的`fix(predict): recover broken connections and await persisted results`，第三个提交为`fix(cache): synchronize candidate score indexes and retain validation evidence`；可通过`git log -3 --oneline`查看完整提交号。本次未推送远程、未部署运行服务。
