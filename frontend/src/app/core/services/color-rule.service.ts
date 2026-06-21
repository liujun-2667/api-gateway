import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { TrafficColorRule, TrafficColorRulePageResponse } from '../../shared/models/color-rule.model';

@Injectable({
  providedIn: 'root'
})
export class ColorRuleService extends ApiService<TrafficColorRule> {
  protected override endpoint = 'color-rules';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getColorRules(tenantId?: number, applicationId?: number, page: number = 0, size: number = 10): Observable<TrafficColorRulePageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) params['tenantId'] = tenantId;
    if (applicationId) params['applicationId'] = applicationId;
    return this.getAll(params) as Observable<TrafficColorRulePageResponse>;
  }

  applyAll(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${this.endpoint}/apply-all`, {});
  }

  clearAll(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${this.endpoint}/clear-all`, {});
  }

  toggle(id: number): Observable<TrafficColorRule> {
    return this.http.post<TrafficColorRule>(`${this.baseUrl}/${this.endpoint}/${id}/toggle`, {});
  }
}
