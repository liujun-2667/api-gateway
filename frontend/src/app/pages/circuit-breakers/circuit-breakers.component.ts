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
import { CircuitBreakerService } from '../../core/services/circuit-breaker.service';
import { Tenant } from '../../shared/models/tenant.model';
import { Application } from '../../shared/models/application.model';
import { CircuitBreakerConfig, CircuitBreakerConfigPageResponse } from '../../shared/models/circuit-breaker.model';

@Component({
  selector: 'app-circuit-breaker-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>{{ data?.config ? '编辑熔断配置' : '创建熔断配置' }}</h2>
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
          <mat-label>配置名称</mat-label>
          <input matInput formControlName="name">
        </mat-form-field>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>失败阈值(%)</mat-label>
            <input matInput type="number" formControlName="failureThreshold">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>慢调用阈值(%)</mat-label>
            <input matInput type="number" formControlName="slowCallThreshold">
          </mat-form-field>
        </div>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>慢调用时长(ms)</mat-label>
            <input matInput type="number" formControlName="slowCallDurationMs">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>熔断等待(ms)</mat-label>
            <input matInput type="number" formControlName="waitDurationInOpenStateMs">
          </mat-form-field>
        </div>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>半开状态调用数</mat-label>
            <input matInput type="number" formControlName="permittedNumberOfCallsInHalfOpenState">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>滑动窗口大小</mat-label>
            <input matInput type="number" formControlName="slidingWindowSize">
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>最小调用数</mat-label>
          <input matInput type="number" formControlName="minimumNumberOfCalls">
        </mat-form-field>
        <mat-slide-toggle formControlName="enabled">启用</mat-slide-toggle>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="onSubmit()">保存</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form-container { display: flex; flex-direction: column; gap: 16px; min-width: 550px; padding-top: 16px; max-height: 60vh; overflow-y: auto; }
    .form-row { display: flex; gap: 16px; }
    .form-row > * { flex: 1; }
  `]
})
export class CircuitBreakerDialogComponent implements OnInit {
  form: FormGroup;
  tenants: Tenant[] = [];
  applications: Application[] = [];

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    public dialogRef: MatDialogRef<CircuitBreakerDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { config?: CircuitBreakerConfig }
  ) {
    this.form = this.fb.group({
      tenantId: [data?.config?.tenantId || '', [Validators.required]],
      applicationId: [data?.config?.applicationId || null],
      name: [data?.config?.name || '', [Validators.required]],
      failureThreshold: [data?.config?.failureThreshold || 50, [Validators.required]],
      slowCallThreshold: [data?.config?.slowCallThreshold || 80, [Validators.required]],
      slowCallDurationMs: [data?.config?.slowCallDurationMs || 2000, [Validators.required]],
      waitDurationInOpenStateMs: [data?.config?.waitDurationInOpenStateMs || 60000, [Validators.required]],
      permittedNumberOfCallsInHalfOpenState: [data?.config?.permittedNumberOfCallsInHalfOpenState || 10, [Validators.required]],
      slidingWindowSize: [data?.config?.slidingWindowSize || 100, [Validators.required]],
      minimumNumberOfCalls: [data?.config?.minimumNumberOfCalls || 20, [Validators.required]],
      enabled: [data?.config?.enabled ?? true]
    });
  }

  ngOnInit(): void {
    this.tenantService.getAllTenants().subscribe(res => this.tenants = res);
    this.form.get('tenantId')?.valueChanges.subscribe(id => {
      if (id) this.applicationService.getApplicationsByTenant(id).subscribe(res => this.applications = res);
    });
    if (this.data?.config?.tenantId) {
      this.applicationService.getApplicationsByTenant(this.data.config.tenantId)
        .subscribe(res => this.applications = res);
    }
  }

  onSubmit(): void {
    if (this.form.valid) this.dialogRef.close(this.form.value);
  }
}

@Component({
  selector: 'app-circuit-breakers',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="熔断配置" subtitle="配置服务熔断保护策略" icon="power_off">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>选择租户</mat-label>
        <mat-select [value]="selectedTenantId" (selectionChange)="onTenantChange($event.value)">
          <mat-option [value]="null">全部</mat-option>
          <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-raised-button color="primary" (click)="openCreateDialog()">
        <mat-icon>add</mat-icon>
        新建配置
      </button>
    </app-page-header>

    <mat-card>
      <div class="table-container">
        <table mat-table [dataSource]="configs" matSort>
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>ID</th>
            <td mat-cell *matCellDef="let c">{{ c.id }}</td>
          </ng-container>
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>名称</th>
            <td mat-cell *matCellDef="let c">{{ c.name }}</td>
          </ng-container>
          <ng-container matColumnDef="tenantName">
            <th mat-header-cell *matHeaderCellDef>租户</th>
            <td mat-cell *matCellDef="let c">{{ c.tenantName }}</td>
          </ng-container>
          <ng-container matColumnDef="applicationName">
            <th mat-header-cell *matHeaderCellDef>应用</th>
            <td mat-cell *matCellDef="let c">{{ c.applicationName || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="failureThreshold">
            <th mat-header-cell *matHeaderCellDef>失败率</th>
            <td mat-cell *matCellDef="let c">{{ c.failureThreshold }}%</td>
          </ng-container>
          <ng-container matColumnDef="state">
            <th mat-header-cell *matHeaderCellDef>当前状态</th>
            <td mat-cell *matCellDef="let c">
              <span [ngClass]="getStateClass(c.state)">{{ c.state || '-' }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="enabled">
            <th mat-header-cell *matHeaderCellDef>启用</th>
            <td mat-cell *matCellDef="let c">
              <span [ngClass]="c.enabled ? 'status-active' : 'status-inactive'">
                {{ c.enabled ? '是' : '否' }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>操作</th>
            <td mat-cell *matCellDef="let c">
              <button mat-icon-button color="primary" (click)="openEditDialog(c)">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button (click)="toggle(c)" matTooltip="启用/禁用">
                <mat-icon>{{ c.enabled ? 'toggle_on' : 'toggle_off' }}</mat-icon>
              </button>
              <button mat-icon-button color="accent" (click)="reset(c)" matTooltip="重置状态">
                <mat-icon>refresh</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="openDeleteDialog(c)">
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
export class CircuitBreakersComponent implements OnInit {
  configs: CircuitBreakerConfig[] = [];
  tenants: Tenant[] = [];
  selectedTenantId: number | null = null;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'name', 'tenantName', 'applicationName', 'failureThreshold', 'state', 'enabled', 'actions'];

  constructor(
    private circuitBreakerService: CircuitBreakerService,
    private tenantService: TenantService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenants();
    this.loadConfigs();
  }

  loadTenants(): void {
    this.tenantService.getAllTenants().subscribe(res => this.tenants = res);
  }

  loadConfigs(): void {
    this.circuitBreakerService.getCircuitBreakers(this.selectedTenantId || undefined, undefined, this.pageIndex, this.pageSize)
      .subscribe((res: CircuitBreakerConfigPageResponse) => {
        this.configs = res.content;
        this.totalElements = res.totalElements;
      });
  }

  onTenantChange(id: number | null): void {
    this.selectedTenantId = id;
    this.pageIndex = 0;
    this.loadConfigs();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadConfigs();
  }

  getStateClass(state?: string): string {
    if (state === 'CLOSED') return 'status-active';
    if (state === 'OPEN') return 'status-inactive';
    return 'status-pending';
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(CircuitBreakerDialogComponent, { data: {} });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.circuitBreakerService.create(r).subscribe(() => {
          this.snackBar.open('创建成功', '关闭', { duration: 3000 });
          this.loadConfigs();
        });
      }
    });
  }

  openEditDialog(c: CircuitBreakerConfig): void {
    const ref = this.dialog.open(CircuitBreakerDialogComponent, { data: { config: c } });
    ref.afterClosed().subscribe(r => {
      if (r && c.id) {
        this.circuitBreakerService.update(c.id, r).subscribe(() => {
          this.snackBar.open('更新成功', '关闭', { duration: 3000 });
          this.loadConfigs();
        });
      }
    });
  }

  toggle(c: CircuitBreakerConfig): void {
    if (!c.id) return;
    this.circuitBreakerService.toggle(c.id).subscribe(() => this.loadConfigs());
  }

  reset(c: CircuitBreakerConfig): void {
    if (!c.id) return;
    this.circuitBreakerService.reset(c.id).subscribe(() => {
      this.snackBar.open('状态已重置', '关闭', { duration: 3000 });
      this.loadConfigs();
    });
  }

  openDeleteDialog(c: CircuitBreakerConfig): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除配置 "${c.name}"吗？`, confirmText: '删除' }
    });
    ref.afterClosed().subscribe(r => {
      if (r && c.id) {
        this.circuitBreakerService.delete(c.id).subscribe(() => {
          this.snackBar.open('删除成功', '关闭', { duration: 3000 });
          this.loadConfigs();
        });
      }
    });
  }
}
