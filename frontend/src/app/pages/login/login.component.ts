import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MaterialModule } from '../../shared/material.module';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, MaterialModule],
  template: `
    <div class="login-container">
      <mat-card class="login-card">
        <mat-card-header>
          <mat-card-title>API Gateway Admin</mat-card-title>
          <mat-card-subtitle>登录控制台</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="form-container">
            <mat-form-field appearance="outline">
              <mat-label>用户名</mat-label>
              <input matInput formControlName="username" placeholder="请输入用户名">
              <mat-icon matPrefix>person</mat-icon>
              <mat-error *ngIf="loginForm.get('username')?.hasError('required')">
                用户名不能为空
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>密码</mat-label>
              <input matInput formControlName="password" type="password" placeholder="请输入密码">
              <mat-icon matPrefix>lock</mat-icon>
              <mat-error *ngIf="loginForm.get('password')?.hasError('required')">
                密码不能为空
              </mat-error>
            </mat-form-field>

            <button mat-raised-button color="primary" type="submit" [disabled]="loginForm.invalid || loading">
              <mat-spinner *ngIf="loading" diameter="20"></mat-spinner>
              <span *ngIf="!loading">登 录</span>
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }
    .login-card {
      width: 400px;
      padding: 16px;
    }
    .login-card mat-card-header {
      justify-content: center;
      margin-bottom: 24px;
    }
    .login-card mat-card-title {
      font-size: 24px;
    }
    .form-container {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    button {
      height: 48px;
      font-size: 16px;
    }
    mat-spinner {
      display: inline-block;
    }
  `]
})
export class LoginComponent {
  loginForm: FormGroup;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    this.loginForm = this.fb.group({
      username: ['', [Validators.required]],
      password: ['', [Validators.required]]
    });
  }

  onSubmit(): void {
    if (this.loginForm.invalid) return;

    this.loading = true;
    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(err.error?.message || '登录失败，请检查用户名和密码', '关闭', {
          duration: 3000
        });
      }
    });
  }
}
