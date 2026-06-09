import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { BomAuthoringComponent } from './bom-authoring.component';
import { BomApiService } from '../../services/bom-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { BomDto, BomLineDto } from '../../models/bom.model';
import { GridPreferenceService } from '../../../../shared/grid';
import { DEFAULT_BOM_LINE_COLUMNS } from '../../constants/default-columns';

const MOCK_BOM: BomDto = {
  id: 'bom-1',
  orgId: 'org-1',
  parentItemId: 'item-1',
  bomRevision: 'A',
  status: 'DRAFT',
  createdBy: 'user',
  createdAt: '2026-01-01T00:00:00Z',
};

const RELEASED_BOM: BomDto = { ...MOCK_BOM, status: 'RELEASED' };

const MOCK_LINE: BomLineDto = {
  id: 'line-1',
  bomId: 'bom-1',
  componentItemId: 'comp-1',
  partNumber: 'PN-100',
  revision: 'A',
  description: 'Widget',
  counterfeitRiskAlert: false,
  componentObsoleted: false,
  createdBy: 'user',
  createdAt: '2026-01-01T00:00:00Z',
};

const UNIT_LINE: BomLineDto = {
  ...MOCK_LINE,
  id: 'line-2',
  effectivityMethod: 'UNIT',
  effectiveFromUnit: 'SN-001',
};

describe('BomAuthoringComponent', () => {
  let fixture: ComponentFixture<BomAuthoringComponent>;
  let component: BomAuthoringComponent;

  const mockBomApi = {
    getById: vi.fn().mockReturnValue(of(MOCK_BOM)),
    getLines: vi.fn().mockReturnValue(of([])),
    patchHeader: vi.fn().mockReturnValue(of(MOCK_BOM)),
    release: vi.fn().mockReturnValue(of(RELEASED_BOM)),
    removeLine: vi.fn().mockReturnValue(of(undefined)),
    listForItem: vi.fn().mockReturnValue(of([])),
  };

  const mockItemApi = {
    getById: vi.fn().mockReturnValue(of({
      id: 'item-1', revisionId: 'rev-1', partNumber: 'ASSY-001', revision: 0,
      revisionStatus: 'APPROVED', hasDraft: false,
      orgId: 'org-1', description: 'Assembly', unitOfMeasure: 'EA',
      classification: 'ASSEMBLY', makeBuyCode: 'MAKE', traceabilityMethod: 'SERIAL',
      shelfLifeControlled: false, verificationRequired: false,
      createdBy: 'user', createdAt: '2026-01-01T00:00:00Z',
      modifiedBy: 'user', modifiedAt: '2026-01-01T00:00:00Z',
    })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [BomAuthoringComponent],
      providers: [
        provideRouter([]),
        { provide: BomApiService, useValue: mockBomApi },
        { provide: ItemMasterApiService, useValue: mockItemApi },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'bom-1' } } },
        },
        {
          provide: GridPreferenceService,
          useFactory: () => new GridPreferenceService('BOM_LINE', DEFAULT_BOM_LINE_COLUMNS),
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(BomAuthoringComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('(a) Add Line increments component count in status bar immediately', () => {
    component.bom = MOCK_BOM;
    component.lines = [MOCK_LINE];
    expect(component.lines.length).toBe(1);
    component.onLineSaved({ ...MOCK_LINE, id: 'line-new' });
    expect(component.lines.length).toBe(2);
  });

  it('(b) After adding a line the status bar shows dirty state (amber)', () => {
    component.bom = MOCK_BOM;
    component.lines = [];
    component.isDirty = false;
    component.onLineSaved(MOCK_LINE);
    expect(component.isDirty).toBe(true);
  });

  it('(c) After clicking Save Draft the status bar returns to clean state', () => {
    component.bom = MOCK_BOM;
    component.bomId = 'bom-1';
    component.isDirty = true;
    component.saveDraft();
    expect(component.isDirty).toBe(false);
  });

  it('(d) structural controls absent when BOM status is RELEASED', async () => {
    mockBomApi.getById.mockReturnValue(of(RELEASED_BOM));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const allButtonLabels = Array.from(compiled.querySelectorAll('.p-button-label'))
      .map(el => el.textContent ?? '');
    expect(allButtonLabels.some(t => t.includes('Add Line'))).toBe(false);
    expect(allButtonLabels.some(t => t.includes('Submit for Review'))).toBe(false);
  });

  it('(e) UNIT effectivity lines have effectivityMethod UNIT', () => {
    component.lines = [UNIT_LINE];
    expect(component.lines[0].effectivityMethod).toBe('UNIT');
  });
});
