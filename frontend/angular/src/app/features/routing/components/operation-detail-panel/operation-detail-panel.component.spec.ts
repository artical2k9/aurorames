import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OperationDetailPanelComponent } from './operation-detail-panel.component';
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { OperationDto } from '../../models/routing.model';

const OP: OperationDto = {
  id: 'op-1', operationNumber: 10, sequenceNumber: 10, derivedType: 'NORMAL',
  optional: false, osp: false, clocking: true, operationRevision: 1, operationStatus: 'DRAFT',
};

describe('OperationDetailPanelComponent', () => {
  let fixture: ComponentFixture<OperationDetailPanelComponent>;
  let component: OperationDetailPanelComponent;

  const api = {
    listResources: vi.fn().mockReturnValue(of([])),
    listLabourPlan: vi.fn().mockReturnValue(of([])),
    listMaterials: vi.fn().mockReturnValue(of([])),
    listTooling: vi.fn().mockReturnValue(of([])),
    listSkills: vi.fn().mockReturnValue(of([])),
    listQualityVariables: vi.fn().mockReturnValue(of([])),
    listWorkInstructions: vi.fn().mockReturnValue(of([])),
    listStepFiles: vi.fn().mockReturnValue(of([])),
    patchOperation: vi.fn().mockReturnValue(of({ ...OP, optional: true })),
    addResource: vi.fn().mockReturnValue(of({ id: 'r1', workCentreId: 'wc-1' })),
    addLabourPlan: vi.fn().mockReturnValue(of({})),
  };
  const refApi = {
    listWorkCentres: vi.fn().mockReturnValue(of([{ id: 'wc-1', code: 'WC', name: 'Cell A', active: true }])),
    listLabourCodes: vi.fn().mockReturnValue(of([])),
    listLabourPlanTypes: vi.fn().mockReturnValue(of([])),
    listSignificantProcessTypes: vi.fn().mockReturnValue(of([])),
    listSuppliers: vi.fn().mockReturnValue(of([])),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [OperationDetailPanelComponent],
      providers: [
        provideNoopAnimations(),
        { provide: RoutingApiService, useValue: api },
        { provide: ReferenceDataApiService, useValue: refApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OperationDetailPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('routeId', 'route-1');
    fixture.componentRef.setInput('operation', OP);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
  });

  it('loads reference data and the operation detail on init', () => {
    expect(refApi.listWorkCentres).toHaveBeenCalled();
    expect(api.listResources).toHaveBeenCalledWith('route-1', 'op-1');
    expect(component.workCentres).toHaveLength(1);
  });

  it('select changes the active tab', () => {
    component.select('Resources');
    expect(component.active).toBe('Resources');
  });

  it('patchOp sends the partial operation update', () => {
    component.patchOp({ optional: true });
    expect(api.patchOperation).toHaveBeenCalledWith('route-1', 'op-1', { optional: true });
  });

  it('addResource posts the selected work centre then clears the draft', () => {
    component.resourceDraft = { workCentreId: 'wc-1' };
    component.addResource();
    expect(api.addResource).toHaveBeenCalledWith('route-1', 'op-1', { workCentreId: 'wc-1' });
    expect(component.resourceDraft.workCentreId).toBeUndefined();
  });

  it('workCentreName resolves the loaded reference name', () => {
    expect(component.workCentreName('wc-1')).toBe('Cell A');
    expect(component.workCentreName('unknown')).toBe('unknown');
  });
});
