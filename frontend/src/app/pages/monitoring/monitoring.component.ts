import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MaterialModule } from '../../shared/material.module';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { NgChartsModule } from 'ng2-charts';
import {
  ChartConfiguration,
  ChartData,
  ChartOptions
} from 'chart.js';
import { MetricsService } from '../../core/services/metrics.service';
import {
  DashboardMetrics,
  QpsMetrics,
  StatusCodeDistribution,
  LatencyMetrics,
  TenantMetrics
} from '../../shared/models/metrics.model';
import { interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-monitoring',
  standalone: true,
  imports: [CommonModule, MaterialModule, PageHeaderComponent, NgChartsModule],
  template: `
    <app-page-header title="实时监控" subtitle="API网关运行状态实时监控面板" icon="monitoring">
      <button mat-raised-button (click)="toggleAutoRefresh()" [color]="autoRefresh ? 'warn' : 'primary'">
        <mat-icon>{{ autoRefresh ? 'pause' : 'play_arrow' }}</mat-icon>
        {{ autoRefresh ? '停止刷新' : '自动刷新' }}
      </button>
      <button mat-icon-button (click)="loadData()" matTooltip="手动刷新">
        <mat-icon>refresh</mat-icon>
      </button>
    </app-page-header>

    <div class="stats-grid">
      <mat-card class="stat-card">
        <mat-card-header>
          <mat-icon mat-card-avatar class="stat-icon total">show_chart</mat-icon>
          <mat-card-title>总请求数</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="stat-value">{{ formatNumber(dashboardMetrics?.totalRequests || 0) }}</div>
        </mat-card-content>
      </mat-card>
      <mat-card class="stat-card">
        <mat-card-header>
          <mat-icon mat-card-avatar class="stat-icon qps">speed</mat-icon>
          <mat-card-title>当前QPS</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="stat-value">{{ (dashboardMetrics?.totalQps || 0).toFixed(2) }}</div>
        </mat-card-content>
      </mat-card>
      <mat-card class="stat-card">
        <mat-card-header>
          <mat-icon mat-card-avatar class="stat-icon latency">timer</mat-icon>
          <mat-card-title>平均延迟</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="stat-value">{{ (dashboardMetrics?.averageLatency || 0).toFixed(2) }} ms</div>
        </mat-card-content>
      </mat-card>
      <mat-card class="stat-card">
        <mat-card-header>
          <mat-icon mat-card-avatar class="stat-icon error">error_outline</mat-icon>
          <mat-card-title>错误率</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="stat-value">{{ (dashboardMetrics?.errorRate || 0).toFixed(2) }}%</div>
        </mat-card-content>
      </mat-card>
    </div>

    <div class="charts-grid">
      <mat-card class="chart-card">
        <mat-card-header>
          <mat-card-title>QPS 趋势</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="chart-container">
            <canvas baseChart
              [type]="'line'"
              [data]="qpsChartData"
              [options]="lineChartOptions">
            </canvas>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="chart-card">
        <mat-card-header>
          <mat-card-title>状态码分布</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="chart-container">
            <canvas baseChart
              [type]="'bar'"
              [data]="statusChartData"
              [options]="barChartOptions">
            </canvas>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="chart-card">
        <mat-card-header>
          <mat-card-title>延迟分布 (P50/P90/P99)</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="chart-container">
            <canvas baseChart
              [type]="'line'"
              [data]="latencyChartData"
              [options]="lineChartOptions">
            </canvas>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="chart-card">
        <mat-card-header>
          <mat-card-title>租户请求占比</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="chart-container">
            <canvas baseChart
              [type]="'pie'"
              [data]="tenantChartData"
              [options]="pieChartOptions">
            </canvas>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
      margin-bottom: 24px;
    }
    .stat-card {
      text-align: center;
    }
    .stat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
    }
    .stat-icon.total { color: #3f51b5; }
    .stat-icon.qps { color: #2e7d32; }
    .stat-icon.latency { color: #f57c00; }
    .stat-icon.error { color: #c62828; }
    .stat-value {
      font-size: 32px;
      font-weight: 600;
      margin-top: 8px;
      color: #333;
    }
    .charts-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;
    }
    .chart-card {
      min-height: 400px;
    }
    .chart-container {
      position: relative;
      height: 300px;
    }
    @media (max-width: 960px) {
      .stats-grid {
        grid-template-columns: repeat(2, 1fr);
      }
      .charts-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class MonitoringComponent implements OnInit, OnDestroy {
  dashboardMetrics: DashboardMetrics | null = null;
  autoRefresh = true;
  private refreshSubscription?: Subscription;

  qpsChartData: ChartData<'line'> = {
    labels: [],
    datasets: [{
      label: 'QPS',
      data: [],
      borderColor: '#3f51b5',
      backgroundColor: 'rgba(63, 81, 181, 0.1)',
      tension: 0.3,
      fill: true
    }]
  };

  statusChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [{
      label: '请求数',
      data: [],
      backgroundColor: [
        '#2e7d32', '#f57c00', '#c62828', '#7b1fa2', '#0288d1'
      ]
    }]
  };

  latencyChartData: ChartData<'line'> = {
    labels: [],
    datasets: [
      {
        label: 'P50',
        data: [],
        borderColor: '#2e7d32',
        backgroundColor: 'rgba(46, 125, 50, 0.1)',
        tension: 0.3
      },
      {
        label: 'P90',
        data: [],
        borderColor: '#f57c00',
        backgroundColor: 'rgba(245, 124, 0, 0.1)',
        tension: 0.3
      },
      {
        label: 'P99',
        data: [],
        borderColor: '#c62828',
        backgroundColor: 'rgba(198, 40, 40, 0.1)',
        tension: 0.3
      }
    ]
  };

  tenantChartData: ChartData<'pie'> = {
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [
        '#3f51b5', '#e91e63', '#009688', '#ff9800', '#673ab7',
        '#03a9f4', '#4caf50', '#ff5722', '#9c27b0', '#795548'
      ]
    }]
  };

  lineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'top' }
    },
    scales: {
      y: { beginAtZero: true }
    }
  };

  barChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false }
    },
    scales: {
      y: { beginAtZero: true }
    }
  };

  pieChartOptions: ChartOptions<'pie'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'right' }
    }
  };

  constructor(private metricsService: MetricsService) {}

  ngOnInit(): void {
    this.loadData();
    this.startAutoRefresh();
  }

  ngOnDestroy(): void {
    this.stopAutoRefresh();
  }

  startAutoRefresh(): void {
    this.refreshSubscription = interval(10000)
      .pipe(switchMap(() => this.metricsService.getDashboardMetrics()))
      .subscribe(metrics => this.updateMetrics(metrics));
  }

  stopAutoRefresh(): void {
    if (this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
      this.refreshSubscription = undefined;
    }
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh) {
      this.startAutoRefresh();
    } else {
      this.stopAutoRefresh();
    }
  }

  loadData(): void {
    this.metricsService.getDashboardMetrics().subscribe(metrics => {
      this.updateMetrics(metrics);
    });
  }

  updateMetrics(metrics: DashboardMetrics): void {
    this.dashboardMetrics = metrics;
    this.updateQpsChart(metrics.qpsMetrics);
    this.updateStatusChart(metrics.statusCodeDistribution);
    this.updateLatencyChart(metrics.latencyMetrics);
    this.updateTenantChart(metrics.tenantMetrics);
  }

  updateQpsChart(qps: QpsMetrics): void {
    this.qpsChartData = {
      labels: qps.timestamps.map(t => this.formatTime(t)),
      datasets: [{
        label: 'QPS',
        data: qps.values,
        borderColor: '#3f51b5',
        backgroundColor: 'rgba(63, 81, 181, 0.1)',
        tension: 0.3,
        fill: true
      }]
    };
  }

  updateStatusChart(dist: StatusCodeDistribution[]): void {
    this.statusChartData = {
      labels: dist.map(d => d.statusCode),
      datasets: [{
        label: '请求数',
        data: dist.map(d => d.count),
        backgroundColor: dist.map(d => this.getStatusColor(d.statusCode))
      }]
    };
  }

  updateLatencyChart(latency: LatencyMetrics): void {
    this.latencyChartData = {
      labels: latency.timestamps.map(t => this.formatTime(t)),
      datasets: [
        {
          label: 'P50',
          data: latency.p50Values,
          borderColor: '#2e7d32',
          backgroundColor: 'rgba(46, 125, 50, 0.1)',
          tension: 0.3
        },
        {
          label: 'P90',
          data: latency.p90Values,
          borderColor: '#f57c00',
          backgroundColor: 'rgba(245, 124, 0, 0.1)',
          tension: 0.3
        },
        {
          label: 'P99',
          data: latency.p99Values,
          borderColor: '#c62828',
          backgroundColor: 'rgba(198, 40, 40, 0.1)',
          tension: 0.3
        }
      ]
    };
  }

  updateTenantChart(tenants: TenantMetrics[]): void {
    this.tenantChartData = {
      labels: tenants.map(t => t.tenantName),
      datasets: [{
        data: tenants.map(t => t.requestCount),
        backgroundColor: [
          '#3f51b5', '#e91e63', '#009688', '#ff9800', '#673ab7',
          '#03a9f4', '#4caf50', '#ff5722', '#9c27b0', '#795548'
        ]
      }]
    };
  }

  private getStatusColor(code: string): string {
    const prefix = code.charAt(0);
    switch (prefix) {
      case '2': return '#2e7d32';
      case '3': return '#0288d1';
      case '4': return '#f57c00';
      case '5': return '#c62828';
      default: return '#7b1fa2';
    }
  }

  private formatNumber(n: number): string {
    if (n >= 1000000) return (n / 1000000).toFixed(2) + 'M';
    if (n >= 1000) return (n / 1000).toFixed(2) + 'K';
    return n.toString();
  }

  private formatTime(timestamp: string): string {
    try {
      const d = new Date(timestamp);
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return timestamp;
    }
  }
}
