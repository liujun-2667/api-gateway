import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  ApiDoc,
  ApiDocGroup,
  ApiEndpoint,
  MockConfig,
  DebugCase,
  ApiChangeRecord,
  BatchReplayResult,
  VersionCompareRequest,
  VersionCompareResponse,
  ChangeRemark,
  ChangeRemarkRequest,
  TestSuite,
  TestSuiteCreateRequest,
  TestSuiteUpdateRequest,
  TestSuiteExecution,
  TestReport,
  TestReportCreateRequest
} from '../../shared/models/api-doc.model';

@Injectable({
  providedIn: 'root'
})
export class ApiDocService extends ApiService<ApiDoc> {
  protected override endpoint = 'api-docs';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getApiDocsByAppId(appId: number): Observable<ApiDoc[]> {
    return this.http.get<ApiDoc[]>(`${this.baseUrl}/apps/${appId}/api-docs`);
  }

  getApiDocById(id: number): Observable<ApiDoc> {
    return this.http.get<ApiDoc>(`${this.baseUrl}/api-docs/${id}`);
  }

  createApiDoc(appId: number, data: any): Observable<ApiDoc> {
    return this.http.post<ApiDoc>(`${this.baseUrl}/apps/${appId}/api-docs`, data);
  }

  updateApiDoc(id: number, data: any): Observable<ApiDoc> {
    return this.http.put<ApiDoc>(`${this.baseUrl}/api-docs/${id}`, data);
  }

  publishApiDoc(id: number): Observable<ApiDoc> {
    return this.http.post<ApiDoc>(`${this.baseUrl}/api-docs/${id}/publish`, {});
  }

  deleteApiDoc(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api-docs/${id}`);
  }

  importOpenApi(appId: number, openApiContent: string): Observable<ApiDoc> {
    return this.http.post<ApiDoc>(`${this.baseUrl}/apps/${appId}/api-docs/import`, { openApiContent });
  }

  createGroup(docId: number, data: any): Observable<ApiDocGroup> {
    return this.http.post<ApiDocGroup>(`${this.baseUrl}/api-docs/${docId}/groups`, data);
  }

  deleteGroup(groupId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api-docs/groups/${groupId}`);
  }

  createEndpoint(groupId: number, data: any): Observable<ApiEndpoint> {
    return this.http.post<ApiEndpoint>(`${this.baseUrl}/api-docs/groups/${groupId}/endpoints`, data);
  }

  updateEndpoint(endpointId: number, data: any): Observable<ApiEndpoint> {
    return this.http.put<ApiEndpoint>(`${this.baseUrl}/api-docs/endpoints/${endpointId}`, data);
  }

  deleteEndpoint(endpointId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api-docs/endpoints/${endpointId}`);
  }

  getChangeHistory(endpointId: number): Observable<ApiChangeRecord[]> {
    return this.http.get<ApiChangeRecord[]>(`${this.baseUrl}/api-docs/endpoints/${endpointId}/changes`);
  }

  getDocChangeHistory(docId: number): Observable<ApiChangeRecord[]> {
    return this.http.get<ApiChangeRecord[]>(`${this.baseUrl}/api-docs/${docId}/changes`);
  }

  updateMockConfig(endpointId: number, data: any): Observable<MockConfig> {
    return this.http.put<MockConfig>(`${this.baseUrl}/api-docs/endpoints/${endpointId}/mock-config`, data);
  }

  getMockConfig(endpointId: number): Observable<MockConfig> {
    return this.http.get<MockConfig>(`${this.baseUrl}/api-docs/endpoints/${endpointId}/mock-config`);
  }

  bindRouteRule(endpointId: number, routeRuleId: number): Observable<MockConfig> {
    return this.http.post<MockConfig>(`${this.baseUrl}/api-docs/endpoints/${endpointId}/bind-route/${routeRuleId}`, {});
  }

  createDebugCase(endpointId: number, data: any): Observable<DebugCase> {
    return this.http.post<DebugCase>(`${this.baseUrl}/api-docs/endpoints/${endpointId}/debug-cases`, data);
  }

  getDebugCases(endpointId: number): Observable<DebugCase[]> {
    return this.http.get<DebugCase[]>(`${this.baseUrl}/api-docs/endpoints/${endpointId}/debug-cases`);
  }

  getDebugCase(id: number): Observable<DebugCase> {
    return this.http.get<DebugCase>(`${this.baseUrl}/api-docs/debug-cases/${id}`);
  }

  updateDebugCase(id: number, data: any): Observable<DebugCase> {
    return this.http.put<DebugCase>(`${this.baseUrl}/api-docs/debug-cases/${id}`, data);
  }

  deleteDebugCase(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api-docs/debug-cases/${id}`);
  }

  batchReplay(caseIds: number[]): Observable<BatchReplayResult[]> {
    return this.http.post<BatchReplayResult[]>(`${this.baseUrl}/api-docs/debug-cases/batch-replay`, { caseIds });
  }

  sendDebugRequest(request: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api-docs/debug`, request);
  }

  getApiDocDetail(id: number): Observable<ApiDoc> {
    return this.http.get<ApiDoc>(`${this.baseUrl}/api-docs/${id}`);
  }

  compareVersions(request: VersionCompareRequest): Observable<VersionCompareResponse> {
    return this.http.post<VersionCompareResponse>(`${this.baseUrl}/version-compare`, request);
  }

  addRemark(changeRecordId: number, request: ChangeRemarkRequest): Observable<ChangeRemark> {
    return this.http.post<ChangeRemark>(`${this.baseUrl}/change-records/${changeRecordId}/remarks`, request);
  }

  getRemarks(changeRecordId: number): Observable<ChangeRemark[]> {
    return this.http.get<ChangeRemark[]>(`${this.baseUrl}/change-records/${changeRecordId}/remarks`);
  }

  deleteRemark(remarkId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/change-records/remarks/${remarkId}`);
  }

  createTestSuite(appId: number, data: TestSuiteCreateRequest): Observable<TestSuite> {
    return this.http.post<TestSuite>(`${this.baseUrl}/apps/${appId}/test-suites`, data);
  }

  getTestSuites(appId: number): Observable<TestSuite[]> {
    return this.http.get<TestSuite[]>(`${this.baseUrl}/apps/${appId}/test-suites`);
  }

  getTestSuite(id: number): Observable<TestSuite> {
    return this.http.get<TestSuite>(`${this.baseUrl}/test-suites/${id}`);
  }

  updateTestSuite(id: number, data: TestSuiteUpdateRequest): Observable<TestSuite> {
    return this.http.put<TestSuite>(`${this.baseUrl}/test-suites/${id}`, data);
  }

  deleteTestSuite(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/test-suites/${id}`);
  }

  executeTestSuite(id: number): Observable<TestSuiteExecution> {
    return this.http.post<TestSuiteExecution>(`${this.baseUrl}/test-suites/${id}/execute`, {});
  }

  getSuiteExecutions(suiteId: number): Observable<TestSuiteExecution[]> {
    return this.http.get<TestSuiteExecution[]>(`${this.baseUrl}/test-suites/${suiteId}/executions`);
  }

  getExecution(id: number): Observable<TestSuiteExecution> {
    return this.http.get<TestSuiteExecution>(`${this.baseUrl}/test-suites/executions/${id}`);
  }

  saveReport(data: TestReportCreateRequest): Observable<TestReport> {
    return this.http.post<TestReport>(`${this.baseUrl}/test-suites/reports`, data);
  }

  getSuiteReports(suiteId: number): Observable<TestReport[]> {
    return this.http.get<TestReport[]>(`${this.baseUrl}/test-suites/${suiteId}/reports`);
  }

  getReport(id: number): Observable<TestReport> {
    return this.http.get<TestReport>(`${this.baseUrl}/test-suites/reports/${id}`);
  }
}
