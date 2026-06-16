import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OperationGridEditorComponent } from './operation-grid-editor.component';
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { OperationDto } from '../../models/routing.model';

function op(partial: Partial<OperationDto>): OperationDto {
  return {
    id: 'op', operationNumber: 10, sequenceNumber: 10, derivedType: 'NORMAL',
    optional: false, osp: false, clocking: true, operationRevision: 1, operationStatus: 'DRAFT',
    ...partial,
  };
}

describe('OperationGridEditorComponent', () => {
  let fixture: ComponentFixture<OperationGridEditorComponent>;
  let component: OperationGridEditorComponent;

  const mockApi = {
    listOperations: vi.fn().mockReturnValue(of([])),
    listGroups: vi.fn().mockReturnValue(of([])),
    listMutuallyExclusiveSets: vi.fn().mockReturnValue(of([])),
    listSteps: vi.fn().mockReturnValue(of([])),
    putMutuallyExclusiveSets: vi.fn().mockReturnValue(of([])),
    addOperation: vi.fn().mockReturnValue(of({})),
  };
  const mockRefApi = {
    listSignificantProcessTypes: vi.fn().mockReturnValue(of([])),
    listSuppliers: vi.fn().mockReturnValue(of([])),
    listWorkCentres: vi.fn().mockReturnValue(of([])),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [OperationGridEditorComponent],
      providers: [
        provideNoopAnimations(),
        { provide: RoutingApiService, useValue: mockApi },
        { provide: ReferenceDataApiService, useValue: mockRefApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OperationGridEditorComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('routeId', 'route-1');
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
  });

  it('derives Normal/Parallel and overlays Optional/OSP/ME badges', () => {
    component['meOperationIds'] = new Set(['op-me']);
    const normal = component.typeBadges(op({ id: 'op-n' })).map(b => b.label);
    expect(normal).toEqual(['Normal']);

    const parallelOptional = component.typeBadges(
      op({ id: 'op-p', derivedType: 'PARALLEL', optional: true })).map(b => b.label);
    expect(parallelOptional).toEqual(['Parallel', 'Optional']);

    const me = component.typeBadges(op({ id: 'op-me', osp: true })).map(b => b.label);
    expect(me).toEqual(['Normal', 'OSP', 'Mutually Exclusive']);
  });

  it('parallelSequences returns only sequence numbers shared by 2+ operations', () => {
    component.operations = [
      op({ id: 'a', sequenceNumber: 10 }),
      op({ id: 'b', sequenceNumber: 20 }),
      op({ id: 'c', sequenceNumber: 20 }),
      op({ id: 'd', sequenceNumber: 30 }),
      op({ id: 'e', sequenceNumber: 30 }),
    ];
    expect(component.parallelSequences()).toEqual([20, 30]);
    expect(component.operationsInSequence(20).map(o => o.id)).toEqual(['b', 'c']);
  });

  it('toggleMeMember adds and removes members without duplicates', () => {
    component.toggleMeMember('x', true);
    component.toggleMeMember('x', true);
    component.toggleMeMember('y', true);
    expect(component.meMembers).toEqual(['x', 'y']);
    component.toggleMeMember('x', false);
    expect(component.meMembers).toEqual(['y']);
  });

  it('saveMutuallyExclusive sends an OPERATION-level set and preserves other levels', () => {
    component.meSets = [
      { id: 's1', level: 'STEP', sequenceNumber: 10, memberIds: ['s-a', 's-b'] },
    ];
    component.meMembers = ['op-1', 'op-2'];
    component.saveMutuallyExclusive();
    expect(mockApi.putMutuallyExclusiveSets).toHaveBeenCalledWith('route-1', [
      { level: 'STEP', memberIds: ['s-a', 's-b'] },
      { level: 'OPERATION', memberIds: ['op-1', 'op-2'] },
    ]);
  });

  it('canAddOp requires operation and sequence numbers', () => {
    component.opDraft = {};
    expect(component.canAddOp()).toBe(false);
    component.opDraft = { operationNumber: 10, sequenceNumber: 10 };
    expect(component.canAddOp()).toBe(true);
  });

  it('toggleGroupOp adds/removes operation ids on a group draft', () => {
    const draft = { operationIds: [] as string[] };
    component.toggleGroupOp(draft, 'op-1', true);
    component.toggleGroupOp(draft, 'op-1', true);
    expect(draft.operationIds).toEqual(['op-1']);
    component.toggleGroupOp(draft, 'op-1', false);
    expect(draft.operationIds).toEqual([]);
  });

  it('filteredOps sorts by sequence then operation number and filters by query', () => {
    component.operations = [
      op({ id: 'b', operationNumber: 20, sequenceNumber: 20, description: 'Weld' }),
      op({ id: 'a', operationNumber: 10, sequenceNumber: 10, description: 'Mill' }),
    ];
    expect(component.filteredOps().map(o => o.id)).toEqual(['a', 'b']);
    component.opSearch = 'weld';
    expect(component.filteredOps().map(o => o.id)).toEqual(['b']);
  });

  it('startAdd opens the inline row with the next op and sequence numbers', () => {
    component.operations = [op({ id: 'a', operationNumber: 10, sequenceNumber: 10 })];
    component.startAdd();
    expect(component.addingOp).toBe(true);
    expect(component.opDraft.operationNumber).toBe(20);
    expect(component.opDraft.sequenceNumber).toBe(20);
  });

  it('duplicate copies the operation header onto a new operation number', () => {
    component.operations = [op({ id: 'a', operationNumber: 10, sequenceNumber: 10, optional: true })];
    component.duplicate(component.operations[0]);
    expect(mockApi.addOperation).toHaveBeenCalledWith('route-1', expect.objectContaining({
      operationNumber: 20, sequenceNumber: 10, optional: true,
    }));
  });

  it('selectOp toggles the selected operation', () => {
    const o = op({ id: 'a' });
    component.selectOp(o);
    expect(component.selectedOp?.id).toBe('a');
    component.selectOp(o);
    expect(component.selectedOp).toBeNull();
  });
});
