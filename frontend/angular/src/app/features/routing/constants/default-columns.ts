import { ColumnDef } from '../../../shared/grid';

export const DEFAULT_ROUTE_COLUMNS: ColumnDef[] = [
  { key: 'partNumber',    label: 'Part Number', visible: true, order: 0 },
  { key: 'partRevision',  label: 'Part Rev',  visible: true,  order: 1 },
  { key: 'routeTypeCode', label: 'Route Type', visible: true, order: 2 },
  { key: 'revision',      label: 'Revision',  visible: true,  order: 3 },
  { key: 'status',        label: 'Status',    visible: true,  order: 4 },
  { key: 'reasonForRevision', label: 'Reason', visible: false, order: 5 },
];
