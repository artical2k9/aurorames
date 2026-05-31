import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ItemMasterCreateComponent } from './item-master-create.component';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import { UdfApiService } from '../../services/udf-api.service';

const MOCK_ITEM = {
  id: 'abc-123',
  orgId: 'org-1',
  partNumber: 'PN-001',
  revision: 'A',
  description: 'Test part',
  unitOfMeasure: 'EA',
  classification: 'ASSEMBLY' as const,
  makeBuyCode: 'MAKE' as const,
  traceabilityMethod: 'SERIAL' as const,
  status: 'ACTIVE' as const,
  shelfLifeControlled: false,
  verificationRequired: false,
  createdBy: 'user',
  createdAt: '2026-01-01T00:00:00Z',
  modifiedBy: 'user',
  modifiedAt: '2026-01-01T00:00:00Z',
};

const CREATED_ITEM = { ...MOCK_ITEM, id: 'new-456' };

describe('ItemMasterCreateComponent', () => {
  let fixture: ComponentFixture<ItemMasterCreateComponent>;
  let component: ItemMasterCreateComponent;

  const mockApi = {
    getById: vi.fn().mockReturnValue(of(MOCK_ITEM)),
    create: vi.fn().mockReturnValue(of(CREATED_ITEM)),
  };

  const mockUdf = {
    listFields: vi.fn().mockReturnValue(of([])),
  };

  function setupComponent(queryParams: Record<string, string> = {}) {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: {
        snapshot: {
          queryParamMap: { get: (key: string) => queryParams[key] ?? null },
        },
      },
    });
    fixture = TestBed.createComponent(ItemMasterCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ItemMasterCreateComponent],
      providers: [
        provideRouter([]),
        { provide: ItemMasterApiService, useValue: mockApi },
        { provide: UdfApiService, useValue: mockUdf },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: { get: () => null },
            },
          },
        },
      ],
    }).compileComponents();
  });

  it('should create', () => {
    setupComponent();
    expect(component).toBeTruthy();
  });

  it('canSave() returns false when form is empty', () => {
    setupComponent();
    expect(component.canSave()).toBe(false);
  });

  it('canSave() returns false when form valid but no Make/Buy selected', () => {
    setupComponent();
    component.form.patchValue({
      partNumber: 'PN-001', revision: 'A', description: 'Desc',
      unitOfMeasure: 'EA', classification: 'ASSEMBLY', traceabilityMethod: 'SERIAL',
    });
    expect(component.canSave()).toBe(false);
  });

  it('canSave() returns true when form valid and Make selected', () => {
    setupComponent();
    component.form.patchValue({
      partNumber: 'PN-001', revision: 'A', description: 'Desc',
      unitOfMeasure: 'EA', classification: 'ASSEMBLY', traceabilityMethod: 'SERIAL',
    });
    component.toggleMake();
    expect(component.canSave()).toBe(true);
  });

  it('toggleMake/toggleBuy derive correct MakeBuyCode', () => {
    setupComponent();
    component.toggleMake();
    expect(component.derivedMakeBuyCode).toBe('MAKE');
    component.toggleBuy();
    expect(component.derivedMakeBuyCode).toBe('EITHER');
    component.toggleMake();
    expect(component.derivedMakeBuyCode).toBe('BUY');
  });

  it('cancel() navigates to /item-master', () => {
    setupComponent();
    const router = TestBed.inject(Router);
    const spy = vi.spyOn(router, 'navigate');
    component.cancel();
    expect(spy).toHaveBeenCalledWith(['/item-master']);
  });

  it('pre-fills form on cloneFrom query param (excluding partNumber/revision)', () => {
    setupComponent({ cloneFrom: 'abc-123' });
    expect(component.form.value.description).toBe('Test part');
    expect(component.form.value.partNumber).toBe('');
    expect(component.form.value.revision).toBe('');
    expect(component.makeActive).toBe(true);
    expect(component.buyActive).toBe(false);
  });
});
