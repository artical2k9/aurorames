import { ColumnDef } from '../../../shared/grid';

export const DEFAULT_INSPECTION_PLAN_COLUMNS: ColumnDef[] = [
  { key: 'partNumber',     label: 'Part Number', visible: true,  order: 0 },
  { key: 'name',           label: 'Plan Name',   visible: true,  order: 1 },
  { key: 'revision',       label: 'Revision',    visible: true,  order: 2 },
  { key: 'revisionStatus', label: 'Status',      visible: true,  order: 3 },
  { key: 'createdBy',      label: 'Created By',  visible: false, order: 4 },
  { key: 'createdAt',      label: 'Created At',  visible: false, order: 5 },
];
