import { Table, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { ReviewFeedback, getReviewFeedback } from '../api/credit';

export default function AdminFeedbackPage() {
  const [data, setData] = useState<ReviewFeedback[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getReviewFeedback(50)
      .then(setData)
      .finally(() => setLoading(false));
  }, []);

  const columns = [
    { title: 'applicationId', dataIndex: 'applicationId', key: 'applicationId' },
    { title: 'agentSuggestion', dataIndex: 'agentSuggestion', key: 'agentSuggestion' },
    { title: 'javaDecision', dataIndex: 'javaDecision', key: 'javaDecision' },
    { title: 'humanDecision', dataIndex: 'humanDecision', key: 'humanDecision' },
    { title: 'riskScore', dataIndex: 'riskScore', key: 'riskScore' },
    { title: 'hitRules', dataIndex: 'hitRules', key: 'hitRules' },
    { title: 'reviewReason', dataIndex: 'reviewReason', key: 'reviewReason' },
    { title: 'reviewedBy', dataIndex: 'reviewedBy', key: 'reviewedBy' },
    { title: 'reviewedAt', dataIndex: 'reviewedAt', key: 'reviewedAt' },
  ];

  return (
    <>
      <Typography.Title level={4}>人工反馈</Typography.Title>
      <Table rowKey={(r, i) => String(r.applicationId ?? i)} loading={loading} columns={columns} dataSource={data} scroll={{ x: true }} />
    </>
  );
}
