import { Component, OnInit, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialog } from '@angular/material/dialog';
import { MaterialModule } from '../../shared/material.module';
import { DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import {
  TestSuite,
  TestSuiteCreateRequest,
  TestSuiteUpdateRequest,
  DebugCase
} from '../../shared/models/api-doc.model';

interface SelectedCase {
  caseId: number;
  caseName: string;
}

interface DependencyItem {
  caseId: number;
  dependsOn: number;
}

@Component({
  selector: 'app-test-suite-editor-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule, DragDropModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>
      <mat-icon>{{ data.mode === 'create' ? 'add_circle' : 'edit' }}</mat-icon>
      {{ data.mode === 'create' ? '创建测试套件' : '编辑测试套件' }}
    </h2>

    <mat-dialog-content class="dialog-content">
      <form [formGroup]="suiteForm" class="suite-form">
        <div class="form-section">
          <h3 class="section-title">基本信息</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>套件名称</mat-label>
              <input matInput formControlName="name" placeholder="请输入测试套件名称" required>
              <mat-error *ngIf="suiteForm.get('name')?.invalid">
                套件名称为必填项
              </mat-error>
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>描述</mat-label>
              <textarea matInput formControlName="description" rows="2" placeholder="请输入描述信息"></textarea>
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline" class="half-width">
              <mat-label>并发级别</mat-label>
              <input matInput type="number" formControlName="concurrencyLevel" min="1" max="10">
              <mat-error *ngIf="suiteForm.get('concurrencyLevel')?.invalid">
                并发级别必须在 1-10 之间
              </mat-error>
            </mat-form-field>
          </div>
        </div>

        <div class="form-section">
          <h3 class="section-title">用例选择</h3>
          <div class="case-selector">
            <div class="case-panel">
              <div class="panel-header">
                <span>可用用例</span>
                <span class="case-count">({{ data.availableCases.length }})</span>
              </div>
              <div class="case-list">
                <div *ngFor="let caseItem of data.availableCases" 
                     class="case-item"
                     [class.selected]="isCaseSelected(caseItem.id)">
                  <mat-checkbox 
                    [checked]="isCaseSelected(caseItem.id)"
                    (change)="toggleCaseSelection(caseItem)"
                    [disabled]="!canSelectCase(caseItem.id)">
                    <div class="case-info">
                      <span class="case-name">{{ caseItem.name }}</span>
                      <span class="case-endpoint" *ngIf="caseItem.endpointName">
                        {{ caseItem.endpointName }}
                      </span>
                    </div>
                  </mat-checkbox>
                </div>
                <div *ngIf="data.availableCases.length === 0" class="empty-state">
                  <mat-icon>inbox</mat-icon>
                  <span>暂无可用用例</span>
                </div>
              </div>
            </div>

            <div class="case-panel">
              <div class="panel-header">
                <span>已选用例</span>
                <span class="case-count">({{ selectedCases.length }})</span>
              </div>
              <div class="case-list" 
                   cdkDropList
                   [cdkDropListData]="selectedCases"
                   (cdkDropListDropped)="onDrop($event)">
                <div *ngFor="let caseItem of selectedCases; let index = index" 
                     class="case-item selected-item"
                     cdkDrag
                     [cdkDragData]="caseItem">
                  <div class="drag-handle" cdkDragHandle>
                    <mat-icon>drag_indicator</mat-icon>
                  </div>
                  <div class="case-info">
                    <span class="case-order">{{ index + 1 }}.</span>
                    <span class="case-name">{{ caseItem.caseName }}</span>
                  </div>
                  <button mat-icon-button 
                          class="remove-btn"
                          (click)="removeCase(caseItem.caseId)"
                          matTooltip="移除">
                    <mat-icon>close</mat-icon>
                  </button>
                  <div class="drag-placeholder" *cdkDragPlaceholder></div>
                </div>
                <div *ngIf="selectedCases.length === 0" class="empty-state">
                  <mat-icon>add_circle_outline</mat-icon>
                  <span>请从左侧选择用例</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3 class="section-title">依赖设置</h3>
          <div class="dependencies-header">
            <span>设置用例执行依赖关系（某个用例必须在另一个用例之后执行）</span>
            <button mat-raised-button 
                    color="primary" 
                    (click)="addDependency()"
                    [disabled]="selectedCases.length < 2">
              <mat-icon>add</mat-icon>
              添加依赖
            </button>
          </div>
          <div class="table-container">
            <table mat-table [dataSource]="dependencies" class="dependency-table">
              <ng-container matColumnDef="caseId">
                <th mat-header-cell *matHeaderCellDef>用例</th>
                <td mat-cell *matCellDef="let dep; let i = index">
                  <mat-form-field appearance="outline" class="select-full">
                    <mat-select [(value)]="dep.caseId" (valueChange)="onDependencyChange()">
                      <mat-option *ngFor="let c of selectedCases" [value]="c.caseId">
                        {{ c.caseName }}
                      </mat-option>
                    </mat-select>
                  </mat-form-field>
                </td>
              </ng-container>

              <ng-container matColumnDef="arrow">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let dep">
                  <mat-icon class="arrow-icon">arrow_forward</mat-icon>
                </td>
              </ng-container>

              <ng-container matColumnDef="dependsOn">
                <th mat-header-cell *matHeaderCellDef>依赖用例（后执行）</th>
                <td mat-cell *matCellDef="let dep">
                  <mat-form-field appearance="outline" class="select-full">
                    <mat-select [(value)]="dep.dependsOn" (valueChange)="onDependencyChange()">
                      <mat-option *ngFor="let c of getAvailableDependencies(dep.caseId)" [value]="c.caseId">
                        {{ c.caseName }}
                      </mat-option>
                    </mat-select>
                  </mat-form-field>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>操作</th>
                <td mat-cell *matCellDef="let dep; let i = index">
                  <button mat-icon-button color="warn" (click)="removeDependency(i)" matTooltip="删除">
                    <mat-icon>delete</mat-icon>
                  </button>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="dependencyColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: dependencyColumns;"></tr>
            </table>
            <div *ngIf="dependencies.length === 0" class="empty-table">
              暂无依赖设置
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3 class="section-title">全局变量</h3>
          <div class="variables-header">
            <span>设置全局变量，可在测试用例中引用</span>
            <button mat-raised-button color="primary" (click)="addVariable()">
              <mat-icon>add</mat-icon>
              添加变量
            </button>
          </div>
          <div formArrayName="globalVariables" class="variables-list">
            <div *ngFor="let varGroup of globalVariables.controls; let i = index" 
                 [formGroupName]="i" 
                 class="variable-row">
              <mat-form-field appearance="outline" class="var-key">
                <mat-label>变量名</mat-label>
                <input matInput formControlName="key" placeholder="例如: token">
              </mat-form-field>
              <mat-form-field appearance="outline" class="var-value">
                <mat-label>变量值</mat-label>
                <input matInput formControlName="value" placeholder="变量值">
              </mat-form-field>
              <button mat-icon-button color="warn" (click)="removeVariable(i)" matTooltip="删除">
                <mat-icon>delete</mat-icon>
              </button>
            </div>
            <div *ngIf="globalVariables.length === 0" class="empty-state">
              <mat-icon>data_object</mat-icon>
              <span>暂无全局变量</span>
            </div>
          </div>
        </div>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">取消</button>
      <button mat-raised-button 
              color="primary" 
              (click)="save()"
              [disabled]="suiteForm.invalid || saving">
        <mat-spinner *ngIf="saving" diameter="20" class="btn-spinner"></mat-spinner>
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-content { min-width: 700px; max-width: 1000px; }
    .suite-form { display: flex; flex-direction: column; gap: 24px; }
    .form-section { 
      padding: 16px; 
      border: 1px solid #e0e0e0; 
      border-radius: 8px; 
      background: #fafafa; 
    }
    .section-title { 
      margin: 0 0 16px 0; 
      font-size: 16px; 
      font-weight: 500; 
      color: #333; 
      display: flex; 
      align-items: center; 
      gap: 8px; 
    }
    .section-title::before { 
      content: ''; 
      width: 4px; 
      height: 20px; 
      background: #1976d2; 
      border-radius: 2px; 
    }
    .form-row { display: flex; gap: 16px; margin-bottom: 16px; }
    .form-row:last-child { margin-bottom: 0; }
    .full-width { width: 100%; }
    .half-width { width: 50%; }

    .case-selector { display: flex; gap: 16px; }
    .case-panel { 
      flex: 1; 
      border: 1px solid #e0e0e0; 
      border-radius: 8px; 
      background: white; 
      display: flex; 
      flex-direction: column; 
      min-height: 200px; 
    }
    .panel-header { 
      padding: 12px 16px; 
      background: #f5f5f5; 
      border-bottom: 1px solid #e0e0e0; 
      border-radius: 8px 8px 0 0; 
      font-weight: 500; 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
    }
    .case-count { color: #666; font-weight: normal; font-size: 14px; }
    .case-list { 
      flex: 1; 
      max-height: 250px; 
      overflow-y: auto; 
      padding: 8px; 
    }
    .case-item { 
      display: flex; 
      align-items: center; 
      padding: 8px; 
      margin-bottom: 4px; 
      border-radius: 4px; 
      cursor: pointer; 
      transition: all 0.2s; 
    }
    .case-item:hover { background: #f0f7ff; }
    .case-item.selected { background: #e3f2fd; }
    .case-item.selected-item { 
      background: #e8f5e9; 
      border: 1px solid #c8e6c9; 
      padding: 8px 12px; 
    }
    .case-item.selected-item.cdk-drag-preview { 
      box-shadow: 0 4px 20px rgba(0,0,0,0.3); 
      opacity: 0.9; 
    }
    .case-item.selected-item.cdk-drag-placeholder { 
      opacity: 0.3; 
      background: #eeeeee; 
      border: 2px dashed #999; 
    }
    .case-item.selected-item.cdk-drag-animating { 
      transition: transform 250ms cubic-bezier(0, 0, 0.2, 1); 
    }
    .drag-handle { 
      color: #999; 
      cursor: grab; 
      margin-right: 8px; 
      display: flex; 
      align-items: center; 
    }
    .drag-handle:active { cursor: grabbing; }
    .drag-placeholder { 
      min-height: 40px; 
      background: #f5f5f5; 
      border: 2px dashed #ccc; 
      border-radius: 4px; 
    }
    .case-info { 
      flex: 1; 
      display: flex; 
      flex-direction: column; 
      gap: 2px; 
    }
    .case-name { font-size: 14px; }
    .case-order { color: #1976d2; font-weight: 500; margin-right: 8px; }
    .case-endpoint { font-size: 12px; color: #888; }
    .remove-btn { opacity: 0.6; transition: opacity 0.2s; }
    .remove-btn:hover { opacity: 1; }
    .case-list.cdk-drop-list-dragging .case-item:not(.cdk-drag-placeholder) { 
      transition: transform 250ms cubic-bezier(0, 0, 0.2, 1); 
    }
    .empty-state { 
      display: flex; 
      flex-direction: column; 
      align-items: center; 
      justify-content: center; 
      padding: 40px 20px; 
      color: #999; 
      gap: 8px; 
    }

    .dependencies-header { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 16px; 
      color: #666; 
    }
    .table-container { border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden; }
    .dependency-table { width: 100%; }
    .dependency-table th { background: #f5f5f5; font-weight: 500; }
    .dependency-table th, .dependency-table td { padding: 8px 12px; }
    .select-full { width: 100%; margin-bottom: -1.25em; }
    .arrow-icon { color: #1976d2; }
    .empty-table { 
      padding: 24px; 
      text-align: center; 
      color: #999; 
      background: white; 
    }

    .variables-header { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 16px; 
      color: #666; 
    }
    .variables-list { display: flex; flex-direction: column; gap: 12px; }
    .variable-row { 
      display: flex; 
      gap: 12px; 
      align-items: flex-start; 
      padding: 12px; 
      background: white; 
      border: 1px solid #e0e0e0; 
      border-radius: 8px; 
    }
    .var-key { width: 200px; flex-shrink: 0; }
    .var-value { flex: 1; }

    .btn-spinner { margin-right: 8px; }
  `]
})
export class TestSuiteEditorDialogComponent implements OnInit {
  suiteForm!: FormGroup;
  selectedCases: SelectedCase[] = [];
  dependencies: DependencyItem[] = [];
  dependencyColumns = ['caseId', 'arrow', 'dependsOn', 'actions'];
  saving = false;

  constructor(
    public dialogRef: MatDialogRef<TestSuiteEditorDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { 
      mode: 'create' | 'edit'; 
      suite?: TestSuite; 
      applicationId: number; 
      availableCases: DebugCase[];
    },
    private fb: FormBuilder,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.initForm();
    if (this.data.mode === 'edit' && this.data.suite) {
      this.loadSuiteData(this.data.suite);
    }
  }

  initForm(): void {
    this.suiteForm = this.fb.group({
      name: ['', [Validators.required]],
      description: [''],
      concurrencyLevel: [1, [Validators.required, Validators.min(1), Validators.max(10)]],
      globalVariables: this.fb.array([])
    });
  }

  get globalVariables(): FormArray {
    return this.suiteForm.get('globalVariables') as FormArray;
  }

  loadSuiteData(suite: TestSuite): void {
    this.suiteForm.patchValue({
      name: suite.name,
      description: suite.description || '',
      concurrencyLevel: suite.concurrencyLevel
    });

    this.selectedCases = (suite.caseOrder || []).map(item => ({
      caseId: item.caseId,
      caseName: item.caseName || this.getCaseNameById(item.caseId)
    }));

    this.dependencies = (suite.dependencies || []).map(dep => ({
      caseId: dep.caseId,
      dependsOn: dep.dependsOn
    }));

    if (suite.globalVariables) {
      Object.entries(suite.globalVariables).forEach(([key, value]) => {
        this.addVariable(key, value);
      });
    }
  }

  getCaseNameById(caseId: number): string {
    const debugCase = this.data.availableCases.find(c => c.id === caseId);
    return debugCase?.name || `用例 ${caseId}`;
  }

  isCaseSelected(caseId: number): boolean {
    return this.selectedCases.some(c => c.caseId === caseId);
  }

  canSelectCase(caseId: number): boolean {
    return true;
  }

  toggleCaseSelection(caseItem: DebugCase): void {
    if (this.isCaseSelected(caseItem.id)) {
      this.removeCase(caseItem.id);
    } else {
      this.selectedCases.push({
        caseId: caseItem.id,
        caseName: caseItem.name
      });
    }
  }

  removeCase(caseId: number): void {
    this.selectedCases = this.selectedCases.filter(c => c.caseId !== caseId);
    this.dependencies = this.dependencies.filter(
      d => d.caseId !== caseId && d.dependsOn !== caseId
    );
  }

  onDrop(event: any): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(
        this.selectedCases,
        event.previousIndex,
        event.currentIndex
      );
    }
  }

  addDependency(): void {
    if (this.selectedCases.length < 2) return;
    this.dependencies.push({
      caseId: this.selectedCases[0].caseId,
      dependsOn: this.selectedCases[1].caseId
    });
  }

  removeDependency(index: number): void {
    this.dependencies.splice(index, 1);
  }

  onDependencyChange(): void {
    // 可以在这里添加验证逻辑
  }

  getAvailableDependencies(currentCaseId: number): SelectedCase[] {
    return this.selectedCases.filter(c => c.caseId !== currentCaseId);
  }

  addVariable(key: string = '', value: string = ''): void {
    this.globalVariables.push(
      this.fb.group({
        key: [key],
        value: [value]
      })
    );
  }

  removeVariable(index: number): void {
    this.globalVariables.removeAt(index);
  }

  save(): void {
    if (this.suiteForm.invalid) return;

    this.saving = true;

    const caseOrder = this.selectedCases.map(c => ({ caseId: c.caseId }));
    const dependencies = this.dependencies
      .filter(d => d.caseId && d.dependsOn)
      .map(d => ({ caseId: d.caseId, dependsOn: d.dependsOn }));

    const globalVariablesObj: { [key: string]: any } = {};
    this.globalVariables.controls.forEach(control => {
      const key = control.get('key')?.value;
      const value = control.get('value')?.value;
      if (key) {
        globalVariablesObj[key] = this.parseValue(value);
      }
    });

    let result: TestSuiteCreateRequest | TestSuiteUpdateRequest;

    if (this.data.mode === 'create') {
      result = {
        name: this.suiteForm.value.name,
        description: this.suiteForm.value.description,
        applicationId: this.data.applicationId,
        caseOrder,
        dependencies,
        globalVariables: Object.keys(globalVariablesObj).length > 0 ? globalVariablesObj : undefined,
        concurrencyLevel: this.suiteForm.value.concurrencyLevel
      };
    } else {
      result = {
        name: this.suiteForm.value.name,
        description: this.suiteForm.value.description,
        caseOrder,
        dependencies,
        globalVariables: Object.keys(globalVariablesObj).length > 0 ? globalVariablesObj : undefined,
        concurrencyLevel: this.suiteForm.value.concurrencyLevel
      };
    }

    setTimeout(() => {
      this.saving = false;
      this.dialogRef.close(result);
    }, 500);
  }

  private parseValue(value: string): any {
    if (value === 'true') return true;
    if (value === 'false') return false;
    if (value === 'null') return null;
    if (value === '' || value === undefined) return '';
    
    const num = Number(value);
    if (!isNaN(num) && value.trim() !== '') return num;
    
    try {
      return JSON.parse(value);
    } catch {
      return value;
    }
  }
}
