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
import { RateLimitService } from '../../core/services/rate-limit.service';
import { Tenant } from '../../shared/models/tenant.model';
import { Application } from '../../shared/models/application.model';
import { RateLimitConfig, RateLimitConfigPageResponse } from '../../shared/models/rate-limit.model';

@Component({
  selector: 'app-rate-limit-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>{{ data?.config ? '编辑限流配置' : '创建限流配置' }}</h2>
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
            <mat-label>每秒请求数</mat-label>
            <input matInput type="number" formControlName="requestsPerSecond">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>突发容量</mat-label>
            <input matInput type="number" formControlName="burstCapacity">
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>时间窗口（秒）</mat-label>
          <input matInput type="number" formControlName="windowSizeSeconds">
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
    .form-container { display: flex; flex-direction: column; gap: 16px; min-width: 500px; padding-top: 16px; }
    .form-row { display: flex; gap: 16px; }
    .form-row > * { flex: 1; }
  `]
})
export class RateLimitDialogComponent implements OnInit {
  form: FormGroup;
  tenants: Tenant[] = [];
  applications: Application[] = [];

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    public dialogRef: MatDialogRef<RateLimitDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { config?: RateLimitConfig }
  ) {
    this.form = this.fb.group({
      tenantId: [data?.config?.tenantId || '', [Validators.required]],
      applicationId: [data?.config?.applicationId || null],
      name: [data?.config?.name || '', [Validators.required]],
      requestsPerSecond: [data?.config?.requestsPerSecond || 100, [Validators.required]],
      burstCapacity: [data?.config?.burstCapacity || 200, [Validators.required]],
      windowSizeSeconds: [data?.config?.windowSizeSeconds || 1, [Validators.required]],
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
  selector: 'app-rate-limits',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="限流配置" subtitle="配置API请求速率限制策略" icon="speed">
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
          <ng-container matColumnDef="requestsPerSecond">
            <th mat-header-cell *matHeaderCellDef>RPS</th>
            <td mat-cell *matCellDef="let c">{{ c.requestsPerSecond }}</td>
          </ng-container>
          <ng-container matColumnDef="burstCapacity">
            <th mat-header-cell *matHeaderCellDef>突发容量</th>
            <td mat-cell *matCellDef="let c">{{ c.burstCapacity }}</td>
          </ng-container>
          <ng-container matColumnDef="windowSizeSeconds">
            <th mat-header-cell *matHeaderCellDef>窗口(秒)</th>
            <td mat-cell *matCellDef="let c">{{ c.windowSizeSeconds }}</td>
          </ng-container>
          <ng-container matColumnDef="enabled">
            <th mat-header-cell *matHeaderCellDef>状态</th>
            <td mat-cell *matCellDef="let c">
              <span [ngClass]="c.enabled ? 'status-active' : 'status-inactive'">
                {{ c.enabled ? '启用' : '禁用' }}
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
export class RateLimitsComponent implements OnInit {
  configs: RateLimitConfig[] = [];
  tenants: Tenant[] = [];
  selectedTenantId: number | null = null;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'name', 'tenantName', 'applicationName', 'requestsPerSecond', 'burstCapacity', 'windowSizeSeconds', 'enabled', 'actions'];

  constructor(
    private rateLimitService: RateLimitService,
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
    this.rateLimitService.getRateLimits(this.selectedTenantId || undefined, undefined, this.pageIndex, this.pageSize)
      .subscribe((res: RateLimitConfigPageResponse) => {
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

  openCreateDialog(): void {
    const ref = this.dialog.open(RateLimitDialogComponent, { data: {} });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.rateLimitService.create(r).subscribe(() => {
          this.snackBar.open('创建成功', '关闭', { duration: 3000 });
          this.loadConfigs();
        });
      }
    });
  }

  openEditDialog(c: RateLimitConfig): void {
    const ref = this.dialog.open(RateLimitDialogComponent, { data: { config: c } });
    ref.afterClosed().subscribe(r => {
      if (r && c.id) {
        this.rateLimitService.update(c.id, r).subscribe(() => {
          this.snackBar.open('更新成功', '关闭', { duration: 3000 });
          this.loadConfigs();
        });
      }
    });
  }

  toggle(c: RateLimitConfig): void {
    if (!c.id) return;
    this.rateLimitService.toggle(c.id).subscribe(() => this.loadConfigs());
  }

  openDeleteDialog(c: RateLimitConfig): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除配置 "${c.name}"吗？`, confirmText: '删除' }
    });
    ref.afterClosed().subscribe(r => {
      if (r && c.id) {
        this.rateLimitService.delete(c.id).subscribe(() => {
          this.snackBar.open('删除成功', '关闭', { duration: 3000 });
          this.loadConfigs();
        });
      }
    });
  }
}
