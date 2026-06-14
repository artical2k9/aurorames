import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { WorkInstructionListComponent } from './work-instruction-list.component';
import { WorkInstructionApiService } from '../../services/work-instruction-api.service';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { WorkInstructionSummaryDto } from '../../models/work-instruction.model';

const MOCK_WI: WorkInstructionSummaryDto = {
  id: 'wi-1',
  identifier: 'WI-001',
  revisionId: 'rev-1',
  revision: 0,
  revisionStatus: 'DRAFT',
  title: 'Deburr bracket',
  hasDraft: true,
  customFields: { line: 'Cell-A' },
};

describe('WorkInstructionListComponent', () => {
  let fixture: ComponentFixture<WorkInstructionListComponent>;
  let component: WorkInstructionListComponent;

  const mockApi = {
    list: vi.fn().mockReturnValue(of({ content: [MOCK_WI], totalElements: 1 })),
    create: vi.fn().mockReturnValue(of({ ...MOCK_WI } as unknown)),
    suggestIdentifier: vi.fn().mockReturnValue(of('WI-002')),
  };

  const mockUdfApi = {
    listFields: vi.fn().mockReturnValue(of([
      { fieldKey: 'line', label: 'Line', fieldType: 'TEXT' },
    ])),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [WorkInstructionListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: WorkInstructionApiService, useValue: mockApi },
        { provide: UdfApiService, useValue: mockUdfApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(WorkInstructionListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads UDF columns for the WORK_INSTRUCTION module on init', () => {
    expect(mockUdfApi.listFields).toHaveBeenCalledWith('WORK_INSTRUCTION');
  });

  it('fetches work instructions and exposes rows', async () => {
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    expect(mockApi.list).toHaveBeenCalled();
    expect(component.rows).toHaveLength(1);
    expect(component.totalRecords).toBe(1);
    expect(component.loading).toBe(false);
  });

  it('getCellValue reads direct properties for standard columns', () => {
    const value = component.getCellValue(MOCK_WI, {
      key: 'identifier', label: 'Identifier', visible: true, order: 0,
    });
    expect(value).toBe('WI-001');
  });

  it('getCellValue falls back to customFields for UDF columns', () => {
    const value = component.getCellValue(MOCK_WI, {
      key: 'line', label: 'Line', visible: true, order: 4, udf: true,
    });
    expect(value).toBe('Cell-A');
  });

  it('maps revision statuses to labels and severities', () => {
    expect(component.statusLabel('APPROVED')).toBe('Approved');
    expect(component.statusLabel('PENDING_APPROVAL')).toBe('Pending');
    expect(component.statusLabel('DRAFT')).toBe('Draft');
    expect(component.statusSeverity('APPROVED')).toBe('success');
    expect(component.statusSeverity('PENDING_APPROVAL')).toBe('warn');
    expect(component.statusSeverity('DRAFT')).toBe('secondary');
  });

  it('canSave requires identifier and title', () => {
    component.draft = {};
    expect(component.canSave()).toBe(false);
    component.draft = { identifier: 'WI-009', title: 'x' };
    expect(component.canSave()).toBe(true);
  });

  it('prefills a suggested identifier when opening the create dialog', () => {
    component.openCreate();
    expect(mockApi.suggestIdentifier).toHaveBeenCalled();
    expect(component.draft.identifier).toBe('WI-002');
  });

  it('clears rows and stops loading on fetch error', () => {
    mockApi.list.mockReturnValueOnce(throwError(() => new Error('boom')));
    component['fetchRows']();
    expect(component.rows).toHaveLength(0);
    expect(component.loading).toBe(false);
  });
});
