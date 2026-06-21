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
import { Tenant } from '../../shared/models/tenant.model';
import { Application, ApplicationPageResponse } from '../../shared/models/application.model';

@Component({
  selector: 'app-application-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>{{ data?.application ? '编辑应用' : '创建应用' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="appForm" class="form-container">
        <mat-form-field appearance="outline">
          <mat-label>所属租户</mat-label>
          <mat-select formControlName="tenantId">
            <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
          </mat-select>
          <mat-error *ngIf="appForm.get('tenantId')?.hasError('required')">必填</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>应用名称</mat-label>
          <input matInput formControlName="name" placeholder="请输入应用名称">
          <mat-error *ngIf="appForm.get('name')?.hasError('required')">必填</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>应用编码</mat-label>
          <input matInput formControlName="code" placeholder="请输入应用编码">
          <mat-error *ngIf="appForm.get('code')?.hasError('required')">必填</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>基础URL</mat-label>
          <input matInput formControlName="baseUrl" placeholder="http://example.com">
          <mat-error *ngIf="appForm.get('baseUrl')?.hasError('required')">必填</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>描述</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea>
        </mat-form-field>
        <mat-slide-toggle formControlName="active">启用</mat-slide-toggle>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="appForm.invalid" (click)="onSubmit()">
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
export class ApplicationDialogComponent implements OnInit {
  appForm: FormGroup;
  tenants: Tenant[] = [];

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    public dialogRef: MatDialogRef<ApplicationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { application?: Application }
  ) {
    this.appForm = this.fb.group({
      tenantId: [data?.application?.tenantId || '', [Validators.required]],
      name: [data?.application?.name || '', [Validators.required]],
      code: [data?.application?.code || '', [Validators.required]],
      baseUrl: [data?.application?.baseUrl || '', [Validators.required]],
      description: [data?.application?.description || ''],
      active: [data?.application?.active ?? true]
    });
  }

  ngOnInit(): void {
    this.tenantService.getAllTenants().subscribe({
      next: (res) => { this.tenants = res; }
    });
  }

  onSubmit(): void {
    if (this.appForm.valid) {
      this.dialogRef.close(this.appForm.value);
    }
  }
}

@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="应用管理" subtitle="管理各租户下的应用" icon="apps">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>选择租户</mat-label>
        <mat-select [value]="selectedTenantId" (selectionChange)="onTenantChange($event.value)">
          <mat-option [value]="null">全部租户</mat-option>
          <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-raised-button color="primary" (click)="openCreateDialog()">
        <mat-icon>add</mat-icon>
        新建应用
      </button>
    </app-page-header>

    <mat-card>
      <div class="table-container">
        <table mat-table [dataSource]="applications" matSort>
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>ID</th>
            <td mat-cell *matCellDef="let a">{{ a.id }}</td>
          </ng-container>
          <ng-container matColumnDef="tenantName">
            <th mat-header-cell *matHeaderCellDef>所属租户</th>
            <td mat-cell *matCellDef="let a">{{ a.tenantName }}</td>
          </ng-container>
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>名称</th>
            <td mat-cell *matCellDef="let a">{{ a.name }}</td>
          </ng-container>
          <ng-container matColumnDef="code">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>编码</th>
            <td mat-cell *matCellDef="let a">{{ a.code }}</td>
          </ng-container>
          <ng-container matColumnDef="baseUrl">
            <th mat-header-cell *matHeaderCellDef>基础URL</th>
            <td mat-cell *matCellDef="let a">{{ a.baseUrl }}</td>
          </ng-container>
          <ng-container matColumnDef="active">
            <th mat-header-cell *matHeaderCellDef>状态</th>
            <td mat-cell *matCellDef="let a">
              <span [ngClass]="a.active ? 'status-active' : 'status-inactive'">
                {{ a.active ? '启用' : '禁用' }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>创建时间</th>
            <td mat-cell *matCellDef="let a">{{ a.createdAt | date:'yyyy-MM-dd HH:mm' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>操作</th>
            <td mat-cell *matCellDef="let a">
              <button mat-icon-button color="primary" (click)="openEditDialog(a)">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="openDeleteDialog(a)">
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
    .filter-field {
      width: 200px;
      margin-right: 12px;
    }
  `]
})
export class ApplicationsComponent implements OnInit {
  applications: Application[] = [];
  tenants: Tenant[] = [];
  selectedTenantId: number | null = null;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'tenantName', 'name', 'code', 'baseUrl', 'active', 'createdAt', 'actions'];

  constructor(
    private applicationService: ApplicationService,
    private tenantService: TenantService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenants();
    this.loadApplications();
  }

  loadTenants(): void {
    this.tenantService.getAllTenants().subscribe({
      next: (res) => { this.tenants = res; }
    });
  }

  loadApplications(): void {
    this.applicationService.getApplications(
      this.selectedTenantId || undefined,
      this.pageIndex,
      this.pageSize
    ).subscribe({
      next: (res: ApplicationPageResponse) => {
        this.applications = res.content;
        this.totalElements = res.totalElements;
      }
    });
  }

  onTenantChange(tenantId: number | null): void {
    this.selectedTenantId = tenantId;
    this.pageIndex = 0;
    this.loadApplications();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadApplications();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(ApplicationDialogComponent, { data: {} });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.applicationService.create(result).subscribe({
          next: () => {
            this.snackBar.open('创建成功', '关闭', { duration: 3000 });
            this.loadApplications();
          }
        });
      }
    });
  }

  openEditDialog(app: Application): void {
    const dialogRef = this.dialog.open(ApplicationDialogComponent, { data: { application: app } });
    dialogRef.afterClosed().subscribe(result => {
      if (result && app.id) {
        this.applicationService.update(app.id, result).subscribe({
          next: () => {
            this.snackBar.open('更新成功', '关闭', { duration: 3000 });
            this.loadApplications();
          }
        });
      }
    });
  }

  openDeleteDialog(app: Application): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除应用"${app.name}"吗？`, confirmText: '删除' }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result && app.id) {
        this.applicationService.delete(app.id).subscribe({
          next: () => {
            this.snackBar.open('删除成功', '关闭', { duration: 3000 });
            this.loadApplications();
          }
        });
      }
    });
  }
}
