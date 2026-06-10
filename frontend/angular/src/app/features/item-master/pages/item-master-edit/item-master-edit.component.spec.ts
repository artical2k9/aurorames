import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ItemMasterEditComponent } from './item-master-edit.component';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { PlatformApiService } from '../../../../shared/platform';
import { BreadcrumbService } from '../../../../shared/ui';

const MOCK_ITEM = {
  id: 'abc-123',
  revisionId: 'rev-1',
  orgId: 'org-1',
  partNumber: 'PN-001',
  revision: 0,
  revisionStatus: 'APPROVED' as const,
  hasDraft: false,
  description: 'Test part',
  unitOfMeasure: 'EA',
  classification: 'ASSEMBLY' as const,
  makeBuyCode: 'EITHER' as const,
  traceabilityMethod: 'SERIAL' as const,
  shelfLifeControlled: false,
  verificationRequired: true,
  stepPartRef: 'S000-BRKT-001',
  createdBy: 'user',
  createdAt: '2026-01-01T00:00:00Z',
  modifiedBy: 'user',
  modifiedAt: '2026-01-01T00:00:00Z',
};

describe('ItemMasterEditComponent', () => {
  let fixture: ComponentFixture<ItemMasterEditComponent>;
  let component: ItemMasterEditComponent;

  const mockApi = {
    getById: vi.fn().mockReturnValue(of(MOCK_ITEM)),
    patch: vi.fn().mockReturnValue(of(MOCK_ITEM)),
    submit: vi.fn().mockReturnValue(of({ ...MOCK_ITEM, revisionStatus: 'PENDING_APPROVAL' as const })),
    cancelDraft: vi.fn().mockReturnValue(of(undefined)),
  };

  const mockUdf      = { listFields: vi.fn().mockReturnValue(of([])) };
  const mockPlatform = { listUom: vi.fn().mockReturnValue(of([])) };
  const mockBreadcrumb = { set: vi.fn() };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ItemMasterEditComponent],
      providers: [
        provideRouter([]),
        { provide: ItemMasterApiService, useValue: mockApi },
        { provide: UdfApiService, useValue: mockUdf },
        { provide: PlatformApiService, useValue: mockPlatform },
        { provide: BreadcrumbService, useValue: mockBreadcrumb },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'abc-123' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ItemMasterEditComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('loads item on init and populates form', () => {
    fixture.detectChanges();
    expect(component.item?.partNumber).toBe('PN-001');
    expect(component.form.value.description).toBe('Test part');
    expect(component.form.value.unitOfMeasure).toBe('EA');
  });

  it('sets makeActive and buyActive from EITHER', () => {
    fixture.detectChanges();
    expect(component.makeActive).toBe(true);
    expect(component.buyActive).toBe(true);
    expect(component.derivedMakeBuyCode).toBe('EITHER');
  });

  it('sets breadcrumbs including partNumber after item loads', () => {
    fixture.detectChanges();
    expect(mockBreadcrumb.set).toHaveBeenCalledWith(
      expect.arrayContaining([
        expect.objectContaining({ label: expect.stringContaining('PN-001') }),
      ]),
    );
  });

  it('canSave() returns true when form valid', () => {
    fixture.detectChanges();
    expect(component.canSave()).toBe(true);
  });

  it('cancel() navigates to detail page', () => {
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const spy = vi.spyOn(router, 'navigate');
    component.cancel();
    expect(spy).toHaveBeenCalledWith(['/item-master', 'abc-123']);
  });
});
