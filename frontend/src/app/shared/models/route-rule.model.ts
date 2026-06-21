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
  createdAt?: string;
  updatedAt?: string;
}

export interface RouteRuleVersion {
  id: number;
  ruleId: number;
  version: number;
  path: string;
  method: HttpMethod;
  matchType: MatchType;
  targetPath?: string;
  priority: number;
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
