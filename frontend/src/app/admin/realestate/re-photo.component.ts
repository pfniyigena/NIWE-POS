import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RealEstateApiService } from '../../core/api/realestate-api.service';

/** A property photo. The picture is fetched with the sign-in token (an img tag cannot send it) and shown from memory. Without a photo it shows a placeholder. */
@Component({
  selector: 'app-re-photo',
  standalone: true,
  imports: [CommonModule],
  template: `
    <img *ngIf="url" [src]="url" [alt]="alt" [style.width]="width" [style.height]="height" style="object-fit:cover; border-radius:4px; display:block;" />
    <div *ngIf="!url" [style.width]="width" [style.height]="height" class="text-muted"
      style="display:flex; align-items:center; justify-content:center; background:rgba(127,127,127,0.15); border-radius:4px; font-size:1.4rem;">{{ photoId ? '…' : '🏠' }}</div>
  `,
})
export class RePhotoComponent implements OnChanges {
  @Input() propertyId!: number;
  @Input() photoId: number | null = null;
  @Input() alt = 'Property photo';
  @Input() width = '4.5rem';
  @Input() height = '3.5rem';
  url: string | null = null;

  constructor(private api: RealEstateApiService) {}

  ngOnChanges(): void {
    this.url = null;
    if (this.photoId == null) return;
    this.api.photoUrl(this.propertyId, this.photoId).subscribe({ next: (u) => (this.url = u), error: () => (this.url = null) });
  }
}
