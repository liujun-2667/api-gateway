import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MaterialModule } from '../../material.module';

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <div class="page-header">
      <div class="header-content">
        <h1 class="title">
          <mat-icon *ngIf="icon" class="title-icon">{{ icon }}</mat-icon>
          {{ title }}
        </h1>
        <p *ngIf="subtitle" class="subtitle">{{ subtitle }}</p>
      </div>
      <div class="header-actions">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [`
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      padding-bottom: 16px;
      border-bottom: 1px solid #e0e0e0;
    }
    .header-content {
      flex: 1;
    }
    .title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 24px;
      font-weight: 500;
    }
    .title-icon {
      color: #3f51b5;
    }
    .subtitle {
      margin: 4px 0 0 0;
      color: #666;
      font-size: 14px;
    }
    .header-actions {
      display: flex;
      gap: 8px;
    }
  `]
})
export class PageHeaderComponent {
  @Input() title!: string;
  @Input() subtitle?: string;
  @Input() icon?: string;
}
