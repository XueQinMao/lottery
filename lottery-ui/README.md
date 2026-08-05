# lottery-ui

双色球遗漏趋势分析前端（Next.js），调用后端 `GET /api/history/trend`，由 `LotteryTrendUtils` 计算遗漏指数与均线。

## 启动

1. 先启动后端 `lottery-api`（默认端口 `8866`）
2. 安装依赖并启动 UI：

```bash
cd lottery-ui
npm install
npm run dev
```

浏览器打开 [http://localhost:3000](http://localhost:3000)

## 环境变量

`.env.local`：

```
NEXT_PUBLIC_API_BASE=http://localhost:8866
```

## API

`GET /api/history/trend?ballType=red&ball=1&sampleSize=100`

| 参数 | 说明 | 默认 |
|------|------|------|
| ballType | `red` / `blue` | red |
| ball | 红 1-33 / 蓝 1-16 | 1 |
| sampleSize | 最近期数 | 100 |
