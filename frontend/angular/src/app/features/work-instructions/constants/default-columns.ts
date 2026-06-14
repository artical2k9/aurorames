import { ColumnDef } from '../../../shared/grid';

export const DEFAULT_WORK_INSTRUCTION_COLUMNS: ColumnDef[] = [
  { key: 'identifier',     label: 'Identifier', visible: true,  order: 0 },
  { key: 'title',          label: 'Title',      visible: true,  order: 1 },
  { key: 'revision',       label: 'Revision',   visible: true,  order: 2 },
  { key: 'revisionStatus', label: 'Status',     visible: true,  order: 3 },
];
