import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardMetrics, QpsMetrics, StatusCodeDistribution, LatencyMetrics, TenantMetrics } from '../../shared/models/metrics.model';

@Injectable({
  providedIn: 'root'
})
export class MetricsService {
  private endpoint = 'metrics';

  constructor(private http: HttpClient) {}

  protected get baseUrl(): string {
    return environment.API_BASE_URL;
  }

  getDashboardMetrics(): Observable<DashboardMetrics> {
    return this.http.get<DashboardMetrics>(`${this.baseUrl}/${this.endpoint}/dashboard`);
  }

  getQpsMetrics(minutes: number = 60): Observable<QpsMetrics> {
    const params = new HttpParams().set('minutes', minutes.toString());
    return this.http.get<QpsMetrics>(`${this.baseUrl}/${this.endpoint}/qps`, { params });
  }

  getStatusCodeDistribution(): Observable<StatusCodeDistribution[]> {
    return this.http.get<StatusCodeDistribution[]>(`${this.baseUrl}/${this.endpoint}/status-codes`);
  }

  getLatencyMetrics(minutes: number = 60): Observable<LatencyMetrics> {
    const params = new HttpParams().set('minutes', minutes.toString());
    return this.http.get<LatencyMetrics>(`${this.baseUrl}/${this.endpoint}/latency`, { params });
  }

  getTenantMetrics(): Observable<TenantMetrics[]> {
    return this.http.get<TenantMetrics[]>(`${this.baseUrl}/${this.endpoint}/tenants`);
  }
}
