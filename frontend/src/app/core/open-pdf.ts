import { Observable } from 'rxjs';
import { downloadBlob } from './download-file';

/**
 * Fetches a PDF and opens it in a new tab (or saves it when the browser blocks the new tab). {@code done} gets null on success, or the server's message
 * (the response body is a blob, so the JSON error has to be read out of it).
 */
export function showPdf(source: Observable<Blob>, file: string, done: (error: string | null) => void): void {
  source.subscribe({
    next: (b) => {
      const url = URL.createObjectURL(b);
      const w = window.open(url, '_blank');
      if (!w) downloadBlob(b, file);
      done(null);
    },
    error: (e) => {
      const blob = e?.error;
      if (blob instanceof Blob) {
        blob.text().then((t) => { try { done(JSON.parse(t).message ?? 'Could not prepare the document'); } catch { done('Could not prepare the document'); } });
      } else done('Could not prepare the document');
    },
  });
}
