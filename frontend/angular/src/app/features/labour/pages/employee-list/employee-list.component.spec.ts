import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { EmployeeListComponent } from './employee-list.component';
import { LabourApiService } from '../../services/labour-api.service';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { EmployeeDto } from '../../models/labour.model';

const MOCK_EMPLOYEE: EmployeeDto = {
  id: 'emp-1',
  employeeNumber: 'E-001',
  firstName: 'Ana',
  lastName: 'Reyes',
  email: 'ana@test.org',
  employmentStatus: 'ACTIVE',
  customFields: { shift: 'Night' },
};

describe('EmployeeListComponent', () => {
  let fixture: ComponentFixture<EmployeeListComponent>;
  let component: EmployeeListComponent;

  const mockApi = {
    listEmployees: vi.fn().mockReturnValue(of({ content: [MOCK_EMPLOYEE], totalElements: 1 })),
    createEmployee: vi.fn().mockReturnValue(of(MOCK_EMPLOYEE)),
  };

  const mockUdfApi = {
    listFields: vi.fn().mockReturnValue(of([
      { fieldKey: 'shift', label: 'Shift', fieldType: 'TEXT' },
    ])),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [EmployeeListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: LabourApiService, useValue: mockApi },
        { provide: UdfApiService, useValue: mockUdfApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EmployeeListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads UDF columns for the EMPLOYEE module on init', () => {
    expect(mockUdfApi.listFields).toHaveBeenCalledWith('EMPLOYEE');
  });

  it('fetches employees and exposes rows', async () => {
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    expect(mockApi.listEmployees).toHaveBeenCalled();
    expect(component.rows).toHaveLength(1);
    expect(component.totalRecords).toBe(1);
    expect(component.loading).toBe(false);
  });

  it('getCellValue reads direct properties for standard columns', () => {
    const value = component.getCellValue(MOCK_EMPLOYEE, {
      key: 'employeeNumber', label: 'Employee #', visible: true, order: 0,
    });
    expect(value).toBe('E-001');
  });

  it('getCellValue falls back to customFields for UDF columns', () => {
    const value = component.getCellValue(MOCK_EMPLOYEE, {
      key: 'shift', label: 'Shift', visible: true, order: 7, udf: true,
    });
    expect(value).toBe('Night');
  });

  it('clears rows and stops loading on fetch error', async () => {
    mockApi.listEmployees.mockReturnValueOnce(throwError(() => new Error('boom')));
    component.reload();
    expect(component.rows).toHaveLength(0);
    expect(component.loading).toBe(false);
  });

  it('search change resets to page 0', async () => {
    component.currentPage = 3;
    component.searchTerm = 'Ana';
    component.onSearchChange();
    await new Promise(resolve => setTimeout(resolve, 450));
    expect(component.currentPage).toBe(0);
  });
});
