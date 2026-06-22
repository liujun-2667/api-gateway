import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  RouteRule,
  RouteRulePageResponse,
  RouteRuleVersion,
  RouteRuleApproval,
  DiffResponse,
  BatchOperationRequest,
  BatchOperationResponse
} from '../../shared/models/route-rule.model';

@Injectable({
  providedIn: 'root'
})
export class RouteRuleService extends ApiService<RouteRule> {
  protected override endpoint = 'rules';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  private getAppBaseUrl(appId: number): string {
    return `${this.baseUrl}/apps/${appId}/${this.endpoint}`;
  }

  getRouteRules(tenantId?: number, appId?: number, page: number = 0, size: number = 10): Observable<RouteRulePageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) params['tenantId'] = tenantId;
    if (appId) {
      return this.http.get<RouteRulePageResponse>(this.getAppBaseUrl(appId), { params });
    }
    return this.getAll(params) as Observable<RouteRulePageResponse>;
  }

  submitForApproval(appId: number, id: number): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.getAppBaseUrl(appId)}/${id}/submit`, {});
  }

  approve(appId: number, id: number, comment?: string): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.getAppBaseUrl(appId)}/${id}/approve`, { comment });
  }

  reject(appId: number, id: number, comment?: string): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.getAppBaseUrl(appId)}/${id}/reject`, { comment });
  }

  getVersions(appId: number, id: number): Observable<RouteRuleVersion[]> {
    return this.http.get<RouteRuleVersion[]>(`${this.getAppBaseUrl(appId)}/${id}/versions`);
  }

  rollback(appId: number, id: number, version: number): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.getAppBaseUrl(appId)}/${id}/rollback`, {}, {
      params: { version }
    });
  }

  rollbackWithReason(appId: number, id: number, version: number, reason: string): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.getAppBaseUrl(appId)}/${id}/rollback-with-reason`, {}, {
      params: { version, reason }
    });
  }

  compareVersions(appId: number, id: number, version1Id: number, version2Id: number): Observable<DiffResponse> {
    return this.http.post<DiffResponse>(`${this.getAppBaseUrl(appId)}/${id}/versions/compare`, {
      version1Id, version2Id
    });
  }

  batchOperation(appId: number, request: BatchOperationRequest): Observable<BatchOperationResponse> {
    return this.http.post<BatchOperationResponse>(`${this.getAppBaseUrl(appId)}/batch`, request);
  }

  getApprovals(appId: number, id: number): Observable<RouteRuleApproval[]> {
    return this.http.get<RouteRuleApproval[]>(`${this.getAppBaseUrl(appId)}/${id}/approvals`);
  }

  override create(appId: number, data: Partial<RouteRule>): Observable<RouteRule> {
    return this.http.post<RouteRule>(this.getAppBaseUrl(appId), data);
  }

  override update(appId: number, id: number, data: Partial<RouteRule>): Observable<RouteRule> {
    return this.http.put<RouteRule>(`${this.getAppBaseUrl(appId)}/${id}`, data);
  }

  override delete(appId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.getAppBaseUrl(appId)}/${id}`);
  }
}
