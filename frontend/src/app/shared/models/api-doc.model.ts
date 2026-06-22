export type DocStatus = 'DRAFT' | 'PUBLISHED';
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS' | 'ALL';

export interface ApiDoc {
  id: number;
  docId: string;
  name: string;
  description?: string;
  version?: string;
  applicationId: number;
  applicationName?: string;
  status: DocStatus;
  createdBy?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
  groups?: ApiDocGroup[];
}

export interface ApiDocGroup {
  id: number;
  name: string;
  description?: string;
  sortOrder: number;
  docId: number;
  createdAt?: string;
  updatedAt?: string;
  endpoints?: ApiEndpoint[];
}

export interface ApiEndpoint {
  id: number;
  name: string;
  description?: string;
  method: HttpMethod;
  path: string;
  sortOrder: number;
  groupId: number;
  groupName?: string;
  requestParams?: any[];
  requestSchema?: any;
  responseSchema?: any;
  statusCodes?: any[];
  deprecated?: string;
  mockConfig?: MockConfig;
  createdAt?: string;
  updatedAt?: string;
}

export interface MockConfig {
  id: number;
  mockConfigId: string;
  endpointId: number;
  routeRuleId?: number;
  routeRuleName?: string;
  enabled: boolean;
  delayMs: number;
  faultInjectionPercent?: number;
  faultErrorCode?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DebugCase {
  id: number;
  name: string;
  description?: string;
  endpointId: number;
  endpointName?: string;
  requestParams?: any;
  requestHeaders?: any;
  requestBody?: any;
  expectedResponse?: any;
  useMock: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiChangeRecord {
  id: number;
  endpointId: number;
  endpointName?: string;
  changeType: string;
  changeSummary: string;
  changeDetails?: any[];
  changedBy?: string;
  createdAt?: string;
}

export interface ChangeNotification {
  docId: number;
  endpointId?: number;
  endpointName?: string;
  changeType: string;
  changeSummary?: string;
  timestamp?: string;
}

export interface DebugResponse {
  statusCode: number;
  responseHeaders?: { [key: string]: string };
  responseBody: any;
  latencyMs: number;
  isMock: boolean;
}

export interface BatchReplayResult {
  caseId: number;
  caseName?: string;
  success: boolean;
  message?: string;
  actualResponse?: DebugResponse;
  diffResult?: any;
}
