import { InjectionToken } from '@angular/core';

export type CsvDownloader = (content: string, fileName: string) => void;

/** Télécharge un texte CSV (UTF-8 avec BOM, pour que Excel lise bien les accents). */
export function downloadCsv(content: string, fileName: string): void {
  const blob = new Blob(['\uFEFF', content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Injectable pour que les tests n'ouvrent pas de téléchargement. */
export const CSV_DOWNLOAD = new InjectionToken<CsvDownloader>('CSV_DOWNLOAD', {
  providedIn: 'root',
  factory: () => downloadCsv,
});
