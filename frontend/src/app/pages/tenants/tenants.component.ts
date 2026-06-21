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
import { Tenant, TenantPageResponse } from '../../shared/models/tenant.model';

@Component({
  selector: 'app-tenant-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>{{ data?.tenant ? '编辑租户' : '创建租户' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="tenantForm" class="form-container">
        <mat-form-field appearance="outline">
          <mat-label>租户名称</mat-label>
          <input matInput formControlName="name" placeholder="请输入租户名称">
          <mat-error *ngIf="tenantForm.get('name')?.hasError('required')">必填</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>租户编码</mat-label>
          <input matInput formControlName="code" placeholder="请输入租户编码">
          <mat-error *ngIf="tenantForm.get('code')?.hasError('required')">必填</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>描述</mat-label>
          <textarea matInput formControlName="description" rows="3" placeholder="请输入描述"></textarea>
        </mat-form-field>
        <mat-slide-toggle formControlName="active">启用</mat-slide-toggle>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="tenantForm.invalid" (click)="onSubmit()">
        保存
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form-container {
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-width: 400px;
      padding-top: 16px;
    }
  `]
})
export class TenantDialogComponent {
  tenantForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<TenantDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { tenant?: Tenant }
  ) {
    this.tenantForm = this.fb.group({
      name: [data?.tenant?.name || '', [Validators.required]],
      code: [data?.tenant?.code || '', [Validators.required]],
      description: [data?.tenant?.description || ''],
      active: [data?.tenant?.active ?? true]
    });
  }

  onSubmit(): void {
    if (this.tenantForm.valid) {
      this.dialogRef.close(this.tenantForm.value);
    }
  }
}

@Component({
  selector: 'app-tenants',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="租户管理" subtitle="管理系统中的所有租户" icon="business">
      <button mat-raised-button color="primary" (click)="openCreateDialog()">
        <mat-icon>add</mat-icon>
        新建租户
      </button>
    </app-page-header>

    <mat-card>
      <div class="table-container">
        <table mat-table [dataSource]="tenants" matSort>
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>ID</th>
            <td mat-cell *matCellDef="let t">{{ t.id }}</td>
          </ng-container>
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>名称</th>
            <td mat-cell *matCellDef="let t">{{ t.name }}</td>
          </ng-container>
          <ng-container matColumnDef="code">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>编码</th>
            <td mat-cell *matCellDef="let t">{{ t.code }}</td>
          </ng-container>
          <ng-container matColumnDef="description">
            <th mat-header-cell *matHeaderCellDef>描述</th>
            <td mat-cell *matCellDef="let t">{{ t.description || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="active">
            <th mat-header-cell *matHeaderCellDef>状态</th>
            <td mat-cell *matCellDef="let t">
              <span [ngClass]="t.active ? 'status-active' : 'status-inactive'">
                {{ t.active ? '启用' : '禁用' }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>创建时间</th>
            <td mat-cell *matCellDef="let t">{{ t.createdAt | date:'yyyy-MM-dd HH:mm' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>操作</th>
            <td mat-cell *matCellDef="let t">
              <button mat-icon-button color="primary" (click)="openEditDialog(t)">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="openDeleteDialog(t)">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
      </div>

      <mat-paginator
        [length]="totalElements"
        [pageSize]="pageSize"
        [pageIndex]="pageIndex"
        [pageSizeOptions]="[10, 20, 50, 100]"
        (page)="onPageChange($event)">
      </mat-paginator>
    </mat-card>
  `
})
export class TenantsComponent implements OnInit {
  tenants: Tenant[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'name', 'code', 'description', 'active', 'createdAt', 'actions'];

  constructor(
    private tenantService: TenantService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenants();
  }

  loadTenants(): void {
    this.tenantService.getTenants(this.pageIndex, this.pageSize).subscribe({
      next: (res: TenantPageResponse) => {
        this.tenants = res.content;
        this.totalElements = res.totalElements;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadTenants();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(TenantDialogComponent, {
      data: {}
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.tenantService.create(result).subscribe({
          next: () => {
            this.snackBar.open('创建成功', '关闭', { duration: 3000 });
            this.loadTenants();
          }
        });
      }
    });
  }

  openEditDialog(tenant: Tenant): void {
    const dialogRef = this.dialog.open(TenantDialogComponent, {
      data: { tenant }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result && tenant.id) {
        this.tenantService.update(tenant.id, result).subscribe({
          next: () => {
            this.snackBar.open('更新成功', '关闭', { duration: 3000 });
            this.loadTenants();
          }
        });
      }
    });
  }

  openDeleteDialog(tenant: Tenant): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: '确认删除',
        message: `确定要删除租户"${tenant.name}"吗？此操作不可撤销。`,
        confirmText: '删除'
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result && tenant.id) {
        this.tenantService.delete(tenant.id).subscribe({
          next: () => {
            this.snackBar.open('删除成功', '关闭', { duration: 3000 });
            this.loadTenants();
          }
        });
      }
    });
  }
}
