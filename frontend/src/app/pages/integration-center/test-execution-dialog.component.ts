import { Component, OnInit, OnDestroy, Inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MaterialModule } from '../../shared/material.module';
import { ApiDocService } from '../../core/services/api-doc.service';
import {
  TestSuite,
  TestSuiteExecution,
  CaseExecutionResult,
  CaseExecutionProgress,
  TestReport,
  TestReportCreateRequest,
  DebugCase,
  CaseExecutionStatus,
  TestSuiteStatus
} from '../../shared/models/api-doc.model';
import { Client, Stomp } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject, takeUntil } from 'rxjs';

interface DiffDetail {
  field: string;
  path: string;
  changeType: string;
  expected?: any;
  actual?: any;
}

@Component({
  selector: 'app-test-execution-dialog',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <h2 mat-dialog-title class="dialog-header">
      <div class="header-content">
        <mat-icon class="header-icon">play_circle_filled</mat-icon>
        <div class="header-text">
          <span class="suite-name">{{ data.testSuite.name }}</span>
          <span class="execution-status" [ngClass]="getStatusClass(execution.status)">
            <mat-icon *ngIf="execution.status === 'RUNNING'" class="spinning">sync</mat-icon>
            <mat-icon *ngIf="execution.status === 'COMPLETED'">check_circle</mat-icon>
            <mat-icon *ngIf="execution.status === 'COMPLETED_WITH_FAILURES'">warning</mat-icon>
            <mat-icon *ngIf="execution.status === 'FAILED'">error</mat-icon>
            <mat-icon *ngIf="execution.status === 'PENDING'">schedule</mat-icon>
            {{ getStatusText(execution.status) }}
          </span>
        </div>
      </div>
    </h2>

    <mat-dialog-content class="dialog-content">
      <div class="progress-section">
        <div class="progress-header">
          <span>执行进度</span>
          <span class="progress-text">{{ completedCases }} / {{ execution.totalCases }}</span>
        </div>
        <mat-progress-bar
          mode="determinate"
          [value]="progressPercent"
          [color]="execution.status === 'COMPLETED_WITH_FAILURES' || execution.status === 'FAILED' ? 'warn' : 'primary'"
        ></mat-progress-bar>
      </div>

      <div class="cases-section">
        <div class="section-title">
          <mat-icon>list_alt</mat-icon>
          <span>用例列表</span>
        </div>
        <div class="case-list">
          <div
            *ngFor="let caseItem of caseResults; let index = index"
            class="case-item"
            [class.selected]="selectedCase?.caseId === caseItem.caseId"
            (click)="selectCase(caseItem)"
          >
            <div class="case-status">
              <mat-icon
                *ngIf="caseItem.status === 'WAITING'"
                class="status-icon waiting"
              >schedule</mat-icon>
              <mat-icon
                *ngIf="caseItem.status === 'RUNNING'"
                class="status-icon running spinning"
              >sync</mat-icon>
              <mat-icon
                *ngIf="caseItem.status === 'PASSED'"
                class="status-icon passed"
              >check_circle</mat-icon>
              <mat-icon
                *ngIf="caseItem.status === 'FAILED'"
                class="status-icon failed"
              >cancel</mat-icon>
            </div>
            <div class="case-info">
              <span class="case-order">{{ index + 1 }}.</span>
              <span class="case-name">{{ caseItem.caseName }}</span>
            </div>
            <div class="case-duration" *ngIf="caseItem.durationMs !== undefined">
              {{ caseItem.durationMs }}ms
            </div>
            <mat-icon
              *ngIf="caseItem.status === 'FAILED'"
              class="diff-icon"
              (click)="toggleDiff(caseItem); $event.stopPropagation()"
              matTooltip="查看详情"
            >visibility</mat-icon>
          </div>
        </div>
      </div>

      <div *ngIf="isExecutionComplete" class="stats-section">
        <div class="section-title">
          <mat-icon>bar_chart</mat-icon>
          <span>执行结果统计</span>
        </div>
        <div class="stats-grid">
          <mat-card class="stat-card passed">
            <mat-icon class="stat-icon">check_circle</mat-icon>
            <div class="stat-content">
              <span class="stat-value">{{ execution.passedCases || 0 }}</span>
              <span class="stat-label">通过</span>
            </div>
          </mat-card>
          <mat-card class="stat-card failed">
            <mat-icon class="stat-icon">cancel</mat-icon>
            <div class="stat-content">
              <span class="stat-value">{{ execution.failedCases || 0 }}</span>
              <span class="stat-label">失败</span>
            </div>
          </mat-card>
          <mat-card class="stat-card total">
            <mat-icon class="stat-icon">trending_up</mat-icon>
            <div class="stat-content">
              <span class="stat-value">{{ successRate }}%</span>
              <span class="stat-label">成功率</span>
            </div>
          </mat-card>
          <mat-card class="stat-card duration">
            <mat-icon class="stat-icon">timer</mat-icon>
            <div class="stat-content">
              <span class="stat-value">{{ formatDuration(execution.totalDurationMs) }}</span>
              <span class="stat-label">总耗时</span>
            </div>
          </mat-card>
        </div>
      </div>

      <div *ngIf="selectedDiffCase" class="diff-section">
        <div class="section-title">
          <mat-icon>difference</mat-icon>
          <span>失败详情 - {{ selectedDiffCase.caseName }}</span>
          <button mat-icon-button class="close-btn" (click)="closeDiff()">
            <mat-icon>close</mat-icon>
          </button>
        </div>
        <div *ngIf="selectedDiffCase.errorMessage" class="error-message">
          <mat-icon>error</mat-icon>
          <span>{{ selectedDiffCase.errorMessage }}</span>
        </div>
        <div *ngIf="diffDetails.length > 0" class="diff-container">
          <div class="diff-header">
            <span class="diff-col expected">预期值</span>
            <span class="diff-col actual">实际值</span>
          </div>
          <div
            *ngFor="let diff of diffDetails; let index = index"
            class="diff-row"
            [class]="diff.changeType.toLowerCase()"
          >
            <div class="diff-field">
              <span class="diff-path">{{ diff.path }}</span>
              <span class="diff-type-badge" [class]="diff.changeType.toLowerCase()">
                {{ diff.changeType }}
              </span>
            </div>
            <div class="diff-col expected">
              <pre *ngIf="diff.expected !== undefined">{{ formatJson(diff.expected) }}</pre>
              <span *ngIf="diff.expected === undefined" class="empty-value">-</span>
            </div>
            <div class="diff-col actual">
              <pre *ngIf="diff.actual !== undefined">{{ formatJson(diff.actual) }}</pre>
              <span *ngIf="diff.actual === undefined" class="empty-value">-</span>
            </div>
          </div>
        </div>
        <div *ngIf="selectedDiffCase.response" class="response-section">
          <div class="response-header">
            <span>实际响应</span>
            <span class="response-meta">
              状态码: {{ selectedDiffCase.response.statusCode }} |
              耗时: {{ selectedDiffCase.response.latencyMs }}ms
            </span>
          </div>
          <pre class="response-body">{{ formatJson(selectedDiffCase.response.responseBody) }}</pre>
        </div>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end" class="dialog-actions">
      <button
        mat-raised-button
        color="primary"
        (click)="saveReport()"
        [disabled]="!isExecutionComplete || savingReport"
      >
        <mat-spinner *ngIf="savingReport" diameter="16" class="btn-spinner"></mat-spinner>
        <mat-icon *ngIf="!savingReport">save</mat-icon>
        {{ savingReport ? '保存中...' : '保存报告' }}
      </button>
      <button
        mat-raised-button
        color="accent"
        (click)="jumpToDebug()"
        [disabled]="!selectedCase || selectedCase.status !== 'FAILED'"
      >
        <mat-icon>bug_report</mat-icon>
        跳转调试
      </button>
      <button mat-button (click)="dialogRef.close()">
        关闭
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-header {
      margin: -24px -24px 0 -24px;
      padding: 16px 24px;
      background: linear-gradient(135deg, #1976d2 0%, #2196f3 100%);
      color: white;
    }

    .header-content {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .header-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
    }

    .header-text {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .suite-name {
      font-size: 18px;
      font-weight: 600;
    }

    .execution-status {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      opacity: 0.9;
    }

    .execution-status.pending { color: #ffeb3b; }
    .execution-status.running { color: #90caf9; }
    .execution-status.completed { color: #a5d6a7; }
    .execution-status.completed_with_failures { color: #ffcc80; }
    .execution-status.failed { color: #ef9a9a; }

    .dialog-content {
      min-width: 800px;
      min-height: 600px;
      padding: 24px 0;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .progress-section {
      padding: 0 24px;
    }

    .progress-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-weight: 500;
      color: #333;
    }

    .progress-text {
      font-size: 14px;
      color: #666;
    }

    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
      font-size: 15px;
      font-weight: 500;
      color: #333;
    }

    .section-title mat-icon {
      color: #1976d2;
    }

    .cases-section {
      padding: 0 24px;
    }

    .case-list {
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      overflow: hidden;
      max-height: 280px;
      overflow-y: auto;
    }

    .case-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      transition: background 0.2s;
    }

    .case-item:last-child {
      border-bottom: none;
    }

    .case-item:hover {
      background: #f5f5f5;
    }

    .case-item.selected {
      background: #e3f2fd;
    }

    .case-status {
      flex-shrink: 0;
    }

    .status-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
    }

    .status-icon.waiting { color: #9e9e9e; }
    .status-icon.running { color: #2196f3; }
    .status-icon.passed { color: #4caf50; }
    .status-icon.failed { color: #f44336; }

    .spinning {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .case-info {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }

    .case-order {
      color: #999;
      font-weight: 500;
      flex-shrink: 0;
    }

    .case-name {
      font-size: 14px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .case-duration {
      color: #666;
      font-size: 13px;
      flex-shrink: 0;
    }

    .diff-icon {
      color: #1976d2;
      flex-shrink: 0;
    }

    .stats-section {
      padding: 0 24px;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
    }

    .stat-card {
      padding: 16px;
      display: flex;
      align-items: center;
      gap: 12px;
      border-radius: 8px;
      transition: transform 0.2s;
    }

    .stat-card:hover {
      transform: translateY(-2px);
    }

    .stat-card.passed {
      background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
      border: 1px solid #a5d6a7;
    }

    .stat-card.failed {
      background: linear-gradient(135deg, #ffebee 0%, #ffcdd2 100%);
      border: 1px solid #ef9a9a;
    }

    .stat-card.total {
      background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
      border: 1px solid #90caf9;
    }

    .stat-card.duration {
      background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
      border: 1px solid #ffcc80;
    }

    .stat-icon {
      font-size: 36px;
      width: 36px;
      height: 36px;
    }

    .stat-card.passed .stat-icon { color: #4caf50; }
    .stat-card.failed .stat-icon { color: #f44336; }
    .stat-card.total .stat-icon { color: #2196f3; }
    .stat-card.duration .stat-icon { color: #ff9800; }

    .stat-content {
      display: flex;
      flex-direction: column;
    }

    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: #333;
    }

    .stat-label {
      font-size: 13px;
      color: #666;
    }

    .diff-section {
      padding: 0 24px;
      border-top: 1px solid #e0e0e0;
      padding-top: 24px;
    }

    .diff-section .section-title {
      position: relative;
    }

    .close-btn {
      position: absolute;
      right: 0;
      top: 50%;
      transform: translateY(-50%);
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: #ffebee;
      border: 1px solid #ef9a9a;
      border-radius: 4px;
      margin-bottom: 16px;
      color: #c62828;
    }

    .error-message mat-icon {
      color: #f44336;
    }

    .diff-container {
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      overflow: hidden;
      margin-bottom: 16px;
    }

    .diff-header {
      display: grid;
      grid-template-columns: 200px 1fr 1fr;
      background: #f5f5f5;
      font-weight: 500;
    }

    .diff-header span {
      padding: 12px 16px;
      border-right: 1px solid #e0e0e0;
    }

    .diff-header span:last-child {
      border-right: none;
    }

    .diff-header .expected { color: #2e7d32; }
    .diff-header .actual { color: #c62828; }

    .diff-row {
      display: grid;
      grid-template-columns: 200px 1fr 1fr;
      border-bottom: 1px solid #f0f0f0;
    }

    .diff-row:last-child {
      border-bottom: none;
    }

    .diff-row.add { background: #f1f8e9; }
    .diff-row.remove { background: #ffebee; }
    .diff-row.modify { background: #fff8e1; }
    .diff-row.type_change { background: #f3e5f5; }

    .diff-field {
      padding: 12px 16px;
      border-right: 1px solid #e0e0e0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .diff-path {
      font-family: monospace;
      font-size: 12px;
      word-break: break-all;
    }

    .diff-type-badge {
      display: inline-block;
      padding: 2px 6px;
      border-radius: 3px;
      font-size: 10px;
      font-weight: 500;
      width: fit-content;
    }

    .diff-type-badge.add { background: #c8e6c9; color: #2e7d32; }
    .diff-type-badge.remove { background: #ffcdd2; color: #c62828; }
    .diff-type-badge.modify { background: #ffe082; color: #f57f17; }
    .diff-type-badge.type_change { background: #ce93d8; color: #6a1b9a; }

    .diff-col {
      padding: 12px 16px;
      border-right: 1px solid #e0e0e0;
      overflow-x: auto;
    }

    .diff-col:last-child {
      border-right: none;
    }

    .diff-col pre {
      margin: 0;
      font-family: monospace;
      font-size: 12px;
      white-space: pre-wrap;
      word-break: break-all;
    }

    .diff-col.expected pre { color: #2e7d32; }
    .diff-col.actual pre { color: #c62828; }

    .empty-value {
      color: #999;
      font-style: italic;
    }

    .response-section {
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      overflow: hidden;
    }

    .response-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      background: #f5f5f5;
      font-weight: 500;
    }

    .response-meta {
      font-size: 13px;
      color: #666;
      font-weight: normal;
    }

    .response-body {
      padding: 16px;
      margin: 0;
      background: #263238;
      color: #aed581;
      font-family: monospace;
      font-size: 12px;
      max-height: 200px;
      overflow-y: auto;
      white-space: pre-wrap;
      word-break: break-all;
    }

    .dialog-actions {
      padding: 16px 24px;
      border-top: 1px solid #e0e0e0;
      margin: 0 -24px -24px -24px;
    }

    .btn-spinner {
      margin-right: 8px;
    }
  `]
})
export class TestExecutionDialogComponent implements OnInit, OnDestroy {
  execution: TestSuiteExecution;
  caseResults: CaseExecutionResult[] = [];
  selectedCase: CaseExecutionResult | null = null;
  selectedDiffCase: CaseExecutionResult | null = null;
  diffDetails: DiffDetail[] = [];
  savingReport = false;

  private stompClient: Client | null = null;
  private destroy$ = new Subject<void>();

  constructor(
    public dialogRef: MatDialogRef<TestExecutionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: {
      testSuite: TestSuite;
      execution: TestSuiteExecution;
      debugCases: DebugCase[];
    },
    private apiDocService: ApiDocService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef
  ) {
    this.execution = { ...data.execution };
    this.initCaseResults();
  }

  ngOnInit(): void {
    this.connectWebSocket();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnectWebSocket();
  }

  private initCaseResults(): void {
    if (this.execution.caseResults && this.execution.caseResults.length > 0) {
      this.caseResults = [...this.execution.caseResults];
    } else {
      const caseOrder = this.data.testSuite.caseOrder || [];
      this.caseResults = caseOrder.map(item => ({
        caseId: item.caseId,
        caseName: item.caseName || `用例 ${item.caseId}`,
        status: 'WAITING' as CaseExecutionStatus
      }));
    }
  }

  private connectWebSocket(): void {
    try {
      const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
      const wsUrl = `${protocol}//${window.location.host}/ws/api-docs`;
      const socket = new SockJS(wsUrl);
      this.stompClient = new Client({
        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        debug: (str) => {}
      });

      this.stompClient.onConnect = (frame) => {
        const suiteId = this.data.testSuite.id;
        const executionId = this.execution.id;

        this.stompClient?.subscribe(
          `/topic/test-suites/${suiteId}/executions/${executionId}/progress`,
          (message) => {
            try {
              const progress: CaseExecutionProgress = JSON.parse(message.body);
              this.updateCaseProgress(progress);
            } catch (e) {
              console.error('Failed to parse progress message:', e);
            }
          }
        );

        this.caseResults.forEach(caseItem => {
          this.stompClient?.subscribe(
            `/topic/test-suites/executions/${executionId}/cases/${caseItem.caseId}/progress`,
            (message) => {
              try {
                const progress: CaseExecutionProgress = JSON.parse(message.body);
                this.updateCaseProgress(progress);
              } catch (e) {
                console.error('Failed to parse case progress message:', e);
              }
            }
          );
        });
      };

      this.stompClient.onStompError = (frame) => {
        console.error('WebSocket STOMP error:', frame);
      };

      this.stompClient.activate();
    } catch (e) {
      console.error('Failed to connect WebSocket:', e);
    }
  }

  private disconnectWebSocket(): void {
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }
  }

  private updateCaseProgress(progress: CaseExecutionProgress): void {
    const index = this.caseResults.findIndex(c => c.caseId === progress.caseId);
    if (index >= 0) {
      this.caseResults[index] = {
        ...this.caseResults[index],
        status: progress.status,
        durationMs: progress.durationMs,
        diffResult: progress.diffResult,
        errorMessage: progress.errorMessage
      };

      this.updateExecutionStats();
      this.cdr.detectChanges();
    }
  }

  private updateExecutionStats(): void {
    const passed = this.caseResults.filter(c => c.status === 'PASSED').length;
    const failed = this.caseResults.filter(c => c.status === 'FAILED').length;
    const completed = passed + failed;

    this.execution.passedCases = passed;
    this.execution.failedCases = failed;

    if (completed === this.execution.totalCases) {
      const totalDuration = this.caseResults.reduce(
        (sum, c) => sum + (c.durationMs || 0),
        0
      );
      this.execution.totalDurationMs = totalDuration;
      this.execution.status = failed > 0 ? 'COMPLETED_WITH_FAILURES' : 'COMPLETED';
    } else if (completed > 0 || this.caseResults.some(c => c.status === 'RUNNING')) {
      this.execution.status = 'RUNNING';
    }
  }

  get completedCases(): number {
    return this.caseResults.filter(c => c.status === 'PASSED' || c.status === 'FAILED').length;
  }

  get progressPercent(): number {
    if (this.execution.totalCases === 0) return 0;
    return (this.completedCases / this.execution.totalCases) * 100;
  }

  get isExecutionComplete(): boolean {
    return this.execution.status === 'COMPLETED' ||
           this.execution.status === 'COMPLETED_WITH_FAILURES' ||
           this.execution.status === 'FAILED';
  }

  get successRate(): number {
    if (this.execution.totalCases === 0) return 0;
    const passed = this.execution.passedCases || 0;
    return Math.round((passed / this.execution.totalCases) * 100);
  }

  getStatusClass(status: TestSuiteStatus): string {
    return status.toLowerCase().replace(/_/g, '_');
  }

  getStatusText(status: TestSuiteStatus): string {
    const statusMap: { [key in TestSuiteStatus]: string } = {
      PENDING: '等待执行',
      RUNNING: '执行中',
      COMPLETED: '执行完成',
      COMPLETED_WITH_FAILURES: '部分失败',
      FAILED: '执行失败'
    };
    return statusMap[status] || status;
  }

  formatDuration(ms?: number): string {
    if (!ms) return '0ms';
    if (ms < 1000) return `${ms}ms`;
    const seconds = Math.floor(ms / 1000);
    const remainingMs = ms % 1000;
    if (seconds < 60) return `${seconds}.${remainingMs.toString().padStart(3, '0')}s`;
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}m ${remainingSeconds}s`;
  }

  formatJson(obj: any): string {
    if (!obj) return '';
    try {
      return JSON.stringify(obj, null, 2);
    } catch (e) {
      return String(obj);
    }
  }

  selectCase(caseItem: CaseExecutionResult): void {
    this.selectedCase = caseItem;
  }

  toggleDiff(caseItem: CaseExecutionResult): void {
    if (this.selectedDiffCase?.caseId === caseItem.caseId) {
      this.closeDiff();
    } else {
      this.selectedDiffCase = caseItem;
      this.parseDiffDetails(caseItem.diffResult);
    }
  }

  closeDiff(): void {
    this.selectedDiffCase = null;
    this.diffDetails = [];
  }

  private parseDiffDetails(diffResult: any): void {
    this.diffDetails = [];
    if (!diffResult) return;

    if (Array.isArray(diffResult)) {
      this.diffDetails = diffResult.map((d: any) => ({
        field: d.field || d.path || '',
        path: d.path || d.field || '',
        changeType: d.changeType || 'MODIFY',
        expected: d.oldValue,
        actual: d.newValue
      }));
    } else if (typeof diffResult === 'object') {
      if (diffResult.diffs && Array.isArray(diffResult.diffs)) {
        this.diffDetails = diffResult.diffs.map((d: any) => ({
          field: d.field || d.path || '',
          path: d.path || d.field || '',
          changeType: d.changeType || 'MODIFY',
          expected: d.expected !== undefined ? d.expected : d.oldValue,
          actual: d.actual !== undefined ? d.actual : d.newValue
        }));
      } else {
        this.diffDetails = [{
          field: '响应内容',
          path: 'responseBody',
          changeType: 'MODIFY',
          expected: diffResult.expected,
          actual: diffResult.actual
        }];
      }
    }
  }

  saveReport(): void {
    if (!this.isExecutionComplete) return;

    this.savingReport = true;
    const reportName = `${this.data.testSuite.name}_${new Date().toLocaleString('zh-CN')}`;

    const request: TestReportCreateRequest = {
      name: reportName,
      testSuiteId: this.data.testSuite.id,
      executionId: this.execution.id,
      remarks: ''
    };

    this.apiDocService.saveReport(request).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (report: TestReport) => {
        this.savingReport = false;
        this.dialogRef.close({ action: 'reportSaved', report });
      },
      error: (err: any) => {
        this.savingReport = false;
        console.error('Failed to save report:', err);
      }
    });
  }

  jumpToDebug(): void {
    if (!this.selectedCase || this.selectedCase.status !== 'FAILED') return;

    const debugCase = this.data.debugCases.find(c => c.id === this.selectedCase?.caseId);
    this.dialogRef.close({
      action: 'jumpToDebug',
      caseId: this.selectedCase.caseId,
      caseName: this.selectedCase.caseName,
      debugCase: debugCase
    });
  }
}
