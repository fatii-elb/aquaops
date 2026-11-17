import { Intervention } from '../interventions/intervention.model';

/** Résumé d'une équipe d'intervention, déduit des interventions existantes (aucune API d'équipes n'existe). */
export interface TeamSummary {
  name: string;
  total: number;
  /** Planifiées ou en cours. */
  open: number;
  done: number;
  /** Techniciens déjà affectés dans cette équipe. */
  members: string[];
}

/** Regroupe les interventions par équipe. Les interventions sans équipe sont ignorées. Tri alphabétique. */
export function summarizeTeams(interventions: Intervention[]): TeamSummary[] {
  const byName = new Map<string, TeamSummary>();
  for (const item of interventions) {
    const name = item.team?.trim();
    if (!name) continue;
    let team = byName.get(name);
    if (!team) {
      team = { name, total: 0, open: 0, done: 0, members: [] };
      byName.set(name, team);
    }
    team.total++;
    if (item.status === 'DONE') team.done++;
    else team.open++;
    const who = item.assignedTo?.trim();
    if (who && !team.members.includes(who)) team.members.push(who);
  }
  return [...byName.values()].sort((a, b) => a.name.localeCompare(b.name, 'fr'));
}

/** Adresse de la console d'administration Keycloak, déduite de l'« issuer » (aucune adresse en dur). */
export function keycloakConsoleUrl(issuer: string): string | null {
  const match = /^(https?:\/\/[^/]+)(?:\/.*)?\/realms\/([^/]+)\/?$/.exec(issuer.trim());
  return match ? `${match[1]}/admin/master/console/#/${match[2]}` : null;
}

export interface AdminTool {
  key: 'keycloak' | 'thresholds' | 'scenarios' | 'grafana';
  icon: string;
  label: string;
  description: string;
  /** Vrai si l'outil n'existe pas encore (carte « Bientôt disponible »). */
  soon: boolean;
}

export const ADMIN_TOOLS: AdminTool[] = [
  { key: 'keycloak', icon: 'manage_accounts', label: 'Utilisateurs et rôles', description: 'Console Keycloak : comptes, rôles, sessions.', soon: false },
  { key: 'thresholds', icon: 'tune', label: 'Seuils par zone', description: 'Seuils de détection de chaque zone (FR-13).', soon: true },
  { key: 'scenarios', icon: 'science', label: 'Scénarios de simulation', description: 'Panneau des scénarios de panne (FR-06).', soon: true },
  { key: 'grafana', icon: 'monitoring', label: 'Grafana', description: 'Tableaux de bord techniques (CPU, mémoire, latence).', soon: true },
];
