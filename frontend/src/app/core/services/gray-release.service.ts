import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  GrayRelease,
  GrayReleaseCreateRequest,
  GrayReleaseActionRequest,
  GrayReleaseStatusResponse
} from '../../shared/models/gray-release.model';

@Injectable({
  providedIn: 'root'
})
export class GrayReleaseService extends ApiService<GrayRelease> {
  protected override endpoint = 'gray-releases';

  constructor(protected override http: HttpClient) {
    super(http);
  }

  createGrayRelease(appId: number, request: GrayReleaseCreateRequest): Observable<GrayRelease> {
    return this.http.post<GrayRelease>(`${this.baseUrl}/apps/${appId}/${this.endpoint}`, request);
  }

  getGrayReleases(appId: number): Observable<GrayRelease[]> {
    return this.http.get<GrayRelease[]>(`${this.baseUrl}/apps/${appId}/${this.endpoint}`);
  }

  getGrayRelease(appId: number, id: number): Observable<GrayRelease> {
    return this.http.get<GrayRelease>(`${this.baseUrl}/apps/${appId}/${this.endpoint}/${id}`);
  }

  getGrayReleaseStatus(appId: number, id: number): Observable<GrayReleaseStatusResponse> {
    return this.http.get<GrayReleaseStatusResponse>(`${this.baseUrl}/apps/${appId}/${this.endpoint}/${id}/status`);
  }

  performAction(appId: number, id: number, request: GrayReleaseActionRequest): Observable<GrayRelease> {
    return this.http.post<GrayRelease>(`${this.baseUrl}/apps/${appId}/${this.endpoint}/${id}/action`, request);
  }

  getActiveGrayReleases(): Observable<GrayRelease[]> {
    return this.http.get<GrayRelease[]>(`${this.baseUrl}/${this.endpoint}/active`);
  }

  getActiveGrayReleasesByApp(appId: number): Observable<GrayRelease[]> {
    return this.http.get<GrayRelease[]>(`${this.baseUrl}/apps/${appId}/${this.endpoint}`);
  }
}
