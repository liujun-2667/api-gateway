import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { RateLimitConfig, RateLimitConfigPageResponse } from '../../shared/models/rate-limit.model';

@Injectable({
  providedIn: 'root'
})
export class RateLimitService extends ApiService<RateLimitConfig> {
  protected override endpoint = 'rate-limits';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getRateLimits(tenantId?: number, applicationId?: number, page: number = 0, size: number = 10): Observable<RateLimitConfigPageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) params['tenantId'] = tenantId;
    if (applicationId) params['applicationId'] = applicationId;
    return this.getAll(params) as Observable<RateLimitConfigPageResponse>;
  }

  toggle(id: number): Observable<RateLimitConfig> {
    return this.http.post<RateLimitConfig>(`${this.baseUrl}/${this.endpoint}/${id}/toggle`, {});
  }
}
