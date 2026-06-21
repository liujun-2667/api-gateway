export interface RateLimitConfig {
  id?: number;
  tenantId: number;
  tenantName?: string;
  applicationId?: number;
  applicationName?: string;
  routeRuleId?: number;
  name: string;
  requestsPerSecond: number;
  burstCapacity: number;
  windowSizeSeconds: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface RateLimitConfigPageResponse {
  content: RateLimitConfig[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
