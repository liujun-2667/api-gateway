import { Routes } from '@angular/router';
import { AuthGuard } from './core/auth/auth.guard';
import { LoginComponent } from './pages/login/login.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { TenantsComponent } from './pages/tenants/tenants.component';
import { ApplicationsComponent } from './pages/applications/applications.component';
import { ApiKeysComponent } from './pages/apikeys/apikeys.component';
import { RouteRulesComponent } from './pages/route-rules/route-rules.component';
import { ColorRulesComponent } from './pages/color-rules/color-rules.component';
import { RateLimitsComponent } from './pages/rate-limits/rate-limits.component';
import { CircuitBreakersComponent } from './pages/circuit-breakers/circuit-breakers.component';
import { AuditLogsComponent } from './pages/audit-logs/audit-logs.component';
import { MonitoringComponent } from './pages/monitoring/monitoring.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: DashboardComponent,
    canActivate: [AuthGuard],
    children: [
      { path: '', redirectTo: 'tenants', pathMatch: 'full' },
      { path: 'tenants', component: TenantsComponent },
      { path: 'applications', component: ApplicationsComponent },
      { path: 'apikeys', component: ApiKeysComponent },
      { path: 'route-rules', component: RouteRulesComponent },
      { path: 'color-rules', component: ColorRulesComponent },
      { path: 'rate-limits', component: RateLimitsComponent },
      { path: 'circuit-breakers', component: CircuitBreakersComponent },
      { path: 'audit-logs', component: AuditLogsComponent },
      { path: 'monitoring', component: MonitoringComponent }
    ]
  },
  { path: '**', redirectTo: '' }
];
