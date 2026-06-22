import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router, RouterModule, ActivatedRoute } from '@angular/router';
import { MaterialModule } from '../../shared/material.module';
import { AuthService } from '../../core/auth/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterModule, MaterialModule],
  template: `
    <mat-sidenav-container class="sidenav-container">
      <mat-sidenav #sidenav mode="side" opened="true" class="sidenav">
        <div class="logo-area">
          <mat-icon class="logo-icon">cloud_circle</mat-icon>
          <span class="logo-text">API Gateway</span>
        </div>
        <mat-nav-list>
          <a mat-list-item *ngFor="let item of navItems"
             [routerLink]="item.path"
             routerLinkActive="active">
            <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
            <span matListItemTitle>{{ item.label }}</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content class="content">
        <mat-toolbar color="primary" class="toolbar">
          <button mat-icon-button (click)="sidenav.toggle()">
            <mat-icon>menu</mat-icon>
          </button>
          <span class="toolbar-title">{{ currentPageTitle }}</span>
          <span class="spacer"></span>
          <button mat-icon-button [matMenuTriggerFor]="userMenu">
            <mat-icon>account_circle</mat-icon>
          </button>
          <mat-menu #userMenu="matMenu">
            <div class="user-info">
              <div class="username">{{ currentUser?.username }}</div>
              <div class="roles" *ngIf="currentUser?.roles">
                {{ currentUser.roles.join(', ') }}
              </div>
            </div>
            <mat-divider></mat-divider>
            <button mat-menu-item (click)="logout()">
              <mat-icon>logout</mat-icon>
              <span>退出登录</span>
            </button>
          </mat-menu>
        </mat-toolbar>

        <div class="main-content">
          <router-outlet></router-outlet>
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .sidenav-container {
      height: 100vh;
    }
    .sidenav {
      width: 240px;
      background: #1e293b;
    }
    .logo-area {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 20px 16px;
      border-bottom: 1px solid #334155;
    }
    .logo-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      color: #60a5fa;
    }
    .logo-text {
      font-size: 18px;
      font-weight: 600;
      color: #fff;
    }
    .mat-mdc-nav-list {
      padding: 8px 0;
    }
    .mat-mdc-list-item {
      color: #cbd5e1;
    }
    .mat-mdc-list-item.active {
      background: #334155;
      color: #60a5fa;
      border-left: 3px solid #60a5fa;
    }
    .content {
      background: #f1f5f9;
    }
    .toolbar {
      position: sticky;
      top: 0;
      z-index: 100;
    }
    .toolbar-title {
      margin-left: 16px;
      font-size: 18px;
    }
    .spacer {
      flex: 1;
    }
    .main-content {
      padding: 24px;
    }
    .user-info {
      padding: 12px 16px;
    }
    .username {
      font-weight: 500;
      font-size: 14px;
    }
    .roles {
      font-size: 12px;
      color: #666;
      margin-top: 2px;
    }
  `]
})
export class DashboardComponent implements OnInit {
  currentUser: { username: string; roles: string[] } | null = null;
  currentPageTitle = '仪表盘';

  navItems: NavItem[] = [
    { path: 'tenants', label: '租户管理', icon: 'business' },
    { path: 'applications', label: '应用管理', icon: 'apps' },
    { path: 'apikeys', label: 'API Key', icon: 'vpn_key' },
    { path: 'route-rules', label: '路由规则', icon: 'route' },
    { path: 'color-rules', label: '染色规则', icon: 'palette' },
    { path: 'rate-limits', label: '限流配置', icon: 'speed' },
    { path: 'circuit-breakers', label: '熔断配置', icon: 'power_off' },
    { path: 'audit-logs', label: '审计日志', icon: 'receipt_long' },
    { path: 'monitoring', label: '实时监控', icon: 'monitoring' },
    { path: 'integration-center', label: '联调中心', icon: 'hub' }
  ];

  private pageTitleMap: Record<string, string> = {
    'tenants': '租户管理',
    'applications': '应用管理',
    'apikeys': 'API Key管理',
    'route-rules': '路由规则',
    'color-rules': '染色规则',
    'rate-limits': '限流配置',
    'circuit-breakers': '熔断配置',
    'audit-logs': '审计日志',
    'monitoring': '实时监控',
    'integration-center': '联调中心'
  };

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser();
    this.router.events.subscribe(() => {
      const path = this.router.url.split('/')[1];
      this.currentPageTitle = this.pageTitleMap[path] || '仪表盘';
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
