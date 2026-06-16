import { ColumnDef } from '../../../shared/grid';

export const DEFAULT_ROUTE_COLUMNS: ColumnDef[] = [
  { key: 'partRevision',  label: 'Part Rev',  visible: true,  order: 0 },
  { key: 'routeTypeCode', label: 'Route Type', visible: true, order: 1 },
  { key: 'revision',      label: 'Revision',  visible: true,  order: 2 },
  { key: 'status',        label: 'Status',    visible: true,  order: 3 },
  { key: 'reasonForRevision', label: 'Reason', visible: false, order: 4 },
];
