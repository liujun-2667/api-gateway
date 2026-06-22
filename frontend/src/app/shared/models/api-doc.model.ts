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

export interface VersionCompareRequest {
  leftRecordId: number;
  rightRecordId: number;
}

export interface VersionCompareResponse {
  leftRecordId: number;
  rightRecordId: number;
  leftTimestamp: string;
  rightTimestamp: string;
  leftChangedBy?: string;
  rightChangedBy?: string;
  leftRequestSchema?: any;
  rightRequestSchema?: any;
  leftResponseSchema?: any;
  rightResponseSchema?: any;
  requestSchemaDiff?: SchemaDiff[];
  responseSchemaDiff?: SchemaDiff[];
}

export interface SchemaDiff {
  field: string;
  path: string;
  changeType: 'ADD' | 'REMOVE' | 'MODIFY' | 'TYPE_CHANGE';
  oldValue?: any;
  newValue?: any;
  oldType?: string;
  newType?: string;
}

export interface ChangeRemark {
  id: number;
  changeRecordId: number;
  fieldPath: string;
  remarkType: 'COMPATIBLE' | 'NEEDS_ADAPTATION';
  remark?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChangeRemarkRequest {
  fieldPath: string;
  remarkType: 'COMPATIBLE' | 'NEEDS_ADAPTATION';
  remark?: string;
}

export type TestSuiteStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_FAILURES' | 'FAILED';
export type CaseExecutionStatus = 'WAITING' | 'RUNNING' | 'PASSED' | 'FAILED';

export interface TestSuite {
  id: number;
  name: string;
  description?: string;
  applicationId: number;
  applicationName?: string;
  caseOrder?: { caseId: number; caseName?: string; }[];
  dependencies?: { caseId: number; dependsOn: number; }[];
  globalVariables?: { [key: string]: any };
  concurrencyLevel: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestSuiteCreateRequest {
  name: string;
  description?: string;
  applicationId: number;
  caseOrder?: { caseId: number; }[];
  dependencies?: { caseId: number; dependsOn: number; }[];
  globalVariables?: { [key: string]: any };
  concurrencyLevel: number;
}

export interface TestSuiteUpdateRequest {
  name?: string;
  description?: string;
  caseOrder?: { caseId: number; }[];
  dependencies?: { caseId: number; dependsOn: number; }[];
  globalVariables?: { [key: string]: any };
  concurrencyLevel?: number;
}

export interface TestSuiteExecution {
  id: number;
  testSuiteId: number;
  testSuiteName: string;
  status: TestSuiteStatus;
  totalCases: number;
  passedCases?: number;
  failedCases?: number;
  totalDurationMs?: number;
  caseResults?: CaseExecutionResult[];
  executedBy?: string;
  createdAt?: string;
  updatedAt?: string;
  completedAt?: string;
}

export interface CaseExecutionResult {
  caseId: number;
  caseName: string;
  status: CaseExecutionStatus;
  durationMs?: number;
  response?: DebugResponse;
  diffResult?: any;
  errorMessage?: string;
}

export interface CaseExecutionProgress {
  caseId: number;
  caseName: string;
  status: CaseExecutionStatus;
  durationMs?: number;
  diffResult?: any;
  errorMessage?: string;
}

export interface TestReport {
  id: number;
  name: string;
  testSuiteId: number;
  testSuiteName: string;
  executionId: number;
  totalCases: number;
  passedCases: number;
  failedCases: number;
  successRate?: number;
  totalDurationMs: number;
  caseDetails?: CaseExecutionResult[];
  summary?: any;
  remarks?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestReportCreateRequest {
  name: string;
  testSuiteId: number;
  executionId: number;
  remarks?: string;
}
