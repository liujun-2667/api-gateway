export interface Application {
  id?: number;
  tenantId: number;
  tenantName?: string;
  name: string;
  code: string;
  description?: string;
  baseUrl: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApplicationPageResponse {
  content: Application[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
