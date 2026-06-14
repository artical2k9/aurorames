import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { InspectionPlanListComponent } from './inspection-plan-list.component';
import { InspectionPlanApiService } from '../../services/inspection-plan-api.service';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { InspectionPlanSummaryDto } from '../../models/inspection-plan.model';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';

const MOCK_PLAN: InspectionPlanSummaryDto = {
  id: 'plan-1',
  itemId: 'item-1',
  partNumber: 'PN-001',
  revisionId: 'rev-1',
  revision: 0,
  revisionStatus: 'DRAFT',
  name: 'Bracket plan',
  hasDraft: true,
  customFields: { line: 'Cell-A' },
};

describe('InspectionPlanListComponent', () => {
  let fixture: ComponentFixture<InspectionPlanListComponent>;
  let component: InspectionPlanListComponent;

  const mockApi = {
    listPlans: vi.fn().mockReturnValue(of({ content: [MOCK_PLAN], totalElements: 1 })),
    createPlan: vi.fn().mockReturnValue(of({ ...MOCK_PLAN } as unknown)),
  };

  const mockUdfApi = {
    listFields: vi.fn().mockReturnValue(of([
      { fieldKey: 'line', label: 'Line', fieldType: 'TEXT' },
    ])),
  };

  const MOCK_ITEM = {
    id: 'item-9', revisionId: 'irev-1', partNumber: 'PN-009', revision: 2,
    revisionStatus: 'APPROVED', description: 'Widget',
  } as unknown as ItemMasterDto;

  const mockItemApi = {
    list: vi.fn().mockReturnValue(of({ content: [MOCK_ITEM], totalElements: 1 })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [InspectionPlanListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: InspectionPlanApiService, useValue: mockApi },
        { provide: UdfApiService, useValue: mockUdfApi },
        { provide: ItemMasterApiService, useValue: mockItemApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InspectionPlanListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads UDF columns for the INSPECTION_PLAN module on init', () => {
    expect(mockUdfApi.listFields).toHaveBeenCalledWith('INSPECTION_PLAN');
  });

  it('fetches plans and exposes rows', async () => {
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    expect(mockApi.listPlans).toHaveBeenCalled();
    expect(component.rows).toHaveLength(1);
    expect(component.totalRecords).toBe(1);
    expect(component.loading).toBe(false);
  });

  it('getCellValue reads direct properties for standard columns', () => {
    const value = component.getCellValue(MOCK_PLAN, {
      key: 'partNumber', label: 'Part Number', visible: true, order: 0,
    });
    expect(value).toBe('PN-001');
  });

  it('getCellValue falls back to customFields for UDF columns', () => {
    const value = component.getCellValue(MOCK_PLAN, {
      key: 'line', label: 'Line', visible: true, order: 6, udf: true,
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

  it('canSave requires itemId and name', () => {
    component.draft = {};
    expect(component.canSave()).toBe(false);
    component.draft = { itemId: 'i', name: 'n' };
    expect(component.canSave()).toBe(true);
  });

  it('onItemSelect resolves part number to itemId and shows an informational label', () => {
    component.onItemSelect({ value: MOCK_ITEM } as never);
    expect(component.draft.itemId).toBe('item-9');
    expect(component.selectedItemLabel).toBe('PN-009 (Rev 2)');
  });

  it('onSearchItem invalidates any prior selection and queries approved items', async () => {
    component.draft.itemId = 'stale';
    component.selectedItemLabel = 'stale label';
    component.onSearchItem({ query: 'PN' });
    expect(component.draft.itemId).toBeUndefined();
    expect(component.selectedItemLabel).toBe('');
    await new Promise(resolve => setTimeout(resolve, 350));
    expect(mockItemApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ search: 'PN', revisionStatus: 'APPROVED' }));
    expect(component.itemSuggestions).toHaveLength(1);
  });

  it('clears rows and stops loading on fetch error', () => {
    mockApi.listPlans.mockReturnValueOnce(throwError(() => new Error('boom')));
    component['fetchRows']();
    expect(component.rows).toHaveLength(0);
    expect(component.loading).toBe(false);
  });
});
