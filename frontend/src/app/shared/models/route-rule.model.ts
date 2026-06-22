export enum HttpMethod {
  GET = 'GET',
  POST = 'POST',
  PUT = 'PUT',
  DELETE = 'DELETE',
  PATCH = 'PATCH',
  HEAD = 'HEAD',
  OPTIONS = 'OPTIONS',
  ALL = 'ALL'
}

export enum MatchType {
  EXACT = 'EXACT',
  PREFIX = 'PREFIX',
  REGEX = 'REGEX'
}

export enum RuleStatus {
  DRAFT = 'DRAFT',
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  DEPRECATED = 'DEPRECATED'
}

export interface TargetBackend {
  url: string;
  weight: number;
  colorTag?: string;
}

export interface DiffField {
  fieldName: string;
  oldValue: string;
  newValue: string;
  changeType: 'ADD' | 'REMOVE' | 'MODIFY';
}

export interface DiffResponse {
  version1Id: number;
  version2Id: number;
  diffs: DiffField[];
  diffsByCategory: { [key: string]: DiffField[] };
}

export interface BatchOperationRequest {
  ids: number[];
  operation: 'ENABLE' | 'DISABLE' | 'SUBMIT_APPROVAL' | 'DELETE';
}

export interface BatchOperationResult {
  id: number;
  success: boolean;
  message: string;
}

export interface BatchOperationResponse {
  results: BatchOperationResult[];
  successCount: number;
  failedCount: number;
}

export interface RouteRule {
  id?: number;
  tenantId: number;
  tenantName?: string;
  applicationId: number;
  applicationName?: string;
  name: string;
  description?: string;
  path: string;
  method: HttpMethod;
  matchType: MatchType;
  targetPath?: string;
  priority: number;
  status: RuleStatus;
  version?: number;
  stripPrefix?: boolean;
  targetBackends?: TargetBackend[];
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  maxRetries?: number;
  retryOn5xx?: boolean;
  retryOnTimeout?: boolean;
  retryIntervalMs?: number;
  requestHeadersToAdd?: { [key: string]: string };
  requestHeadersToRemove?: string[];
  pathPrefixReplacement?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RouteRuleVersion {
  id: number;
  ruleId: number;
  version: number;
  name: string;
  description?: string;
  path: string;
  method: HttpMethod;
  matchType: MatchType;
  targetPath?: string;
  priority: number;
  status: RuleStatus;
  targetBackends?: TargetBackend[];
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  maxRetries?: number;
  retryOn5xx?: boolean;
  retryOnTimeout?: boolean;
  retryIntervalMs?: number;
  requestHeadersToAdd?: { [key: string]: string };
  requestHeadersToRemove?: string[];
  pathPrefixReplacement?: string;
  changeLog?: string;
  createdBy?: string;
  createdAt: string;
}

export interface RouteRuleApproval {
  id: number;
  ruleId: number;
  status: RuleStatus;
  comment?: string;
  approvedBy?: string;
  approvedAt?: string;
}

export interface RouteRulePageResponse {
  content: RouteRule[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
