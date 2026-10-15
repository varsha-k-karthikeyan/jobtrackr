/**
 * The backend's own PageResponse record, not Spring Data's Page: a small, stable shape.
 * `page` is zero-based, matching MatPaginator's pageIndex.
 */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
