import { Component, OnInit, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MaterialModule } from '../../shared/material.module';
import { ApiDocService } from '../../core/services/api-doc.service';
import {
  VersionCompareResponse,
  SchemaDiff,
  ChangeRemark,
  ChangeRemarkRequest
} from '../../shared/models/api-doc.model';

interface DiffDisplayItem {
  path: string;
  field: string;
  changeType: string;
  leftValue: any;
  rightValue: any;
  leftType?: string;
  rightType?: string;
  remarks: ChangeRemark[];
}

@Component({
  selector: 'app-add-remark-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>添加备注</h2>
    <mat-dialog-content>
      <form [formGroup]="remarkForm" class="remark-form">
        <div class="field-path">
          <strong>字段:</strong> {{ data.fieldName }}
        </div>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>备注类型</mat-label>
          <mat-select formControlName="remarkType">
            <mat-option value="COMPATIBLE">已确认兼容</mat-option>
            <mat-option value="NEEDS_ADAPTATION">需要适配</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>备注说明</mat-label>
          <textarea matInput formControlName="remark" rows="3" placeholder="可选，输入详细说明"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">取消</button>
      <button mat-raised-button color="primary" 
              (click)="save()" 
              [disabled]="remarkForm.invalid || saving">
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .remark-form { display: flex; flex-direction: column; gap: 16px; min-width: 400px; }
    .field-path { margin-bottom: 12px; padding: 8px; background: #f5f5f5; border-radius: 4px; }
    .full-width { width: 100%; }
  `]
})
export class AddRemarkDialogComponent {
  remarkForm: FormGroup;
  saving = false;

  constructor(
    public dialogRef: MatDialogRef<AddRemarkDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { fieldName: string; fieldPath: string; changeRecordId: number },
    private fb: FormBuilder,
    private apiDocService: ApiDocService,
    private snackBar: MatSnackBar
  ) {
    this.remarkForm = this.fb.group({
      remarkType: ['', [Validators.required]],
      remark: ['']
    });
  }

  save(): void {
    if (this.remarkForm.invalid) return;
    
    this.saving = true;
    const request: ChangeRemarkRequest = {
      fieldPath: this.data.fieldPath,
      remarkType: this.remarkForm.value.remarkType as 'COMPATIBLE' | 'NEEDS_ADAPTATION',
      remark: this.remarkForm.value.remark
    };

    this.apiDocService.addRemark(this.data.changeRecordId, request).subscribe({
      next: (remark) => {
        this.saving = false;
        this.snackBar.open('备注已保存', '关闭', { duration: 3000 });
        this.dialogRef.close(remark);
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open('保存失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }
}

@Component({
  selector: 'app-version-compare-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>
      <mat-icon>compare_arrows</mat-icon>
      接口版本对比
    </h2>

    <mat-dialog-content class="dialog-content" *ngIf="compareResult">
      <div class="compare-header">
        <div class="version-info left">
          <div class="version-label">版本 A (旧)</div>
          <div class="version-time">{{ compareResult.leftTimestamp | date:'yyyy-MM-dd HH:mm:ss' }}</div>
          <div class="version-user">{{ compareResult.leftChangedBy || '系统' }}</div>
        </div>
        <div class="compare-icon">
          <mat-icon>arrow_forward</mat-icon>
        </div>
        <div class="version-info right">
          <div class="version-label">版本 B (新)</div>
          <div class="version-time">{{ compareResult.rightTimestamp | date:'yyyy-MM-dd HH:mm:ss' }}</div>
          <div class="version-user">{{ compareResult.rightChangedBy || '系统' }}</div>
        </div>
      </div>

      <mat-tab-group [(selectedIndex)]="diffTabIndex">
        <mat-tab label="请求参数 Schema">
          <div class="diff-container">
            <div *ngIf="requestDiffItems.length === 0" class="no-diff">
              <mat-icon>check_circle</mat-icon>
              <span>请求参数无差异</span>
            </div>
            <div *ngFor="let item of requestDiffItems" 
                 class="diff-row"
                 [ngClass]="getDiffClass(item.changeType)">
              <div class="diff-left">
                <div class="diff-field">{{ item.field }}</div>
                <div class="diff-type" *ngIf="item.leftType">类型: {{ item.leftType }}</div>
                <div class="diff-value" *ngIf="item.changeType !== 'ADD'">
                  <pre>{{ formatValue(item.leftValue) }}</pre>
                </div>
              </div>
              <div class="diff-indicator">
                <mat-icon>{{ getDiffIcon(item.changeType) }}</mat-icon>
              </div>
              <div class="diff-right">
                <div class="diff-field">{{ item.field }}</div>
                <div class="diff-type" *ngIf="item.rightType">类型: {{ item.rightType }}</div>
                <div class="diff-value" *ngIf="item.changeType !== 'REMOVE'">
                  <pre>{{ formatValue(item.rightValue) }}</pre>
                </div>
              </div>
              <div class="diff-actions">
                <button mat-icon-button (click)="openAddRemarkDialog(item)" matTooltip="添加备注">
                  <mat-icon>comment</mat-icon>
                </button>
                <span class="remark-count" *ngIf="item.remarks.length > 0">
                  {{ item.remarks.length }}
                </span>
              </div>
            </div>
          </div>
        </mat-tab>

        <mat-tab label="响应 Schema">
          <div class="diff-container">
            <div *ngIf="responseDiffItems.length === 0" class="no-diff">
              <mat-icon>check_circle</mat-icon>
              <span>响应Schema无差异</span>
            </div>
            <div *ngFor="let item of responseDiffItems" 
                 class="diff-row"
                 [ngClass]="getDiffClass(item.changeType)">
              <div class="diff-left">
                <div class="diff-field">{{ item.field }}</div>
                <div class="diff-type" *ngIf="item.leftType">类型: {{ item.leftType }}</div>
                <div class="diff-value" *ngIf="item.changeType !== 'ADD'">
                  <pre>{{ formatValue(item.leftValue) }}</pre>
                </div>
              </div>
              <div class="diff-indicator">
                <mat-icon>{{ getDiffIcon(item.changeType) }}</mat-icon>
              </div>
              <div class="diff-right">
                <div class="diff-field">{{ item.field }}</div>
                <div class="diff-type" *ngIf="item.rightType">类型: {{ item.rightType }}</div>
                <div class="diff-value" *ngIf="item.changeType !== 'REMOVE'">
                  <pre>{{ formatValue(item.rightValue) }}</pre>
                </div>
              </div>
              <div class="diff-actions">
                <button mat-icon-button (click)="openAddRemarkDialog(item)" matTooltip="添加备注">
                  <mat-icon>comment</mat-icon>
                </button>
                <span class="remark-count" *ngIf="item.remarks.length > 0">
                  {{ item.remarks.length }}
                </span>
              </div>
            </div>
          </div>
        </mat-tab>
      </mat-tab-group>

      <div class="legend">
        <div class="legend-item">
          <span class="legend-color add"></span>
          <span>新增</span>
        </div>
        <div class="legend-item">
          <span class="legend-color remove"></span>
          <span>删除</span>
        </div>
        <div class="legend-item">
          <span class="legend-color type-change"></span>
          <span>类型变化</span>
        </div>
        <div class="legend-item">
          <span class="legend-color modify"></span>
          <span>修改</span>
        </div>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">关闭</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-content { min-width: 900px; max-width: 1200px; min-height: 500px; }
    .compare-header { 
      display: flex; align-items: center; gap: 16px; 
      padding: 16px; background: #f5f5f5; border-radius: 8px; margin-bottom: 16px; 
    }
    .version-info { flex: 1; text-align: center; }
    .version-info.left { text-align: left; }
    .version-info.right { text-align: right; }
    .version-label { font-size: 12px; color: #666; margin-bottom: 4px; }
    .version-time { font-size: 14px; font-weight: 500; }
    .version-user { font-size: 12px; color: #888; margin-top: 2px; }
    .compare-icon { color: #999; }
    .diff-container { max-height: 400px; overflow-y: auto; }
    .no-diff { 
      display: flex; align-items: center; justify-content: center; gap: 8px; 
      padding: 40px; color: #4caf50; 
    }
    .diff-row { 
      display: flex; gap: 8px; padding: 12px; border-radius: 4px; margin-bottom: 8px; 
      border: 1px solid #e0e0e0; 
    }
    .diff-row.add { background: #e8f5e9; border-color: #c8e6c9; }
    .diff-row.remove { background: #ffebee; border-color: #ffcdd2; }
    .diff-row.type-change { background: #fff3e0; border-color: #ffe0b2; }
    .diff-row.modify { background: #fff8e1; border-color: #ffecb3; }
    .diff-left, .diff-right { flex: 1; min-width: 0; }
    .diff-indicator { display: flex; align-items: center; color: #666; }
    .diff-field { font-weight: 500; font-family: monospace; margin-bottom: 4px; }
    .diff-type { font-size: 12px; color: #666; margin-bottom: 4px; }
    .diff-value pre { 
      margin: 0; padding: 8px; background: rgba(0,0,0,0.05); 
      border-radius: 4px; font-size: 12px; max-height: 100px; overflow: auto; 
    }
    .diff-actions { display: flex; align-items: flex-start; gap: 4px; }
    .remark-count { 
      background: #ff9800; color: white; border-radius: 10px; 
      padding: 2px 6px; font-size: 11px; font-weight: 500; 
    }
    .legend { display: flex; gap: 16px; margin-top: 16px; padding-top: 16px; border-top: 1px solid #e0e0e0; }
    .legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; }
    .legend-color { width: 16px; height: 16px; border-radius: 3px; }
    .legend-color.add { background: #e8f5e9; }
    .legend-color.remove { background: #ffebee; }
    .legend-color.type-change { background: #fff3e0; }
    .legend-color.modify { background: #fff8e1; }
  `]
})
export class VersionCompareDialogComponent implements OnInit {
  compareResult!: VersionCompareResponse;
  diffTabIndex = 0;
  requestDiffItems: DiffDisplayItem[] = [];
  responseDiffItems: DiffDisplayItem[] = [];
  remarksMap = new Map<string, ChangeRemark[]>();
  leftRecordId!: number;
  rightRecordId!: number;

  constructor(
    public dialogRef: MatDialogRef<VersionCompareDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { compareResult: VersionCompareResponse; leftRecordId: number; rightRecordId: number },
    private apiDocService: ApiDocService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.compareResult = this.data.compareResult;
    this.leftRecordId = this.data.leftRecordId;
    this.rightRecordId = this.data.rightRecordId;
    this.loadRemarks();
  }

  loadRemarks(): void {
    this.apiDocService.getRemarks(this.leftRecordId).subscribe({
      next: (remarks) => {
        remarks.forEach(r => {
          const list = this.remarksMap.get(r.fieldPath) || [];
          list.push(r);
          this.remarksMap.set(r.fieldPath, list);
        });
        this.buildDiffItems();
      }
    });
    this.apiDocService.getRemarks(this.rightRecordId).subscribe({
      next: (remarks) => {
        remarks.forEach(r => {
          const list = this.remarksMap.get(r.fieldPath) || [];
          list.push(r);
          this.remarksMap.set(r.fieldPath, list);
        });
        this.buildDiffItems();
      }
    });
  }

  buildDiffItems(): void {
    this.requestDiffItems = (this.compareResult.requestSchemaDiff || []).map(d => 
      this.toDisplayItem(d));
    this.responseDiffItems = (this.compareResult.responseSchemaDiff || []).map(d => 
      this.toDisplayItem(d));
  }

  toDisplayItem(diff: SchemaDiff): DiffDisplayItem {
    return {
      path: diff.path,
      field: diff.field,
      changeType: diff.changeType,
      leftValue: diff.oldValue,
      rightValue: diff.newValue,
      leftType: diff.oldType,
      rightType: diff.newType,
      remarks: this.remarksMap.get(diff.path) || []
    };
  }

  getDiffClass(changeType: string): string {
    return changeType.toLowerCase().replace('_', '-');
  }

  getDiffIcon(changeType: string): string {
    switch (changeType) {
      case 'ADD': return 'add';
      case 'REMOVE': return 'remove';
      case 'TYPE_CHANGE': return 'swap_horiz';
      case 'MODIFY': return 'edit';
      default: return 'help';
    }
  }

  formatValue(value: any): string {
    if (!value) return '';
    try {
      return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    } catch (e) {
      return String(value);
    }
  }

  openAddRemarkDialog(item: DiffDisplayItem): void {
    const dialogRef = this.dialog.open(AddRemarkDialogComponent, {
      data: {
        fieldName: item.field,
        fieldPath: item.path,
        changeRecordId: this.rightRecordId
      },
      width: '500px'
    });

    dialogRef.afterClosed().subscribe((result: ChangeRemark) => {
      if (result) {
        const list = this.remarksMap.get(result.fieldPath) || [];
        list.unshift(result);
        this.remarksMap.set(result.fieldPath, list);
        this.buildDiffItems();
      }
    });
  }
}
