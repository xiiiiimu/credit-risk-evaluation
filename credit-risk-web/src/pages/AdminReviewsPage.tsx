import { Button, Form, Input, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { CreditApplication, approveReview, getPendingReviews, rejectReview } from '../api/credit';

export default function AdminReviewsPage() {
  const [data, setData] = useState<CreditApplication[]>([]);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState<{ type: 'approve' | 'reject'; id: number } | null>(null);
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      setData(await getPendingReviews());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const submit = async () => {
    if (!modal) return;
    const values = await form.validateFields();
    if (modal.type === 'approve') {
      await approveReview(modal.id, {
        reviewReason: values.reviewReason,
        approvedAmount: values.approvedAmount,
      });
      message.success('已通过');
    } else {
      await rejectReview(modal.id, values.reviewReason);
      message.success('已拒绝');
    }
    setModal(null);
    form.resetFields();
    load();
  };

  const columns = [
    { title: 'applicationId', dataIndex: 'id', key: 'id' },
    { title: 'userId', dataIndex: 'userId', key: 'userId' },
    { title: 'productId', dataIndex: 'productId', key: 'productId' },
    { title: 'applyAmount', dataIndex: 'applyAmount', key: 'applyAmount' },
    { title: 'status', dataIndex: 'status', render: (v: string) => <Tag>{v}</Tag> },
    { title: 'riskScore', dataIndex: 'riskScore', render: (v?: number) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, row: CreditApplication) => (
        <Space>
          <Button type="primary" size="small" onClick={() => { setModal({ type: 'approve', id: row.id! }); form.resetFields(); }}>
            Approve
          </Button>
          <Button danger size="small" onClick={() => { setModal({ type: 'reject', id: row.id! }); form.resetFields(); }}>
            Reject
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Typography.Title level={4}>人工复核（MANUAL_REVIEW）</Typography.Title>
      <Table rowKey={(r) => String(r.id)} loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={!!modal}
        title={modal?.type === 'approve' ? '审批通过' : '审批拒绝'}
        onCancel={() => setModal(null)}
        onOk={submit}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="reviewReason" label="reviewReason" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="填写复核理由" />
          </Form.Item>
          {modal?.type === 'approve' && (
            <Form.Item name="approvedAmount" label="approvedAmount（可选）">
              <Input placeholder="默认使用建议额度" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}
