import { ColumnDef } from '../../../shared/grid';

export const DEFAULT_BOM_LINE_COLUMNS: ColumnDef[] = [
  { key: 'seq',        label: 'Seq',        visible: true,  order: 0, locked: true  },
  { key: 'findNumber', label: 'Find #',     visible: true,  order: 1, locked: true  },
  { key: 'partNumber', label: 'Part Number',visible: true,  order: 2, locked: true  },
  { key: 'description',label: 'Description',visible: true,  order: 3, locked: true  },
  { key: 'revision',   label: 'Revision',   visible: true,  order: 4 },
  { key: 'quantity',   label: 'Qty',        visible: true,  order: 5 },
  { key: 'unitOfMeasure', label: 'Unit',    visible: true,  order: 6 },
  { key: 'effectiveFromDate', label: 'Eff From', visible: true, order: 7 },
  { key: 'effectiveToDate',   label: 'Eff To',   visible: true, order: 8 },
  { key: 'referenceDesignators', label: 'Ref Designators', visible: false, order: 9 },
];
