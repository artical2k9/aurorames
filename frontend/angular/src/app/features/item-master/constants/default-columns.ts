import { ColumnDef } from '../../../shared/grid';

export const DEFAULT_ITEM_MASTER_COLUMNS: ColumnDef[] = [
  { key: 'partNumber',    label: 'Part Number',     visible: true,  order: 0, locked: true  },
  { key: 'revision',      label: 'Revision',        visible: true,  order: 1, locked: true  },
  { key: 'description',   label: 'Description',     visible: true,  order: 2, locked: true  },
  { key: 'classification', label: 'Classification', visible: true,  order: 3 },
  { key: 'makeBuyCode',   label: 'Make/Buy',        visible: true,  order: 4 },
  { key: 'unitOfMeasure', label: 'Unit of Measure', visible: true,  order: 5 },
  { key: 'status',        label: 'Status',          visible: true,  order: 6 },
  { key: 'cageCode',      label: 'CAGE Code',       visible: false, order: 7 },
  { key: 'shelfLifeDays', label: 'Shelf Life Days', visible: false, order: 8 },
];
