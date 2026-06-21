import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { CircuitBreakerConfig, CircuitBreakerConfigPageResponse } from '../../shared/models/circuit-breaker.model';

@Injectable({
  providedIn: 'root'
})
export class CircuitBreakerService extends ApiService<CircuitBreakerConfig> {
  protected override endpoint = 'circuit-breakers';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getCircuitBreakers(tenantId?: number, applicationId?: number, page: number = 0, size: number = 10): Observable<CircuitBreakerConfigPageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) params['tenantId'] = tenantId;
    if (applicationId) params['applicationId'] = applicationId;
    return this.getAll(params) as Observable<CircuitBreakerConfigPageResponse>;
  }

  toggle(id: number): Observable<CircuitBreakerConfig> {
    return this.http.post<CircuitBreakerConfig>(`${this.baseUrl}/${this.endpoint}/${id}/toggle`, {});
  }

  reset(id: number): Observable<CircuitBreakerConfig> {
    return this.http.post<CircuitBreakerConfig>(`${this.baseUrl}/${this.endpoint}/${id}/reset`, {});
  }
}
