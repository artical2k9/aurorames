import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { RoutingSettingsComponent } from './routing-settings.component';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { IamApiService } from '../../../settings/services/iam-api.service';

describe('RoutingSettingsComponent', () => {
  let fixture: ComponentFixture<RoutingSettingsComponent>;
  let component: RoutingSettingsComponent;

  const mockRefApi = {
    listWorkCentres: vi.fn().mockReturnValue(of([])),
    listLabourCodes: vi.fn().mockReturnValue(of([])),
    listLabourPlanTypes: vi.fn().mockReturnValue(of([])),
    listSuppliers: vi.fn().mockReturnValue(of([])),
    listRouteTypes: vi.fn().mockReturnValue(of([])),
    listSignificantProcessTypes: vi.fn().mockReturnValue(of([])),
    createRef: vi.fn().mockReturnValue(of({})),
    updateRef: vi.fn().mockReturnValue(of({})),
    deleteRef: vi.fn().mockReturnValue(of(undefined)),
  };

  const mockIamApi = {
    listRoles: vi.fn().mockReturnValue(of([{ id: 'r1', name: 'WELD_SME' }, { id: 'r2', name: 'QA_LEAD' }])),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [RoutingSettingsComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ReferenceDataApiService, useValue: mockRefApi },
        { provide: IamApiService, useValue: mockIamApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RoutingSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads all six reference-data lists on init', () => {
    expect(mockRefApi.listWorkCentres).toHaveBeenCalled();
    expect(mockRefApi.listLabourCodes).toHaveBeenCalled();
    expect(mockRefApi.listLabourPlanTypes).toHaveBeenCalled();
    expect(mockRefApi.listSuppliers).toHaveBeenCalled();
    expect(mockRefApi.listRouteTypes).toHaveBeenCalled();
    expect(mockRefApi.listSignificantProcessTypes).toHaveBeenCalled();
  });

  it('binds the required-approver-role options to the IAM role catalogue', () => {
    expect(mockIamApi.listRoles).toHaveBeenCalled();
    const roleField = component.sigFields.find(f => f.key === 'requiredApproverRole')!;
    expect(roleField.type).toBe('select');
    expect(roleField.options).toEqual([
      { label: 'WELD_SME', value: 'WELD_SME' },
      { label: 'QA_LEAD', value: 'QA_LEAD' },
    ]);
  });

  it('reload(resource) refetches only that list', () => {
    mockRefApi.listRouteTypes.mockClear();
    component.reload('route-types');
    expect(mockRefApi.listRouteTypes).toHaveBeenCalledTimes(1);
  });
});
