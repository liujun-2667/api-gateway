import { Component, OnInit, Inject, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, FormControl } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { SelectionModel } from '@angular/cdk/collections';
import { MaterialModule } from '../../shared/material.module';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { TenantService } from '../../core/services/tenant.service';
import { ApplicationService } from '../../core/services/application.service';
import { RouteRuleService } from '../../core/services/route-rule.service';
import { Tenant } from '../../shared/models/tenant.model';
import { Application } from '../../shared/models/application.model';
import {
  RouteRule, RouteRulePageResponse, RouteRuleVersion,
  HttpMethod, MatchType, RuleStatus, DiffResponse, DiffField,
  BatchOperationRequest, BatchOperationResponse, TargetBackend
} from '../../shared/models/route-rule.model';

@Component({
  selector: 'app-route-rule-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>{{ data?.rule ? '编辑路由规则' : '创建路由规则' }}</h2>
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
            <mat-label>应用</mat-label>
            <mat-select formControlName="applicationId">
              <mat-option *ngFor="let a of applications" [value]="a.id">{{ a.name }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>规则名称</mat-label>
          <input matInput formControlName="name">
        </mat-form-field>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>HTTP方法</mat-label>
            <mat-select formControlName="method">
              <mat-option *ngFor="let m of httpMethods" [value]="m">{{ m }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>匹配类型</mat-label>
            <mat-select formControlName="matchType">
              <mat-option *ngFor="let m of matchTypes" [value]="m">{{ m }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>路径</mat-label>
          <input matInput formControlName="path" placeholder="/api/users">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>目标路径</mat-label>
          <input matInput formControlName="targetPath" placeholder="/backend/users">
        </mat-form-field>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>优先级</mat-label>
            <input matInput type="number" formControlName="priority">
          </mat-form-field>
          <mat-slide-toggle formControlName="stripPrefix" style="margin: auto 0;">去除前缀</mat-slide-toggle>
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
export class RouteRuleDialogComponent implements OnInit {
  form: FormGroup;
  tenants: Tenant[] = [];
  applications: Application[] = [];
  httpMethods = Object.values(HttpMethod);
  matchTypes = Object.values(MatchType);

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    public dialogRef: MatDialogRef<RouteRuleDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { rule?: RouteRule }
  ) {
    this.form = this.fb.group({
      tenantId: [data?.rule?.tenantId || '', [Validators.required]],
      applicationId: [data?.rule?.applicationId || '', [Validators.required]],
      name: [data?.rule?.name || '', [Validators.required]],
      method: [data?.rule?.method || HttpMethod.ALL, [Validators.required]],
      matchType: [data?.rule?.matchType || MatchType.PREFIX, [Validators.required]],
      path: [data?.rule?.path || '', [Validators.required]],
      targetPath: [data?.rule?.targetPath || ''],
      priority: [data?.rule?.priority || 100],
      stripPrefix: [data?.rule?.stripPrefix ?? false],
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
    if (this.form.valid) {
      const value = this.form.value;
      if (!this.data?.rule) {
        value.status = RuleStatus.DRAFT;
      }
      this.dialogRef.close(value);
    }
  }
}

@Component({
  selector: 'app-version-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>版本历史 - {{ ruleName }}</h2>
    <mat-dialog-content>
      <table mat-table [dataSource]="versions" class="full-width">
        <ng-container matColumnDef="version">
          <th mat-header-cell *matHeaderCellDef>版本</th>
          <td mat-cell *matCellDef="let v">v{{ v.version }}</td>
        </ng-container>
        <ng-container matColumnDef="path">
          <th mat-header-cell *matHeaderCellDef>路径</th>
          <td mat-cell *matCellDef="let v">{{ v.path }}</td>
        </ng-container>
        <ng-container matColumnDef="method">
          <th mat-header-cell *matHeaderCellDef>方法</th>
          <td mat-cell *matCellDef="let v">{{ v.method }}</td>
        </ng-container>
        <ng-container matColumnDef="createdAt">
          <th mat-header-cell *matHeaderCellDef>创建时间</th>
          <td mat-cell *matCellDef="let v">{{ v.createdAt | date:'yyyy-MM-dd HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="action">
          <th mat-header-cell *matHeaderCellDef>操作</th>
          <td mat-cell *matCellDef="let v">
            <button mat-button color="primary" (click)="rollback(v)">回滚到此版本</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="['version','path','method','createdAt','action']"></tr>
        <tr mat-row *matRowDef="let row; columns: ['version','path','method','createdAt','action'];"></tr>
      </table>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">关闭</button>
    </mat-dialog-actions>
  `
})
export class VersionDialogComponent {
  versions: RouteRuleVersion[] = [];
  ruleName = '';
  ruleId!: number;
  appId!: number;

  constructor(
    public dialogRef: MatDialogRef<VersionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { ruleId: number; ruleName: string; versions: RouteRuleVersion[]; appId: number },
    private routeRuleService: RouteRuleService,
    private snackBar: MatSnackBar
  ) {
    this.ruleId = data.ruleId;
    this.ruleName = data.ruleName;
    this.versions = data.versions;
    this.appId = data.appId;
  }

  rollback(v: RouteRuleVersion): void {
    this.routeRuleService.rollback(this.appId, this.ruleId, v.id).subscribe({
      next: () => {
        this.snackBar.open('版本回滚成功', '关闭', { duration: 3000 });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.snackBar.open('回滚失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }
}

@Component({
  selector: 'app-rollback-reason-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>确认回滚</h2>
    <mat-dialog-content>
      <p class="confirm-message">确定要回滚到版本 v{{ data.version }} 吗？</p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>回滚原因</mat-label>
        <textarea matInput [formControl]="reasonControl" rows="4" placeholder="请输入回滚原因"></textarea>
        <mat-error *ngIf="reasonControl.invalid && reasonControl.touched">回滚原因为必填项</mat-error>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="warn" [disabled]="reasonControl.invalid" (click)="onConfirm()">确认回滚</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .confirm-message {
      margin: 0 0 16px 0;
      font-size: 14px;
    }
    .full-width {
      width: 100%;
    }
  `]
})
export class RollbackReasonDialogComponent {
  reasonControl = new FormControl('', [Validators.required]);

  constructor(
    public dialogRef: MatDialogRef<RollbackReasonDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { ruleId: number; version: number }
  ) {}

  onConfirm(): void {
    if (this.reasonControl.valid) {
      this.dialogRef.close(this.reasonControl.value);
    }
  }
}

@Component({
  selector: 'app-version-compare-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>版本对比</h2>
    <mat-dialog-content class="compare-content">
      <div class="version-headers">
        <div class="version-header">
          <div class="version-label">版本 v{{ version1?.version }}</div>
          <div class="version-meta">{{ version1?.createdBy }} · {{ version1?.createdAt | date:'yyyy-MM-dd HH:mm' }}</div>
        </div>
        <div class="vs-divider">VS</div>
        <div class="version-header">
          <div class="version-label">版本 v{{ version2?.version }}</div>
          <div class="version-meta">{{ version2?.createdBy }} · {{ version2?.createdAt | date:'yyyy-MM-dd HH:mm' }}</div>
        </div>
      </div>

      <mat-tab-group class="compare-tabs">
        <mat-tab label="目标后端">
          <div class="tab-content">
            <table mat-table [dataSource]="getDiffsByCategory('目标后端')" class="full-width">
              <ng-container matColumnDef="fieldName">
                <th mat-header-cell *matHeaderCellDef>字段</th>
                <td mat-cell *matCellDef="let diff">{{ diff.fieldName }}</td>
              </ng-container>
              <ng-container matColumnDef="oldValue">
                <th mat-header-cell *matHeaderCellDef>旧值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'old')">
                  {{ formatValue(diff.oldValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="newValue">
                <th mat-header-cell *matHeaderCellDef>新值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'new')">
                  {{ formatValue(diff.newValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="changeType">
                <th mat-header-cell *matHeaderCellDef>变更类型</th>
                <td mat-cell *matCellDef="let diff">
                  <mat-chip [ngClass]="getChipClass(diff.changeType)">{{ getChangeTypeLabel(diff.changeType) }}</mat-chip>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['fieldName', 'oldValue', 'newValue', 'changeType']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['fieldName', 'oldValue', 'newValue', 'changeType'];"></tr>
            </table>
            <div *ngIf="getDiffsByCategory('目标后端').length === 0" class="no-changes">
              <mat-icon class="info-icon">info</mat-icon>
              <span>该分类无变更</span>
            </div>
          </div>
        </mat-tab>

        <mat-tab label="超时设置">
          <div class="tab-content">
            <table mat-table [dataSource]="getDiffsByCategory('超时设置')" class="full-width">
              <ng-container matColumnDef="fieldName">
                <th mat-header-cell *matHeaderCellDef>字段</th>
                <td mat-cell *matCellDef="let diff">{{ diff.fieldName }}</td>
              </ng-container>
              <ng-container matColumnDef="oldValue">
                <th mat-header-cell *matHeaderCellDef>旧值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'old')">
                  {{ formatValue(diff.oldValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="newValue">
                <th mat-header-cell *matHeaderCellDef>新值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'new')">
                  {{ formatValue(diff.newValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="changeType">
                <th mat-header-cell *matHeaderCellDef>变更类型</th>
                <td mat-cell *matCellDef="let diff">
                  <mat-chip [ngClass]="getChipClass(diff.changeType)">{{ getChangeTypeLabel(diff.changeType) }}</mat-chip>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['fieldName', 'oldValue', 'newValue', 'changeType']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['fieldName', 'oldValue', 'newValue', 'changeType'];"></tr>
            </table>
            <div *ngIf="getDiffsByCategory('超时设置').length === 0" class="no-changes">
              <mat-icon class="info-icon">info</mat-icon>
              <span>该分类无变更</span>
            </div>
          </div>
        </mat-tab>

        <mat-tab label="重试策略">
          <div class="tab-content">
            <table mat-table [dataSource]="getDiffsByCategory('重试策略')" class="full-width">
              <ng-container matColumnDef="fieldName">
                <th mat-header-cell *matHeaderCellDef>字段</th>
                <td mat-cell *matCellDef="let diff">{{ diff.fieldName }}</td>
              </ng-container>
              <ng-container matColumnDef="oldValue">
                <th mat-header-cell *matHeaderCellDef>旧值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'old')">
                  {{ formatValue(diff.oldValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="newValue">
                <th mat-header-cell *matHeaderCellDef>新值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'new')">
                  {{ formatValue(diff.newValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="changeType">
                <th mat-header-cell *matHeaderCellDef>变更类型</th>
                <td mat-cell *matCellDef="let diff">
                  <mat-chip [ngClass]="getChipClass(diff.changeType)">{{ getChangeTypeLabel(diff.changeType) }}</mat-chip>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['fieldName', 'oldValue', 'newValue', 'changeType']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['fieldName', 'oldValue', 'newValue', 'changeType'];"></tr>
            </table>
            <div *ngIf="getDiffsByCategory('重试策略').length === 0" class="no-changes">
              <mat-icon class="info-icon">info</mat-icon>
              <span>该分类无变更</span>
            </div>
          </div>
        </mat-tab>

        <mat-tab label="请求改写">
          <div class="tab-content">
            <table mat-table [dataSource]="getDiffsByCategory('请求改写')" class="full-width">
              <ng-container matColumnDef="fieldName">
                <th mat-header-cell *matHeaderCellDef>字段</th>
                <td mat-cell *matCellDef="let diff">{{ diff.fieldName }}</td>
              </ng-container>
              <ng-container matColumnDef="oldValue">
                <th mat-header-cell *matHeaderCellDef>旧值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'old')">
                  {{ formatValue(diff.oldValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="newValue">
                <th mat-header-cell *matHeaderCellDef>新值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'new')">
                  {{ formatValue(diff.newValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="changeType">
                <th mat-header-cell *matHeaderCellDef>变更类型</th>
                <td mat-cell *matCellDef="let diff">
                  <mat-chip [ngClass]="getChipClass(diff.changeType)">{{ getChangeTypeLabel(diff.changeType) }}</mat-chip>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['fieldName', 'oldValue', 'newValue', 'changeType']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['fieldName', 'oldValue', 'newValue', 'changeType'];"></tr>
            </table>
            <div *ngIf="getDiffsByCategory('请求改写').length === 0" class="no-changes">
              <mat-icon class="info-icon">info</mat-icon>
              <span>该分类无变更</span>
            </div>
          </div>
        </mat-tab>

        <mat-tab label="其他">
          <div class="tab-content">
            <table mat-table [dataSource]="getDiffsByCategory('其他')" class="full-width">
              <ng-container matColumnDef="fieldName">
                <th mat-header-cell *matHeaderCellDef>字段</th>
                <td mat-cell *matCellDef="let diff">{{ diff.fieldName }}</td>
              </ng-container>
              <ng-container matColumnDef="oldValue">
                <th mat-header-cell *matHeaderCellDef>旧值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'old')">
                  {{ formatValue(diff.oldValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="newValue">
                <th mat-header-cell *matHeaderCellDef>新值</th>
                <td mat-cell *matCellDef="let diff" [ngClass]="getDiffClass(diff.changeType, 'new')">
                  {{ formatValue(diff.newValue) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="changeType">
                <th mat-header-cell *matHeaderCellDef>变更类型</th>
                <td mat-cell *matCellDef="let diff">
                  <mat-chip [ngClass]="getChipClass(diff.changeType)">{{ getChangeTypeLabel(diff.changeType) }}</mat-chip>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['fieldName', 'oldValue', 'newValue', 'changeType']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['fieldName', 'oldValue', 'newValue', 'changeType'];"></tr>
            </table>
            <div *ngIf="getDiffsByCategory('其他').length === 0" class="no-changes">
              <mat-icon class="info-icon">info</mat-icon>
              <span>该分类无变更</span>
            </div>
          </div>
        </mat-tab>
      </mat-tab-group>
    </mat-dialog-content>
    <mat-dialog-actions class="rollback-actions" align="end">
      <button mat-button (click)="onRollback(version1)" *ngIf="version1">
        <mat-icon>undo</mat-icon>
        回滚到 v{{ version1?.version }}
      </button>
      <button mat-button (click)="onRollback(version2)" *ngIf="version2">
        <mat-icon>undo</mat-icon>
        回滚到 v{{ version2?.version }}
      </button>
      <button mat-button [mat-dialog-close]="false">关闭</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .compare-content {
      min-width: 800px;
    }
    .version-headers {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;
      padding: 16px;
      background: #f5f5f5;
      border-radius: 8px;
    }
    .version-header {
      flex: 1;
      text-align: center;
    }
    .version-label {
      font-size: 18px;
      font-weight: bold;
      color: #1976d2;
    }
    .version-meta {
      font-size: 12px;
      color: #666;
      margin-top: 4px;
    }
    .vs-divider {
      padding: 8px 16px;
      background: #1976d2;
      color: white;
      border-radius: 4px;
      font-weight: bold;
    }
    .compare-tabs {
      margin-top: 16px;
    }
    .tab-content {
      padding-top: 16px;
    }
    .full-width {
      width: 100%;
    }
    .no-changes {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
      color: #999;
    }
    .info-icon {
      margin-right: 8px;
    }
    .diff-add-old {
      background: #e0e0e0;
    }
    .diff-add-new {
      background: #e8f5e9;
    }
    .diff-remove-old {
      background: #ffebee;
    }
    .diff-remove-new {
      background: #e0e0e0;
    }
    .diff-modify-old {
      background: #fff8e1;
    }
    .diff-modify-new {
      background: #fff8e1;
    }
    .chip-add {
      background: #c8e6c9 !important;
      color: #2e7d32 !important;
    }
    .chip-remove {
      background: #ffcdd2 !important;
      color: #c62828 !important;
    }
    .chip-modify {
      background: #fff9c4 !important;
      color: #f57f17 !important;
    }
    .rollback-actions {
      gap: 8px;
    }
  `]
})
export class VersionCompareDialogComponent implements OnInit {
  version1: RouteRuleVersion | null = null;
  version2: RouteRuleVersion | null = null;
  diffResponse: DiffResponse | null = null;
  appId!: number;

  private categoryMapping: { [key: string]: string[] } = {
    '目标后端': ['targetBackends', 'targetUrl', 'targetPath'],
    '超时设置': ['connectTimeoutMs', 'readTimeoutMs'],
    '重试策略': ['maxRetries', 'retryOn5xx', 'retryOnTimeout', 'retryIntervalMs'],
    '请求改写': ['requestHeadersToAdd', 'requestHeadersToRemove', 'pathPrefixReplacement', 'stripPrefix'],
    '其他': ['name', 'description', 'path', 'method', 'matchType', 'priority', 'status']
  };

  constructor(
    public dialogRef: MatDialogRef<VersionCompareDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: {
      ruleId: number;
      version1: RouteRuleVersion;
      version2: RouteRuleVersion;
      diffResponse: DiffResponse;
      appId: number;
    },
    private dialog: MatDialog,
    private routeRuleService: RouteRuleService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.version1 = this.data.version1;
    this.version2 = this.data.version2;
    this.diffResponse = this.data.diffResponse;
    this.appId = this.data.appId;
  }

  getDiffsByCategory(category: string): DiffField[] {
    if (!this.diffResponse?.diffsByCategory) {
      const fields = this.categoryMapping[category] || [];
      return (this.diffResponse?.diffs || []).filter(d => fields.includes(d.fieldName));
    }
    return this.diffResponse.diffsByCategory[category] || [];
  }

  formatValue(value: string): string {
    if (!value) return '-';
    try {
      const parsed = JSON.parse(value);
      if (Array.isArray(parsed) || typeof parsed === 'object') {
        return JSON.stringify(parsed, null, 2);
      }
      return String(parsed);
    } catch {
      return value;
    }
  }

  getDiffClass(changeType: string, side: 'old' | 'new'): string {
    return `diff-${changeType.toLowerCase()}-${side}`;
  }

  getChipClass(changeType: string): string {
    return `chip-${changeType.toLowerCase()}`;
  }

  getChangeTypeLabel(changeType: string): string {
    switch (changeType) {
      case 'ADD': return '新增';
      case 'REMOVE': return '删除';
      case 'MODIFY': return '修改';
      default: return changeType;
    }
  }

  onRollback(version: RouteRuleVersion | null): void {
    if (!version) return;
    const ref = this.dialog.open(RollbackReasonDialogComponent, {
      data: { ruleId: this.data.ruleId, version: version.version },
      width: '500px'
    });
    ref.afterClosed().subscribe((reason: string | false) => {
      if (reason) {
        this.routeRuleService.rollbackWithReason(this.appId, this.data.ruleId, version.id, reason).subscribe({
          next: () => {
            this.snackBar.open(`已回滚到版本 v${version.version}`, '关闭', { duration: 3000 });
            this.dialogRef.close(true);
          },
          error: (err) => {
            this.snackBar.open('回滚失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }
}

@Component({
  selector: 'app-route-rule-detail',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <div class="detail-container">
      <mat-tab-group>
        <mat-tab label="基本信息">
          <div class="tab-content">
            <mat-card class="info-card">
              <mat-card-title>基础信息</mat-card-title>
              <mat-card-content>
                <div class="info-grid">
                  <div class="info-item">
                    <span class="label">规则名称</span>
                    <span class="value">{{ rule?.name || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">租户</span>
                    <span class="value">{{ rule?.tenantName || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">应用</span>
                    <span class="value">{{ rule?.applicationName || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">状态</span>
                    <span class="value" [ngClass]="getStatusClass(rule?.status || '')">{{ rule?.status || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">版本</span>
                    <span class="value">v{{ rule?.version || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">优先级</span>
                    <span class="value">{{ rule?.priority || '-' }}</span>
                  </div>
                  <div class="info-item full-width">
                    <span class="label">描述</span>
                    <span class="value">{{ rule?.description || '-' }}</span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="info-card">
              <mat-card-title>路由匹配</mat-card-title>
              <mat-card-content>
                <div class="info-grid">
                  <div class="info-item">
                    <span class="label">HTTP方法</span>
                    <span class="value">
                      <mat-chip>{{ rule?.method || '-' }}</mat-chip>
                    </span>
                  </div>
                  <div class="info-item">
                    <span class="label">匹配类型</span>
                    <span class="value">{{ rule?.matchType || '-' }}</span>
                  </div>
                  <div class="info-item full-width">
                    <span class="label">路径</span>
                    <span class="value">{{ rule?.path || '-' }}</span>
                  </div>
                  <div class="info-item full-width">
                    <span class="label">目标路径</span>
                    <span class="value">{{ rule?.targetPath || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">去除前缀</span>
                    <span class="value">{{ rule?.stripPrefix ? '是' : '否' }}</span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="info-card" *ngIf="rule?.targetBackends?.length">
              <mat-card-title>目标后端</mat-card-title>
              <mat-card-content>
                <table mat-table [dataSource]="rule?.targetBackends || []" class="full-width">
                  <ng-container matColumnDef="url">
                    <th mat-header-cell *matHeaderCellDef>URL</th>
                    <td mat-cell *matCellDef="let backend">{{ backend.url }}</td>
                  </ng-container>
                  <ng-container matColumnDef="weight">
                    <th mat-header-cell *matHeaderCellDef>权重</th>
                    <td mat-cell *matCellDef="let backend">{{ backend.weight }}</td>
                  </ng-container>
                  <ng-container matColumnDef="colorTag">
                    <th mat-header-cell *matHeaderCellDef>流量标签</th>
                    <td mat-cell *matCellDef="let backend">{{ backend.colorTag || '-' }}</td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="['url', 'weight', 'colorTag']"></tr>
                  <tr mat-row *matRowDef="let row; columns: ['url', 'weight', 'colorTag'];"></tr>
                </table>
              </mat-card-content>
            </mat-card>

            <mat-card class="info-card">
              <mat-card-title>超时设置</mat-card-title>
              <mat-card-content>
                <div class="info-grid">
                  <div class="info-item">
                    <span class="label">连接超时</span>
                    <span class="value">{{ rule?.connectTimeoutMs ? rule.connectTimeoutMs + ' ms' : '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">读取超时</span>
                    <span class="value">{{ rule?.readTimeoutMs ? rule.readTimeoutMs + ' ms' : '-' }}</span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="info-card">
              <mat-card-title>重试策略</mat-card-title>
              <mat-card-content>
                <div class="info-grid">
                  <div class="info-item">
                    <span class="label">最大重试次数</span>
                    <span class="value">{{ rule?.maxRetries ?? '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">5xx重试</span>
                    <span class="value">{{ rule?.retryOn5xx ? '是' : '否' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">超时重试</span>
                    <span class="value">{{ rule?.retryOnTimeout ? '是' : '否' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="label">重试间隔</span>
                    <span class="value">{{ rule?.retryIntervalMs ? rule.retryIntervalMs + ' ms' : '-' }}</span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="info-card">
              <mat-card-title>请求改写</mat-card-title>
              <mat-card-content>
                <div class="info-grid">
                  <div class="info-item full-width">
                    <span class="label">路径前缀替换</span>
                    <span class="value">{{ rule?.pathPrefixReplacement || '-' }}</span>
                  </div>
                </div>
                <div *ngIf="rule?.requestHeadersToAdd && Object.keys(rule.requestHeadersToAdd).length > 0" class="headers-section">
                  <h4>新增请求头</h4>
                  <div class="header-list">
                    <div *ngFor="let value of getHeaderEntries(rule.requestHeadersToAdd)" class="header-item">
                      <span class="header-key">{{ value.key }}:</span>
                      <span class="header-value">{{ value.value }}</span>
                    </div>
                  </div>
                </div>
                <div *ngIf="rule?.requestHeadersToRemove?.length" class="headers-section">
                  <h4>移除请求头</h4>
                  <div class="header-list">
                    <div *ngFor="let header of rule.requestHeadersToRemove" class="header-item">
                      <mat-chip color="warn">{{ header }}</mat-chip>
                    </div>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </mat-tab>

        <mat-tab label="历史版本">
          <div class="tab-content">
            <div class="action-toolbar">
              <button mat-raised-button color="primary" [disabled]="selectedVersions.length !== 2" (click)="compareVersions()">
                <mat-icon>compare_arrows</mat-icon>
                对比选中版本
              </button>
              <span class="selected-info" *ngIf="selectedVersions.length > 0">
                已选择 {{ selectedVersions.length }} 个版本
              </span>
            </div>
            <table mat-table [dataSource]="versions" class="full-width">
              <ng-container matColumnDef="select">
                <th mat-header-cell *matHeaderCellDef>
                  <mat-checkbox
                    [checked]="isAllSelected()"
                    [indeterminate]="isIndeterminate()"
                    (change)="toggleAll($event.checked)">
                  </mat-checkbox>
                </th>
                <td mat-cell *matCellDef="let version">
                  <mat-checkbox
                    [checked]="isSelected(version)"
                    (change)="toggleSelection(version, $event.checked)">
                  </mat-checkbox>
                </td>
              </ng-container>
              <ng-container matColumnDef="version">
                <th mat-header-cell *matHeaderCellDef>版本</th>
                <td mat-cell *matCellDef="let v">v{{ v.version }}</td>
              </ng-container>
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef>创建时间</th>
                <td mat-cell *matCellDef="let v">{{ v.createdAt | date:'yyyy-MM-dd HH:mm:ss' }}</td>
              </ng-container>
              <ng-container matColumnDef="createdBy">
                <th mat-header-cell *matHeaderCellDef>创建人</th>
                <td mat-cell *matCellDef="let v">{{ v.createdBy || '-' }}</td>
              </ng-container>
              <ng-container matColumnDef="changeLog">
                <th mat-header-cell *matHeaderCellDef>变更说明</th>
                <td mat-cell *matCellDef="let v">{{ v.changeLog || '-' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['select', 'version', 'createdAt', 'createdBy', 'changeLog']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['select', 'version', 'createdAt', 'createdBy', 'changeLog'];"></tr>
            </table>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .detail-container {
      min-width: 700px;
    }
    .tab-content {
      padding: 16px 0;
    }
    .info-card {
      margin-bottom: 16px;
    }
    .info-card mat-card-title {
      font-size: 16px;
      font-weight: bold;
      color: #333;
    }
    .info-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;
    }
    .info-item {
      display: flex;
      flex-direction: column;
    }
    .info-item.full-width {
      grid-column: 1 / -1;
    }
    .label {
      font-size: 12px;
      color: #666;
      margin-bottom: 4px;
    }
    .value {
      font-size: 14px;
      color: #333;
    }
    .full-width {
      width: 100%;
    }
    .action-toolbar {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 16px;
    }
    .selected-info {
      font-size: 13px;
      color: #666;
    }
    .headers-section {
      margin-top: 16px;
    }
    .headers-section h4 {
      margin: 0 0 8px 0;
      font-size: 13px;
      color: #666;
    }
    .header-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .header-item {
      background: #f5f5f5;
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 13px;
    }
    .header-key {
      font-weight: bold;
      margin-right: 4px;
    }
  `]
})
export class RouteRuleDetailComponent {
  @Input() rule!: RouteRule;
  @Input() versions: RouteRuleVersion[] = [];
  @Output() compare = new EventEmitter<RouteRuleVersion[]>();
  @Output() rollback = new EventEmitter<RouteRuleVersion>();

  selectedVersions = new SelectionModel<RouteRuleVersion>(true, []);

  getHeaderEntries(obj: { [key: string]: string }): { key: string; value: string }[] {
    return Object.entries(obj || {}).map(([key, value]) => ({ key, value }));
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'APPROVED': return 'status-active';
      case 'REJECTED': return 'status-inactive';
      case 'PENDING_APPROVAL': return 'status-pending';
      default: return '';
    }
  }

  isSelected(version: RouteRuleVersion): boolean {
    return this.selectedVersions.isSelected(version);
  }

  isAllSelected(): boolean {
    return this.selectedVersions.selected.length === this.versions.length;
  }

  isIndeterminate(): boolean {
    return this.selectedVersions.selected.length > 0 && this.selectedVersions.selected.length < this.versions.length;
  }

  toggleSelection(version: RouteRuleVersion, checked: boolean): void {
    if (checked) {
      this.selectedVersions.select(version);
    } else {
      this.selectedVersions.deselect(version);
    }
  }

  toggleAll(checked: boolean): void {
    if (checked) {
      this.versions.forEach(v => this.selectedVersions.select(v));
    } else {
      this.selectedVersions.clear();
    }
  }

  compareVersions(): void {
    if (this.selectedVersions.selected.length === 2) {
      this.compare.emit(this.selectedVersions.selected);
    }
  }
}

@Component({
  selector: 'app-route-rule-detail-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule, RouteRuleDetailComponent],
  template: `
    <h2 mat-dialog-title>路由规则详情 - {{ rule?.name }}</h2>
    <mat-dialog-content *ngIf="!loading; else loadingTemplate">
      <app-route-rule-detail
        *ngIf="rule"
        [rule]="rule"
        [versions]="versions"
        (compare)="onCompare($event)"
        (rollback)="onRollback($event)">
      </app-route-rule-detail>
    </mat-dialog-content>
    <ng-template #loadingTemplate>
      <mat-dialog-content class="loading-content">
        <mat-spinner diameter="40"></mat-spinner>
        <p>加载中...</p>
      </mat-dialog-content>
    </ng-template>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">关闭</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .loading-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 40px;
      min-width: 400px;
      min-height: 200px;
    }
    .loading-content p {
      margin-top: 16px;
      color: #666;
    }
  `]
})
export class RouteRuleDetailDialogComponent implements OnInit {
  rule: RouteRule | null = null;
  versions: RouteRuleVersion[] = [];
  loading = true;
  appId!: number;

  constructor(
    public dialogRef: MatDialogRef<RouteRuleDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { ruleId: number; appId: number },
    private routeRuleService: RouteRuleService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.appId = this.data.appId;
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.routeRuleService.getById(this.data.ruleId).subscribe({
      next: (rule) => {
        this.rule = rule;
        this.routeRuleService.getVersions(this.appId, this.data.ruleId).subscribe({
          next: (versions) => {
            this.versions = versions.slice(0, 10);
            this.loading = false;
          },
          error: (err) => {
            this.loading = false;
            this.snackBar.open('加载版本历史失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open('加载规则详情失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  onCompare(versions: RouteRuleVersion[]): void {
    const [v1, v2] = versions.sort((a, b) => a.version - b.version);
    this.routeRuleService.compareVersions(this.appId, this.data.ruleId, v1.id, v2.id).subscribe({
      next: (diffResponse) => {
        this.dialog.open(VersionCompareDialogComponent, {
          data: {
            ruleId: this.data.ruleId,
            version1: v1,
            version2: v2,
            diffResponse,
            appId: this.appId
          },
          width: '900px',
          maxWidth: '95vw'
        });
      },
      error: (err) => {
        this.snackBar.open('版本对比失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  onRollback(version: RouteRuleVersion): void {
    const ref = this.dialog.open(RollbackReasonDialogComponent, {
      data: { ruleId: this.data.ruleId, version: version.version },
      width: '500px'
    });
    ref.afterClosed().subscribe((reason: string | false) => {
      if (reason) {
        this.routeRuleService.rollbackWithReason(this.appId, this.data.ruleId, version.id, reason).subscribe({
          next: () => {
            this.snackBar.open(`已回滚到版本 v${version.version}`, '关闭', { duration: 3000 });
            this.dialogRef.close(true);
          },
          error: (err) => {
            this.snackBar.open('回滚失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }
}

@Component({
  selector: 'app-route-rules',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="路由规则" subtitle="管理API请求路由转发规则" icon="route">
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
      <button mat-raised-button color="primary" (click)="openCreateDialog()">
        <mat-icon>add</mat-icon>
        新建规则
      </button>
    </app-page-header>

    <mat-card>
      <div class="batch-toolbar" *ngIf="selection.selected.length > 0">
        <span class="selected-count">已选择 {{ selection.selected.length }} 项</span>
        <button mat-raised-button color="primary" (click)="batchOperation('ENABLE')">
          <mat-icon>play_arrow</mat-icon>
          启用
        </button>
        <button mat-raised-button (click)="batchOperation('DISABLE')">
          <mat-icon>pause</mat-icon>
          禁用
        </button>
        <button mat-raised-button color="accent" (click)="batchOperation('SUBMIT_APPROVAL')">
          <mat-icon>send</mat-icon>
          提交审批
        </button>
        <button mat-raised-button color="warn" (click)="batchOperation('DELETE')">
          <mat-icon>delete</mat-icon>
          删除
        </button>
        <button mat-button (click)="selection.clear()">取消选择</button>
      </div>

      <div class="table-container">
        <table mat-table [dataSource]="rules" matSort>
          <ng-container matColumnDef="select">
            <th mat-header-cell *matHeaderCellDef>
              <mat-checkbox
                [checked]="selection.hasValue() && isAllSelected()"
                [indeterminate]="selection.hasValue() && !isAllSelected()"
                (change)="toggleAll($event.checked)">
              </mat-checkbox>
            </th>
            <td mat-cell *matCellDef="let r">
              <mat-checkbox
                [checked]="selection.isSelected(r)"
                (change)="selection.toggle(r)">
              </mat-checkbox>
            </td>
          </ng-container>
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
          <ng-container matColumnDef="applicationName">
            <th mat-header-cell *matHeaderCellDef>应用</th>
            <td mat-cell *matCellDef="let r">{{ r.applicationName }}</td>
          </ng-container>
          <ng-container matColumnDef="method">
            <th mat-header-cell *matHeaderCellDef>方法</th>
            <td mat-cell *matCellDef="let r">
              <mat-chip>{{ r.method }}</mat-chip>
            </td>
          </ng-container>
          <ng-container matColumnDef="path">
            <th mat-header-cell *matHeaderCellDef>路径</th>
            <td mat-cell *matCellDef="let r">{{ r.path }}</td>
          </ng-container>
          <ng-container matColumnDef="version">
            <th mat-header-cell *matHeaderCellDef>版本</th>
            <td mat-cell *matCellDef="let r">v{{ r.version }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>状态</th>
            <td mat-cell *matCellDef="let r">
              <span [ngClass]="getStatusClass(r.status)">{{ r.status }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>操作</th>
            <td mat-cell *matCellDef="let r">
              <button mat-icon-button color="primary" (click)="viewDetail(r)" matTooltip="查看详情">
                <mat-icon>visibility</mat-icon>
              </button>
              <button mat-icon-button color="primary" (click)="openEditDialog(r)" *ngIf="r.status === 'DRAFT' || r.status === 'REJECTED'" matTooltip="编辑">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button (click)="submitForApproval(r)" *ngIf="r.status === 'DRAFT'" matTooltip="提交审批">
                <mat-icon>send</mat-icon>
              </button>
              <button mat-icon-button color="primary" (click)="approve(r)" *ngIf="r.status === 'PENDING_APPROVAL'" matTooltip="审批通过">
                <mat-icon>check_circle</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="reject(r)" *ngIf="r.status === 'PENDING_APPROVAL'" matTooltip="拒绝">
                <mat-icon>cancel</mat-icon>
              </button>
              <button mat-icon-button (click)="showVersions(r)" matTooltip="版本历史">
                <mat-icon>history</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="openDeleteDialog(r)" matTooltip="删除">
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
    .batch-toolbar {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: #e3f2fd;
      border-radius: 4px;
      margin-bottom: 16px;
    }
    .selected-count {
      font-size: 14px;
      color: #1976d2;
      font-weight: 500;
      margin-right: 8px;
    }
  `]
})
export class RouteRulesComponent implements OnInit {
  rules: RouteRule[] = [];
  tenants: Tenant[] = [];
  applications: Application[] = [];
  selectedTenantId: number | null = null;
  selectedAppId: number | null = 1;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['select', 'id', 'name', 'tenantName', 'applicationName', 'method', 'path', 'version', 'status', 'actions'];
  selection = new SelectionModel<RouteRule>(true, []);

  constructor(
    private routeRuleService: RouteRuleService,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenants();
    this.loadApplications();
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
      },
      error: (err) => {
        this.snackBar.open('加载应用列表失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  loadRules(): void {
    if (!this.selectedAppId) return;
    this.routeRuleService.getRouteRules(this.selectedTenantId || undefined, this.selectedAppId, this.pageIndex, this.pageSize)
      .subscribe({
        next: (res: RouteRulePageResponse) => {
          this.rules = res.content;
          this.totalElements = res.totalElements;
        },
        error: (err) => {
          this.snackBar.open('加载规则列表失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
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
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadRules();
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'APPROVED': return 'status-active';
      case 'REJECTED': return 'status-inactive';
      case 'PENDING_APPROVAL': return 'status-pending';
      default: return '';
    }
  }

  openCreateDialog(): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(RouteRuleDialogComponent, { data: {} });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.routeRuleService.create(this.selectedAppId, r).subscribe({
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

  openEditDialog(rule: RouteRule): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(RouteRuleDialogComponent, { data: { rule } });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.routeRuleService.update(this.selectedAppId, rule.id, r).subscribe({
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

  submitForApproval(rule: RouteRule): void {
    if (!this.selectedAppId || !rule.id) return;
    this.routeRuleService.submitForApproval(this.selectedAppId, rule.id).subscribe({
      next: () => {
        this.snackBar.open('已提交审批', '关闭', { duration: 3000 });
        this.loadRules();
      },
      error: (err) => {
        this.snackBar.open('提交审批失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  approve(rule: RouteRule): void {
    if (!this.selectedAppId || !rule.id) return;
    this.routeRuleService.approve(this.selectedAppId, rule.id).subscribe({
      next: () => {
        this.snackBar.open('审批通过', '关闭', { duration: 3000 });
        this.loadRules();
      },
      error: (err) => {
        this.snackBar.open('审批失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  reject(rule: RouteRule): void {
    if (!this.selectedAppId || !rule.id) return;
    this.routeRuleService.reject(this.selectedAppId, rule.id).subscribe({
      next: () => {
        this.snackBar.open('已拒绝', '关闭', { duration: 3000 });
        this.loadRules();
      },
      error: (err) => {
        this.snackBar.open('拒绝失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  showVersions(rule: RouteRule): void {
    if (!this.selectedAppId || !rule.id) return;
    this.routeRuleService.getVersions(this.selectedAppId, rule.id).subscribe({
      next: (versions) => {
        const ref = this.dialog.open(VersionDialogComponent, {
          data: { ruleId: rule.id!, ruleName: rule.name, versions, appId: this.selectedAppId },
          width: '600px'
        });
        ref.afterClosed().subscribe(r => { if (r) this.loadRules(); });
      },
      error: (err) => {
        this.snackBar.open('加载版本历史失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  openDeleteDialog(rule: RouteRule): void {
    if (!this.selectedAppId) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除规则 "${rule.name}"吗？`, confirmText: '删除' }
    });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.routeRuleService.delete(this.selectedAppId, rule.id).subscribe({
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

  isAllSelected(): boolean {
    return this.selection.selected.length === this.rules.length;
  }

  toggleAll(checked: boolean): void {
    if (checked) {
      this.rules.forEach(r => this.selection.select(r));
    } else {
      this.selection.clear();
    }
  }

  viewDetail(rule: RouteRule): void {
    if (!this.selectedAppId || !rule.id) return;
    const ref = this.dialog.open(RouteRuleDetailDialogComponent, {
      data: { ruleId: rule.id, appId: this.selectedAppId },
      width: '900px',
      maxWidth: '95vw',
      maxHeight: '90vh'
    });
    ref.afterClosed().subscribe(r => {
      if (r) this.loadRules();
    });
  }

  batchOperation(operation: 'ENABLE' | 'DISABLE' | 'SUBMIT_APPROVAL' | 'DELETE'): void {
    if (!this.selectedAppId) return;
    const selectedIds = this.selection.selected
      .filter(r => r.id !== undefined)
      .map(r => r.id as number);

    if (selectedIds.length === 0) return;

    const operationNames: { [key: string]: string } = {
      'ENABLE': '启用',
      'DISABLE': '禁用',
      'SUBMIT_APPROVAL': '提交审批',
      'DELETE': '删除'
    };

    if (operation === 'DELETE') {
      const ref = this.dialog.open(ConfirmDialogComponent, {
        data: {
          title: `确认${operationNames[operation]}`,
          message: `确定要${operationNames[operation]}选中的 ${selectedIds.length} 条规则吗？`,
          confirmText: operationNames[operation]
        }
      });
      ref.afterClosed().subscribe(r => {
        if (r) {
          this.executeBatchOperation(selectedIds, operation, operationNames[operation]);
        }
      });
    } else {
      this.executeBatchOperation(selectedIds, operation, operationNames[operation]);
    }
  }

  private executeBatchOperation(
    ids: number[],
    operation: 'ENABLE' | 'DISABLE' | 'SUBMIT_APPROVAL' | 'DELETE',
    operationName: string
  ): void {
    if (!this.selectedAppId) return;
    const request: BatchOperationRequest = { ids, operation };

    this.routeRuleService.batchOperation(this.selectedAppId, request).subscribe({
      next: (response: BatchOperationResponse) => {
        const successCount = response.successCount;
        const failedCount = response.failedCount;

        let message = `${operationName}完成：成功 ${successCount} 条`;
        if (failedCount > 0) {
          message += `，失败 ${failedCount} 条`;
        }

        const failedItems = response.results.filter(r => !r.success);
        if (failedItems.length > 0) {
          const errorDetails = failedItems.map(r => `ID ${r.id}: ${r.message}`).join('\n');
          message += `\n失败详情：\n${errorDetails}`;
        }

        this.snackBar.open(message, '关闭', {
          duration: failedCount > 0 ? 8000 : 3000
        });

        this.selection.clear();
        this.loadRules();
      },
      error: (err) => {
        this.snackBar.open(`${operationName}失败：${err.message || '未知错误'}`, '关闭', {
          duration: 5000
        });
      }
    });
  }
}
