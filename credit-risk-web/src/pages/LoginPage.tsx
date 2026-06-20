import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { setAuth } from '../utils/request';

export default function LoginPage() {
  const navigate = useNavigate();
  const [form] = Form.useForm();

  const onFinish = (values: { userId: string; token: string }) => {
    setAuth(values.userId, values.token);
    message.success('登录信息已保存');
    navigate('/apply');
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f0f2f5' }}>
      <Card title="信贷风控演示登录" style={{ width: 420 }}>
        <Typography.Paragraph type="secondary">
          输入 userId 与 token（来自 POST /user/login 或 mock）。后续请求将携带 authorization 头。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" onFinish={onFinish} initialValues={{ userId: '1' }}>
          <Form.Item name="userId" label="User ID" rules={[{ required: true }]}>
            <Input placeholder="例如 1（管理员）" />
          </Form.Item>
          <Form.Item name="token" label="Token" rules={[{ required: true }]}>
            <Input placeholder="粘贴 authorization token" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            进入系统
          </Button>
        </Form>
      </Card>
    </div>
  );
}
