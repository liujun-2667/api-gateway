import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { TrafficColorRule, TrafficColorRulePageResponse } from '../../shared/models/color-rule.model';
import { GrayRelease } from '../../shared/models/gray-release.model';

@Injectable({
  providedIn: 'root'
})
export class ColorRuleService extends ApiService<TrafficColorRule> {
  protected override endpoint = 'color-rules';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  private getAppBaseUrl(appId: number): string {
    return `${this.baseUrl}/apps/${appId}/${this.endpoint}`;
  }

  getColorRules(tenantId?: number, appId?: number, page: number = 0, size: number = 10): Observable<TrafficColorRulePageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) params['tenantId'] = tenantId;
    if (appId) {
      return this.http.get<TrafficColorRulePageResponse>(this.getAppBaseUrl(appId), { params });
    }
    return this.getAll(params) as Observable<TrafficColorRulePageResponse>;
  }

  applyAll(appId: number, tenantId: number): Observable<void> {
    return this.http.post<void>(`${this.getAppBaseUrl(appId)}/enable-all`, {}, {
      params: { tenantId }
    });
  }

  clearAll(appId: number, tenantId: number): Observable<void> {
    return this.http.delete<void>(`${this.getAppBaseUrl(appId)}/clear-all`, {
      params: { tenantId }
    });
  }

  toggle(appId: number, id: number): Observable<TrafficColorRule> {
    return this.http.post<TrafficColorRule>(`${this.getAppBaseUrl(appId)}/${id}/toggle`, {});
  }

  getActiveGrayReleases(appId: number): Observable<GrayRelease[]> {
    return this.http.get<GrayRelease[]>(`${this.getAppBaseUrl(appId)}/gray-releases`);
  }

  override create(appId: number, data: Partial<TrafficColorRule>): Observable<TrafficColorRule> {
    return this.http.post<TrafficColorRule>(this.getAppBaseUrl(appId), data);
  }

  override update(appId: number, id: number, data: Partial<TrafficColorRule>): Observable<TrafficColorRule> {
    return this.http.put<TrafficColorRule>(`${this.getAppBaseUrl(appId)}/${id}`, data);
  }

  override delete(appId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.getAppBaseUrl(appId)}/${id}`);
  }
}
