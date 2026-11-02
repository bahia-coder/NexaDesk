/** Pagination envelope of the API (backend: common.web.PageResponse). */
export interface Page<T> {
  content: T[];
  /** 0-based. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
