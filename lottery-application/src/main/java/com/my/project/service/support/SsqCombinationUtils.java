package com.my.project.service.support;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.selection.pojo.bo.SsqCombinationBo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * SsqCombinationUtils
 *
 * @author 刘强
 * @version 2025/10/23 17:35
 **/
public class SsqCombinationUtils {

    private static final Logger logger = LoggerFactory.getLogger(SsqCombinationUtils.class);

    public static final List<SsqCombinationBo> CACHE_SSQ_COMBINATION = new ArrayList<>();

    private static final int RED_MAX = 33;   // 红球最大号码
    private static final int BLUE_MAX = 16;  // 蓝球最大号码
    private static final int RED_COUNT = 6;  // 红球个数

    /** 双色球全组合总数：C(33,6) × 16 */
    public static final long TOTAL_COMBINATIONS = 17_721_088L;


    /**
     * 判断历史记录是否与号码组合匹配
     * @param record 历史开奖记录
     * @param combination 号码组合
     * @return 是否匹配
     */
    public static boolean matchesCombination(List<HistoryRecord> record, SsqCombinationBo combination) {
        Predicate<HistoryRecord> predicate = r -> {
            List<Integer> redBalls = List.of(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
            return new HashSet<>(redBalls).containsAll(combination.getRedBalls()) &&
                    redBalls.size() == combination.getRedBalls().size() &&
                    r.getSpecial().equals(combination.getBlueBall());
        };
        return record.stream().anyMatch(predicate);
    }

    /**
     * 获取未中奖的号码组
     * @param record
     * @return
     */
    public static List<SsqCombinationBo> generateRandomDraw(List<HistoryRecord> record, int count) {
        if(CollectionUtil.isNotEmpty(CACHE_SSQ_COMBINATION)){
            return CACHE_SSQ_COMBINATION;
        }

        for (int i = 0; i < count; i++) {
            boolean flag = true;
            SsqCombinationBo combination = null;
            while (flag) {
                combination = generateRandomDraw();
                logger.info("未中奖号码组：{}", JSON.toJSONString(combination));
                CACHE_SSQ_COMBINATION.add(combination);
                flag = matchesCombination(record, combination);
            }
        }
        return CACHE_SSQ_COMBINATION;
    }

    /**
     * 随机生成号码组
     * @return
     */
    public static SsqCombinationBo generateRandomDraw() {
        Random random = new Random();
        Set<Integer> redSet = new HashSet<>();
        while (redSet.size() < 6) {
            redSet.add(random.nextInt(33) + 1);
        }
        List<Integer> redBalls = new ArrayList<>(redSet);
        Collections.sort(redBalls);
        int blueBall = random.nextInt(16) + 1;
        return SsqCombinationBo.of(redBalls, blueBall);
    }

    /**
     * 无序随机生成 {@code count} 组唯一号码（更自然、无字典序顺序感）。
     *
     * <p>与 {@link #generateFromIndex} 不同：不按红球组合字典序 + 蓝球 1→16 顺序吐出，
     * 而是在全空间 {@link #TOTAL_COMBINATIONS} 上均匀抽随机下标再 {@link #unrankCombination}，
     * 并对和值/奇偶/跨度/三区做软过滤，使形态更接近真实开奖分布；最后再打乱列表顺序。
     *
     * @param count 生成组数，须 &gt; 0 且不超过全空间
     * @return 去重后的号码组列表（红球已升序，组间顺序已打乱）
     */
    public static List<SsqCombinationBo> generateNaturalRandom(int count) {
        return generateNaturalRandom(count, ThreadLocalRandom.current().nextLong());
    }

    /**
     * 同 {@link #generateNaturalRandom(int)}，可指定随机种子便于复现。
     */
    public static List<SsqCombinationBo> generateNaturalRandom(int count, long seed) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        if (count > TOTAL_COMBINATIONS) {
            throw new IllegalArgumentException("count 不能超过全空间总数 " + TOTAL_COMBINATIONS);
        }

        Random rng = new Random(seed);
        Set<Long> pickedIndexes = new HashSet<>(Math.min(count * 2, 1 << 20));
        List<SsqCombinationBo> result = new ArrayList<>(count);
        int maxAttempts = Math.max(count * 80, 2000);
        int attempts = 0;
        // 前 85% 优先收「形态自然」的票，后段放宽以保证凑满 count
        int preferNaturalUntil = Math.max(1, (int) Math.ceil(count * 0.85));

        while (result.size() < count && attempts < maxAttempts) {
            attempts++;
            long index = rng.nextLong(TOTAL_COMBINATIONS);
            if (!pickedIndexes.add(index)) {
                continue;
            }
            SsqCombinationBo combo = fromCombinationIndex(index);
            boolean preferNatural = result.size() < preferNaturalUntil;
            if (!preferNatural || isNaturalShape(combo)) {
                result.add(combo);
            } else {
                pickedIndexes.remove(index);
            }
        }

        // 兜底：纯随机补齐，仍保证全空间下标唯一
        while (result.size() < count) {
            long index = rng.nextLong(TOTAL_COMBINATIONS);
            if (pickedIndexes.add(index)) {
                result.add(fromCombinationIndex(index));
            }
        }

        Collections.shuffle(result, rng);
        return result;
    }

    /**
     * 无序随机生成并逐组回调（适合流式消费，避免一次性囤大 List）。
     */
    public static void generateNaturalRandom(int count, Consumer<SsqCombinationBo> consumer) {
        generateNaturalRandom(count, ThreadLocalRandom.current().nextLong(), consumer);
    }

    public static void generateNaturalRandom(int count, long seed, Consumer<SsqCombinationBo> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (SsqCombinationBo combo : generateNaturalRandom(count, seed)) {
            consumer.accept(combo);
        }
    }

    /**
     * 全空间下标 → 一注号码（与 {@link #toCombinationIndex} 互逆）。
     */
    public static SsqCombinationBo fromCombinationIndex(long index) {
        if (index < 0 || index >= TOTAL_COMBINATIONS) {
            throw new IllegalArgumentException("index 越界: " + index);
        }
        long redIndex = index / BLUE_MAX;
        int blueBall = (int) (index % BLUE_MAX) + 1;
        int[] redBalls = unrankCombination(redIndex);
        return SsqCombinationBo.of(Arrays.asList(toIntegerArray(redBalls)), blueBall);
    }

    /**
     * 形态是否接近常见开奖：和值、奇偶、跨度、三区不过分极端。
     */
    private static boolean isNaturalShape(SsqCombinationBo combo) {
        List<Integer> reds = combo.getRedBalls();
        if (reds == null || reds.size() != RED_COUNT) {
            return false;
        }
        int sum = 0;
        int odd = 0;
        int z1 = 0, z2 = 0, z3 = 0;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int n : reds) {
            if (n < 1 || n > RED_MAX) {
                return false;
            }
            sum += n;
            if ((n & 1) == 1) {
                odd++;
            }
            if (n <= 11) {
                z1++;
            } else if (n <= 22) {
                z2++;
            } else {
                z3++;
            }
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        int span = max - min;
        // 经验区间：和值 / 奇偶 / 跨度 / 三区不过分偏科
        return sum >= 70 && sum <= 140
            && odd >= 2 && odd <= 4
            && span >= 12 && span <= 30
            && z1 >= 1 && z1 <= 4
            && z2 >= 1 && z2 <= 4
            && z3 >= 1 && z3 <= 4;
    }

    /**
     * 组合数 C(n, k)
     */
    public static long combination(int n, int k) {
        if (k == 0 || k == n) return 1;
        long result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - i + 1) / i;
        }
        return result;
    }

    /**
     * 根据组合索引反推出红球组合
     */
    public static int[] unrankCombination(long redIndex) {
        int[] result = new int[RED_COUNT];
        int start = 1; // 红球从1开始
        for (int i = 0; i < RED_COUNT; i++) {
            for (int num = start; num <= RED_MAX; num++) {
                long count = combination(RED_MAX - num, RED_COUNT - (i + 1));
                if (redIndex >= count) {
                    redIndex -= count;
                } else {
                    result[i] = num;
                    start = num + 1;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 计算红球组合索引
     */
    public static long getRedCombinationIndex(List<Integer> redBalls) {
        long index = 0;
        int prev = 0;
        for (int i = 0; i < RED_COUNT; i++) {
            int current = redBalls.get(i);
            for (int j = prev + 1; j < current; j++) {
                index += combination(RED_MAX - j, RED_COUNT - (i + 1));
            }
            prev = current;
        }
        return index;
    }

    /**
     * 根据已预测组数，得到续跑起始下标。
     *
     * <p>已完成 {@code position} 组（下标 {@code 0 .. position-1}）时传入 {@code position}，
     * 从第 {@code position + 1} 组（下标 {@code position}）继续。
     * 例如已预测 1000 组，传入 1000，则从第 1001 组开始。
     *
     * @param position 已完成的预测组数，可为 null
     * @return 传给 {@link #generateFromIndex(long, Consumer)} 的起始下标
     */
    public static long getIndex(Long position) {
        if (position == null || position <= 0) {
            return 0L;
        }
        return Math.min(position, TOTAL_COMBINATIONS);
    }

    /**
     * 将一注红蓝球映射为全空间字典序下标（调试/对账用）
     */
    public static long toCombinationIndex(List<Integer> redBalls, int blueBall) {
        long redIndex = getRedCombinationIndex(redBalls);
        return redIndex * BLUE_MAX + (blueBall - 1);
    }

    /**
     * 从指定 skipCount 开始生成组合（skipCount=已完成组数，从下一组开始）
     */
    public static void generateFromIndex(long skipCount, Consumer<SsqCombinationBo> consumer) {
        long redIndex = skipCount / BLUE_MAX;
        int blueStart = (int) (skipCount % BLUE_MAX);

        int[] redBalls = unrankCombination(redIndex);

        // 先生成当前红球组合的蓝球
        for (int blue = blueStart + 1; blue <= BLUE_MAX; blue++) {
            consumer.accept(SsqCombinationBo.of(Arrays.asList(toIntegerArray(redBalls)), blue));
        }

        // 从下一个红球组合开始
        for (long rIndex = redIndex + 1; rIndex < combination(RED_MAX, RED_COUNT); rIndex++) {
            redBalls = unrankCombination(rIndex);
            for (int blue = 1; blue <= BLUE_MAX; blue++) {
                consumer.accept(SsqCombinationBo.of(Arrays.asList(toIntegerArray(redBalls)), blue));
            }
        }
    }

    private static Integer[] toIntegerArray(int[] arr) {
        Integer[] res = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            res[i] = arr[i];
        }
        return res;
    }

}
