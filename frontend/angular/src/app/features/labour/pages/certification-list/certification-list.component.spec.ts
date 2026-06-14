import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { CertificationListComponent } from './certification-list.component';
import { LabourApiService } from '../../services/labour-api.service';
import { CertificationDto } from '../../models/labour.model';

const MOCK_CERT: CertificationDto = {
  id: 'cert-1',
  employeeId: 'emp-1',
  employeeNumber: 'E-001',
  skillId: 'skill-1',
  skillCode: 'IPC-610',
  skillName: 'IPC-A-610 Soldering',
  awardDate: '2026-01-01',
  expiryDate: '2026-07-01',
  state: 'EXPIRING_SOON',
  revoked: false,
};

describe('CertificationListComponent', () => {
  let fixture: ComponentFixture<CertificationListComponent>;
  let component: CertificationListComponent;

  const mockApi = {
    listCertifications: vi.fn().mockReturnValue(of({ content: [MOCK_CERT], totalElements: 1 })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CertificationListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: LabourApiService, useValue: mockApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CertificationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('fetches certifications after view init', async () => {
    await new Promise(resolve => setTimeout(resolve));
    expect(mockApi.listCertifications).toHaveBeenCalledWith({ expiringWithinDays: undefined });
    expect(component.rows).toHaveLength(1);
  });

  it('passes the expiry window filter to the API on reload', async () => {
    await new Promise(resolve => setTimeout(resolve));
    component.expiringWithinDays = 60;
    component.reload();
    expect(mockApi.listCertifications).toHaveBeenLastCalledWith({ expiringWithinDays: 60 });
  });

  it('maps certification states to badge labels and severities', () => {
    expect(component.stateLabel('ACTIVE')).toBe('Active');
    expect(component.stateLabel('EXPIRING_SOON')).toBe('Expiring Soon');
    expect(component.stateLabel('EXPIRED')).toBe('Expired');
    expect(component.stateLabel('REVOKED')).toBe('Revoked');
    expect(component.stateSeverity('ACTIVE')).toBe('success');
    expect(component.stateSeverity('EXPIRING_SOON')).toBe('warn');
    expect(component.stateSeverity('EXPIRED')).toBe('danger');
    expect(component.stateSeverity('REVOKED')).toBe('secondary');
  });
});
