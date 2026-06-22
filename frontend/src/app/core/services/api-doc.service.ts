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
  BatchReplayResult
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
}
