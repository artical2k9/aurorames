import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { BomApiService } from './bom-api.service';
import { BomDto, RevisionStatus } from '../models/bom.model';

const MOCK_BOM: BomDto = {
  id: 'bom-1',
  orgId: 'org-1',
  parentItemId: 'item-1',
  bomRevisionId: 'bom-rev-1',
  revision: 0,
  revisionStatus: 'DRAFT' as RevisionStatus,
  hasDraft: false,
  createdBy: 'user',
  createdAt: '2026-01-01T00:00:00Z',
};

describe('BomApiService — workflow methods', () => {
  let service: BomApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BomApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('submitBom() sends POST /api/v1/boms/:id/submit', () => {
    const submitted = { ...MOCK_BOM, revisionStatus: 'PENDING_APPROVAL' as RevisionStatus };
    service.submitBom('bom-1').subscribe(res => expect(res.revisionStatus).toBe('PENDING_APPROVAL'));
    const req = httpMock.expectOne('/api/v1/boms/bom-1/submit');
    expect(req.request.method).toBe('POST');
    req.flush(submitted);
  });

  it('approveBom() sends POST /api/v1/boms/:id/approve', () => {
    const approved = { ...MOCK_BOM, revisionStatus: 'APPROVED' as RevisionStatus };
    service.approveBom('bom-1').subscribe(res => expect(res.revisionStatus).toBe('APPROVED'));
    const req = httpMock.expectOne('/api/v1/boms/bom-1/approve');
    expect(req.request.method).toBe('POST');
    req.flush(approved);
  });

  it('cancelBomDraft() sends DELETE /api/v1/boms/:id/draft', () => {
    service.cancelBomDraft('bom-1').subscribe();
    const req = httpMock.expectOne('/api/v1/boms/bom-1/draft');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
