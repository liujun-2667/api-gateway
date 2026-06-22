import { Component, OnInit, Inject, Input, Output, EventEmitter, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatChipInputEvent } from '@angular/material/chips';
import { PageEvent } from '@angular/material/paginator';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { interval, Subscription } from 'rxjs';
import { MaterialModule } from '../../shared/material.module';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { TenantService } from '../../core/services/tenant.service';
import { ApplicationService } from '../../core/services/application.service';
import { ColorRuleService } from '../../core/services/color-rule.service';
import { RouteRuleService } from '../../core/services/route-rule.service';
import { GrayReleaseService } from '../../core/services/gray-release.service';
import { Tenant } from '../../shared/models/tenant.model';
import { Application } from '../../shared/models/application.model';
import { RouteRule } from '../../shared/models/route-rule.model';
import {
  TrafficColorRule, TrafficColorRulePageResponse,
  TrafficConditionType, ColorTagOperation
} from '../../shared/models/color-rule.model';
import {
  GrayRelease,
  GrayReleaseCreateRequest,
  GrayReleaseStatus,
  GrayReleasePhase,
  GrayReleaseActionRequest
} from '../../shared/models/gray-release.model';

function stagesValidator(control: AbstractControl): ValidationErrors | null {
  const stages = control.value as number[];
  if (!stages || stages.length === 0) {
    return { required: true };
  }
  const sorted = [...stages].sort((a, b) => a - b);
  if (sorted[sorted.length - 1] !== 100) {
    return { mustEndWith100: true };
  }
  for (let i = 0; i < sorted.length - 1; i++) {
    if (sorted[i] >= sorted[i + 1]) {
      return { increasing: true };
    }
  }
  return null;
}

@Component({
  selector: 'app-gray-release-wizard',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>灰度发布向导</h2>
    <mat-dialog-content>
      <mat-horizontal-stepper [linear]="true" #stepper>
        <mat-step [stepControl]="step1Form" label="选择目标应用和路由规则">
          <form [formGroup]="step1Form" class="step-form">
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>选择租户</mat-label>
                <mat-select formControlName="tenantId" (selectionChange)="onTenantChange($event.value)">
                  <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
                </mat-select>
                <mat-error *ngIf="step1Form.get('tenantId')?.hasError('required')">
                  请选择租户
                </mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>选择应用</mat-label>
                <mat-select formControlName="appId" (selectionChange)="onAppChange($event.value)">
                  <mat-option *ngFor="let a of applications" [value]="a.id">{{ a.name }}</mat-option>
                </mat-select>
                <mat-error *ngIf="step1Form.get('appId')?.hasError('required')">
                  请选择应用
                </mat-error>
              </mat-form-field>
            </div>
            <mat-form-field appearance="outline">
              <mat-label>选择路由规则</mat-label>
              <mat-select formControlName="routeRuleId">
                <mat-option *ngFor="let r of routeRules" [value]="r.id">{{ r.name }} ({{ r.path }})</mat-option>
              </mat-select>
              <mat-error *ngIf="step1Form.get('routeRuleId')?.hasError('required')">
                请选择路由规则
              </mat-error>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>灰度发布名称</mat-label>
              <input matInput formControlName="name" placeholder="例如：用户服务 v2.0 灰度发布">
              <mat-error *ngIf="step1Form.get('name')?.hasError('required')">
                请输入名称
              </mat-error>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>描述</mat-label>
              <textarea matInput formControlName="description" rows="3" placeholder="描述本次灰度发布的内容和目的"></textarea>
            </mat-form-field>
            <div class="step-actions">
              <button mat-raised-button color="primary" matStepperNext [disabled]="step1Form.invalid">
                下一步
                <mat-icon>arrow_forward</mat-icon>
              </button>
            </div>
          </form>
        </mat-step>

        <mat-step [stepControl]="step2Form" label="配置灰度策略">
          <form [formGroup]="step2Form" class="step-form">
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>初始流量百分比</mat-label>
                <input matInput type="number" formControlName="initialPercent" min="1" max="100">
                <span matTextSuffix>%</span>
                <mat-error *ngIf="step2Form.get('initialPercent')?.hasError('required')">
                  请输入初始流量百分比
                </mat-error>
                <mat-error *ngIf="step2Form.get('initialPercent')?.hasError('min')">
                  最小值为 1%
                </mat-error>
                <mat-error *ngIf="step2Form.get('initialPercent')?.hasError('max')">
                  最大值为 100%
                </mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>每阶段观察时间</mat-label>
                <input matInput type="number" formControlName="observationMinutesPerStage" min="1">
                <span matTextSuffix>分钟</span>
                <mat-error *ngIf="step2Form.get('observationMinutesPerStage')?.hasError('required')">
                  请输入观察时间
                </mat-error>
                <mat-error *ngIf="step2Form.get('observationMinutesPerStage')?.hasError('min')">
                  最小值为 1 分钟
                </mat-error>
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="chip-input">
              <mat-label>发布阶段（按 Enter 添加，最后一个阶段必须是 100）</mat-label>
              <mat-chip-grid #chipGrid>
                <mat-chip-row *ngFor="let stage of releaseStages" (removed)="removeStage(stage)">
                  {{ stage }}%
                  <button matChipRemove>
                    <mat-icon>cancel</mat-icon>
                  </button>
                </mat-chip-row>
              </mat-chip-grid>
              <input
                placeholder="例如：10, 20, 50, 100"
                [matChipInputFor]="chipGrid"
                [matChipInputSeparatorKeyCodes]="separatorKeysCodes"
                [matChipInputAddOnBlur]="true"
                (matChipInputTokenEnd)="addStage($event)"
              >
              <mat-error *ngIf="step2Form.get('releaseStages')?.hasError('required')">
                请至少添加一个发布阶段
              </mat-error>
              <mat-error *ngIf="step2Form.get('releaseStages')?.hasError('mustEndWith100')">
                最后一个阶段必须是 100%
              </mat-error>
              <mat-error *ngIf="step2Form.get('releaseStages')?.hasError('increasing')">
                阶段必须按递增顺序排列
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>错误率阈值</mat-label>
              <input matInput type="number" formControlName="errorRateThreshold" min="0" max="100" step="0.1">
              <span matTextSuffix>%</span>
              <mat-error *ngIf="step2Form.get('errorRateThreshold')?.hasError('required')">
                请输入错误率阈值
              </mat-error>
              <mat-error *ngIf="step2Form.get('errorRateThreshold')?.hasError('min')">
                最小值为 0%
              </mat-error>
              <mat-error *ngIf="step2Form.get('errorRateThreshold')?.hasError('max')">
                最大值为 100%
              </mat-error>
            </mat-form-field>

            <mat-card class="info-card" appearance="outlined">
              <mat-card-content>
                <div class="info-item">
                  <mat-icon color="primary">info</mat-icon>
                  <div>
                    <strong>初始流量百分比：</strong>
                    灰度发布开始时，将多少比例的流量引导到新版本。建议从较小值开始。
                  </div>
                </div>
                <div class="info-item">
                  <mat-icon color="primary">info</mat-icon>
                  <div>
                    <strong>发布阶段：</strong>
                    流量逐步提升的各个阶段。例如 [5, 20, 50, 100] 表示流量将依次提升到 5%、20%、50%，最后全量 100%。
                  </div>
                </div>
                <div class="info-item">
                  <mat-icon color="primary">info</mat-icon>
                  <div>
                    <strong>每阶段观察时间：</strong>
                    在每个流量阶段停留观察的时间。如果在此期间错误率超过阈值，将自动回滚。
                  </div>
                </div>
                <div class="info-item">
                  <mat-icon color="primary">info</mat-icon>
                  <div>
                    <strong>错误率阈值：</strong>
                    当错误率超过此值时，系统将自动停止灰度发布并回滚到上一阶段。
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <div class="step-actions">
              <button mat-button matStepperPrevious>
                <mat-icon>arrow_back</mat-icon>
                上一步
              </button>
              <button mat-raised-button color="primary" matStepperNext [disabled]="step2Form.invalid">
                下一步
                <mat-icon>arrow_forward</mat-icon>
              </button>
            </div>
          </form>
        </mat-step>

        <mat-step [stepControl]="step3Form" label="确认并启动灰度">
          <form [formGroup]="step3Form" class="step-form">
            <div class="summary-section">
              <h3>配置摘要</h3>
              <mat-divider></mat-divider>
              <div class="summary-grid">
                <div class="summary-item">
                  <span class="summary-label">租户：</span>
                  <span class="summary-value">{{ getTenantName(step1Form.value.tenantId) }}</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">应用：</span>
                  <span class="summary-value">{{ getApplicationName(step1Form.value.appId) }}</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">路由规则：</span>
                  <span class="summary-value">{{ getRouteRuleName(step1Form.value.routeRuleId) }}</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">发布名称：</span>
                  <span class="summary-value">{{ step1Form.value.name }}</span>
                </div>
                <div class="summary-item full-width">
                  <span class="summary-label">描述：</span>
                  <span class="summary-value">{{ step1Form.value.description || '无' }}</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">初始流量：</span>
                  <span class="summary-value">{{ step2Form.value.initialPercent }}%</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">观察时间：</span>
                  <span class="summary-value">{{ step2Form.value.observationMinutesPerStage }} 分钟/阶段</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">错误率阈值：</span>
                  <span class="summary-value">{{ step2Form.value.errorRateThreshold }}%</span>
                </div>
                <div class="summary-item full-width">
                  <span class="summary-label">发布阶段：</span>
                  <span class="summary-value">
                    <mat-chip *ngFor="let stage of step2Form.value.releaseStages" class="stage-chip">
                      {{ stage }}%
                    </mat-chip>
                  </span>
                </div>
              </div>
            </div>

            <mat-checkbox formControlName="confirmation" class="confirm-checkbox">
              我已确认以上配置信息正确，了解灰度发布的风险，并同意启动灰度发布
            </mat-checkbox>
            <mat-error *ngIf="step3Form.get('confirmation')?.touched && step3Form.get('confirmation')?.hasError('requiredTrue')">
              请确认以上信息
            </mat-error>

            <div class="step-actions">
              <button mat-button matStepperPrevious>
                <mat-icon>arrow_back</mat-icon>
                上一步
              </button>
              <button mat-raised-button color="primary" [disabled]="step3Form.invalid || isSubmitting" (click)="onSubmit()">
                <mat-icon *ngIf="isSubmitting"><mat-spinner diameter="16"></mat-spinner></mat-icon>
                <mat-icon *ngIf="!isSubmitting">play_arrow</mat-icon>
                {{ isSubmitting ? '启动中...' : '启动灰度发布' }}
              </button>
            </div>
          </form>
        </mat-step>
      </mat-horizontal-stepper>
    </mat-dialog-content>
  `,
  styles: [`
    .step-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding-top: 16px;
      min-width: 700px;
    }
    .form-row {
      display: flex;
      gap: 16px;
    }
    .form-row > * {
      flex: 1;
    }
    .chip-input {
      width: 100%;
    }
    .info-card {
      background: #f5f5f5;
    }
    .info-item {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      margin-bottom: 12px;
    }
    .info-item:last-child {
      margin-bottom: 0;
    }
    .info-item mat-icon {
      flex-shrink: 0;
    }
    .step-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 8px;
    }
    .summary-section {
      background: #fafafa;
      padding: 16px;
      border-radius: 8px;
      margin-bottom: 16px;
    }
    .summary-section h3 {
      margin: 0 0 12px 0;
      font-size: 16px;
      font-weight: 500;
    }
    .summary-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .summary-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .summary-item.full-width {
      grid-column: 1 / -1;
    }
    .summary-label {
      font-size: 12px;
      color: #666;
    }
    .summary-value {
      font-size: 14px;
      font-weight: 500;
    }
    .stage-chip {
      margin-right: 4px;
    }
    .confirm-checkbox {
      margin: 8px 0;
    }
    ::ng-deep .mat-horizontal-stepper-wrapper {
      min-height: 500px;
    }
  `]
})
export class GrayReleaseWizardComponent implements OnInit {
  step1Form: FormGroup;
  step2Form: FormGroup;
  step3Form: FormGroup;
  tenants: Tenant[] = [];
  applications: Application[] = [];
  routeRules: RouteRule[] = [];
  releaseStages: number[] = [5, 20, 50, 100];
  readonly separatorKeysCodes = [ENTER, COMMA] as const;
  isSubmitting = false;

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    private routeRuleService: RouteRuleService,
    private grayReleaseService: GrayReleaseService,
    private snackBar: MatSnackBar,
    public dialogRef: MatDialogRef<GrayReleaseWizardComponent>
  ) {
    this.step1Form = this.fb.group({
      tenantId: ['', [Validators.required]],
      appId: ['', [Validators.required]],
      routeRuleId: ['', [Validators.required]],
      name: ['', [Validators.required]],
      description: ['']
    });

    this.step2Form = this.fb.group({
      initialPercent: [5, [Validators.required, Validators.min(1), Validators.max(100)]],
      releaseStages: [[5, 20, 50, 100], [stagesValidator]],
      observationMinutesPerStage: [30, [Validators.required, Validators.min(1)]],
      errorRateThreshold: [5, [Validators.required, Validators.min(0), Validators.max(100)]]
    });

    this.step3Form = this.fb.group({
      confirmation: [false, [Validators.requiredTrue]]
    });
  }

  ngOnInit(): void {
    this.tenantService.getAllTenants().subscribe(res => {
      this.tenants = res;
    });
  }

  onTenantChange(tenantId: number): void {
    this.step1Form.get('appId')?.setValue('');
    this.step1Form.get('routeRuleId')?.setValue('');
    this.applications = [];
    this.routeRules = [];
    if (tenantId) {
      this.applicationService.getApplicationsByTenant(tenantId).subscribe(res => {
        this.applications = res;
      });
    }
  }

  onAppChange(appId: number): void {
    this.step1Form.get('routeRuleId')?.setValue('');
    this.routeRules = [];
    if (appId) {
      this.routeRuleService.getRouteRules(undefined, appId, 0, 100).subscribe(res => {
        this.routeRules = res.content;
      });
    }
  }

  addStage(event: MatChipInputEvent): void {
    const value = (event.value || '').trim();
    if (value) {
      const num = parseInt(value, 10);
      if (!isNaN(num) && num >= 1 && num <= 100 && !this.releaseStages.includes(num)) {
        this.releaseStages.push(num);
        this.releaseStages.sort((a, b) => a - b);
        this.step2Form.get('releaseStages')?.setValue([...this.releaseStages]);
      }
    }
    event.chipInput!.clear();
  }

  removeStage(stage: number): void {
    const index = this.releaseStages.indexOf(stage);
    if (index >= 0) {
      this.releaseStages.splice(index, 1);
      this.step2Form.get('releaseStages')?.setValue([...this.releaseStages]);
    }
  }

  getTenantName(id: number): string {
    return this.tenants.find(t => t.id === id)?.name || '';
  }

  getApplicationName(id: number): string {
    return this.applications.find(a => a.id === id)?.name || '';
  }

  getRouteRuleName(id: number): string {
    const rule = this.routeRules.find(r => r.id === id);
    return rule ? `${rule.name} (${rule.path})` : '';
  }

  onSubmit(): void {
    if (this.step1Form.invalid || this.step2Form.invalid || this.step3Form.invalid) {
      return;
    }

    this.isSubmitting = true;
    const request: GrayReleaseCreateRequest = {
      ...this.step1Form.value,
      ...this.step2Form.value,
      ...this.step3Form.value
    };

    this.grayReleaseService.createGrayRelease(this.step1Form.value.appId, request).subscribe({
      next: () => {
        this.snackBar.open('灰度发布启动成功', '关闭', { duration: 3000 });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.snackBar.open('灰度发布启动失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
        this.isSubmitting = false;
      }
    });
  }
}

@Component({
  selector: 'app-gray-release-status-card',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <mat-card class="status-card" [ngClass]="statusClass">
      <mat-card-header>
        <mat-card-title class="card-title">
          <mat-icon>{{ statusIcon }}</mat-icon>
          {{ grayRelease.name }}
        </mat-card-title>
        <mat-card-subtitle>
          {{ getPhaseText(grayRelease.currentPhase) }}
        </mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <div class="status-grid">
          <div class="status-item">
            <span class="status-label">当前阶段</span>
            <span class="status-value">
              <mat-chip [color]="isHighErrorRate ? 'warn' : 'primary'" highlighted>
                {{ grayRelease.currentTrafficPercent }}%
              </mat-chip>
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">错误率</span>
            <span class="status-value error-rate" [ngClass]="{'high-error': isHighErrorRate}">
              {{ grayRelease.currentErrorRate?.toFixed(2) || '0.00' }}%
              <mat-icon *ngIf="isHighErrorRate" color="warn">error</mat-icon>
            </span>
          </div>
          <div class="status-item" *ngIf="countdown">
            <span class="status-label">下一阶段</span>
            <span class="status-value countdown">
              <mat-icon>schedule</mat-icon>
              {{ countdown }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">完成进度</span>
            <span class="status-value">
              {{ grayRelease.completedStages }} / {{ grayRelease.totalStages }}
            </span>
          </div>
        </div>

        <div class="progress-section">
          <mat-progress-bar
            mode="determinate"
            [value]="progressPercent"
            [color]="isHighErrorRate ? 'warn' : 'primary'"
          ></mat-progress-bar>
          <div class="stage-markers">
            <span
              *ngFor="let stage of stages; let i = index"
              class="stage-marker"
              [ngClass]="{'completed': i < grayRelease.completedStages, 'current': i === grayRelease.completedStages}"
              [style.left.%]="stage"
            >
              <span class="stage-dot"></span>
              <span class="stage-label">{{ stage }}%</span>
            </span>
          </div>
        </div>

        <div class="description" *ngIf="grayRelease.description">
          {{ grayRelease.description }}
        </div>
      </mat-card-content>
      <mat-card-actions align="end">
        <button
          mat-raised-button
          color="primary"
          (click)="onFullRelease()"
          [disabled]="!canPerformAction"
        >
          <mat-icon>trending_up</mat-icon>
          立即全量
        </button>
        <button
          mat-raised-button
          color="warn"
          (click)="onRollback()"
          [disabled]="!canPerformAction"
        >
          <mat-icon>rollback</mat-icon>
          立即回滚
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    .status-card {
      margin-bottom: 16px;
      border-left: 4px solid #3f51b5;
      transition: all 0.3s ease;
    }
    .status-card.status-normal {
      border-left-color: #4caf50;
    }
    .status-card.status-warning {
      border-left-color: #ff9800;
    }
    .status-card.status-error {
      border-left-color: #f44336;
    }
    .card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .status-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 16px;
      margin: 16px 0;
    }
    .status-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .status-label {
      font-size: 12px;
      color: #666;
    }
    .status-value {
      font-size: 16px;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .error-rate.high-error {
      color: #f44336;
    }
    .countdown {
      color: #ff9800;
    }
    .progress-section {
      position: relative;
      margin: 24px 0 16px 0;
    }
    .stage-markers {
      position: relative;
      height: 40px;
      margin-top: 8px;
    }
    .stage-marker {
      position: absolute;
      transform: translateX(-50%);
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .stage-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #e0e0e0;
      border: 2px solid #fff;
      box-shadow: 0 0 0 1px #e0e0e0;
    }
    .stage-marker.completed .stage-dot {
      background: #4caf50;
      box-shadow: 0 0 0 1px #4caf50;
    }
    .stage-marker.current .stage-dot {
      background: #3f51b5;
      box-shadow: 0 0 0 2px #3f51b5;
      animation: pulse 2s infinite;
    }
    .stage-label {
      font-size: 11px;
      color: #666;
      margin-top: 4px;
    }
    .stage-marker.completed .stage-label {
      color: #4caf50;
    }
    .stage-marker.current .stage-label {
      color: #3f51b5;
      font-weight: 500;
    }
    .description {
      color: #666;
      font-size: 13px;
      padding: 8px 12px;
      background: #f5f5f5;
      border-radius: 4px;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }
  `]
})
export class GrayReleaseStatusCardComponent implements OnInit, OnDestroy {
  @Input() grayRelease!: GrayRelease;
  @Output() fullRelease = new EventEmitter<GrayRelease>();
  @Output() rollback = new EventEmitter<GrayRelease>();

  private countdownTimer: Subscription | null = null;
  countdown: string = '';
  stages: number[] = [];

  ngOnInit(): void {
    this.initStages();
    this.startCountdown();
  }

  ngOnDestroy(): void {
    if (this.countdownTimer) {
      this.countdownTimer.unsubscribe();
    }
  }

  private initStages(): void {
    if (this.grayRelease.currentTrafficPercent === 100 && this.grayRelease.currentPhase === 'FULL') {
      this.stages = [100];
    } else {
      this.stages = [
        this.grayRelease.currentTrafficPercent,
        ...Array.from({ length: this.grayRelease.totalStages - this.grayRelease.completedStages }, (_, i) => {
          const remaining = this.grayRelease.totalStages - this.grayRelease.completedStages;
          const increment = (100 - this.grayRelease.currentTrafficPercent) / remaining;
          return Math.round(this.grayRelease.currentTrafficPercent + increment * (i + 1));
        })
      ];
    }
  }

  private startCountdown(): void {
    if (this.grayRelease.nextStageTime) {
      this.updateCountdown();
      this.countdownTimer = interval(1000).subscribe(() => {
        this.updateCountdown();
      });
    }
  }

  private updateCountdown(): void {
    const now = new Date().getTime();
    const nextStage = new Date(this.grayRelease.nextStageTime).getTime();
    const diff = nextStage - now;

    if (diff <= 0) {
      this.countdown = '即将进入下一阶段';
      return;
    }

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    if (hours > 0) {
      this.countdown = `${hours}小时 ${minutes}分钟 ${seconds}秒`;
    } else if (minutes > 0) {
      this.countdown = `${minutes}分钟 ${seconds}秒`;
    } else {
      this.countdown = `${seconds}秒`;
    }
  }

  get isHighErrorRate(): boolean {
    return this.grayRelease.currentErrorRate >= this.grayRelease.errorRateThreshold;
  }

  get canPerformAction(): boolean {
    return this.grayRelease.status === GrayReleaseStatus.IN_PROGRESS;
  }

  get statusClass(): string {
    if (this.isHighErrorRate) {
      return 'status-error';
    }
    if (this.grayRelease.status === GrayReleaseStatus.PAUSED) {
      return 'status-warning';
    }
    return 'status-normal';
  }

  get statusIcon(): string {
    if (this.isHighErrorRate) {
      return 'error';
    }
    if (this.grayRelease.status === GrayReleaseStatus.PAUSED) {
      return 'pause_circle';
    }
    if (this.grayRelease.status === GrayReleaseStatus.COMPLETED) {
      return 'check_circle';
    }
    return 'trending_up';
  }

  get progressPercent(): number {
    if (this.grayRelease.totalStages === 0) return 0;
    return (this.grayRelease.completedStages / this.grayRelease.totalStages) * 100;
  }

  getPhaseText(phase: GrayReleasePhase): string {
    const phaseMap: Record<GrayReleasePhase, string> = {
      [GrayReleasePhase.INITIAL]: '初始阶段',
      [GrayReleasePhase.STAGE_1]: '第一阶段',
      [GrayReleasePhase.STAGE_2]: '第二阶段',
      [GrayReleasePhase.STAGE_3]: '第三阶段',
      [GrayReleasePhase.FULL]: '全量发布'
    };
    return phaseMap[phase] || phase;
  }

  onFullRelease(): void {
    this.fullRelease.emit(this.grayRelease);
  }

  onRollback(): void {
    this.rollback.emit(this.grayRelease);
  }
}

@Component({
  selector: 'app-color-rule-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>{{ data?.rule ? '编辑染色规则' : '创建染色规则' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-container">
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>租户</mat-label>
            <mat-select formControlName="tenantId">
              <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>应用（可选）</mat-label>
            <mat-select formControlName="applicationId">
              <mat-option [value]="null">全部应用</mat-option>
              <mat-option *ngFor="let a of applications" [value]="a.id">{{ a.name }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>规则名称</mat-label>
          <input matInput formControlName="name">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>颜色标签</mat-label>
          <input matInput formControlName="colorTag" placeholder="gray, canary, ...">
        </mat-form-field>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>条件类型</mat-label>
            <mat-select formControlName="conditionType">
              <mat-option *ngFor="let t of conditionTypes" [value]="t">{{ t }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>操作</mat-label>
            <mat-select formControlName="operation">
              <mat-option *ngFor="let o of operations" [value]="o">{{ o }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" *ngIf="form.value.conditionType === 'HEADER' || form.value.conditionType === 'COOKIE' || form.value.conditionType === 'QUERY'">
          <mat-label>条件键名</mat-label>
          <input matInput formControlName="conditionKey">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>条件值</mat-label>
          <input matInput formControlName="conditionValue">
        </mat-form-field>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>优先级</mat-label>
            <input matInput type="number" formControlName="priority">
          </mat-form-field>
          <mat-slide-toggle formControlName="enabled" style="margin: auto 0;">启用</mat-slide-toggle>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>描述</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="onSubmit()">保存</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form-container { display: flex; flex-direction: column; gap: 16px; min-width: 500px; padding-top: 16px; }
    .form-row { display: flex; gap: 16px; }
    .form-row > * { flex: 1; }
  `]
})
export class ColorRuleDialogComponent implements OnInit {
  form: FormGroup;
  tenants: Tenant[] = [];
  applications: Application[] = [];
  conditionTypes = Object.values(TrafficConditionType);
  operations = Object.values(ColorTagOperation);

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    public dialogRef: MatDialogRef<ColorRuleDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { rule?: TrafficColorRule }
  ) {
    this.form = this.fb.group({
      tenantId: [data?.rule?.tenantId || '', [Validators.required]],
      applicationId: [data?.rule?.applicationId || null],
      name: [data?.rule?.name || '', [Validators.required]],
      colorTag: [data?.rule?.colorTag || '', [Validators.required]],
      conditionType: [data?.rule?.conditionType || TrafficConditionType.HEADER, [Validators.required]],
      operation: [data?.rule?.operation || ColorTagOperation.ADD, [Validators.required]],
      conditionKey: [data?.rule?.conditionKey || ''],
      conditionValue: [data?.rule?.conditionValue || '', [Validators.required]],
      priority: [data?.rule?.priority || 100],
      enabled: [data?.rule?.enabled ?? true],
      description: [data?.rule?.description || '']
    });
  }

  ngOnInit(): void {
    this.tenantService.getAllTenants().subscribe(res => this.tenants = res);
    this.form.get('tenantId')?.valueChanges.subscribe(id => {
      if (id) this.applicationService.getApplicationsByTenant(id).subscribe(res => this.applications = res);
    });
    if (this.data?.rule?.tenantId) {
      this.applicationService.getApplicationsByTenant(this.data.rule.tenantId)
        .subscribe(res => this.applications = res);
    }
  }

  onSubmit(): void {
    if (this.form.valid) this.dialogRef.close(this.form.value);
  }
}

@Component({
  selector: 'app-color-rules',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent, GrayReleaseStatusCardComponent],
  template: `
    <app-page-header title="染色规则" subtitle="对流量进行染色标记，用于灰度发布等场景" icon="palette">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>选择租户</mat-label>
        <mat-select [value]="selectedTenantId" (selectionChange)="onTenantChange($event.value)">
          <mat-option [value]="null">全部</mat-option>
          <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>选择应用</mat-label>
        <mat-select [value]="selectedAppId" (selectionChange)="onAppChange($event.value)">
          <mat-option *ngFor="let a of applications" [value]="a.id">{{ a.name }}</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-raised-button (click)="openGrayReleaseWizard()" color="accent">
        <mat-icon>auto_awesome</mat-icon>
        灰度发布向导
      </button>
      <button mat-raised-button (click)="applyAll()" color="accent">
        <mat-icon>play_arrow</mat-icon>
        全量应用
      </button>
      <button mat-raised-button (click)="clearAll()" color="warn">
        <mat-icon>clear_all</mat-icon>
        清除全部
      </button>
      <button mat-raised-button color="primary" (click)="openCreateDialog()">
        <mat-icon>add</mat-icon>
        新建规则
      </button>
    </app-page-header>

    <div *ngIf="activeGrayReleases.length > 0" class="gray-release-section">
      <h3 class="section-title">
        <mat-icon color="primary">trending_up</mat-icon>
        进行中的灰度发布 ({{ activeGrayReleases.length }})
      </h3>
      <app-gray-release-status-card
        *ngFor="let release of activeGrayReleases"
        [grayRelease]="release"
        (fullRelease)="onFullRelease($event)"
        (rollback)="onRollback($event)"
      ></app-gray-release-status-card>
    </div>

    <mat-card>
      <div class="table-container">
        <table mat-table [dataSource]="rules" matSort>
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>ID</th>
            <td mat-cell *matCellDef="let r">{{ r.id }}</td>
          </ng-container>
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>名称</th>
            <td mat-cell *matCellDef="let r">{{ r.name }}</td>
          </ng-container>
          <ng-container matColumnDef="tenantName">
            <th mat-header-cell *matHeaderCellDef>租户</th>
            <td mat-cell *matCellDef="let r">{{ r.tenantName }}</td>
          </ng-container>
          <ng-container matColumnDef="colorTag">
            <th mat-header-cell *matHeaderCellDef>颜色标签</th>
            <td mat-cell *matCellDef="let r">
              <mat-chip>{{ r.colorTag }}</mat-chip>
            </td>
          </ng-container>
          <ng-container matColumnDef="condition">
            <th mat-header-cell *matHeaderCellDef>条件</th>
            <td mat-cell *matCellDef="let r">
              {{ r.conditionType }}
              <span *ngIf="r.conditionKey">: {{ r.conditionKey }}</span>
              = {{ r.conditionValue }}
            </td>
          </ng-container>
          <ng-container matColumnDef="operation">
            <th mat-header-cell *matHeaderCellDef>操作</th>
            <td mat-cell *matCellDef="let r">{{ r.operation }}</td>
          </ng-container>
          <ng-container matColumnDef="priority">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>优先级</th>
            <td mat-cell *matCellDef="let r">{{ r.priority }}</td>
          </ng-container>
          <ng-container matColumnDef="enabled">
            <th mat-header-cell *matHeaderCellDef>状态</th>
            <td mat-cell *matCellDef="let r">
              <span [ngClass]="r.enabled ? 'status-active' : 'status-inactive'">
                {{ r.enabled ? '启用' : '禁用' }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>操作</th>
            <td mat-cell *matCellDef="let r">
              <button mat-icon-button color="primary" (click)="openEditDialog(r)">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button (click)="toggle(r)" matTooltip="启用/禁用">
                <mat-icon>{{ r.enabled ? 'toggle_on' : 'toggle_off' }}</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="openDeleteDialog(r)">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
      </div>
      <mat-paginator [length]="totalElements" [pageSize]="pageSize" [pageIndex]="pageIndex"
        [pageSizeOptions]="[10, 20, 50]" (page)="onPageChange($event)"></mat-paginator>
    </mat-card>
  `,
  styles: [`
    .filter-field { width: 200px; margin-right: 12px; }
    .gray-release-section {
      margin-bottom: 24px;
    }
    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 16px 0;
      font-size: 18px;
      font-weight: 500;
      color: #333;
    }
  `]
})
export class ColorRulesComponent implements OnInit, OnDestroy {
  rules: TrafficColorRule[] = [];
  tenants: Tenant[] = [];
  applications: Application[] = [];
  activeGrayReleases: GrayRelease[] = [];
  selectedTenantId: number | null = null;
  selectedAppId: number | null = 1;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'name', 'tenantName', 'colorTag', 'condition', 'operation', 'priority', 'enabled', 'actions'];
  private refreshTimer: Subscription | null = null;

  constructor(
    private colorRuleService: ColorRuleService,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    private grayReleaseService: GrayReleaseService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenants();
    this.loadApplications();
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      this.refreshTimer.unsubscribe();
    }
  }

  private startAutoRefresh(): void {
    this.refreshTimer = interval(30000).subscribe(() => {
      this.loadActiveGrayReleases();
    });
  }

  loadTenants(): void {
    this.tenantService.getAllTenants().subscribe(res => this.tenants = res);
  }

  loadApplications(): void {
    this.applicationService.getApplications(undefined, 0, 100).subscribe({
      next: (res) => {
        this.applications = res.content;
        if (this.applications.length > 0 && !this.selectedAppId) {
          this.selectedAppId = this.applications[0].id;
        }
        this.loadRules();
        this.loadActiveGrayReleases();
        this.startAutoRefresh();
      },
      error: (err) => {
        this.snackBar.open('加载应用列表失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  loadRules(): void {
    if (!this.selectedAppId) return;
    this.colorRuleService.getColorRules(this.selectedTenantId || undefined, this.selectedAppId, this.pageIndex, this.pageSize)
      .subscribe({
        next: (res: TrafficColorRulePageResponse) => {
          this.rules = res.content;
          this.totalElements = res.totalElements;
        },
        error: (err) => {
          this.snackBar.open('加载规则列表失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
        }
      });
  }

  loadActiveGrayReleases(): void {
    if (!this.selectedAppId) return;
    this.colorRuleService.getActiveGrayReleases(this.selectedAppId).subscribe({
      next: (res) => {
        this.activeGrayReleases = res;
      },
      error: (err) => {
        this.snackBar.open('加载灰度发布列表失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  onTenantChange(id: number | null): void {
    this.selectedTenantId = id;
    this.pageIndex = 0;
    this.loadRules();
  }

  onAppChange(id: number | null): void {
    this.selectedAppId = id;
    this.pageIndex = 0;
    this.loadRules();
    this.loadActiveGrayReleases();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadRules();
  }

  openGrayReleaseWizard(): void {
    const ref = this.dialog.open(GrayReleaseWizardComponent, {
      width: '800px',
      disableClose: true
    });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.loadActiveGrayReleases();
      }
    });
  }

  onFullRelease(release: GrayRelease): void {
    if (!release.appId || !release.id) {
      this.snackBar.open('无效的灰度发布数据', '关闭', { duration: 5000 });
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: '确认立即全量',
        message: `确定要将灰度发布 "${release.name}" 立即全量发布到 100% 流量吗？`,
        confirmText: '立即全量'
      }
    });
    ref.afterClosed().subscribe(r => {
      if (r) {
        const request: GrayReleaseActionRequest = {
          action: 'FULL',
          reason: '手动立即全量'
        };
        this.grayReleaseService.performAction(release.appId, release.id, request).subscribe({
          next: () => {
            this.snackBar.open('已执行全量发布', '关闭', { duration: 3000 });
            this.loadActiveGrayReleases();
          },
          error: (err) => {
            this.snackBar.open('全量发布失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  onRollback(release: GrayRelease): void {
    if (!release.appId || !release.id) {
      this.snackBar.open('无效的灰度发布数据', '关闭', { duration: 5000 });
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: '确认立即回滚',
        message: `确定要将灰度发布 "${release.name}" 立即回滚吗？这将取消灰度发布并恢复到原有版本。`,
        confirmText: '立即回滚'
      }
    });
    ref.afterClosed().subscribe(r => {
      if (r) {
        const request: GrayReleaseActionRequest = {
          action: 'ROLLBACK',
          reason: '手动立即回滚'
        };
        this.grayReleaseService.performAction(release.appId, release.id, request).subscribe({
          next: () => {
            this.snackBar.open('已执行回滚', '关闭', { duration: 3000 });
            this.loadActiveGrayReleases();
          },
          error: (err) => {
            this.snackBar.open('回滚失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  openCreateDialog(): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(ColorRuleDialogComponent, { data: {} });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.colorRuleService.create(this.selectedAppId, r).subscribe({
          next: () => {
            this.snackBar.open('创建成功', '关闭', { duration: 3000 });
            this.loadRules();
          },
          error: (err) => {
            this.snackBar.open('创建失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  openEditDialog(rule: TrafficColorRule): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(ColorRuleDialogComponent, { data: { rule } });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.colorRuleService.update(this.selectedAppId, rule.id, r).subscribe({
          next: () => {
            this.snackBar.open('更新成功', '关闭', { duration: 3000 });
            this.loadRules();
          },
          error: (err) => {
            this.snackBar.open('更新失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  toggle(rule: TrafficColorRule): void {
    if (!this.selectedAppId || !rule.id) return;
    this.colorRuleService.toggle(this.selectedAppId, rule.id).subscribe({
      next: () => this.loadRules(),
      error: (err) => {
        this.snackBar.open('操作失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  applyAll(): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '全量应用', message: '确定要将所有染色规则全量应用到网关吗？', confirmText: '确定' }
    });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.colorRuleService.applyAll(this.selectedAppId, this.selectedTenantId || 0).subscribe({
          next: () => {
            this.snackBar.open('已全量应用', '关闭', { duration: 3000 });
          },
          error: (err) => {
            this.snackBar.open('全量应用失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  clearAll(): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '清除全部', message: '确定要清除所有已应用的染色规则吗？', confirmText: '确定' }
    });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.colorRuleService.clearAll(this.selectedAppId, this.selectedTenantId || 0).subscribe({
          next: () => {
            this.snackBar.open('已清除全部', '关闭', { duration: 3000 });
          },
          error: (err) => {
            this.snackBar.open('清除失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  openDeleteDialog(rule: TrafficColorRule): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除规则 "${rule.name}"吗？`, confirmText: '删除' }
    });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.colorRuleService.delete(this.selectedAppId, rule.id).subscribe({
          next: () => {
            this.snackBar.open('删除成功', '关闭', { duration: 3000 });
            this.loadRules();
          },
          error: (err) => {
            this.snackBar.open('删除失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }
}
