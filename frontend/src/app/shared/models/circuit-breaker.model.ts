export enum CircuitBreakerState {
  CLOSED = 'CLOSED',
  OPEN = 'OPEN',
  HALF_OPEN = 'HALF_OPEN'
}

export interface CircuitBreakerConfig {
  id?: number;
  tenantId: number;
  tenantName?: string;
  applicationId?: number;
  applicationName?: string;
  routeRuleId?: number;
  name: string;
  failureThreshold: number;
  slowCallThreshold: number;
  slowCallDurationMs: number;
  waitDurationInOpenStateMs: number;
  permittedNumberOfCallsInHalfOpenState: number;
  slidingWindowSize: number;
  minimumNumberOfCalls: number;
  enabled: boolean;
  state?: CircuitBreakerState;
  createdAt?: string;
  updatedAt?: string;
}

export interface CircuitBreakerConfigPageResponse {
  content: CircuitBreakerConfig[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
