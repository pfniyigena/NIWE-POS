import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PROJECT_STATUS_LABEL, ProjectDto, ProjectPayload, ProjectStatus, RealEstateApiService } from '../../core/api/realestate-api.service';

/** Real Estate > Projects > new project, or change one. */
@Component({
  selector: 'app-project-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Change ' + (project?.name ?? 'project') : 'New project' }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="id ? ['/admin/realestate/projects', id] : ['/admin/realestate/projects']">Cancel</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form class="card" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
        <div class="form-group mb-2"><label class="form-label">Name *</label><input class="form-control" name="nm" [(ngModel)]="f.name" maxlength="200" placeholder="e.g. Sunrise Estate" /></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">Area</label><input class="form-control" name="ar" [(ngModel)]="f.area" maxlength="120" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">Developer</label><input class="form-control" name="dv" [(ngModel)]="f.developerName" maxlength="200" placeholder="Who is building it" /></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">Address / location</label><input class="form-control" name="ad" [(ngModel)]="f.address" maxlength="255" /></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Status</label>
            <select class="form-select" name="stt" [(ngModel)]="f.status"><option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option></select></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Launch</label><input class="form-control" type="date" name="ld" [(ngModel)]="f.launchDate" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Handover</label><input class="form-control" type="date" name="hd" [(ngModel)]="f.handoverDate" /></div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;">Units can be reserved only while the project is <strong>Selling</strong>. Planning is for setting up units and prices first.</div>
        <div class="form-group mb-3"><label class="form-label">Description</label><textarea class="form-control" rows="3" name="ds" [(ngModel)]="f.description" maxlength="2000"></textarea></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : id ? 'Save changes' : 'Create project' }}</button></div>
      </form>
    </app-shell>
  `,
})
export class ProjectFormComponent implements OnInit {
  id: number | null = null;
  project: ProjectDto | null = null;
  error: string | null = null;
  saving = false;
  statuses = (Object.keys(PROJECT_STATUS_LABEL) as ProjectStatus[]).map((k) => ({ value: k, label: PROJECT_STATUS_LABEL[k] }));
  f = { name: '', area: '', address: '', developerName: '', status: 'PLANNING' as ProjectStatus, launchDate: '', handoverDate: '', description: '' };

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.api.project(this.id).subscribe({
      next: (p) => {
        this.project = p;
        this.f = { name: p.name, area: p.area ?? '', address: p.address ?? '', developerName: p.developerName ?? '', status: p.status, launchDate: p.launchDate ?? '', handoverDate: p.handoverDate ?? '', description: p.description ?? '' };
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the project'),
    });
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!f.name.trim()) { this.error = 'Give the project a name.'; return; }
    const payload: ProjectPayload = {
      name: f.name.trim(), area: f.area, address: f.address, developerName: f.developerName, status: f.status, description: f.description,
      launchDate: f.launchDate || null, handoverDate: f.handoverDate || null,
    };
    this.saving = true;
    const call = this.id ? this.api.updateProject(this.id, payload) : this.api.createProject(payload);
    call.subscribe({
      next: (p) => { this.saving = false; this.router.navigate(['/admin/realestate/projects', p.id]); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the project'; },
    });
  }
}
