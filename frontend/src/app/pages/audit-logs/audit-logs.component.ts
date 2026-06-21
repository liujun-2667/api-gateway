import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup } from '@angular/forms';
import { PageEvent } from '@angular/material/paginator';
import { MaterialModule } from '../../shared/material.module';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AuditService } from '../../core/services/audit.service';
import { AuditLog, AuditLogPageResponse, OperationType } from '../../shared/models/audit-log.model';

@Component({
  selector: 'app-audit-logs',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="审计日志" subtitle="查询系统操作审计日志" icon="receipt_long">
    </app-page-header>

    <mat-card>
      <form [formGroup]="filterForm" class="filter-form">
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>操作类型</mat-label>
            <mat-select formControlName="operationType">
              <mat-option [value]="null">全部</mat-option>
              <mat-option *ngFor="let t of operationTypes" [value]="t">{{ t }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>实体类型</mat-label>
            <input matInput formControlName="entityType" placeholder="TENANT, APPLICATION...">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>操作用户</mat-label>
            <input matInput formControlName="username" placeholder="用户名">
          </mat-form-field>
        </div>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>开始日期</mat-label>
            <input matInput [matDatepicker]="startPicker" formControlName="startDate">
            <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
            <mat-datepicker #startPicker></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>结束日期</mat-label>
            <input matInput [matDatepicker]="endPicker" formControlName="endDate">
            <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
            <mat-datepicker #endPicker></mat-datepicker>
          </mat-form-field>
          <div class="actions">
            <button mat-raised-button color="primary" (click)="search()">
              <mat-icon>search</mat-icon>
              查询
            </button>
            <button mat-button (click)="reset()">重置</button>
          </div>
        </div>
      </form>

      <div class="table-container">
        <table mat-table [dataSource]="logs" matSort>
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>ID</th>
            <td mat-cell *matCellDef="let l">{{ l.id }}</td>
          </ng-container>
          <ng-container matColumnDef="operationType">
            <th mat-header-cell *matHeaderCellDef>操作类型</th>
            <td mat-cell *matCellDef="let l">
              <mat-chip>{{ l.operationType }}</mat-chip>
            </td>
          </ng-container>
          <ng-container matColumnDef="entityType">
            <th mat-header-cell *matHeaderCellDef>实体类型</th>
            <td mat-cell *matCellDef="let l">{{ l.entityType }}</td>
          </ng-container>
          <ng-container matColumnDef="entityId">
            <th mat-header-cell *matHeaderCellDef>实体ID</th>
            <td mat-cell *matCellDef="let l">{{ l.entityId || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="username">
            <th mat-header-cell *matHeaderCellDef>操作用户</th>
            <td mat-cell *matCellDef="let l">{{ l.username || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="ipAddress">
            <th mat-header-cell *matHeaderCellDef>IP地址</th>
            <td mat-cell *matCellDef="let l">{{ l.ipAddress || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="details">
            <th mat-header-cell *matHeaderCellDef>详情</th>
            <td mat-cell *matCellDef="let l" class="details-cell">
              <span *ngIf="l.details; else noDetails">{{ l.details }}</span>
              <ng-template #noDetails>-</ng-template>
            </td>
          </ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>操作时间</th>
            <td mat-cell *matCellDef="let l">{{ l.createdAt | date:'yyyy-MM-dd HH:mm:ss' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
      </div>
      <mat-paginator [length]="totalElements" [pageSize]="pageSize" [pageIndex]="pageIndex"
        [pageSizeOptions]="[10, 20, 50, 100]" (page)="onPageChange($event)"></mat-paginator>
    </mat-card>
  `,
  styles: [`
    .filter-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      margin-bottom: 24px;
    }
    .form-row {
      display: flex;
      gap: 16px;
      align-items: center;
    }
    .form-row > * {
      flex: 1;
    }
    .form-row .actions {
      flex: 0 0 auto;
      display: flex;
      gap: 8px;
    }
    .details-cell {
      max-width: 300px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `]
})
export class AuditLogsComponent implements OnInit {
  logs: AuditLog[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'operationType', 'entityType', 'entityId', 'username', 'ipAddress', 'details', 'createdAt'];
  operationTypes = Object.values(OperationType);

  filterForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private auditService: AuditService
  ) {
    this.filterForm = this.fb.group({
      operationType: [null],
      entityType: [''],
      username: [''],
      startDate: [null],
      endDate: [null]
    });
  }

  ngOnInit(): void {
    this.loadLogs();
  }

  loadLogs(): void {
    const value = this.filterForm.value;
    this.auditService.getAuditLogs(this.pageIndex, this.pageSize, {
      operationType: value.operationType || undefined,
      entityType: value.entityType || undefined,
      username: value.username || undefined,
      startDate: value.startDate ? value.startDate.toISOString() : undefined,
      endDate: value.endDate ? value.endDate.toISOString() : undefined
    }).subscribe((res: AuditLogPageResponse) => {
      this.logs = res.content;
      this.totalElements = res.totalElements;
    });
  }

  search(): void {
    this.pageIndex = 0;
    this.loadLogs();
  }

  reset(): void {
    this.filterForm.reset({
      operationType: null,
      entityType: '',
      username: '',
      startDate: null,
      endDate: null
    });
    this.pageIndex = 0;
    this.loadLogs();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadLogs();
  }
}
