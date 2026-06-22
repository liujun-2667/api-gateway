import { TrafficColorRule } from './color-rule.model';
import { RouteRule } from './route-rule.model';

export enum GrayReleaseStatus {
  PENDING = 'PENDING',
  IN_PROGRESS = 'IN_PROGRESS',
  PAUSED = 'PAUSED',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  ROLLED_BACK = 'ROLLED_BACK'
}

export enum GrayReleasePhase {
  INITIAL = 'INITIAL',
  STAGE_1 = 'STAGE_1',
  STAGE_2 = 'STAGE_2',
  STAGE_3 = 'STAGE_3',
  FULL = 'FULL'
}

export interface GrayReleaseWizardStep1 {
  appId: number;
  routeRuleId: number;
  name: string;
  description: string;
}

export interface GrayReleaseWizardStep2 {
  initialPercent: number;
  releaseStages: number[];
  observationMinutesPerStage: number;
  errorRateThreshold: number;
}

export interface GrayReleaseWizardStep3 {
  confirmation: boolean;
}

export interface GrayReleaseCreateRequest extends GrayReleaseWizardStep1, GrayReleaseWizardStep2, GrayReleaseWizardStep3 {}

export interface GrayRelease {
  id: number;
  grayReleaseId: string;
  name: string;
  description: string;
  routeRuleId: number;
  appId: number;
  status: GrayReleaseStatus;
  currentPhase: GrayReleasePhase;
  currentTrafficPercent: number;
  errorRateThreshold: number;
  currentErrorRate: number;
  nextStageTime: string;
  totalStages: number;
  completedStages: number;
  createdAt: string;
  createdBy: string;
}

export interface GrayReleaseActionRequest {
  action: 'FULL' | 'ROLLBACK' | 'PAUSE' | 'RESUME';
  reason: string;
}

export interface GrayReleaseStatusResponse {
  grayRelease: GrayRelease;
  colorRule: TrafficColorRule;
  routeRule: RouteRule;
}
