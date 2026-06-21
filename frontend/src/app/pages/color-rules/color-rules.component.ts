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
import { ColorRuleService } from '../../core/services/color-rule.service';
import { Tenant } from '../../shared/models/tenant.model';
import { Application } from '../../shared/models/application.model';
import {
  TrafficColorRule, TrafficColorRulePageResponse,
  TrafficConditionType, ColorTagOperation
} from '../../shared/models/color-rule.model';

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
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="染色规则" subtitle="对流量进行染色标记，用于灰度发布等场景" icon="palette">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>选择租户</mat-label>
        <mat-select [value]="selectedTenantId" (selectionChange)="onTenantChange($event.value)">
          <mat-option [value]="null">全部</mat-option>
          <mat-option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</mat-option>
        </mat-select>
      </mat-form-field>
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
  styles: [`.filter-field { width: 200px; margin-right: 12px; }`]
})
export class ColorRulesComponent implements OnInit {
  rules: TrafficColorRule[] = [];
  tenants: Tenant[] = [];
  selectedTenantId: number | null = null;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'name', 'tenantName', 'colorTag', 'condition', 'operation', 'priority', 'enabled', 'actions'];

  constructor(
    private colorRuleService: ColorRuleService,
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
    this.colorRuleService.getColorRules(this.selectedTenantId || undefined, undefined, this.pageIndex, this.pageSize)
      .subscribe((res: TrafficColorRulePageResponse) => {
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

  openCreateDialog(): void {
    const ref = this.dialog.open(ColorRuleDialogComponent, { data: {} });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.colorRuleService.create(r).subscribe(() => {
          this.snackBar.open('创建成功', '关闭', { duration: 3000 });
          this.loadRules();
        });
      }
    });
  }

  openEditDialog(rule: TrafficColorRule): void {
    const ref = this.dialog.open(ColorRuleDialogComponent, { data: { rule } });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.colorRuleService.update(rule.id, r).subscribe(() => {
          this.snackBar.open('更新成功', '关闭', { duration: 3000 });
          this.loadRules();
        });
      }
    });
  }

  toggle(rule: TrafficColorRule): void {
    if (!rule.id) return;
    this.colorRuleService.toggle(rule.id).subscribe(() => this.loadRules());
  }

  applyAll(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '全量应用', message: '确定要将所有染色规则全量应用到网关吗？', confirmText: '确定' }
    });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.colorRuleService.applyAll().subscribe(() => {
          this.snackBar.open('已全量应用', '关闭', { duration: 3000 });
        });
      }
    });
  }

  clearAll(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '清除全部', message: '确定要清除所有已应用的染色规则吗？', confirmText: '确定' }
    });
    ref.afterClosed().subscribe(r => {
      if (r) {
        this.colorRuleService.clearAll().subscribe(() => {
          this.snackBar.open('已清除全部', '关闭', { duration: 3000 });
        });
      }
    });
  }

  openDeleteDialog(rule: TrafficColorRule): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { title: '确认删除', message: `确定要删除规则 "${rule.name}"吗？`, confirmText: '删除' }
    });
    ref.afterClosed().subscribe(r => {
      if (r && rule.id) {
        this.colorRuleService.delete(rule.id).subscribe(() => {
          this.snackBar.open('删除成功', '关闭', { duration: 3000 });
          this.loadRules();
        });
      }
    });
  }
}
