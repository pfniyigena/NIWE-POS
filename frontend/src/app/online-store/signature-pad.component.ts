import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * A box to sign in with a finger, a pen or the mouse (v1.56.0). Gives the signature as a PNG data URL (black strokes on a transparent background),
 * or null while the box is empty or cleared. Used by the customer on the order page and by the driver on his phone.
 */
@Component({
  selector: 'app-signature-pad',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="sig-wrap">
      <canvas #cv class="sig-canvas" [style.height.px]="height" style="width:100%; background:#fff; border:1px solid #9ca3af; border-radius:6px; touch-action:none; cursor:crosshair; display:block;"></canvas>
      <div class="flex justify-between align-center mt-1" style="gap:0.5rem;">
        <span class="text-muted sig-hint" style="font-size:0.8rem;">{{ 'Sign inside the box.' | translate }}</span>
        <button type="button" class="btn btn-sm btn-outline-secondary sig-clear" [disabled]="empty" (click)="clear()">{{ 'Clear' | translate }}</button>
      </div>
    </div>
  `,
})
export class SignaturePadComponent implements AfterViewInit, OnDestroy {
  @Input() height = 160;
  /** The picture as a PNG data URL, or null when nothing is drawn. */
  @Output() signature = new EventEmitter<string | null>();
  @ViewChild('cv', { static: true }) cv!: ElementRef<HTMLCanvasElement>;

  empty = true;
  private ctx!: CanvasRenderingContext2D;
  private drawing = false;
  private resizeObserver: ResizeObserver | null = null;

  ngAfterViewInit(): void {
    const canvas = this.cv.nativeElement;
    this.fit();
    canvas.addEventListener('pointerdown', this.down);
    canvas.addEventListener('pointermove', this.move);
    canvas.addEventListener('pointerup', this.up);
    canvas.addEventListener('pointercancel', this.up);
    canvas.addEventListener('pointerleave', this.up);
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => { if (this.empty) this.fit(); });
      this.resizeObserver.observe(canvas);
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  /** The drawing surface follows the size on the screen (sharp on phones), keeping what was drawn only while it is empty. */
  private fit(): void {
    const canvas = this.cv.nativeElement;
    const ratio = Math.min(2, window.devicePixelRatio || 1);
    const w = Math.max(200, Math.round(canvas.clientWidth * ratio));
    const h = Math.max(80, Math.round(this.height * ratio));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
    this.ctx = canvas.getContext('2d')!;
    this.ctx.lineWidth = 2.5 * ratio;
    this.ctx.lineCap = 'round';
    this.ctx.lineJoin = 'round';
    this.ctx.strokeStyle = '#000';
  }

  private point(e: PointerEvent): { x: number; y: number } {
    const r = this.cv.nativeElement.getBoundingClientRect();
    return { x: ((e.clientX - r.left) * this.cv.nativeElement.width) / r.width, y: ((e.clientY - r.top) * this.cv.nativeElement.height) / r.height };
  }

  private down = (e: PointerEvent): void => {
    e.preventDefault();
    this.cv.nativeElement.setPointerCapture?.(e.pointerId);
    this.drawing = true;
    const p = this.point(e);
    this.ctx.beginPath();
    this.ctx.moveTo(p.x, p.y);
    this.ctx.lineTo(p.x + 0.01, p.y + 0.01);
    this.ctx.stroke();
  };

  private move = (e: PointerEvent): void => {
    if (!this.drawing) return;
    e.preventDefault();
    const p = this.point(e);
    this.ctx.lineTo(p.x, p.y);
    this.ctx.stroke();
    this.ctx.beginPath();
    this.ctx.moveTo(p.x, p.y);
  };

  private up = (): void => {
    if (!this.drawing) return;
    this.drawing = false;
    this.empty = false;
    this.signature.emit(this.cv.nativeElement.toDataURL('image/png'));
  };

  clear(): void {
    const c = this.cv.nativeElement;
    this.ctx.clearRect(0, 0, c.width, c.height);
    this.empty = true;
    this.signature.emit(null);
  }
}
