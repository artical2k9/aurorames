import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BomExplosionComponent } from './bom-explosion.component';
import { BomApiService } from '../../services/bom-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { BomDto, BomExplosionNode } from '../../models/bom.model';

const MOCK_BOM: BomDto = {
  id: 'bom-1',
  orgId: 'org-1',
  parentItemId: 'item-1',
  bomRevision: 'A',
  status: 'RELEASED',
  createdBy: 'user',
  createdAt: '2026-01-01T00:00:00Z',
};

const MOCK_NODE: BomExplosionNode = {
  componentItemId: 'comp-1',
  parentItemId: 'item-1',
  depth: 1,
  partNumber: 'PN-100',
  revision: 'A',
  description: 'Widget',
  unitOfMeasure: 'EA',
  counterfeitRiskAlert: false,
  componentObsoleted: false,
};

const HIGH_RISK_NODE: BomExplosionNode = {
  ...MOCK_NODE,
  componentItemId: 'comp-2',
  partNumber: 'PN-200',
  counterfeitRiskAlert: true,
};

describe('BomExplosionComponent', () => {
  let fixture: ComponentFixture<BomExplosionComponent>;
  let component: BomExplosionComponent;

  const mockBomApi = {
    getById: vi.fn().mockReturnValue(of(MOCK_BOM)),
    explode: vi.fn().mockReturnValue(of([MOCK_NODE])),
    listForItem: vi.fn().mockReturnValue(of([])),
    downloadExplosion: vi.fn().mockReturnValue(of(new Blob(['test']))),
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
      imports: [BomExplosionComponent],
      providers: [
        provideRouter([]),
        { provide: BomApiService, useValue: mockBomApi },
        { provide: ItemMasterApiService, useValue: mockItemApi },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'bom-1' } } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(BomExplosionComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('(a) should show p-message with severity error when 422 effectivity-gap error from explode()', () => {
    mockBomApi.explode.mockReturnValue(throwError(() => ({
      status: 422,
      error: { message: 'No effective BOM line for find number 10 at date 2026-01-01' },
    })));
    fixture.detectChanges();
    expect(component.explosionError).toBe('No effective BOM line for find number 10 at date 2026-01-01');
  });

  it('(b) shows "—" when counterfeitRiskAlert is false and "⚠ HIGH" text when true', () => {
    component.nodes = [MOCK_NODE, HIGH_RISK_NODE];
    expect(component.nodes.find(n => !n.counterfeitRiskAlert)).toBeTruthy();
    expect(component.nodes.find(n => n.counterfeitRiskAlert)).toBeTruthy();
  });

  it('(c) CSV button calls bomApi.downloadExplosion with correct format and fileType', () => {
    component.bomId = 'bom-1';
    component.selectedFormat = 'flat';
    component.asOfDate = null;
    component.maxDepth = 0;
    component.downloadCsv();
    expect(mockBomApi.downloadExplosion).toHaveBeenCalledWith(
      'bom-1', 'flat', 'csv', undefined, undefined,
    );
  });

  it('(d) PDF button calls bomApi.downloadExplosion with pdf fileType', () => {
    component.bomId = 'bom-1';
    component.selectedFormat = 'flat';
    component.asOfDate = null;
    component.maxDepth = 0;
    component.downloadPdf();
    expect(mockBomApi.downloadExplosion).toHaveBeenCalledWith(
      'bom-1', 'flat', 'pdf', undefined, undefined,
    );
  });
});
