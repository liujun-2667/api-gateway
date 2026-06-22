import { Component, OnInit, OnDestroy, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, FormControl } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MaterialModule } from '../../shared/material.module';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ApiDocService } from '../../core/services/api-doc.service';
import { ApplicationService } from '../../core/services/application.service';
import { RouteRuleService } from '../../core/services/route-rule.service';
import { Application } from '../../shared/models/application.model';
import {
  ApiDoc, ApiDocGroup, ApiEndpoint, MockConfig,
  DebugCase, ApiChangeRecord, ChangeNotification, BatchReplayResult
} from '../../shared/models/api-doc.model';
import { Subscription } from 'rxjs';
import { webSocket } from 'rxjs/webSocket';

@Component({
  selector: 'app-create-doc-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>创建API文档</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-container">
        <mat-form-field appearance="outline">
          <mat-label>文档名称</mat-label>
          <input matInput formControlName="name">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>描述</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>版本</mat-label>
          <input matInput formControlName="version" placeholder="1.0.0">
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="onSubmit()">创建</button>
    </mat-dialog-actions>
  `,
  styles: [`.form-container { display: flex; flex-direction: column; gap: 16px; min-width: 400px; padding-top: 16px; }`]
})
export class CreateDocDialogComponent implements OnInit {
  form: FormGroup;
  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<CreateDocDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { appId: number }
  ) {
    this.form = this.fb.group({
      name: ['', [Validators.required]],
      description: [''],
      version: ['1.0.0']
    });
  }
  ngOnInit(): void {}
  onSubmit(): void {
    if (this.form.valid) {
      this.dialogRef.close({ ...this.form.value, applicationId: this.data.appId });
    }
  }
}

@Component({
  selector: 'app-create-endpoint-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>创建接口定义</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-container">
        <mat-form-field appearance="outline">
          <mat-label>接口名称</mat-label>
          <input matInput formControlName="name">
        </mat-form-field>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>请求方法</mat-label>
            <mat-select formControlName="method">
              <mat-option *ngFor="let m of methods" [value]="m">{{ m }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>路径</mat-label>
            <input matInput formControlName="path" placeholder="/api/users">
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>描述</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="onSubmit()">创建</button>
    </mat-dialog-actions>
  `,
  styles: [`.form-container { display: flex; flex-direction: column; gap: 16px; min-width: 500px; padding-top: 16px; } .form-row { display: flex; gap: 16px; } .form-row > * { flex: 1; }`]
})
export class CreateEndpointDialogComponent implements OnInit {
  form: FormGroup;
  methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'];
  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<CreateEndpointDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { groupId: number }
  ) {
    this.form = this.fb.group({
      name: ['', [Validators.required]],
      method: ['GET', [Validators.required]],
      path: ['', [Validators.required]],
      description: ['']
    });
  }
  ngOnInit(): void {}
  onSubmit(): void {
    if (this.form.valid) {
      this.dialogRef.close({ ...this.form.value, groupId: this.data.groupId });
    }
  }
}

@Component({
  selector: 'app-import-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title>导入OpenAPI 3.0文档</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>OpenAPI JSON内容</mat-label>
        <textarea matInput [formControl]="contentControl" rows="12" placeholder='{"openapi": "3.0.0", ...}'></textarea>
        <mat-error *ngIf="contentControl.invalid && contentControl.touched">请输入OpenAPI JSON内容</mat-error>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">取消</button>
      <button mat-raised-button color="primary" [disabled]="contentControl.invalid" (click)="onSubmit()">导入</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; min-width: 500px; }`]
})
export class ImportDialogComponent {
  contentControl = new FormControl('', [Validators.required]);
  constructor(public dialogRef: MatDialogRef<ImportDialogComponent>) {}
  onSubmit(): void {
    if (this.contentControl.valid) {
      this.dialogRef.close(this.contentControl.value);
    }
  }
}

@Component({
  selector: 'app-integration-center',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent],
  template: `
    <app-page-header title="联调中心" subtitle="API文档管理、Mock服务与在线调试" icon="hub">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>选择应用</mat-label>
        <mat-select [value]="selectedAppId" (selectionChange)="onAppChange($event.value)">
          <mat-option *ngFor="let a of applications" [value]="a.id">{{ a.name }}</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-raised-button color="primary" (click)="openCreateDocDialog()" [disabled]="!selectedAppId">
        <mat-icon>add</mat-icon>新建文档
      </button>
      <button mat-raised-button color="accent" (click)="openImportDialog()" [disabled]="!selectedAppId">
        <mat-icon>upload_file</mat-icon>导入OpenAPI
      </button>
    </app-page-header>

    <div *ngIf="changeNotification" class="change-banner" (click)="onChangeNotificationClick()">
      <mat-icon>notifications_active</mat-icon>
      <span>接口 {{ changeNotification.endpointName || changeNotification.endpointId }} 有更新，点击刷新</span>
      <button mat-icon-button (click)="dismissNotification(); $event.stopPropagation()">
        <mat-icon>close</mat-icon>
      </button>
    </div>

    <div class="integration-layout" *ngIf="selectedAppId">
      <div class="left-panel">
        <mat-card>
          <mat-card-title>接口列表</mat-card-title>
          <mat-card-content>
            <mat-tree [dataSource]="treeDataSource" [treeControl]="treeControl" class="api-tree">
              <mat-tree-node *matTreeNodeDef="let node" class="tree-node endpoint-node"
                  [class.selected]="selectedEndpoint?.id === node.id"
                  (click)="selectEndpoint(node)">
                <mat-icon class="tree-icon" [ngClass]="getMethodClass(node.method)">{{ getMethodIcon(node.method) }}</mat-icon>
                <span class="node-label">{{ node.name }}</span>
                <span class="node-path">{{ node.path }}</span>
                <mat-chip *ngIf="node.mockConfig?.enabled" class="mock-chip" color="accent" selected>Mock</mat-chip>
              </mat-tree-node>
              <mat-nested-tree-node *matNestedTreeNodeDef="let node; let children = children">
                <div class="tree-node group-node" (click)="toggleGroup(node)">
                  <mat-icon class="tree-icon">folder</mat-icon>
                  <span class="node-label">{{ node.name }}</span>
                  <span class="node-count">({{ node.endpoints?.length || 0 }})</span>
                  <button mat-icon-button (click)="openCreateEndpointDialog(node); $event.stopPropagation()" matTooltip="添加接口">
                    <mat-icon>add</mat-icon>
                  </button>
                </div>
                <div [class.tree-invisible]="!node.expanded">
                  <ng-container *ngIf="children">
                    <ng-container *matTreeNodeOutlet="let child; children: children">
                      <div [class.selected]="selectedEndpoint?.id === child.id"
                           class="tree-node endpoint-node"
                           (click)="selectEndpoint(child)">
                        <mat-icon class="tree-icon" [ngClass]="getMethodClass(child.method)">{{ getMethodIcon(child.method) }}</mat-icon>
                        <span class="node-label">{{ child.name }}</span>
                        <span class="node-path">{{ child.path }}</span>
                        <mat-chip *ngIf="child.mockConfig?.enabled" class="mock-chip" color="accent" selected>Mock</mat-chip>
                      </div>
                    </ng-container>
                  </ng-container>
                </div>
              </mat-nested-tree-node>
            </mat-tree>
          </mat-card-content>
        </mat-card>
      </div>

      <div class="right-panel" *ngIf="selectedEndpoint">
        <mat-tab-group [(selectedIndex)]="rightTabIndex">
          <mat-tab label="接口详情">
            <div class="tab-content">
              <mat-card class="detail-card">
                <mat-card-title>{{ selectedEndpoint.name }}</mat-card-title>
                <mat-card-subtitle>{{ selectedEndpoint.description }}</mat-card-subtitle>
                <mat-card-content>
                  <div class="endpoint-header">
                    <mat-chip [ngClass]="getMethodClass(selectedEndpoint.method)">{{ selectedEndpoint.method }}</mat-chip>
                    <code class="endpoint-path">{{ selectedEndpoint.path }}</code>
                  </div>

                  <div *ngIf="selectedEndpoint.requestParams?.length" class="schema-section">
                    <h4>请求参数</h4>
                    <table mat-table [dataSource]="selectedEndpoint.requestParams" class="full-width">
                      <ng-container matColumnDef="name">
                        <th mat-header-cell *matHeaderCellDef>参数名</th>
                        <td mat-cell *matCellDef="let p">{{ p.name }}</td>
                      </ng-container>
                      <ng-container matColumnDef="in">
                        <th mat-header-cell *matHeaderCellDef>位置</th>
                        <td mat-cell *matCellDef="let p">{{ p.in }}</td>
                      </ng-container>
                      <ng-container matColumnDef="type">
                        <th mat-header-cell *matHeaderCellDef>类型</th>
                        <td mat-cell *matCellDef="let p">{{ p.type }}</td>
                      </ng-container>
                      <ng-container matColumnDef="required">
                        <th mat-header-cell *matHeaderCellDef>必填</th>
                        <td mat-cell *matCellDef="let p">{{ p.required ? '是' : '否' }}</td>
                      </ng-container>
                      <ng-container matColumnDef="description">
                        <th mat-header-cell *matHeaderCellDef>描述</th>
                        <td mat-cell *matCellDef="let p">{{ p.description || '-' }}</td>
                      </ng-container>
                      <tr mat-header-row *matHeaderRowDef="['name','in','type','required','description']"></tr>
                      <tr mat-row *matRowDef="let row; columns: ['name','in','type','required','description'];"></tr>
                    </table>
                  </div>

                  <div *ngIf="selectedEndpoint.responseSchema" class="schema-section">
                    <h4>响应Schema</h4>
                    <pre class="schema-json">{{ formatJson(selectedEndpoint.responseSchema) }}</pre>
                  </div>

                  <div *ngIf="selectedEndpoint.statusCodes?.length" class="schema-section">
                    <h4>状态码</h4>
                    <table mat-table [dataSource]="selectedEndpoint.statusCodes" class="full-width">
                      <ng-container matColumnDef="statusCode">
                        <th mat-header-cell *matHeaderCellDef>状态码</th>
                        <td mat-cell *matCellDef="let s">{{ s.statusCode }}</td>
                      </ng-container>
                      <ng-container matColumnDef="description">
                        <th mat-header-cell *matHeaderCellDef>描述</th>
                        <td mat-cell *matCellDef="let s">{{ s.description || '-' }}</td>
                      </ng-container>
                      <tr mat-header-row *matHeaderRowDef="['statusCode','description']"></tr>
                      <tr mat-row *matRowDef="let row; columns: ['statusCode','description'];"></tr>
                    </table>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </mat-tab>

          <mat-tab label="Mock配置">
            <div class="tab-content">
              <mat-card class="detail-card">
                <mat-card-title>Mock配置</mat-card-title>
                <mat-card-content>
                  <form [formGroup]="mockForm" class="mock-form">
                    <mat-slide-toggle formControlName="enabled" class="mock-toggle">
                      启用Mock模式
                    </mat-slide-toggle>
                    <mat-form-field appearance="outline">
                      <mat-label>延迟 (毫秒)</mat-label>
                      <input matInput type="number" formControlName="delayMs" min="0" max="5000">
                      <mat-hint>0-5000ms，模拟网络延迟</mat-hint>
                    </mat-form-field>
                    <div class="form-row">
                      <mat-form-field appearance="outline">
                        <mat-label>故障注入比例 (%)</mat-label>
                        <input matInput type="number" formControlName="faultInjectionPercent" min="0" max="100">
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>故障错误码</mat-label>
                        <mat-select formControlName="faultErrorCode">
                          <mat-option [value]="null">无</mat-option>
                          <mat-option value="500">500</mat-option>
                          <mat-option value="503">503</mat-option>
                          <mat-option value="502">502</mat-option>
                          <mat-option value="504">504</mat-option>
                        </mat-select>
                      </mat-form-field>
                    </div>
                    <button mat-raised-button color="primary" (click)="saveMockConfig()" [disabled]="mockForm.invalid">
                      <mat-icon>save</mat-icon>保存Mock配置
                    </button>
                  </form>
                </mat-card-content>
              </mat-card>
            </div>
          </mat-tab>

          <mat-tab label="在线调试">
            <div class="tab-content">
              <mat-card class="detail-card">
                <mat-card-title>在线调试</mat-card-title>
                <mat-card-content>
                  <div class="debug-section">
                    <mat-slide-toggle [(ngModel)]="debugUseMock" class="debug-toggle">
                      使用Mock地址
                    </mat-slide-toggle>
                    <div class="debug-url">
                      <mat-chip [ngClass]="getMethodClass(selectedEndpoint.method)">{{ selectedEndpoint.method }}</mat-chip>
                      <code>{{ selectedEndpoint.path }}</code>
                    </div>
                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>请求体 (JSON)</mat-label>
                      <textarea matInput [(ngModel)]="debugRequestBody" rows="6" placeholder='{"key": "value"}'></textarea>
                    </mat-form-field>
                    <div class="debug-actions">
                      <button mat-raised-button color="primary" (click)="sendDebugRequest()">
                        <mat-icon>send</mat-icon>发送请求
                      </button>
                      <button mat-raised-button color="accent" (click)="saveAsDebugCase()">
                        <mat-icon>save</mat-icon>保存为用例
                      </button>
                    </div>
                  </div>

                  <div *ngIf="debugResponse" class="debug-result">
                    <h4>响应结果
                      <mat-chip *ngIf="debugResponse.isMock" class="mock-chip" color="accent" selected>Mock</mat-chip>
                    </h4>
                    <div class="response-meta">
                      <span>状态码: <strong>{{ debugResponse.statusCode }}</strong></span>
                      <span>延迟: <strong>{{ debugResponse.latencyMs }}ms</strong></span>
                    </div>
                    <pre class="response-json">{{ formatJson(debugResponse.responseBody) }}</pre>
                  </div>

                  <div *ngIf="debugCases.length" class="debug-cases">
                    <h4>调试用例</h4>
                    <table mat-table [dataSource]="debugCases" class="full-width">
                      <ng-container matColumnDef="select">
                        <th mat-header-cell *matHeaderCellDef>
                          <mat-checkbox (change)="toggleAllCases($event.checked)" [checked]="isAllCasesSelected()"></mat-checkbox>
                        </th>
                        <td mat-cell *matCellDef="let c">
                          <mat-checkbox [checked]="selectedCases.has(c.id)" (change)="toggleCase(c.id, $event.checked)"></mat-checkbox>
                        </td>
                      </ng-container>
                      <ng-container matColumnDef="name">
                        <th mat-header-cell *matHeaderCellDef>名称</th>
                        <td mat-cell *matCellDef="let c">{{ c.name }}</td>
                      </ng-container>
                      <ng-container matColumnDef="useMock">
                        <th mat-header-cell *matHeaderCellDef>Mock</th>
                        <td mat-cell *matCellDef="let c">{{ c.useMock ? '是' : '否' }}</td>
                      </ng-container>
                      <ng-container matColumnDef="actions">
                        <th mat-header-cell *matHeaderCellDef>操作</th>
                        <td mat-cell *matCellDef="let c">
                          <button mat-icon-button color="primary" (click)="loadDebugCase(c)" matTooltip="加载">
                            <mat-icon>play_arrow</mat-icon>
                          </button>
                          <button mat-icon-button color="warn" (click)="deleteDebugCase(c.id)" matTooltip="删除">
                            <mat-icon>delete</mat-icon>
                          </button>
                        </td>
                      </ng-container>
                      <tr mat-header-row *matHeaderRowDef="['select','name','useMock','actions']"></tr>
                      <tr mat-row *matRowDef="let row; columns: ['select','name','useMock','actions'];"></tr>
                    </table>
                    <button mat-raised-button color="primary" [disabled]="selectedCases.size === 0" (click)="batchReplay()" style="margin-top: 12px;">
                      <mat-icon>replay</mat-icon>批量回放 ({{ selectedCases.size }})
                    </button>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </mat-tab>

          <mat-tab label="变更历史">
            <div class="tab-content">
              <mat-card class="detail-card">
                <mat-card-title>变更历史</mat-card-title>
                <mat-card-content>
                  <div *ngIf="changeRecords.length === 0" class="no-data">
                    <mat-icon>info</mat-icon>
                    <span>暂无变更记录</span>
                  </div>
                  <mat-list *ngIf="changeRecords.length > 0">
                    <mat-list-item *ngFor="let record of changeRecords">
                      <mat-icon matListItemIcon color="primary">history</mat-icon>
                      <span matListItemTitle>{{ record.changeSummary }}</span>
                      <span matListItemLine>{{ record.changedBy || '系统' }} · {{ record.createdAt | date:'yyyy-MM-dd HH:mm:ss' }}</span>
                    </mat-list-item>
                  </mat-list>
                </mat-card-content>
              </mat-card>
            </div>
          </mat-tab>
        </mat-tab-group>
      </div>

      <div class="right-panel empty-state" *ngIf="!selectedEndpoint">
        <mat-card>
          <mat-card-content class="empty-content">
            <mat-icon class="empty-icon">api</mat-icon>
            <p>请从左侧接口列表中选择一个接口查看详情</p>
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .filter-field { width: 200px; margin-right: 12px; }
    .change-banner {
      display: flex; align-items: center; gap: 8px;
      padding: 12px 16px; background: #fff3e0; border-radius: 4px;
      margin-bottom: 16px; cursor: pointer; border-left: 4px solid #ff9800;
    }
    .change-banner span { flex: 1; font-size: 14px; }
    .integration-layout { display: flex; gap: 24px; }
    .left-panel { width: 320px; min-width: 280px; }
    .right-panel { flex: 1; min-width: 0; }
    .right-panel.empty-state { display: flex; align-items: flex-start; }
    .api-tree { overflow: auto; max-height: calc(100vh - 260px); }
    .tree-node { display: flex; align-items: center; gap: 8px; padding: 6px 8px; cursor: pointer; border-radius: 4px; }
    .tree-node:hover { background: #f5f5f5; }
    .tree-node.selected { background: #e3f2fd; }
    .tree-node.endpoint-node { padding-left: 24px; }
    .tree-icon { font-size: 18px; width: 18px; height: 18px; }
    .node-label { font-size: 13px; font-weight: 500; }
    .node-path { font-size: 11px; color: #666; margin-left: auto; }
    .node-count { font-size: 11px; color: #999; }
    .mock-chip { font-size: 10px !important; height: 20px !important; line-height: 20px !important; }
    .tree-invisible { display: none; }
    .detail-card { margin-bottom: 16px; }
    .tab-content { padding: 16px 0; }
    .endpoint-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    .endpoint-path { background: #f5f5f5; padding: 6px 12px; border-radius: 4px; font-size: 14px; }
    .schema-section { margin-top: 16px; }
    .schema-section h4 { margin: 0 0 8px 0; font-size: 14px; color: #333; }
    .schema-json { background: #263238; color: #aed581; padding: 16px; border-radius: 4px; font-size: 12px; overflow-x: auto; max-height: 400px; }
    .full-width { width: 100%; }
    .mock-form { display: flex; flex-direction: column; gap: 16px; max-width: 500px; }
    .mock-toggle { margin-bottom: 8px; }
    .form-row { display: flex; gap: 16px; }
    .form-row > * { flex: 1; }
    .debug-section { margin-bottom: 24px; }
    .debug-toggle { margin-bottom: 12px; }
    .debug-url { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
    .debug-actions { display: flex; gap: 12px; margin-bottom: 16px; }
    .debug-result { background: #fafafa; padding: 16px; border-radius: 4px; margin-top: 16px; }
    .debug-result h4 { display: flex; align-items: center; gap: 8px; margin: 0 0 8px 0; }
    .response-meta { display: flex; gap: 16px; margin-bottom: 12px; font-size: 13px; }
    .response-json { background: #263238; color: #aed581; padding: 16px; border-radius: 4px; font-size: 12px; overflow-x: auto; max-height: 400px; }
    .debug-cases { margin-top: 24px; border-top: 1px solid #e0e0e0; padding-top: 16px; }
    .debug-cases h4 { margin: 0 0 12px 0; }
    .empty-content { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 60px; color: #999; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 16px; color: #bbb; }
    .no-data { display: flex; align-items: center; gap: 8px; padding: 24px; color: #999; justify-content: center; }
    .method-get { color: #4caf50 !important; background: #e8f5e9 !important; }
    .method-post { color: #2196f3 !important; background: #e3f2fd !important; }
    .method-put { color: #ff9800 !important; background: #fff3e0 !important; }
    .method-delete { color: #f44336 !important; background: #ffebee !important; }
    .method-patch { color: #9c27b0 !important; background: #f3e5f5 !important; }
  `]
})
export class IntegrationCenterComponent implements OnInit, OnDestroy {
  applications: Application[] = [];
  selectedAppId: number | null = null;
  apiDocs: ApiDoc[] = [];
  treeDataSource: any[] = [];
  selectedEndpoint: ApiEndpoint | null = null;
  rightTabIndex = 0;
  changeNotification: ChangeNotification | null = null;

  mockForm: FormGroup;
  debugUseMock = true;
  debugRequestBody = '';
  debugResponse: any = null;
  debugCases: DebugCase[] = [];
  selectedCases = new Set<number>();
  changeRecords: ApiChangeRecord[] = [];

  expandedGroups = new Set<number>();
  private wsSubscription: Subscription | null = null;

  constructor(
    private apiDocService: ApiDocService,
    private applicationService: ApplicationService,
    private routeRuleService: RouteRuleService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private fb: FormBuilder
  ) {
    this.mockForm = this.fb.group({
      enabled: [false],
      delayMs: [0],
      faultInjectionPercent: [0],
      faultErrorCode: [null]
    });
  }

  ngOnInit(): void {
    this.applicationService.getAllApplications().subscribe({
      next: (apps: Application[]) => {
        this.applications = apps;
        if (apps.length > 0) {
          this.selectedAppId = apps[0].id;
          this.loadApiDocs();
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.wsSubscription?.unsubscribe();
  }

  onAppChange(appId: number): void {
    this.selectedAppId = appId;
    this.selectedEndpoint = null;
    this.loadApiDocs();
  }

  loadApiDocs(): void {
    if (!this.selectedAppId) return;
    this.apiDocService.getApiDocsByAppId(this.selectedAppId).subscribe({
      next: (docs: ApiDoc[]) => {
        this.apiDocs = docs;
        this.buildTree();
      }
    });
  }

  buildTree(): void {
    this.treeDataSource = [];
    for (const doc of this.apiDocs) {
      if (doc.groups) {
        for (const group of doc.groups) {
          const treeNode = {
            ...group,
            expanded: this.expandedGroups.has(group.id),
            isGroup: true
          };
          this.treeDataSource.push(treeNode);
        }
      }
    }
  }

  toggleGroup(node: any): void {
    node.expanded = !node.expanded;
    if (node.expanded) {
      this.expandedGroups.add(node.id);
    } else {
      this.expandedGroups.delete(node.id);
    }
  }

  selectEndpoint(endpoint: ApiEndpoint): void {
    this.selectedEndpoint = endpoint;
    this.rightTabIndex = 0;
    this.loadEndpointDetails(endpoint.id);
    this.connectWebSocket(endpoint);
  }

  loadEndpointDetails(endpointId: number): void {
    this.apiDocService.getMockConfig(endpointId).subscribe({
      next: (config: MockConfig) => {
        if (this.selectedEndpoint) {
          this.selectedEndpoint.mockConfig = config;
        }
        this.mockForm.patchValue({
          enabled: config.enabled,
          delayMs: config.delayMs,
          faultInjectionPercent: config.faultInjectionPercent || 0,
          faultErrorCode: config.faultErrorCode || null
        });
      },
      error: () => {
        this.mockForm.patchValue({ enabled: false, delayMs: 0, faultInjectionPercent: 0, faultErrorCode: null });
      }
    });

    this.apiDocService.getDebugCases(endpointId).subscribe({
      next: (cases: DebugCase[]) => { this.debugCases = cases; }
    });

    this.apiDocService.getChangeHistory(endpointId).subscribe({
      next: (records: ApiChangeRecord[]) => { this.changeRecords = records; }
    });
  }

  connectWebSocket(endpoint: ApiEndpoint): void {
    this.wsSubscription?.unsubscribe();
    try {
      const wsUrl = `ws://${window.location.host}/ws/api-docs`;
      const subject = webSocket(wsUrl);
      this.wsSubscription = subject.subscribe({
        next: (msg: any) => {
          if (msg.endpointId === endpoint.id) {
            this.changeNotification = msg;
          }
        },
        error: () => {}
      });
    } catch (e) {}
  }

  saveMockConfig(): void {
    if (!this.selectedEndpoint || this.mockForm.invalid) return;
    this.apiDocService.updateMockConfig(this.selectedEndpoint.id, this.mockForm.value).subscribe({
      next: (config: MockConfig) => {
        if (this.selectedEndpoint) {
          this.selectedEndpoint.mockConfig = config;
        }
        this.snackBar.open('Mock配置已保存', '关闭', { duration: 3000 });
      },
      error: (err: any) => {
        this.snackBar.open('保存失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  sendDebugRequest(): void {
    if (!this.selectedEndpoint) return;
    this.debugResponse = {
      statusCode: 200,
      responseBody: { message: 'Debug request simulated', mock: this.debugUseMock },
      latencyMs: this.debugUseMock ? (this.mockForm.value.delayMs || 0) : 45,
      isMock: this.debugUseMock
    };
  }

  saveAsDebugCase(): void {
    if (!this.selectedEndpoint) return;
    const name = `用例 ${this.debugCases.length + 1}`;
    let requestBody = null;
    try { requestBody = JSON.parse(this.debugRequestBody); } catch (e) { requestBody = this.debugRequestBody; }

    this.apiDocService.createDebugCase(this.selectedEndpoint.id, {
      name,
      endpointId: this.selectedEndpoint.id,
      requestBody,
      useMock: this.debugUseMock,
      expectedResponse: this.debugResponse?.responseBody
    }).subscribe({
      next: () => {
        this.snackBar.open('调试用例已保存', '关闭', { duration: 3000 });
        this.loadEndpointDetails(this.selectedEndpoint!.id);
      },
      error: (err: any) => {
        this.snackBar.open('保存失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  loadDebugCase(c: DebugCase): void {
    this.debugRequestBody = c.requestBody ? JSON.stringify(c.requestBody, null, 2) : '';
    this.debugUseMock = c.useMock;
  }

  deleteDebugCase(id: number): void {
    this.apiDocService.deleteDebugCase(id).subscribe({
      next: () => {
        this.snackBar.open('用例已删除', '关闭', { duration: 3000 });
        if (this.selectedEndpoint) this.loadEndpointDetails(this.selectedEndpoint.id);
      }
    });
  }

  toggleCase(id: number, checked: boolean): void {
    if (checked) { this.selectedCases.add(id); } else { this.selectedCases.delete(id); }
  }

  toggleAllCases(checked: boolean): void {
    if (checked) { this.debugCases.forEach(c => this.selectedCases.add(c.id)); }
    else { this.selectedCases.clear(); }
  }

  isAllCasesSelected(): boolean {
    return this.debugCases.length > 0 && this.selectedCases.size === this.debugCases.length;
  }

  batchReplay(): void {
    this.apiDocService.batchReplay(Array.from(this.selectedCases)).subscribe({
      next: (results: BatchReplayResult[]) => {
        const failed = results.filter(r => !r.success).length;
        this.snackBar.open(`回放完成: ${results.length - failed}成功, ${failed}失败`, '关闭', { duration: 5000 });
      },
      error: (err: any) => {
        this.snackBar.open('回放失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
      }
    });
  }

  openCreateDocDialog(): void {
    const ref = this.dialog.open(CreateDocDialogComponent, { data: { appId: this.selectedAppId }, width: '500px' });
    ref.afterClosed().subscribe((result: any) => {
      if (result && this.selectedAppId) {
        this.apiDocService.createApiDoc(this.selectedAppId, result).subscribe({
          next: () => {
            this.snackBar.open('文档创建成功', '关闭', { duration: 3000 });
            this.loadApiDocs();
          },
          error: (err: any) => {
            this.snackBar.open('创建失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  openImportDialog(): void {
    const ref = this.dialog.open(ImportDialogComponent, { width: '600px' });
    ref.afterClosed().subscribe((content: string) => {
      if (content && this.selectedAppId) {
        this.apiDocService.importOpenApi(this.selectedAppId, content).subscribe({
          next: () => {
            this.snackBar.open('导入成功', '关闭', { duration: 3000 });
            this.loadApiDocs();
          },
          error: (err: any) => {
            this.snackBar.open('导入失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  openCreateEndpointDialog(groupNode: any): void {
    const ref = this.dialog.open(CreateEndpointDialogComponent, { data: { groupId: groupNode.id }, width: '550px' });
    ref.afterClosed().subscribe((result: any) => {
      if (result) {
        this.apiDocService.createEndpoint(groupNode.id, result).subscribe({
          next: () => {
            this.snackBar.open('接口创建成功', '关闭', { duration: 3000 });
            this.loadApiDocs();
          },
          error: (err: any) => {
            this.snackBar.open('创建失败: ' + (err.error?.message || err.message), '关闭', { duration: 5000 });
          }
        });
      }
    });
  }

  onChangeNotificationClick(): void {
    if (this.selectedEndpoint) {
      this.loadEndpointDetails(this.selectedEndpoint.id);
      this.apiDocService.getChangeHistory(this.selectedEndpoint.id).subscribe({
        next: (records: ApiChangeRecord[]) => { this.changeRecords = records; }
      });
    }
    this.changeNotification = null;
  }

  dismissNotification(): void {
    this.changeNotification = null;
  }

  getMethodClass(method: string): string {
    return 'method-' + (method || 'get').toLowerCase();
  }

  getMethodIcon(method: string): string {
    switch ((method || '').toUpperCase()) {
      case 'GET': return 'arrow_downward';
      case 'POST': return 'arrow_upward';
      case 'PUT': return 'edit';
      case 'DELETE': return 'delete_outline';
      case 'PATCH': return 'build';
      default: return 'http';
    }
  }

  formatJson(obj: any): string {
    if (!obj) return '';
    try { return JSON.stringify(obj, null, 2); }
    catch (e) { return String(obj); }
  }
}
