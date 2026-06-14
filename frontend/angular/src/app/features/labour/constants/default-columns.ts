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

export const DEFAULT_SKILL_COLUMNS: ColumnDef[] = [
  { key: 'skillCode',             label: 'Code',              visible: true,  order: 0 },
  { key: 'name',                  label: 'Name',              visible: true,  order: 1 },
  { key: 'category',              label: 'Category',          visible: true,  order: 2 },
  { key: 'certificationRequired', label: 'Cert Required',     visible: true,  order: 3 },
  { key: 'validityMonths',        label: 'Validity (months)', visible: true,  order: 4 },
  { key: 'active',                label: 'Active',            visible: true,  order: 5 },
];

export const DEFAULT_CERTIFICATION_COLUMNS: ColumnDef[] = [
  { key: 'employeeNumber', label: 'Employee #',  visible: true, order: 0 },
  { key: 'skillCode',      label: 'Skill',       visible: true, order: 1 },
  { key: 'skillName',      label: 'Name',        visible: true, order: 2 },
  { key: 'state',          label: 'State',       visible: true, order: 3 },
  { key: 'awardDate',      label: 'Award Date',  visible: true, order: 4 },
  { key: 'expiryDate',     label: 'Expiry',      visible: true, order: 5 },
];
