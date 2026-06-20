import { Button, Descriptions, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { CreditApplication, getApplicationDetail } from '../api/credit';

function safeList(value?: string[] | string): string {
  if (Array.isArray(value)) return value.join('；') || '-';
  return value || '-';
}

function parsePlatformDecision(detail: CreditApplication) {
  if (!detail.platformDecisionJson) {
    return {};
  }
  try {
    return JSON.parse(detail.platformDecisionJson) as {
      approvedAmount?: number;
      approvedRate?: number;
      approvedTerm?: number;
    };
  } catch {
    return {};
  }
}

export default function ApplicationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<CreditApplication | null>(null);

  useEffect(() => {
    if (!id) return;
    getApplicationDetail(id).then(setDetail).catch(() => setDetail(null));
  }, [id]);

  if (!detail) {
    return <Typography.Text>加载中或暂无数据...</Typography.Text>;
  }

  const platform = parsePlatformDecision(detail);

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4}>申请详情 #{detail.id}</Typography.Title>
      <Descriptions bordered column={2}>
        <Descriptions.Item label="产品">{detail.productName || detail.productId}</Descriptions.Item>
        <Descriptions.Item label="金额">{detail.applyAmount ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="期限">{detail.applyTerm ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="用途">{detail.purpose ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="状态"><Tag>{detail.status || '-'}</Tag></Descriptions.Item>
        <Descriptions.Item label="risk_score">{detail.riskScore ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="risk_level">{detail.riskLevel ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="platform_decision">{detail.finalDecision ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="approved_amount">{detail.approvedAmount ?? platform.approvedAmount ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="approved_rate">{detail.approvedRate ?? platform.approvedRate ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="approved_term">{detail.approvedTerm ?? platform.approvedTerm ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="hit_rules" span={2}>{safeList(detail.hitRules)}</Descriptions.Item>
        <Descriptions.Item label="decision_reason" span={2}>{safeList(detail.decisionReason)}</Descriptions.Item>
        <Descriptions.Item label="agent_suggestion">{detail.agentSuggestion ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="fraud_score">{detail.fraudScore ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="bureau_unavailable">{String(detail.bureauUnavailable ?? false)}</Descriptions.Item>
        <Descriptions.Item label="conflict_detected">{String(detail.conflictDetected ?? false)}</Descriptions.Item>
        <Descriptions.Item label="申请说明" span={2}>{detail.content ?? '-'}</Descriptions.Item>
      </Descriptions>
      <Space>
        <Button onClick={() => navigate(`/admin/applications/${detail.id}/trace`)}>查看 Workflow Trace</Button>
        {detail.workflowId && <Typography.Text type="secondary">workflowId: {detail.workflowId}</Typography.Text>}
      </Space>
    </Space>
  );
}
