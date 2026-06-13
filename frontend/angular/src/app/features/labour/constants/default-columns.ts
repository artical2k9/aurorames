import { ColumnDef } from '../../../shared/grid';

export const DEFAULT_EMPLOYEE_COLUMNS: ColumnDef[] = [
  { key: 'employeeNumber',   label: 'Employee #',  visible: true,  order: 0 },
  { key: 'firstName',        label: 'First Name',  visible: true,  order: 1 },
  { key: 'lastName',         label: 'Last Name',   visible: true,  order: 2 },
  { key: 'email',            label: 'Email',       visible: true,  order: 3 },
  { key: 'employmentStatus', label: 'Status',      visible: true,  order: 4 },
  { key: 'hireDate',         label: 'Hire Date',   visible: false, order: 5 },
  { key: 'iamUserId',        label: 'IAM User',    visible: false, order: 6 },
];
