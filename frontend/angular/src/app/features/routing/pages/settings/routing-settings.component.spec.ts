import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { RoutingSettingsComponent } from './routing-settings.component';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';

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
    createWorkCentre: vi.fn().mockReturnValue(of({ id: 'wc-1', code: 'WC', name: 'Cell', active: true })),
    createRouteType: vi.fn().mockReturnValue(of({ id: 'rt', code: 'NPI', name: 'NPI', isStandard: false, active: true })),
    createSignificantProcessType: vi.fn().mockReturnValue(
      of({ id: 'sp', code: 'WELD', name: 'Weld', requiredApproverRole: 'WELD_SME', active: true })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [RoutingSettingsComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ReferenceDataApiService, useValue: mockRefApi },
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

  it('adds a work centre via the simple section with active=true', () => {
    const section = component.simpleSections.find(s => s.key === 'work-centres')!;
    section.draft.code = 'WC-1';
    section.draft.name = 'Mill';
    section.add();
    expect(mockRefApi.createWorkCentre).toHaveBeenCalledWith({ code: 'WC-1', name: 'Mill', active: true });
  });

  it('adds a route type with the isStandard flag', () => {
    component.routeTypeDraft = { code: 'NPI', name: 'NPI Route', isStandard: true };
    component.addRouteType();
    expect(mockRefApi.createRouteType).toHaveBeenCalledWith(
      { code: 'NPI', name: 'NPI Route', isStandard: true, active: true });
  });

  it('adds a significant-process type with the required approver role', () => {
    component.sigDraft = { code: 'WELD', name: 'Welding', requiredApproverRole: 'WELD_SME' };
    component.addSignificantProcessType();
    expect(mockRefApi.createSignificantProcessType).toHaveBeenCalledWith(
      { code: 'WELD', name: 'Welding', requiredApproverRole: 'WELD_SME', active: true });
  });
});
