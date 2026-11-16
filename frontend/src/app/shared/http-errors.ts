/** Erreur d'appel HTTP traduite pour l'utilisateur. Sans dépendance Angular : utilisable partout, testable seule. */
export type HttpErrorKind = 'network' | 'auth' | 'forbidden' | 'not-found' | 'invalid' | 'server' | 'unknown';

export interface HttpErrorInfo {
  kind: HttpErrorKind;
  status: number;
  /** Message prêt à être affiché. */
  message: string;
  /** Vrai si un nouvel essai a une chance de réussir (réseau, serveur). */
  retryable: boolean;
}

/**
 * Transforme une erreur HTTP en message compréhensible.
 * `subject` complète les phrases : « Impossible de charger les anomalies ».
 */
export function describeHttpError(err: unknown, subject = 'les données'): HttpErrorInfo {
  const raw = err as { status?: unknown; error?: { message?: unknown } | null } | null;
  const status = typeof raw?.status === 'number' ? raw.status : 0;

  if (status === 0) {
    return { kind: 'network', status, retryable: true, message: `Impossible de charger ${subject} : le service est injoignable.` };
  }
  if (status === 401) {
    return { kind: 'auth', status, retryable: false, message: 'Votre session a expiré. Reconnectez-vous pour continuer.' };
  }
  if (status === 403) {
    return { kind: 'forbidden', status, retryable: false, message: `Accès refusé : votre rôle ne permet pas de consulter ${subject}.` };
  }
  if (status === 404) {
    return { kind: 'not-found', status, retryable: false, message: `Impossible de trouver ${subject}.` };
  }
  if (status === 400) {
    const server = raw?.error?.message;
    const detail = typeof server === 'string' && server.trim() !== '' ? ` (${server.trim()})` : '';
    return { kind: 'invalid', status, retryable: false, message: `La demande a été refusée par le serveur${detail}.` };
  }
  if (status >= 500) {
    return { kind: 'server', status, retryable: true, message: `Le service a rencontré une erreur en chargeant ${subject}. Réessayez dans un instant.` };
  }
  return { kind: 'unknown', status, retryable: false, message: `Erreur inattendue (code ${status}).` };
}
