export interface Page<T> {
  content: T[];
  totalElements: number;
}

export type EmploymentStatus = 'ACTIVE' | 'INACTIVE';

export interface EmployeeDto {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  email?: string;
  employmentStatus: EmploymentStatus;
  hireDate?: string;
  iamUserId?: string;
  customFields?: Record<string, unknown>;
  createdBy?: string;
  createdAt?: string;
  modifiedBy?: string;
  modifiedAt?: string;
}

export interface SkillDto {
  id: string;
  skillCode: string;
  name: string;
  description?: string;
  category?: string;
  certificationRequired: boolean;
  validityMonths?: number;
  active: boolean;
  customFields?: Record<string, unknown>;
}

export type CertificationState = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'REVOKED';

export interface TrainingHistoryEntryDto {
  trainingEventId: string;
  title: string;
  trainingDate: string;
  durationMinutes?: number;
  trainer?: string;
  outcome: string;
  skillIds: string[];
}

export interface CertificationDto {
  id: string;
  employeeId: string;
  employeeNumber: string;
  skillId: string;
  skillCode: string;
  skillName: string;
  awardDate: string;
  expiryDate?: string;
  assessor?: string;
  evidenceRef?: string;
  state: CertificationState;
  revoked: boolean;
  revokedBy?: string;
  revokedAt?: string;
  revocationReason?: string;
  customFields?: Record<string, unknown>;
  supportingTraining?: TrainingHistoryEntryDto[];
}

export interface EmployeeProfileDto {
  employee: EmployeeDto;
  certifications: CertificationDto[];
}
