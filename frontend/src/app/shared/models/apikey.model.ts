export enum ApiKeyStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  EXPIRED = 'EXPIRED'
}

export interface ApiKey {
  id?: number;
  tenantId: number;
  tenantName?: string;
  applicationId?: number;
  applicationName?: string;
  name: string;
  key?: string;
  secret?: string;
  status: ApiKeyStatus;
  quotaPerDay: number;
  rateLimitPerSecond: number;
  expiresAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiKeyPageResponse {
  content: ApiKey[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
