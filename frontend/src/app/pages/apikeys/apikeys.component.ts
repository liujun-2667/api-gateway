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
import { ApiKeyService } from '../../core/services/apikey.service';
import { Tenant } from '../../shared/models/tenant.model';
import { Application } from '../../shared/models/application.model';
import { ApiKey, ApiKeyPageResponse, ApiKeyStatus } from '../../shared/models/apikey.model';

@Component({
  selector: 'app-apikey-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>{{ data?.apiKey ? '编辑API Key' : '创建API Key' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-container">
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
        <mat-form-field appearance="outline">
          <mat-label>名称</mat-label>
          <input matInput formControlName="name">
          <mat-error *ngIf="form.get('name')?.hasError('required')">必填</mat-error>
        </mat-form-field>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>每日配额</mat-label>
            <input matInput type="number" formControlName="quotaPerDay">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>每秒限流</mat-label>
            <input matInput type="number" formControlName="rateLimitPerSecond">
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>过期时间</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="expiresAt">
          <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="onSubmit()">保存</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form-container {
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-width: 450px;
      padding-top: 16px;
    }
    .form-row {
      display: flex;
      gap: 16px;
    }
    .form-row > * {
      flex: 1;
    }
  `]
})
export class ApiKeyDialogComponent implements OnInit {
  form: FormGroup;
  tenants: Tenant[] = [];
  applications: Application[] = [];

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private applicationService: ApplicationService,
    public dialogRef: MatDialogRef<ApiKeyDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { apiKey?: ApiKey }
  ) {
    this.form = this.fb.group({
      tenantId: [data?.apiKey?.tenantId || '', [Validators.required]],
      applicationId: [data?.apiKey?.applicationId || null],
      name: [data?.apiKey?.name || '', [Validators.required]],
      quotaPerDay: [data?.apiKey?.quotaPerDay || 10000],
      rateLimitPerSecond: [data?.apiKey?.rateLimitPerSecond || 100],
      expiresAt: [data?.apiKey?.expiresAt || null]
    });
  }

  ngOnInit(): void {
    this.tenantService.getAllTenants().subscribe(res => this.tenants = res);
    this.form.get('tenantId')?.valueChanges.subscribe(id => {
      if (id) {
        this.applicationService.getApplicationsByTenant(id).subscribe(res => this.applications = res);
      }
    });
    if (this.data?.apiKey?.tenantId) {
      this.applicationService.getApplicationsByTenant(this.data.apiKey.tenantId)
        .subscribe(res => this.applications = res);
    }
  }

  onSubmit(): void {
    if (this.form.valid) this.dialogRef.close(this.form.value);
  }
}

@Component({
  selector: 'app-apikeys',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="API Key管理" subtitle="管理租户和应用的API访问凭证" icon="vpn_key">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>选择租户</mat-label>
        <mat-select [value]="selectedTenantId" (selectionChange)="onTenantChange($event.value)">
          <mat-option [value]="null">全部租户</mat-option>
          <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-raised-button color="primary" (click)="openCreateDialog()">
        <mat-icon>add</mat-icon>
        新建API Key
      </button>
    </app-page-header>

    <mat-card>
      <div class="table-container">
        <table mat-table [dataSource]="apiKeys" matSort>
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>ID</th>
            <td mat-cell *matCellDef="let k">{{ k.id }}</td>
          </ng-container>
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>名称</th>
            <td mat-cell *matCellDef="let k">{{ k.name }}</td>
          </ng-container>
          <ng-container matColumnDef="tenantName">
            <th mat-header-cell *matHeaderCellDef>租户</th>
            <td mat-cell *matCellDef="let k">{{ k.tenantName }}</td>
          </ng-container>
          <ng-container matColumnDef="applicationName">
            <th mat-header-cell *matHeaderCellDef>应用</th>
            <td mat-cell *matCellDef="let k">{{ k.applicationName || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="key">
            <th mat-header-cell *matHeaderCellDef>Key</th>
            <td mat-cell *matCellDef="let k">
              <span *ngIf="k.key">{{ showKeys[k.id!] ? k.key : '••••••••••••' }}</span>
              <button mat-icon-button (click)="toggleKeyVisibility(k.id!)" *ngIf="k.key">
                <mat-icon>{{ showKeys[k.id!] ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </td>
          </ng-container>
          <ng-container matColumnDef="quotaPerDay">
            <th mat-header-cell *matHeaderCellDef>每日配额</th>
            <td mat-cell *matCellDef="let k">{{ k.quotaPerDay }}</td>
          </ng-container>
          <ng-container matColumnDef="rateLimitPerSecond">
            <th mat-header-cell *matHeaderCellDef>每秒限流</th>
            <td mat-cell *matCellDef="let k">{{ k.rateLimitPerSecond }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>状态</th>
            <td mat-cell *matCellDef="let k">
              <span [ngClass]="k.status === 'ACTIVE' ? 'status-active' : 'status-inactive'">
                {{ k.status }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="expiresAt">
            <th mat-header-cell *matHeaderCellDef>过期时间</th>
            <td mat-cell *matCellDef="let k">{{ k.expiresAt ? (k.expiresAt | date:'yyyy-MM-dd') : '永不过期' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>操作</th>
            <td mat-cell *matCellDef="let k">
              <button mat-icon-button color="primary" (click)="regenerateKey(k)" matTooltip="重新生成">
                <mat-icon>refresh</mat-icon>
              </button>
              <button mat-icon-button (click)="toggleStatus(k)" matTooltip="启用/禁用">
                <mat-icon>{{ k.status === 'ACTIVE' ? 'lock_open' : 'lock' }}</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="openDeleteDialog(k)">
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
export class ApiKeysComponent implements OnInit {
  apiKeys: ApiKey[] = [];
  tenants: Tenant[] = [];
  selectedTenantId: number | null = null;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  showKeys: Record<number, boolean> = {};
  displayedColumns = ['id', 'name', 'tenantName', 'applicationName', 'key', 'quotaPerDay', 'rateLimitPerSecond', 'status', 'expiresAt', 'actions'];

  constructor(
    private apiKeyService: ApiKeyService,
    private tenantService: TenantService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenants();
    this.loadApiKeys();
  }

  loadTenants(): void {
    this.tenantService.getAllTenants().subscribe(res => this.tenants = res);
  }

  loadApiKeys(): void {
    this.apiKeyService.getApiKeys(this.selectedTenantId || undefined, undefined, this.pageIndex, this.pageSize)
      .subscribe((res: ApiKeyPageResponse) => {
        this.apiKeys = res.content;
        this.totalElements = res.totalElements;
      });
  }

  onTenantChange(id: number | null): void {
    this.selectedTenantId = id;
    this.pageIndex = 0;
    this.loadApiKeys();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadApiKeys();
  }

  toggleKeyVisibility(id: number): void {
    this.showKeys[id] = !this.showKeys[id];
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(ApiKeyDialogComponent, { data: {} });
    ref.afterClosed().subscribe(r => {
      if (r) {
        r.status = ApiKeyStatus.ACTIVE;
        this.apiKeyService.create(r).subscribe(() => {
          this.snackBar.open('创建成功', '关闭', { duration: 3000 });
          this.loadApiKeys();
        });
      }
    });
  }

  regenerateKey(k: ApiKey): void {
    if (!k.id) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '重新生成', message: '确定要重新生成Key吗？原Key将失效。', confirmText: '确定' }
    });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.apiKeyService.regenerateKey(k.id!).subscribe(res => {
          this.snackBar.open('新Key已生成，请妥善保管', '关闭', { duration: 5000 });
          this.showKeys[res.id!] = true;
          this.loadApiKeys();
        });
      }
    });
  }

  toggleStatus(k: ApiKey): void {
    if (!k.id) return;
    this.apiKeyService.toggleStatus(k.id).subscribe(() => {
      this.snackBar.open('状态已更新', '关闭', { duration: 3000 });
      this.loadApiKeys();
    });
  }

  openDeleteDialog(k: ApiKey): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除API Key "${k.name}"吗？`, confirmText: '删除' }
    });
    ref.afterClosed().subscribe(r => {
      if (r && k.id) {
        this.apiKeyService.delete(k.id).subscribe(() => {
          this.snackBar.open('删除成功', '关闭', { duration: 3000 });
          this.loadApiKeys();
        });
      }
    });
  }
}
