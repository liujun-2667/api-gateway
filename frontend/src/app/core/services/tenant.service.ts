import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Tenant, TenantPageResponse } from '../../shared/models/tenant.model';

@Injectable({
  providedIn: 'root'
})
export class TenantService extends ApiService<Tenant> {
  protected override endpoint = 'tenants';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  getTenants(page: number = 0, size: number = 10): Observable<TenantPageResponse> {
    return this.getAll({ page, size }) as Observable<TenantPageResponse>;
  }

  getAllTenants(): Observable<Tenant[]> {
    return this.http.get<Tenant[]>(`${this.baseUrl}/${this.endpoint}/all`);
  }
}
