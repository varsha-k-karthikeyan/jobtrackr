/** GET /stats (StatsResponse). */
export interface Stats {
  total: number;
  applied: number;
  interview: number;
  offer: number;
  rejected: number;
  /** (interview + offer + rejected) / total, between 0 and 1; 0 when total is 0. */
  responseRate: number;
}
