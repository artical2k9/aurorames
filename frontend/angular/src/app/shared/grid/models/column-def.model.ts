export interface ColumnDef {
  key: string;
  label: string;
  visible: boolean;
  order: number;
  locked?: boolean;
  udf?: boolean;
}
