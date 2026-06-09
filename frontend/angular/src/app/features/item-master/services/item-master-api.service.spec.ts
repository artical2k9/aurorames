import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ItemMasterApiService } from './item-master-api.service';
import { ItemMasterDto } from '../models/item-master.model';

const MOCK_DTO: ItemMasterDto = {
  id: 'item-1',
  revisionId: 'rev-1',
  orgId: 'org-1',
  partNumber: 'PN-001',
  revision: 0,
  revisionStatus: 'DRAFT',
  hasDraft: true,
  description: 'Widget',
  unitOfMeasure: 'EA',
  classification: 'ASSEMBLY',
  makeBuyCode: 'MAKE',
  traceabilityMethod: 'SERIAL',
  shelfLifeControlled: false,
  verificationRequired: false,
  createdBy: 'user',
  createdAt: '2026-01-01T00:00:00Z',
  modifiedBy: 'user',
  modifiedAt: '2026-01-01T00:00:00Z',
};

describe('ItemMasterApiService — workflow methods', () => {
  let service: ItemMasterApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ItemMasterApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ItemMasterApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('submit(id) calls POST /api/v1/item-master/:id/submit and returns ItemMasterDto', () => {
    let result: ItemMasterDto | undefined;
    service.submit('item-1').subscribe(dto => result = dto);
    const req = http.expectOne('/api/v1/item-master/item-1/submit');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_DTO, revisionStatus: 'PENDING_APPROVAL' });
    expect(result?.revisionStatus).toBe('PENDING_APPROVAL');
  });

  it('approve(id) calls POST /api/v1/item-master/:id/approve and returns ItemMasterDto', () => {
    let result: ItemMasterDto | undefined;
    service.approve('item-1').subscribe(dto => result = dto);
    const req = http.expectOne('/api/v1/item-master/item-1/approve');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_DTO, revisionStatus: 'APPROVED' });
    expect(result?.revisionStatus).toBe('APPROVED');
  });

  it('cancelDraft(id) calls DELETE /api/v1/item-master/:id/draft', () => {
    let completed = false;
    service.cancelDraft('item-1').subscribe({ complete: () => completed = true });
    const req = http.expectOne('/api/v1/item-master/item-1/draft');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(completed).toBe(true);
  });
});
