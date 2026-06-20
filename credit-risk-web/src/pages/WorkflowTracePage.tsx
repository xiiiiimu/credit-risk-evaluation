import { Alert, Button, Input, Space, Table, Tag, Timeline, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { WorkflowTraceNode, getWorkflowTrace } from '../api/credit';

function parseToolCalls(json?: string): string {
  if (!json) return '-';
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed.join(', ') : json;
  } catch {
    return json;
  }
}

function isAbnormal(node: WorkflowTraceNode): boolean {
  const status = (node.status || '').toUpperCase();
  return status === 'FAILED' || status === 'DEGRADED' || !!node.errorMessage;
}

function isMcpTimeout(node: WorkflowTraceNode): boolean {
  return (node.nodeName || '') === 'credit_assessment'
    && ((node.status || '').toUpperCase() === 'DEGRADED' || (node.errorMessage || '').toLowerCase().includes('timeout'));
}

export default function WorkflowTracePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [applicationId, setApplicationId] = useState(id && id !== '0' ? id : '');
  const [nodes, setNodes] = useState<WorkflowTraceNode[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async (appId: string) => {
    if (!appId) return;
    setLoading(true);
    try {
      const data = await getWorkflowTrace(appId);
      setNodes(data.nodes || data.workflowNodes || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id && id !== '0') {
      setApplicationId(id);
      load(id);
    }
  }, [id]);

  const columns = [
    {
      title: 'node_name',
      dataIndex: 'nodeName',
      render: (v: string, row: WorkflowTraceNode) => (
        <Space>
          {v}
          {isAbnormal(row) && <Tag color="error">异常</Tag>}
          {isMcpTimeout(row) && <Tag color="orange">MCP超时</Tag>}
        </Space>
      ),
    },
    { title: 'latency_ms', dataIndex: 'latencyMs', render: (v?: number) => v ?? '-' },
    { title: 'status', dataIndex: 'status', render: (v?: string) => <Tag>{v || '-'}</Tag> },
    { title: 'error_message', dataIndex: 'errorMessage', render: (v?: string) => v || '-' },
    { title: 'tool_calls', dataIndex: 'toolCallsJson', render: (v?: string) => parseToolCalls(v) },
    { title: 'mcp_latency_ms', dataIndex: 'mcpLatencyMs', render: (v?: number) => v ?? '-' },
    { title: 'task_id', dataIndex: 'taskId', render: (v?: number) => v ?? '-' },
    { title: 'trace_id', dataIndex: 'traceId', render: (v?: string) => v || '-' },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4}>Workflow Trace</Typography.Title>
      <Space>
        <Input
          placeholder="输入 applicationId"
          value={applicationId}
          onChange={(e) => setApplicationId(e.target.value)}
          style={{ width: 240 }}
        />
        <Button type="primary" loading={loading} onClick={() => { navigate(`/admin/applications/${applicationId}/trace`); load(applicationId); }}>
          查询
        </Button>
      </Space>
      {nodes.length === 0 && <Alert type="info" message="暂无 trace 数据，请先提交申请并完成 Agent 分析" />}
      <Timeline
        items={nodes.map((n) => ({
          color: isAbnormal(n) ? 'red' : isMcpTimeout(n) ? 'orange' : 'green',
          children: `${n.nodeName} · ${n.status || '-'} · ${n.latencyMs ?? '-'}ms`,
        }))}
      />
      <Table rowKey={(r, i) => String(r.id ?? i)} loading={loading} columns={columns} dataSource={nodes} pagination={false} />
    </Space>
  );
}
