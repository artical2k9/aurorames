import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { RouteListComponent } from './route-list.component';
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';
import { RouteDto } from '../../models/routing.model';

const MOCK_ROUTE: RouteDto = {
  id: 'route-1',
  partId: 'item-1',
  partRevision: 'A',
  routeTypeId: 'rt-std',
  revision: 1,
  status: 'DRAFT',
  reasonForRevision: 'Initial',
  bomRevisionSuperseded: false,
  inspectionPlanRevisionSuperseded: false,
  customFields: { line: 'Cell-A' },
};

describe('RouteListComponent', () => {
  let fixture: ComponentFixture<RouteListComponent>;
  let component: RouteListComponent;

  const mockApi = {
    list: vi.fn().mockReturnValue(of({ content: [MOCK_ROUTE], totalElements: 1 })),
    create: vi.fn().mockReturnValue(of({ ...MOCK_ROUTE } as unknown)),
    startRouteRevision: vi.fn().mockReturnValue(of({ ...MOCK_ROUTE })),
  };
  const mockRefApi = {
    listRouteTypes: vi.fn().mockReturnValue(of([
      { id: 'rt-std', code: 'STANDARD', name: 'Standard', isStandard: true, active: true },
    ])),
  };
  const mockUdfApi = {
    listFields: vi.fn().mockReturnValue(of([{ fieldKey: 'line', label: 'Line', fieldType: 'TEXT' }])),
  };
  const MOCK_ITEM = {
    id: 'item-9', partNumber: 'PN-009', revision: 2, revisionStatus: 'APPROVED',
  } as unknown as ItemMasterDto;
  const mockItemApi = {
    list: vi.fn().mockReturnValue(of({ content: [MOCK_ITEM], totalElements: 1 })),
    getById: vi.fn().mockReturnValue(of({ ...MOCK_ITEM, partNumber: 'PN-001' })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [RouteListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: RoutingApiService, useValue: mockApi },
        { provide: ReferenceDataApiService, useValue: mockRefApi },
        { provide: UdfApiService, useValue: mockUdfApi },
        { provide: ItemMasterApiService, useValue: mockItemApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RouteListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads UDF columns for the ROUTE module and active route types on init', () => {
    expect(mockUdfApi.listFields).toHaveBeenCalledWith('ROUTE');
    expect(mockRefApi.listRouteTypes).toHaveBeenCalled();
    expect(component.routeTypes).toHaveLength(1);
  });

  it('fetches routes and exposes rows', async () => {
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    expect(mockApi.list).toHaveBeenCalled();
    expect(component.rows).toHaveLength(1);
    expect(component.totalRecords).toBe(1);
    expect(component.loading).toBe(false);
  });

  it('getCellValue resolves routeTypeCode from the loaded map', () => {
    const value = component.getCellValue(MOCK_ROUTE, {
      key: 'routeTypeCode', label: 'Route Type', visible: true, order: 1,
    });
    expect(value).toBe('STANDARD');
  });

  it('getCellValue falls back to customFields for UDF columns', () => {
    const value = component.getCellValue(MOCK_ROUTE, {
      key: 'line', label: 'Line', visible: true, order: 5, udf: true,
    });
    expect(value).toBe('Cell-A');
  });

  it('maps route statuses to labels and severities', () => {
    expect(component.statusLabel('APPROVED')).toBe('Approved');
    expect(component.statusLabel('REJECTED')).toBe('Rejected');
    expect(component.statusSeverity('PENDING_APPROVAL')).toBe('warn');
    expect(component.statusSeverity('REJECTED')).toBe('danger');
  });

  it('canSave requires part, revision, route type and reason', () => {
    component.draft = {};
    expect(component.canSave()).toBe(false);
    component.draft = { partId: 'p', partRevision: 'A', routeTypeId: 'rt-std', reasonForRevision: 'x' };
    expect(component.canSave()).toBe(true);
  });

  it('onItemSelect captures partId and defaults the revision', () => {
    component.draft = {};
    component.onItemSelect({ value: MOCK_ITEM } as never);
    expect(component.draft.partId).toBe('item-9');
    expect(component.draft.partRevision).toBe('2');
  });

  it('clears rows and stops loading on fetch error', () => {
    mockApi.list.mockReturnValueOnce(throwError(() => new Error('boom')));
    component['fetchRows']();
    expect(component.rows).toHaveLength(0);
    expect(component.loading).toBe(false);
  });

  it('resolves partId to partNumber for the part-number column', async () => {
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    expect(mockItemApi.getById).toHaveBeenCalledWith('item-1');
    const value = component.getCellValue(MOCK_ROUTE, {
      key: 'partNumber', label: 'Part Number', visible: true, order: 0,
    });
    expect(value).toBe('PN-001');
  });

  it('createRevision starts a revision and navigates to detail', () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const approved = { ...MOCK_ROUTE, status: 'APPROVED' } as RouteDto;
    component.createRevision(approved);
    expect(mockApi.startRouteRevision).toHaveBeenCalledWith('route-1');
  });
});
