import { Button, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CreditApplication, getMyApplications } from '../api/credit';

export default function ApplicationsPage() {
  const [data, setData] = useState<CreditApplication[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    try {
      setData(await getMyApplications());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const columns = [
    { title: 'applicationId', dataIndex: 'id', key: 'id' },
    { title: 'productId', dataIndex: 'productId', key: 'productId' },
    { title: 'applyAmount', dataIndex: 'applyAmount', key: 'applyAmount' },
    {
      title: 'status',
      dataIndex: 'status',
      key: 'status',
      render: (v: string) => <Tag>{v || '-'}</Tag>,
    },
    {
      title: 'platformDecision',
      key: 'platformDecision',
      render: (_: unknown, row: CreditApplication) => row.finalDecision || row.platformDecision || '-',
    },
    { title: 'riskScore', dataIndex: 'riskScore', key: 'riskScore', render: (v?: number) => v ?? '-' },
    {
      title: 'createdAt',
      key: 'createdAt',
      render: (_: unknown, row: CreditApplication) => row.createTime || row.createdAt || '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, row: CreditApplication) => (
        <Button type="link" onClick={() => navigate(`/applications/${row.id}`)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <>
      <Typography.Title level={4}>我的申请</Typography.Title>
      <Table rowKey={(r) => String(r.id)} loading={loading} columns={columns} dataSource={data} />
    </>
  );
}
