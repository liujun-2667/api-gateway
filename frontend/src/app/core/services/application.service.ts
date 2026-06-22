import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Application, ApplicationPageResponse } from '../../shared/models/application.model';

@Injectable({
  providedIn: 'root'
})
export class ApplicationService extends ApiService<Application> {
  protected override endpoint = 'applications';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getApplications(tenantId?: number, page: number = 0, size: number = 10): Observable<ApplicationPageResponse> {
    const params: Record<string, any> = { page, size };
    if (tenantId) {
      params['tenantId'] = tenantId;
    }
    return this.getAll(params) as Observable<ApplicationPageResponse>;
  }

  getApplicationsByTenant(tenantId: number): Observable<Application[]> {
    return this.http.get<Application[]>(`${this.baseUrl}/${this.endpoint}`, {
      params: { tenantId }
    });
  }

  getAllApplications(): Observable<Application[]> {
    return this.http.get<Application[]>(`${this.baseUrl}/${this.endpoint}`);
  }
}
