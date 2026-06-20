import request, { ApiResult } from '../utils/request';

export interface CreditApplication {
  id?: number;
  applicationId?: number;
  applicationNo?: string;
  productId?: number;
  productName?: string;
  applyAmount?: number;
  applyTerm?: number;
  purpose?: string;
  content?: string;
  status?: string;
  finalDecision?: string;
  platformDecision?: string;
  approvedAmount?: number;
  approvedRate?: number;
  approvedTerm?: number;
  riskScore?: number;
  riskLevel?: string;
  hitRules?: string[];
  decisionReason?: string[];
  agentSuggestion?: string;
  fraudScore?: number;
  bureauUnavailable?: boolean;
  conflictDetected?: boolean;
  platformDecisionJson?: string;
  workflowId?: string;
  createdAt?: string;
  createTime?: string;
}

export interface WorkflowTraceNode {
  id?: number;
  applicationId?: number;
  taskId?: number;
  workflowId?: string;
  traceId?: string;
  nodeName?: string;
  latencyMs?: number;
  status?: string;
  errorMessage?: string;
  toolCallsJson?: string;
  mcpLatencyMs?: number;
}

export interface ReviewFeedback {
  applicationId?: number;
  agentSuggestion?: string;
  javaDecision?: string;
  humanDecision?: string;
  riskScore?: number;
  hitRules?: string;
  reviewReason?: string;
  reviewedBy?: number;
  reviewedAt?: string;
}

export async function submitApply(payload: Record<string, unknown>) {
  const res = await request.post<ApiResult>('/credit/apply/submit', payload);
  return res.data.data as { taskId?: number; workflowId?: string; pollUrl?: string };
}

export async function getTaskStatus(taskId: string | number) {
  const res = await request.get<ApiResult>(`/credit/apply/task/${taskId}`);
  return res.data.data;
}

export async function getMyApplications() {
  const res = await request.get<ApiResult>('/credit/apply/mine');
  return (res.data.data || []) as CreditApplication[];
}

export async function getApplicationDetail(id: string | number) {
  const res = await request.get<ApiResult>(`/credit/apply/${id}`);
  return res.data.data as CreditApplication;
}

export async function getProduct(productId: string | number) {
  const res = await request.get<ApiResult>(`/credit/product/${productId}`);
  return res.data.data;
}

export async function getPendingReviews() {
  const res = await request.get<ApiResult>('/admin/credit/review/pending');
  return (res.data.data || []) as CreditApplication[];
}

export async function approveReview(id: string | number, body: Record<string, unknown>) {
  const res = await request.put<ApiResult>(`/admin/credit/review/${id}/approve`, body);
  return res.data.data;
}

export async function rejectReview(id: string | number, reason: string) {
  const res = await request.put<ApiResult>(`/admin/credit/review/${id}/reject`, { reason });
  return res.data.data;
}

export async function getWorkflowTrace(applicationId: string | number) {
  const res = await request.get<ApiResult>(`/admin/credit/apply/${applicationId}/trace`);
  return res.data.data as { nodes?: WorkflowTraceNode[]; workflowNodes?: WorkflowTraceNode[] };
}

export async function getReviewFeedback(limit = 20) {
  const res = await request.get<ApiResult>(`/admin/credit/review/feedback?limit=${limit}`);
  return (res.data.data || []) as ReviewFeedback[];
}
