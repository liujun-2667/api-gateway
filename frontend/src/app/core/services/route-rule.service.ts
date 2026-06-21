import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  RouteRule,
  RouteRulePageResponse,
  RouteRuleVersion,
  RouteRuleApproval
} from '../../shared/models/route-rule.model';

@Injectable({
  providedIn: 'root'
})
export class RouteRuleService extends ApiService<RouteRule> {
  protected override endpoint = 'route-rules';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getRouteRules(tenantId?: number, applicationId?: number, page: number = 0, size: number = 10): Observable<RouteRulePageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) params['tenantId'] = tenantId;
    if (applicationId) params['applicationId'] = applicationId;
    return this.getAll(params) as Observable<RouteRulePageResponse>;
  }

  submitForApproval(id: number): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.baseUrl}/${this.endpoint}/${id}/submit`, {});
  }

  approve(id: number, comment?: string): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.baseUrl}/${this.endpoint}/${id}/approve`, { comment });
  }

  reject(id: number, comment?: string): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.baseUrl}/${this.endpoint}/${id}/reject`, { comment });
  }

  getVersions(id: number): Observable<RouteRuleVersion[]> {
    return this.http.get<RouteRuleVersion[]>(`${this.baseUrl}/${this.endpoint}/${id}/versions`);
  }

  rollback(id: number, versionId: number): Observable<RouteRule> {
    return this.http.post<RouteRule>(`${this.baseUrl}/${this.endpoint}/${id}/rollback/${versionId}`, {});
  }

  getApprovals(id: number): Observable<RouteRuleApproval[]> {
    return this.http.get<RouteRuleApproval[]>(`${this.baseUrl}/${this.endpoint}/${id}/approvals`);
  }
}
