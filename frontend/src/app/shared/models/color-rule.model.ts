export enum TrafficConditionType {
  HEADER = 'HEADER',
  COOKIE = 'COOKIE',
  QUERY = 'QUERY',
  IP = 'IP',
  USER_AGENT = 'USER_AGENT'
}

export enum ColorTagOperation {
  ADD = 'ADD',
  REMOVE = 'REMOVE'
}

export interface TrafficColorRule {
  id?: number;
  tenantId: number;
  tenantName?: string;
  applicationId?: number;
  applicationName?: string;
  name: string;
  description?: string;
  colorTag: string;
  conditionType: TrafficConditionType;
  conditionKey?: string;
  conditionValue: string;
  operation: ColorTagOperation;
  priority: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface TrafficColorRulePageResponse {
  content: TrafficColorRule[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
