export interface QpsMetrics {
  timestamps: string[];
  values: number[];
}

export interface StatusCodeDistribution {
  statusCode: string;
  count: number;
  percentage: number;
}

export interface LatencyMetrics {
  p50: number;
  p90: number;
  p99: number;
  p50Values: number[];
  p90Values: number[];
  p99Values: number[];
  timestamps: string[];
}

export interface TenantMetrics {
  tenantId: number;
  tenantName: string;
  requestCount: number;
  percentage: number;
}

export interface DashboardMetrics {
  totalRequests: number;
  totalQps: number;
  averageLatency: number;
  errorRate: number;
  qpsMetrics: QpsMetrics;
  statusCodeDistribution: StatusCodeDistribution[];
  latencyMetrics: LatencyMetrics;
  tenantMetrics: TenantMetrics[];
}
