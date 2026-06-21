export interface Tenant {
  id?: number;
  name: string;
  code: string;
  description?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface TenantPageResponse {
  content: Tenant[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
