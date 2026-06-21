import { Component, OnInit, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
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
  HttpMethod, MatchType, RuleStatus
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

  constructor(
    public dialogRef: MatDialogRef<VersionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { ruleId: number; ruleName: string; versions: RouteRuleVersion[] },
    private routeRuleService: RouteRuleService,
    private snackBar: MatSnackBar
  ) {
    this.ruleId = data.ruleId;
    this.ruleName = data.ruleName;
    this.versions = data.versions;
  }

  rollback(v: RouteRuleVersion): void {
    this.routeRuleService.rollback(this.ruleId, v.id).subscribe({
      next: () => {
        this.snackBar.open('版本回滚成功', '关闭', { duration: 3000 });
        this.dialogRef.close(true);
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
      <button mat-raised-button color="primary" (click)="openCreateDialog()">
        <mat-icon>add</mat-icon>
        新建规则
      </button>
    </app-page-header>

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
              <button mat-icon-button color="primary" (click)="openEditDialog(r)" *ngIf="r.status === 'DRAFT' || r.status === 'REJECTED'">
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
  styles: [`.filter-field { width: 200px; margin-right: 12px; }`]
})
export class RouteRulesComponent implements OnInit {
  rules: RouteRule[] = [];
  tenants: Tenant[] = [];
  selectedTenantId: number | null = null;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'name', 'tenantName', 'applicationName', 'method', 'path', 'version', 'status', 'actions'];

  constructor(
    private routeRuleService: RouteRuleService,
    private tenantService: TenantService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenants();
    this.loadRules();
  }

  loadTenants(): void {
    this.tenantService.getAllTenants().subscribe(res => this.tenants = res);
  }

  loadRules(): void {
    this.routeRuleService.getRouteRules(this.selectedTenantId || undefined, undefined, this.pageIndex, this.pageSize)
      .subscribe((res: RouteRulePageResponse) => {
        this.rules = res.content;
        this.totalElements = res.totalElements;
      });
  }

  onTenantChange(id: number | null): void {
    this.selectedTenantId = id;
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
    const ref = this.dialog.open(RouteRuleDialogComponent, { data: {} });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.routeRuleService.create(r).subscribe(() => {
          this.snackBar.open('创建成功', '关闭', { duration: 3000 });
          this.loadRules();
        });
      }
    });
  }

  openEditDialog(rule: RouteRule): void {
    const ref = this.dialog.open(RouteRuleDialogComponent, { data: { rule } });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.routeRuleService.update(rule.id, r).subscribe(() => {
          this.snackBar.open('更新成功', '关闭', { duration: 3000 });
          this.loadRules();
        });
      }
    });
  }

  submitForApproval(rule: RouteRule): void {
    if (!rule.id) return;
    this.routeRuleService.submitForApproval(rule.id).subscribe(() => {
      this.snackBar.open('已提交审批', '关闭', { duration: 3000 });
      this.loadRules();
    });
  }

  approve(rule: RouteRule): void {
    if (!rule.id) return;
    this.routeRuleService.approve(rule.id).subscribe(() => {
      this.snackBar.open('审批通过', '关闭', { duration: 3000 });
      this.loadRules();
    });
  }

  reject(rule: RouteRule): void {
    if (!rule.id) return;
    this.routeRuleService.reject(rule.id).subscribe(() => {
      this.snackBar.open('已拒绝', '关闭', { duration: 3000 });
      this.loadRules();
    });
  }

  showVersions(rule: RouteRule): void {
    if (!rule.id) return;
    this.routeRuleService.getVersions(rule.id).subscribe(versions => {
      const ref = this.dialog.open(VersionDialogComponent, {
        data: { ruleId: rule.id!, ruleName: rule.name, versions },
        width: '600px'
      });
      ref.afterClosed().subscribe(r => { if (r) this.loadRules(); });
    });
  }

  openDeleteDialog(rule: RouteRule): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除规则 "${rule.name}"吗？`, confirmText: '删除' }
    });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.routeRuleService.delete(rule.id).subscribe(() => {
          this.snackBar.open('删除成功', '关闭', { duration: 3000 });
          this.loadRules();
        });
      }
    });
  }
}
