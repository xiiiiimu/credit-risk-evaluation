import { Alert, Descriptions, Spin, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getTaskStatus } from '../api/credit';

const TERMINAL = new Set(['SUCCESS', 'FAILED']);

export default function TaskStatusPage() {
  const { taskId } = useParams();
  const [task, setTask] = useState<Record<string, unknown> | null>(null);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    if (!taskId) return;
    const poll = async () => {
      try {
        const data = (await getTaskStatus(taskId)) as Record<string, unknown>;
        setTask(data);
        const status = String(data?.status || '');
        if (TERMINAL.has(status) && timerRef.current) {
          window.clearInterval(timerRef.current);
        }
      } catch {
        /* handled by interceptor */
      }
    };
    poll();
    timerRef.current = window.setInterval(poll, 2000);
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current);
    };
  }, [taskId]);

  if (!task) {
    return <Spin tip="加载任务状态..." />;
  }

  const status = String(task.status || '-');

  return (
    <>
      <Typography.Title level={4}>任务状态 #{taskId}</Typography.Title>
      <Alert
        type={status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'error' : 'info'}
        message={<><Tag>{status}</Tag> 每 2 秒自动刷新，终态后停止</>}
        style={{ marginBottom: 16 }}
      />
      <Descriptions bordered column={1}>
        {Object.entries(task).map(([key, value]) => (
          <Descriptions.Item key={key} label={key}>
            {typeof value === 'object' ? JSON.stringify(value) : String(value ?? '-')}
          </Descriptions.Item>
        ))}
      </Descriptions>
    </>
  );
}
