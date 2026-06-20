import { Alert, Button, Card, Form, Input, InputNumber, Space, Typography, message } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { submitApply } from '../api/credit';

const DEMO_CONTENT: Record<string, string> = {
  low_risk: 'low_risk 申请个人消费贷，已提交身份证与收入证明，用途装修。',
  high_risk: 'high_risk device_abnormal 设备异常，申请大额消费贷。',
  mcp_timeout: 'mcp_timeout 征信查询超时演示场景。',
  ip_abnormal: 'ip_abnormal multi_account proxy 多账号共用设备与代理 IP。',
};

export default function ApplyPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [taskId, setTaskId] = useState<number | null>(null);

  const fillDemo = (key: string) => {
    form.setFieldsValue({ content: DEMO_CONTENT[key] });
  };

  const onFinish = async (values: Record<string, unknown>) => {
    setLoading(true);
    try {
      const data = await submitApply(values);
      setTaskId(data?.taskId ?? null);
      message.success(`提交成功，taskId=${data?.taskId}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4}>信贷申请</Typography.Title>
      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button onClick={() => fillDemo('low_risk')}>低风险 demo</Button>
          <Button danger onClick={() => fillDemo('high_risk')}>高风险 device_abnormal</Button>
          <Button onClick={() => fillDemo('mcp_timeout')}>MCP 超时</Button>
          <Button onClick={() => fillDemo('ip_abnormal')}>IP/多账号/代理</Button>
        </Space>
        <Form form={form} layout="vertical" onFinish={onFinish} initialValues={{ productId: 1, applyAmount: 30000, applyTerm: 12, purpose: 'CONSUMER' }}>
          <Form.Item name="productId" label="产品 ID" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="applyAmount" label="申请金额" rules={[{ required: true }]}>
            <InputNumber min={1000} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="applyTerm" label="申请期限（月）">
            <InputNumber min={1} max={360} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="purpose" label="用途">
            <Input />
          </Form.Item>
          <Form.Item name="content" label="申请说明" rules={[{ required: true }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>
            提交申请
          </Button>
        </Form>
      </Card>
      {taskId && (
        <Alert
          type="success"
          message={`任务已创建：taskId=${taskId}`}
          action={
            <Button size="small" onClick={() => navigate(`/tasks/${taskId}`)}>
              查看任务状态
            </Button>
          }
        />
      )}
    </Space>
  );
}
