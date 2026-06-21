import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditLogPageResponse } from '../../shared/models/audit-log.model';

@Injectable({
  providedIn: 'root'
})
export class AuditService {
  private endpoint = 'audit-logs';

  constructor(private http: HttpClient) {}

  protected get baseUrl(): string {
    return environment.API_BASE_URL;
  }

  getAuditLogs(
    page: number = 0,
    size: number = 10,
    filters?: {
      operationType?: string;
      entityType?: string;
      username?: string;
      startDate?: string;
      endDate?: string;
    }
  ): Observable<AuditLogPageResponse> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (filters) {
      if (filters.operationType) params = params.set('operationType', filters.operationType);
      if (filters.entityType) params = params.set('entityType', filters.entityType);
      if (filters.username) params = params.set('username', filters.username);
      if (filters.startDate) params = params.set('startDate', filters.startDate);
      if (filters.endDate) params = params.set('endDate', filters.endDate);
    }

    return this.http.get<AuditLogPageResponse>(`${this.baseUrl}/${this.endpoint}`, { params });
  }
}
