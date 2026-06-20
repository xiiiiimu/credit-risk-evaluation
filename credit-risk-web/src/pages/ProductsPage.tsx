import { Alert, Button, Card, InputNumber, Space, Typography, message } from 'antd';
import { useState } from 'react';
import { getProduct } from '../api/credit';

export default function ProductsPage() {
  const [productId, setProductId] = useState<number>(1);
  const [result, setResult] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  const query = async () => {
    setLoading(true);
    try {
      const data = await getProduct(productId);
      setResult(data);
      message.success('查询完成');
    } catch {
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  const burstTest = async () => {
    setLoading(true);
    try {
      await Promise.all(Array.from({ length: 10 }, () => getProduct(productId)));
      message.success('并发 10 次请求完成，可在 Redis/日志观察击穿防护');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4}>信贷产品查询</Typography.Title>
      <Alert
        type="info"
        message="演示 Redis 缓存：productId=999999 测穿透；productId=1 并发请求测击穿"
      />
      <Card>
        <Space>
          <InputNumber min={1} value={productId} onChange={(v) => setProductId(v || 1)} />
          <Button type="primary" loading={loading} onClick={query}>查询</Button>
          <Button loading={loading} onClick={burstTest}>并发击穿测试 (×10)</Button>
          <Button onClick={() => { setProductId(999999); }}>穿透 ID 999999</Button>
        </Space>
      </Card>
      <Card title="响应数据">
        <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
          {result ? JSON.stringify(result, null, 2) : '暂无数据'}
        </pre>
      </Card>
    </Space>
  );
}
