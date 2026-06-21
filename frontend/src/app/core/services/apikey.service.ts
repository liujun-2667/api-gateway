import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { ApiKey, ApiKeyPageResponse } from '../../shared/models/apikey.model';

@Injectable({
  providedIn: 'root'
})
export class ApiKeyService extends ApiService<ApiKey> {
  protected override endpoint = 'api-keys';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getApiKeys(tenantId?: number, applicationId?: number, page: number = 0, size: number = 10): Observable<ApiKeyPageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) params['tenantId'] = tenantId;
    if (applicationId) params['applicationId'] = applicationId;
    return this.getAll(params) as Observable<ApiKeyPageResponse>;
  }

  regenerateKey(id: number): Observable<ApiKey> {
    return this.http.post<ApiKey>(`${this.baseUrl}/${this.endpoint}/${id}/regenerate`, {});
  }

  toggleStatus(id: number): Observable<ApiKey> {
    return this.http.post<ApiKey>(`${this.baseUrl}/${this.endpoint}/${id}/toggle`, {});
  }
}
